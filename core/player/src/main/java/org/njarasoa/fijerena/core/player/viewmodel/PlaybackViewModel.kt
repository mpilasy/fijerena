package org.njarasoa.fijerena.core.player.viewmodel
import android.app.Application
import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.model.AudioTrackInfo
import org.njarasoa.fijerena.core.player.model.ChapterInfo
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo
import org.njarasoa.fijerena.core.player.model.VideoQualityInfo
import org.njarasoa.fijerena.core.player.service.PlaybackServiceConnection
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

class PlaybackViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val context = application
    private val serviceConnection = PlaybackServiceConnection(context)

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMetadata = MutableStateFlow(PlayerMetadata())
    val currentMetadata: StateFlow<PlayerMetadata> = _currentMetadata.asStateFlow()

    private val _rebufferCount = MutableStateFlow(0)
    val rebufferCount: StateFlow<Int> = _rebufferCount.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    // Audio enhancement state
    private val _nightModeEnabled = MutableStateFlow(false)
    val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private var isInErrorState = false
    private var focusLostJob: Job? = null

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
            }

            override fun onPlayerError(error: PlaybackException) {
                isInErrorState = true
                // Service handles the specific error message and propagates it via flow
                // (observeServiceState collects service.playbackState).
                // Only set a fallback here if the service isn't reachable.
                if (StreamingPlaybackService.getInstance() == null) {
                    _playbackState.value = PlaybackState.Error("Playback error occurred", error)
                }
            }

            private fun updatePlaybackState() {
                // Don't overwrite error state
                if (isInErrorState) return

                val controller = _controller.value ?: return
                val state =
                    when (controller.playbackState) {
                        Player.STATE_IDLE -> PlaybackState.Idle
                        Player.STATE_BUFFERING -> PlaybackState.Buffering
                        Player.STATE_READY -> {
                            if (controller.playWhenReady) {
                                PlaybackState.Playing(
                                    position = controller.currentPosition,
                                    duration = controller.duration.coerceAtLeast(0L),
                                )
                            } else {
                                PlaybackState.Paused(
                                    position = controller.currentPosition,
                                    duration = controller.duration.coerceAtLeast(0L),
                                )
                            }
                        }
                        Player.STATE_ENDED -> PlaybackState.Ended
                        else -> PlaybackState.Idle
                    }
                _playbackState.value = state
            }
        }

    init {
        // Load persisted audio enhancement settings
        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        _nightModeEnabled.value = prefs.getBoolean("night_mode_enabled", false)

        viewModelScope.launch {
            startService()
            connectToService()
            observeServiceState()
        }
    }

    private suspend fun observeServiceState() {
        val service = StreamingPlaybackService.awaitInstance()

        viewModelScope.launch {
            service.playbackState.collect { state ->
                _playbackState.value = state
            }
        }

        viewModelScope.launch {
            service.currentMetadata.collect { metadata ->
                _currentMetadata.value = metadata
            }
        }
    }

    private fun startService() {
        val intent = Intent(context, StreamingPlaybackService::class.java)
        context.startService(intent)
    }

    private suspend fun connectToService() {
        serviceConnection.connect().collect { controller ->
            // Remove listener from old controller
            _controller.value?.removeListener(playerListener)

            // Add listener to new controller
            controller?.addListener(playerListener)

            _controller.value = controller
        }
    }

    fun playStream(
        metadata: PlayerMetadata,
        resumeFromPosition: Long = 0L,
    ) {
        // Reset error state on new stream
        isInErrorState = false
        onFocusRegained()
        _currentMetadata.value = metadata
        _playbackState.value = PlaybackState.Buffering

        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance()
            service?.playStream(metadata, resumeFromPosition)
        }
    }

    fun pause() {
        viewModelScope.launch {
            StreamingPlaybackService.getInstance()?.pause()
        }
    }

    fun resume() {
        onFocusRegained()
        viewModelScope.launch {
            StreamingPlaybackService.getInstance()?.resume()
        }
    }

    fun stop() {
        // Reset error state when user goes back
        isInErrorState = false
        onFocusRegained()
        viewModelScope.launch {
            StreamingPlaybackService.getInstance()?.stop()
        }
    }

    /**
     * Called when the app loses focus (e.g. backgrounded).
     * Pauses playback and starts a 30s timer to stop playback completely.
     */
    fun onFocusLost() {
        val currentState = _playbackState.value
        if (currentState is PlaybackState.Playing || currentState is PlaybackState.Buffering) {
            pause()
        }

        // Always start/reset the stop timer if we were at least in a non-idle state
        if (currentState !is PlaybackState.Idle && currentState !is PlaybackState.Ended) {
            focusLostJob?.cancel()
            focusLostJob =
                viewModelScope.launch {
                    delay(30_000L)
                    stop()
                }
        }
    }

    /**
     * Called when the app regains focus.
     * Cancels the pending stop timer.
     */
    fun onFocusRegained() {
        focusLostJob?.cancel()
        focusLostJob = null
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            StreamingPlaybackService.getInstance()?.seekTo(position)
        }
    }

    fun seekRelative(offsetMs: Long) {
        val state = _playbackState.value
        val currentPos =
            when (state) {
                is PlaybackState.Playing -> state.position
                is PlaybackState.Paused -> state.position
                PlaybackState.Idle,
                PlaybackState.Buffering,
                PlaybackState.Ended,
                is PlaybackState.Error,
                -> return
            }
        val duration =
            when (state) {
                is PlaybackState.Playing -> state.duration
                is PlaybackState.Paused -> state.duration
                PlaybackState.Idle,
                PlaybackState.Buffering,
                PlaybackState.Ended,
                is PlaybackState.Error,
                -> return
            }
        seekTo((currentPos + offsetMs).coerceIn(0L, duration))
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            StreamingPlaybackService.getInstance()?.setPlaybackSpeed(speed)
        }
    }

    /**
     * Get available audio tracks from the player.
     * Returns a list of audio track info (language, label, track group index, track index).
     */
    fun getAudioTracks(): List<AudioTrackInfo> {
        val controller = _controller.value ?: return emptyList()
        val tracks = controller.currentTracks
        val audioTracks = mutableListOf<AudioTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)

                    audioTracks.add(
                        AudioTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language ?: "Unknown",
                            label = format.label ?: "${format.language ?: "Track"} - ${format.channelCount}ch",
                            channelCount = format.channelCount,
                            sampleRate = format.sampleRate,
                            bitrate = format.bitrate,
                            isSelected = isSelected,
                        ),
                    )
                }
            }
        }

        return audioTracks
    }

    /**
     * Select an audio track by group and track index.
     */
    fun selectAudioTrack(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance()
            service?.selectAudioTrack(groupIndex, trackIndex)
        }
    }

    /**
     * Get available subtitle tracks from the player.
     * Returns a list of subtitle track info (language, label, mime type, selection status).
     */
    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val controller = _controller.value ?: return emptyList()
        val tracks = controller.currentTracks
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)

                    subtitleTracks.add(
                        SubtitleTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language ?: "Unknown",
                            label = format.label ?: format.language ?: "Subtitle ${trackIndex + 1}",
                            mimeType = format.sampleMimeType ?: "unknown",
                            isSelected = isSelected,
                        ),
                    )
                }
            }
        }

        return subtitleTracks
    }

    /**
     * Select a subtitle track by group and track index.
     */
    fun selectSubtitleTrack(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance()
            service?.selectSubtitleTrack(groupIndex, trackIndex)
        }
    }

    /**
     * Disable all subtitle tracks.
     */
    fun disableSubtitles() {
        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance()
            service?.disableSubtitles()
        }
    }

    /**
     * Get available video quality levels from the player.
     * Returns a list sorted by resolution (highest first).
     */
    fun getVideoQualities(): List<VideoQualityInfo> {
        val controller = _controller.value ?: return emptyList()
        val tracks = controller.currentTracks
        val qualities = mutableListOf<VideoQualityInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)

                    val resolutionLabel =
                        when {
                            format.height >= 2160 -> "4K"
                            format.height >= 1440 -> "1440p"
                            format.height >= 1080 -> "1080p"
                            format.height >= 720 -> "720p"
                            format.height >= 480 -> "480p"
                            else -> "${format.height}p"
                        }

                    val bitrateLabel =
                        if (format.bitrate > 0) {
                            String.format("%.1f Mbps", format.bitrate / 1_000_000f)
                        } else {
                            "Unknown"
                        }

                    qualities.add(
                        VideoQualityInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            width = format.width,
                            height = format.height,
                            bitrate = format.bitrate,
                            frameRate = format.frameRate,
                            label = "$resolutionLabel ($bitrateLabel)",
                            isSelected = isSelected,
                        ),
                    )
                }
            }
        }

        // Sort by resolution (highest first)
        return qualities.sortedByDescending { it.height }
    }

    /**
     * Get chapter markers from the current media item's metadata.
     * Jellyfin stores chapters in mediaMetadata.extras as parallel arrays.
     */
    fun getChapters(): List<ChapterInfo> {
        val controller = _controller.value ?: return emptyList()
        val extras = controller.mediaMetadata.extras ?: return emptyList()

        val titles = extras.getStringArrayList("chapterTitles") ?: return emptyList()
        val startTimesMs = extras.getLongArray("chapterStartTimesMs") ?: return emptyList()

        if (titles.size != startTimesMs.size) return emptyList()

        val duration = controller.duration.coerceAtLeast(0L)
        return titles.mapIndexed { index, title ->
            val startMs = startTimesMs[index]
            val endMs = if (index + 1 < startTimesMs.size) startTimesMs[index + 1] else duration
            ChapterInfo(
                title = title.ifEmpty { "Chapter ${index + 1}" },
                startTimeMs = startMs,
                endTimeMs = endMs,
            )
        }
    }

    /**
     * Select a specific video quality.
     */
    fun selectVideoQuality(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance()
            service?.selectVideoQuality(groupIndex, trackIndex)
        }
    }

    /**
     * Enable automatic quality selection (adaptive bitrate).
     */
    fun enableAutoQuality() {
        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance()
            service?.enableAutoQuality()
        }
    }

    /**
     * Toggle Night Mode on/off. Persists setting and immediately applies to the audio session.
     */
    fun setNightMode(enabled: Boolean) {
        _nightModeEnabled.value = enabled
        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit { putBoolean("night_mode_enabled", enabled) }

        viewModelScope.launch {
            val service = StreamingPlaybackService.getInstance() ?: return@launch
            service.nightModeManager.enabled = enabled
            service.nightModeProcessor.enabled = enabled
        }
    }

    override fun onCleared() {
        super.onCleared()
        _controller.value?.removeListener(playerListener)
        serviceConnection.disconnect()
        viewModelScope.launch {
            _controller.value?.stop()
            _controller.value?.release()
        }
    }
}
