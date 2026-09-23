package org.njarasoa.fijerena.core.player.viewmodel
import android.app.Application
import android.content.Intent
import org.njarasoa.fijerena.core.player.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
import org.njarasoa.fijerena.core.player.service.ServiceDestroyedException
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

    // Bumped on every onTracksChanged — getAudioTracks()/getSubtitleTracks()/getVideoQualities()
    // are plain synchronous reads of controller.currentTracks with nothing to observe on their
    // own, so a caller that only recomposed on `controller` or `metadata` changing could freeze
    // on an empty list forever if it read tracks before ExoPlayer resolved them (metadata is set
    // once at playStream() time, well before tracks are typically ready). Callers that need to
    // stay current key their remember{} on this instead.
    private val _tracksVersion = MutableStateFlow(0)
    val tracksVersion: StateFlow<Int> = _tracksVersion.asStateFlow()

    private val _isInPictureInPictureMode = MutableStateFlow(false)
    val isInPictureInPictureMode: StateFlow<Boolean> = _isInPictureInPictureMode.asStateFlow()

    private var isInErrorState = false
    private var focusLostJob: Job? = null

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Rely on service.playbackState Flow for state updates
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                // Rely on service.playbackState Flow for state updates
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Rely on service.playbackState Flow for state updates
            }

            override fun onTracksChanged(tracks: Tracks) {
                _tracksVersion.value++
            }

            override fun onPlayerError(error: PlaybackException) {
                isInErrorState = true
                // Service handles the specific error message and propagates it via flow
                // (observeServiceState collects service.playbackState).
                // Only set a fallback here if the service isn't reachable.
                if (StreamingPlaybackService.getInstance() == null) {
                    _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred), error)
                }
            }
        }

    private var observeStateJob: Job? = null
    private var connectStateJob: Job? = null

    init {
        // connectToService() never returns (it collects a connection flow that stays open
        // for the life of the controller), so it must run in its own coroutine. Otherwise it
        // blocks observeServiceState() from ever starting, leaving playbackState stuck at Idle.
        startService()
        connectStateJob = viewModelScope.launch { connectToService() }
        observeStateJob = viewModelScope.launch { observeServiceState() }
    }

    /**
     * Re-subscribes to the service if it died since this ViewModel was created. It's common
     * for one PlaybackViewModel to outlive several playback sessions (e.g. TV's embedded
     * preview panel), but observeServiceState() only ever awaits and collects from a single
     * service instance — once that instance is torn down by stopAndRelease(), a *new* service
     * starting later is never subscribed to, leaving playbackState/currentMetadata frozen even
     * though playback is actually working again. Call before starting a fresh stream.
     */
    private fun ensureServiceRunning() {
        if (StreamingPlaybackService.getInstance() == null) {
            // The start-claim (StreamingPlaybackService.tryClaimStart()) is owned and reset by
            // the service itself, exactly when it actually tears down — not guessed here from
            // getInstance() == null, which is also true during Android's normal, harmless
            // startService() -> onCreate() gap (tens to hundreds of ms). A caller-side reset
            // keyed on that observation can't tell "starting" from "just died" apart, and two
            // ViewModels racing this path during the starting gap could each reset and reclaim,
            // both issuing a redundant startService() call. startService() below just attempts
            // the claim; if it's already held (by a start in flight or a live instance), it's a
            // no-op.
            startService()
            observeStateJob?.cancel()
            observeStateJob = viewModelScope.launch { observeServiceState() }
            // connectToService() rebinds _controller to a fresh MediaController for the new
            // service instance. Without this, callers reading _controller (getAudioTracks(),
            // getChapters()) keep whatever controller — possibly none — was bound to the service
            // that just died.
            connectStateJob?.cancel()
            connectStateJob = viewModelScope.launch { connectToService() }
        }
    }

    private suspend fun observeServiceState() =
        coroutineScope {
            // launch on this coroutineScope, not viewModelScope: that makes both collectors
            // structural children of observeStateJob, so ensureServiceRunning()'s
            // observeStateJob?.cancel() actually tears them down. Launching on viewModelScope
            // directly (as this used to) made them siblings of observeStateJob instead — the
            // outer suspend function returned as soon as both launches fired, so the job was
            // already completed by the time cancel() ran, and every service restart leaked two
            // more collectors bound to the old service's now-dead flows.
            val service =
                try {
                    StreamingPlaybackService.awaitInstance()
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // Service never came up in time — nothing to observe.
                    return@coroutineScope
                } catch (e: ServiceDestroyedException) {
                    // Service was torn down while we were waiting on it — nothing to observe.
                    return@coroutineScope
                }

            launch {
                service.playbackState.collect { state ->
                    android.util.Log.d("PlaybackViewModel", "observeServiceState: received state=$state")
                    _playbackState.value = state
                }
            }

            launch {
                service.currentMetadata.collect { metadata ->
                    _currentMetadata.value = metadata
                }
            }
        }

    private fun startService() {
        // tryClaimStart() ensures only the first caller across all PlaybackViewModel instances
        // (and across restarts — see its own doc) actually issues the (ANR-risky) startService()
        // call.
        if (StreamingPlaybackService.tryClaimStart()) {
            val intent = Intent(context, StreamingPlaybackService::class.java)
            context.startService(intent)
        }
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
        ensureServiceRunning()
        // DO NOT set _playbackState.value = Buffering here!
        // Let the service handle the state transitions.

        viewModelScope.launch {
            try {
                val service = StreamingPlaybackService.awaitInstance()
                _currentMetadata.value = metadata
                service.playStream(metadata, resumeFromPosition)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // awaitInstance() gave up after AWAIT_INSTANCE_TIMEOUT_MS — the service never
                // came up in time.
                _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred))
            } catch (e: ServiceDestroyedException) {
                // The service was torn down while we were waiting on it — a real failure to
                // surface, not the coroutine's own cancellation (see the exception's kdoc).
                _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // A genuine structured-concurrency cancellation (e.g. this ViewModel being
                // cleared) — propagate it rather than swallowing it into an error state nobody
                // will see.
                throw e
            }
        }
    }

    /**
     * Runs [action] against the running service once it's up, swallowing the failure if the
     * service never came up or was torn down while we were waiting on it (see [ServiceDestroyedException]'s
     * kdoc) — these are fire-and-forget control actions with nothing left to act on in that case,
     * not a failure worth surfacing as a player error. A genuine structured-concurrency
     * cancellation (e.g. this ViewModel being cleared) still propagates.
     */
    private fun launchServiceAction(action: suspend (StreamingPlaybackService) -> Unit) {
        viewModelScope.launch {
            try {
                action(StreamingPlaybackService.awaitInstance())
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // no-op: service never came up in time
            } catch (e: ServiceDestroyedException) {
                // no-op: service died mid-wait or mid-call
            }
        }
    }

    /** See [StreamingPlaybackService.updateMetadata]. */
    fun updateMetadata(
        streamUrl: String,
        update: (PlayerMetadata) -> PlayerMetadata,
    ) {
        launchServiceAction { it.updateMetadata(streamUrl, update) }
    }

    fun pause() {
        launchServiceAction { it.pause() }
    }

    fun resume() {
        onFocusRegained()
        launchServiceAction { it.resume() }
    }

    fun stop() {
        // Reset error state when user goes back
        isInErrorState = false
        onFocusRegained()
        launchServiceAction { it.stop() }
    }

    /** TV-only wrapper for [StreamingPlaybackService.stopAndRelease] — see its kdoc. */
    fun stopAndRelease() {
        isInErrorState = false
        onFocusRegained()
        // getInstance(), not awaitInstance(): there's nothing to release if the service is
        // already dead, and awaiting a service that will never start just pays the full
        // 10-second AWAIT_INSTANCE_TIMEOUT_MS for no reason. Neither this nor stopAndRelease()
        // itself suspends, so no coroutine is needed here at all.
        StreamingPlaybackService.getInstance()?.stopAndRelease()
    }

    /**
     * Called when the app loses focus (e.g. backgrounded).
     * Pauses playback and starts a 30s timer to stop playback completely.
     * @param isInPip Current Picture-in-Picture state of the activity.
     */
    fun onFocusLost(isInPip: Boolean = false) {
        if (isInPip || _isInPictureInPictureMode.value) return

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
        launchServiceAction { it.seekTo(position) }
    }

    fun seekRelative(offsetMs: Long) {
        // Only meaningful while actually playing/paused — do nothing on Idle/Buffering/Ended/Error.
        val isSeekableState =
            when (_playbackState.value) {
                is PlaybackState.Playing, is PlaybackState.Paused -> true
                else -> false
            }
        // Read the live player position/duration rather than the cached PlaybackState: that
        // StateFlow only updates on discrete Player.Listener events (state/playWhenReady/
        // isPlaying changes), so during steady playback it stays frozen at whatever it was
        // when last set — often minutes stale. Seeking relative to it jumped to the wrong
        // spot (the bug behind "fast forward/rewind fucked up").
        val player = if (isSeekableState) StreamingPlaybackService.getInstance()?.getPlayer() else null
        val duration = player?.duration?.coerceAtLeast(0L) ?: 0L
        if (player != null && duration > 0L) {
            seekTo((player.currentPosition + offsetMs).coerceIn(0L, duration))
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        launchServiceAction { it.setPlaybackSpeed(speed) }
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
                            language = format.language ?: context.getString(R.string.player_track_language_unknown),
                            label = format.label ?: context.getString(R.string.player_track_audio_fallback_label_format, format.language ?: context.getString(R.string.player_track_generic_label), format.channelCount),
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
        launchServiceAction { it.selectAudioTrack(groupIndex, trackIndex) }
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
                            language = format.language ?: context.getString(R.string.player_track_language_unknown),
                            label = format.label ?: format.language ?: context.getString(R.string.player_track_subtitle_fallback_label_format, trackIndex + 1),
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
        launchServiceAction { it.selectSubtitleTrack(groupIndex, trackIndex) }
    }

    /**
     * Disable all subtitle tracks.
     */
    fun disableSubtitles() {
        launchServiceAction { it.disableSubtitles() }
    }

    /**
     * Get available video quality levels from the player.
     * Returns a list sorted by resolution (highest first).
     */
    fun getVideoQualities(): List<VideoQualityInfo> {
        val qualities = mutableListOf<VideoQualityInfo>()
        val groups = _controller.value?.currentTracks?.groups.orEmpty()

        groups.forEachIndexed { groupIndex, group ->
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
        val controller = _controller.value
        val extras = controller?.mediaMetadata?.extras
        val titles = extras?.getStringArrayList("chapterTitles")
        val startTimesMs = extras?.getLongArray("chapterStartTimesMs")

        val chapters =
            if (controller == null || titles == null || startTimesMs == null || titles.size != startTimesMs.size) {
                emptyList()
            } else {
                val duration = controller.duration.coerceAtLeast(0L)
                titles.mapIndexed { index, title ->
                    val startMs = startTimesMs[index]
                    val endMs = if (index + 1 < startTimesMs.size) startTimesMs[index + 1] else duration
                    ChapterInfo(
                        title = title.ifEmpty { context.getString(R.string.player_chapter_fallback_format, index + 1) },
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                    )
                }
            }
        return chapters
    }

    /**
     * Select a specific video quality.
     */
    fun selectVideoQuality(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        launchServiceAction { it.selectVideoQuality(groupIndex, trackIndex) }
    }

    /**
     * Enable automatic quality selection (adaptive bitrate).
     */
    fun enableAutoQuality() {
        launchServiceAction { it.enableAutoQuality() }
    }

    fun updatePictureInPictureMode(inPip: Boolean) {
        _isInPictureInPictureMode.value = inPip
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled by the time onCleared() runs, so cleanup
        // must happen synchronously here rather than via viewModelScope.launch.
        _controller.value?.let {
            it.removeListener(playerListener)
            it.stop()
            it.release()
        }
        StreamingPlaybackService.getInstance()?.stop()
        serviceConnection.disconnect()
        // The start-claim itself is no longer this ViewModel's to clear — see
        // StreamingPlaybackService.tryClaimStart()'s doc. .stop() above doesn't tear the service
        // down (unlike stopAndRelease()), so the service may still be alive and its claim still
        // legitimately held after this ViewModel is cleared.
    }
}
