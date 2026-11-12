package org.njarasoa.fijerena.core.player.viewmodel
import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.R
import org.njarasoa.fijerena.core.player.api.XtreamStreamUrl
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.model.AudioTrackInfo
import org.njarasoa.fijerena.core.player.model.ChapterInfo
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo
import org.njarasoa.fijerena.core.player.model.VideoQualityInfo
import org.njarasoa.fijerena.core.player.model.audioTrackName
import org.njarasoa.fijerena.core.player.model.playingTitles
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

    // The stream this ViewModel last asked to play ([currentMetadata] follows the shared service,
    // whoever started it). Null until it plays one. See onCleared.
    private var ownStreamUrl: String? = null

    private val _rebufferCount = MutableStateFlow(0)
    val rebufferCount: StateFlow<Int> = _rebufferCount.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    // Bumped on every onTracksChanged. getAudioTracks() / getSubtitleTracks() / getVideoQualities()
    // are plain reads of controller.currentTracks with nothing to observe, and metadata is set at
    // playStream(), before tracks are ready. A caller that keys its remember{} only on `controller`
    // or `metadata` can freeze on an empty list; key it on this.
    private val _tracksVersion = MutableStateFlow(0)
    val tracksVersion: StateFlow<Int> = _tracksVersion.asStateFlow()

    // Where the last seek sent the player, until it is playing or paused there again (null: no
    // seek pending). The phone shows it at once on the seek bar, and seekRelative counts from it.
    private val _seekTarget = MutableStateFlow<Long?>(null)
    val seekTarget: StateFlow<Long?> = _seekTarget.asStateFlow()

    private val _isInPictureInPictureMode = MutableStateFlow(false)
    val isInPictureInPictureMode: StateFlow<Boolean> = _isInPictureInPictureMode.asStateFlow()

    private var isInErrorState = false
    private var focusLostJob: Job? = null

    // Set when onAppStopped() stopped a live stream, so onAppResumed() restarts it. Cleared by any
    // explicit playStream() / stop(), so a stale flag cannot restart a stream the user left.
    private var liveStoppedInBackground = false

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // service.playbackState carries the state.
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                // service.playbackState carries the state.
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // service.playbackState carries the state.
            }

            override fun onTracksChanged(tracks: Tracks) {
                _tracksVersion.value++
            }

            override fun onPlayerError(error: PlaybackException) {
                isInErrorState = true
                // The service sets the error message and publishes it through
                // service.playbackState. A fallback only when the service cannot be reached.
                if (StreamingPlaybackService.getInstance() == null) {
                    _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred), error)
                }
            }
        }

    private var observeStateJob: Job? = null
    private var connectStateJob: Job? = null

    init {
        // connectToService() never returns (it collects a connection flow open for the controller's
        // life), so it runs in its own coroutine. Else observeServiceState() never starts and
        // playbackState stays Idle.
        startService()
        connectStateJob = viewModelScope.launch { connectToService() }
        observeStateJob = viewModelScope.launch { observeServiceState() }
    }

    /**
     * Subscribes again if the service died since this ViewModel was created. One PlaybackViewModel
     * often outlives several playback sessions (for example TV's preview panel), but
     * observeServiceState() only collects from one service instance. After stopAndRelease(), a new
     * service is never subscribed to and playbackState / currentMetadata freeze. Call before a
     * fresh stream.
     */
    private fun ensureServiceRunning() {
        if (StreamingPlaybackService.getInstance() == null) {
            // The service owns and resets the start claim
            // (StreamingPlaybackService.tryClaimStart()) when it tears down. getInstance() == null
            // is also true in the normal startService() -> onCreate() gap, so a reset here could
            // make two ViewModels both call startService(). startService() below only tries the
            // claim; when it is held, it is a no-op.
            startService()
            observeStateJob?.cancel()
            observeStateJob = viewModelScope.launch { observeServiceState() }
            // connectToService() binds _controller to a fresh MediaController for the new service.
            // Else getAudioTracks() and getChapters() keep the dead service's controller (or none).
            connectStateJob?.cancel()
            connectStateJob = viewModelScope.launch { connectToService() }
        }
    }

    private suspend fun observeServiceState() =
        coroutineScope {
            // Launch on this coroutineScope, not viewModelScope: both collectors are then children
            // of observeStateJob, so ensureServiceRunning()'s observeStateJob?.cancel() stops them.
            // On viewModelScope they were siblings, the job was complete by the time cancel() ran,
            // and every service restart leaked two collectors on the dead service's flows.
            val service =
                try {
                    StreamingPlaybackService.awaitInstance()
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    // The service did not come up in time: nothing to observe.
                    return@coroutineScope
                } catch (e: ServiceDestroyedException) {
                    // The service was torn down while waiting: nothing to observe.
                    return@coroutineScope
                }

            launch {
                service.playbackState.collect { state ->
                    android.util.Log.d("PlaybackViewModel", "observeServiceState: received state=$state")
                    _playbackState.value = state
                    // Playing or paused again: the player is at the seek target. Any other state
                    // than Buffering ends the seek too.
                    if (state !is PlaybackState.Buffering) _seekTarget.value = null
                }
            }

            launch {
                service.currentMetadata.collect { metadata ->
                    _currentMetadata.value = metadata
                }
            }
        }

    private fun startService() {
        // tryClaimStart() lets only the first caller across all PlaybackViewModel instances (and
        // across restarts, see its doc) make the ANR-risky startService() call.
        if (StreamingPlaybackService.tryClaimStart()) {
            val intent = Intent(context, StreamingPlaybackService::class.java)
            context.startService(intent)
        }
    }

    private suspend fun connectToService() {
        serviceConnection.connect().collect { controller ->
            _controller.value?.removeListener(playerListener)

            controller?.addListener(playerListener)

            _controller.value = controller
        }
    }

    fun playStream(
        metadata: PlayerMetadata,
        resumeFromPosition: Long = 0L,
    ) {
        isInErrorState = false
        liveStoppedInBackground = false
        _seekTarget.value = null
        onFocusRegained()
        ensureServiceRunning()
        // Do NOT set _playbackState to Buffering here: the service handles the state transitions.

        viewModelScope.launch {
            try {
                val service = StreamingPlaybackService.awaitInstance()
                ownStreamUrl = metadata.streamUrl
                _currentMetadata.value = metadata
                service.playStream(metadata, resumeFromPosition)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // awaitInstance() gave up after AWAIT_INSTANCE_TIMEOUT_MS: the service did not come
                // up in time.
                _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred))
            } catch (e: ServiceDestroyedException) {
                // The service was torn down while waiting: a real failure to show, not this
                // coroutine's cancellation (see the exception's KDoc).
                _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Real cancellation (for example this ViewModel being cleared): propagate it, do
                // not turn it into an error state nobody sees.
                throw e
            }
        }
    }

    /**
     * Runs [action] against the service once it is up. Ignores a service that did not come up or
     * was torn down while waiting (see [ServiceDestroyedException]): these are fire-and-forget
     * control actions with nothing to act on then. Real cancellation still propagates.
     */
    private fun launchServiceAction(action: suspend (StreamingPlaybackService) -> Unit) {
        viewModelScope.launch {
            try {
                action(StreamingPlaybackService.awaitInstance())
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // no-op: the service did not come up in time
            } catch (e: ServiceDestroyedException) {
                // no-op: the service died during the wait or the call
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A Media3 call failing here must not take the app down with it (R-25).
                Log.e("PlaybackViewModel", "Service action failed", e)
                CrashLog.record("PlaybackViewModel.launchServiceAction", e)
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
        isInErrorState = false
        liveStoppedInBackground = false
        onFocusRegained()
        launchServiceAction { it.stop() }
    }

    /** TV-only wrapper for [StreamingPlaybackService.stopAndRelease]; see its KDoc. */
    fun stopAndRelease() {
        isInErrorState = false
        onFocusRegained()
        // getInstance(), not awaitInstance(): a dead service has nothing to release, and awaiting
        // one that never starts costs the full AWAIT_INSTANCE_TIMEOUT_MS. Nothing here suspends, so
        // no coroutine.
        StreamingPlaybackService.getInstance()?.stopAndRelease()
    }

    /**
     * Called when the app loses focus (for example backgrounded). Pauses playback and starts a 30 s
     * timer to stop it.
     */
    fun onFocusLost(isInPip: Boolean = false) {
        if (isInPip || _isInPictureInPictureMode.value) return
        pauseAndScheduleStop()
    }

    /**
     * Mobile: the activity is no longer visible (ON_STOP). Unlike ON_PAUSE, this never fires in
     * PiP: ON_PAUSE arrives before isInPictureInPictureMode flips on a swipe-home PiP entry, and
     * only ON_STOP follows a dismissed PiP window. Live stops instead of pausing (a paused live
     * stream is stale on resume), and [onAppResumed] restarts it.
     */
    fun onAppStopped() {
        val currentState = _playbackState.value
        if (_currentMetadata.value.isLive && currentState !is PlaybackState.Idle && currentState !is PlaybackState.Ended) {
            liveStoppedInBackground = true
            isInErrorState = false
            onFocusRegained()
            launchServiceAction { it.stop() }
            return
        }
        pauseAndScheduleStop()
    }

    /** Mobile counterpart of [onAppStopped], for ON_RESUME. */
    fun onAppResumed() {
        onFocusRegained()
        if (liveStoppedInBackground) {
            // No Idle check: onAppStopped()'s stop is async, so a quick return can land before it;
            // playStream() replaces it either way.
            playStream(_currentMetadata.value)
        }
    }

    private fun pauseAndScheduleStop() {
        val currentState = _playbackState.value
        if (currentState is PlaybackState.Playing || currentState is PlaybackState.Buffering) {
            pause()
        }

        if (currentState !is PlaybackState.Idle && currentState !is PlaybackState.Ended) {
            focusLostJob?.cancel()
            focusLostJob =
                viewModelScope.launch {
                    delay(30_000L)
                    stop()
                }
        }
    }

    /** Called when the app regains focus. Cancels the pending stop timer. */
    fun onFocusRegained() {
        focusLostJob?.cancel()
        focusLostJob = null
    }

    fun seekTo(position: Long) {
        _seekTarget.value = position
        launchServiceAction { it.seekTo(position) }
    }

    /**
     * Seeks [offsetMs] from the pending seek target, or from the player's position when no seek is
     * pending, so taps sent while the player buffers after a seek add up. Returns the target, or
     * null when nothing was sought.
     */
    fun seekRelative(offsetMs: Long): Long? {
        // Playing, paused or buffering (most often after an earlier seek); nothing on Idle / Ended
        // / Error.
        val isSeekableState =
            when (_playbackState.value) {
                is PlaybackState.Playing, is PlaybackState.Paused, is PlaybackState.Buffering -> true
                else -> false
            }
        // Read the live player position and duration, not the cached PlaybackState: that StateFlow
        // only updates on discrete Player.Listener events, so in steady playback it can be minutes
        // stale and a relative seek went to the wrong place.
        val player = if (isSeekableState) StreamingPlaybackService.getInstance()?.getPlayer() else null
        val duration = player?.duration?.coerceAtLeast(0L) ?: 0L
        val target =
            if (player != null && duration > 0L) {
                relativeSeekTarget(_seekTarget.value, player.currentPosition, offsetMs, duration)
            } else {
                null
            }
        target?.let { seekTo(it) }
        return target
    }

    fun setPlaybackSpeed(speed: Float) {
        launchServiceAction { it.setPlaybackSpeed(speed) }
    }

    fun getAudioTracks(): List<AudioTrackInfo> {
        val controller = _controller.value ?: return emptyList()
        val tracks = controller.currentTracks
        val audioTracks = mutableListOf<AudioTrackInfo>()

        // What plays, by its names: a track label equal to one of them is the file's name, not the track's.
        val titles = playingTitles(controller)
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
                            // A label that only repeats the file's name gives way to language · codec · channels.
                            label =
                                audioTrackName(
                                    label = format.label,
                                    language = format.language,
                                    mimeType = format.sampleMimeType,
                                    codecs = format.codecs,
                                    channelCount = format.channelCount,
                                    titles = titles,
                                    mono = context.getString(R.string.player_audio_mono),
                                    stereo = context.getString(R.string.player_audio_stereo),
                                    fallback = context.getString(R.string.player_track_generic_label),
                                ),
                            channelCount = format.channelCount,
                            sampleRate = format.sampleRate,
                            bitrate = format.bitrate,
                            isSelected = isSelected,
                            languageCode = format.language,
                            roleFlags = format.roleFlags,
                            selectionFlags = format.selectionFlags,
                        ),
                    )
                }
            }
        }

        return audioTracks
    }

    fun selectAudioTrack(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        launchServiceAction { it.selectAudioTrack(groupIndex, trackIndex) }
    }

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
                            label =
                                format.label ?: format.language
                                    ?: context.getString(R.string.player_track_subtitle_fallback_label_format, trackIndex + 1),
                            // Subtitles the player reads ahead come as its own cue format, with the
                            // file's format kept in codecs.
                            mimeType =
                                (
                                    if (format.sampleMimeType == androidx.media3.common.MimeTypes.APPLICATION_MEDIA3_CUES) {
                                        format.codecs
                                    } else {
                                        format.sampleMimeType
                                    }
                                ) ?: "unknown",
                            isSelected = isSelected,
                            languageCode = format.language,
                            roleFlags = format.roleFlags,
                            selectionFlags = format.selectionFlags,
                        ),
                    )
                }
            }
        }

        return subtitleTracks
    }

    fun selectSubtitleTrack(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        launchServiceAction { it.selectSubtitleTrack(groupIndex, trackIndex) }
    }

    fun disableSubtitles() {
        launchServiceAction { it.disableSubtitles() }
    }

    /** The player's video quality levels, highest resolution first. */
    fun getVideoQualities(): List<VideoQualityInfo> {
        val qualities = mutableListOf<VideoQualityInfo>()
        val groups =
            _controller.value
                ?.currentTracks
                ?.groups
                .orEmpty()

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
                            String.format(java.util.Locale.US, "%.1f Mbps", format.bitrate / 1_000_000f)
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

        return qualities.sortedByDescending { it.height }
    }

    /**
     * Chapter markers from the current media item's metadata. Jellyfin stores them in
     * mediaMetadata.extras as parallel arrays.
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

    fun selectVideoQuality(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        launchServiceAction { it.selectVideoQuality(groupIndex, trackIndex) }
    }

    fun enableAutoQuality() {
        launchServiceAction { it.enableAutoQuality() }
    }

    fun updatePictureInPictureMode(inPip: Boolean) {
        _isInPictureInPictureMode.value = inPip
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled when onCleared() runs, so clean up synchronously
        // here. Stop the shared player only while it plays this ViewModel's stream: the screen this
        // one left for can already play its own on it (catch-up's Watch live and Back hand the
        // player to the live preview; the catchup plan).
        val service = StreamingPlaybackService.getInstance()
        val own = ownStreamUrl
        val ownsPlayback = service == null || own == null || XtreamStreamUrl.sameStream(service.currentMetadata.value.streamUrl, own)
        _controller.value?.let {
            it.removeListener(playerListener)
            if (ownsPlayback) it.stop()
            it.release()
        }
        if (ownsPlayback) service?.stop()
        serviceConnection.disconnect()
        // The start claim is not this ViewModel's to clear (see
        // StreamingPlaybackService.tryClaimStart()). stop() above does not tear the service down,
        // so its claim can still be held.
    }
}

/**
 * Where a relative seek of [offsetMs] goes: from [pendingTargetMs] (a seek the player has not
 * reached yet) when set, else from [currentMs]; kept inside [0, [durationMs]].
 */
internal fun relativeSeekTarget(
    pendingTargetMs: Long?,
    currentMs: Long,
    offsetMs: Long,
    durationMs: Long,
): Long = ((pendingTargetMs ?: currentMs) + offsetMs).coerceIn(0L, durationMs)
