package org.njarasoa.fijerena.core.player.service

import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.config.AdaptiveLoadControl
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import org.njarasoa.fijerena.core.player.source.StreamingMediaSourceFactory

@androidx.media3.common.util.UnstableApi
class StreamingPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var playerListener: PlayerListener? = null
    private var analyticsListener: PerformanceAnalyticsListener? = null
    private var mediaSourceFactory: StreamingMediaSourceFactory? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMetadata = MutableStateFlow(PlayerMetadata())
    val currentMetadata: StateFlow<PlayerMetadata> = _currentMetadata.asStateFlow()

    private val _droppedFrames = MutableStateFlow(0L)
    val droppedFrames: StateFlow<Long> = _droppedFrames.asStateFlow()

    private val _totalFrames = MutableStateFlow(0L)
    val totalFrames: StateFlow<Long> = _totalFrames.asStateFlow()

    private val _streamRetryCount = MutableStateFlow(0)
    val streamRetryCount: StateFlow<Int> = _streamRetryCount.asStateFlow()

    private val _streamStartTimeMs = MutableStateFlow(0L)
    val streamStartTimeMs: StateFlow<Long> = _streamStartTimeMs.asStateFlow()

    private val _rebufferCount = MutableStateFlow(0)
    val rebufferCount: StateFlow<Int> = _rebufferCount.asStateFlow()

    private val _exhaustionRebufferCount = MutableStateFlow(0)
    val exhaustionRebufferCount: StateFlow<Int> = _exhaustionRebufferCount.asStateFlow()

    private val _totalRebufferTimeMs = MutableStateFlow(0L)
    val totalRebufferTimeMs: StateFlow<Long> = _totalRebufferTimeMs.asStateFlow()

    private val _bandwidthEstimate = MutableStateFlow(0L)
    val bandwidthEstimate: StateFlow<Long> = _bandwidthEstimate.asStateFlow()

    private val _qualitySwitchCount = MutableStateFlow(0)
    val qualitySwitchCount: StateFlow<Int> = _qualitySwitchCount.asStateFlow()

    private val _measuredFps = MutableStateFlow(0f)
    val measuredFps: StateFlow<Float> = _measuredFps.asStateFlow()

    private var onPositionSaveListener: ((Long, Long, Boolean, Int?, Int?) -> Unit)? = null

    private var liveRetryCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRetry: Runnable? = null

    private var adaptiveLoadControl: AdaptiveLoadControl? = null
    private var bandwidthMeter: androidx.media3.exoplayer.upstream.DefaultBandwidthMeter? = null
    private var serviceScope: CoroutineScope? = null
    private var healthMonitor: org.njarasoa.fijerena.core.player.network.StreamHealthMonitor? = null

    private val _isRecycling = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRecyclingFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRecycling.asStateFlow()

    fun isRecycling(): Boolean = _isRecycling.value

    private fun setRecycling(recycling: Boolean) {
        _isRecycling.value = recycling
        adaptiveLoadControl?.setRecycling(recycling)
    }

    private val recycleHandler = Runnable {
        val metadata = _currentMetadata.value
        val player = getPlayer()
        if (metadata.isLive && player != null) {
            val currentPos = player.currentPosition
            Log.i(TAG, "Executing silent stream recycle at position: $currentPos")

            // 1. Evict network connection pool to bypass ISP/CDN shaping
            org.njarasoa.fijerena.core.player.network.NetworkModule.evictConnectionPool()

            // 2. Restart stream seamlessly without clearing the screen
            performSeamlessRecycle(metadata, currentPos)

            // 3. Reset monitor for the fresh connection
            healthMonitor?.reset()
        }
    }

    private fun performSeamlessRecycle(metadata: PlayerMetadata, currentPos: Long) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        
        // Temporarily increase buffer requirement for recycling to ensure a 100% smooth handover
        setRecycling(true)

        val mediaSource = mediaSourceFactory?.createMediaSource(
            streamUrl = metadata.streamUrl,
            headers = metadata.headers,
            isLive = metadata.isLive,
            onRetry = { _streamRetryCount.value++ },
            transferListener = bandwidthMeter,
        ) ?: return

        // setMediaSource(source, resetPosition=false) keeps the current frame on screen
        // while the new source prepares in the background.
        player.setMediaSource(mediaSource, false)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        instanceReady.complete(this)
        NetworkMonitor.init(this)
        mediaSourceFactory = StreamingMediaSourceFactory(this)

        healthMonitor = org.njarasoa.fijerena.core.player.network.StreamHealthMonitor {
            mainHandler.post(recycleHandler)
        }

        initializePlayer()
        acquireWakeLock()
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        serviceScope = scope
        scope.launch {
            NetworkMonitor.networkType.collect { networkType ->
                adaptiveLoadControl?.updateForNetwork(networkType)
            }
        }
    }

    private fun initializePlayer(contentType: PlayerConfigFactory.ContentType = PlayerConfigFactory.ContentType.VOD) {
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        Log.i(TAG, "FFmpeg library available: $ffmpegAvailable")

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val cellularLiveMultiplier = prefs.getFloat("cellular_live_multiplier", 1.0f)
        val cellularVodMultiplier = prefs.getFloat("cellular_vod_multiplier", 1.0f)

        val loadControl =
            AdaptiveLoadControl(
                contentType = contentType,
                cellularLiveMultiplier = cellularLiveMultiplier,
                cellularVodMultiplier = cellularVodMultiplier,
            )
        adaptiveLoadControl = loadControl

        val bm =
            androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
                .getSingletonInstance(this)
        bandwidthMeter = bm

        // Build ExoPlayer with standard factory
        val playerBuilder =
            androidx.media3.exoplayer.ExoPlayer
                .Builder(this)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bm)
                .setTrackSelector(PlayerConfigFactory.createTrackSelector(this))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                ).setWakeMode(C.WAKE_MODE_NETWORK)

        val player = playerBuilder.build()

        mediaSession = MediaSession.Builder(this, player).build()

        playerListener =
            PlayerListener(
                onStateChanged = { newState ->
                    val isRecycling = isRecycling()
                    Log.d(TAG, "onStateChanged: newState=$newState, isRecycling=$isRecycling")
                    
                    if (newState is PlaybackState.Playing) {
                        liveRetryCount = 0
                        // Reset recycling mode once we are successfully playing the new stream
                        if (isRecycling) {
                            Log.i(TAG, "Successfully resumed after recycle. Resetting recycling flag.")
                            setRecycling(false)
                        }
                    }

                    // Suppress 'Buffering' state updates during a silent recycle to keep it seamless
                    if (isRecycling && newState is PlaybackState.Buffering) {
                        Log.d(TAG, "Suppressing Buffering state during silent recycle.")
                        return@PlayerListener
                    }

                    _playbackState.value = newState
                },
                onWakeLockRequired = {
                    acquireWakeLock()
                },
                player = player,
                onPositionSave = { position, duration, isPaused, audioIndex, subtitleIndex ->
                    onPositionSaveListener?.invoke(position, duration, isPaused, audioIndex, subtitleIndex)
                },
                onStreamEndedOrError = { errorMessage ->
                    handleStreamEndedOrError(errorMessage)
                },
            )
        player.addListener(playerListener!!)

        analyticsListener =
            PerformanceAnalyticsListener(
                onMetricsUpdate = { dropped, total ->
                    _droppedFrames.value = dropped
                    _totalFrames.value = total
                },
                onRebuffer = { count, totalTimeMs ->
                    _rebufferCount.value = count
                    _totalRebufferTimeMs.value = totalTimeMs
                },
                onExhaustionRebuffer = { count ->
                    _exhaustionRebufferCount.value = count
                },
                onBandwidthUpdate = { bitrateEstimate ->
                    _bandwidthEstimate.value = bitrateEstimate
                },
                onQualitySwitch = { count ->
                    _qualitySwitchCount.value = count
                },
                onFpsUpdate = { fps ->
                    _measuredFps.value = fps
                },
            )
        player.addAnalyticsListener(analyticsListener!!)
    }

    fun setContentType(contentType: PlayerConfigFactory.ContentType) {
        adaptiveLoadControl?.updateContentType(contentType)
    }

    fun setPositionSaveListener(
        listener: (position: Long, duration: Long, isPaused: Boolean, audioIndex: Int?, subtitleIndex: Int?) -> Unit,
    ) {
        onPositionSaveListener = listener
    }

    fun playStream(
        metadata: PlayerMetadata,
        startPositionMs: Long = 0L,
    ) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        cancelPendingRetry()
        playerListener?.resetErrorState()
        liveRetryCount = 0
        _streamRetryCount.value = 0
        _rebufferCount.value = 0
        _exhaustionRebufferCount.value = 0
        _totalRebufferTimeMs.value = 0L
        _bandwidthEstimate.value = 0L
        _qualitySwitchCount.value = 0
        _streamStartTimeMs.value = SystemClock.elapsedRealtime()
        _currentMetadata.value = metadata

        // Ensure we are in fast-startup mode
        setRecycling(false)

        val mediaSource =
            mediaSourceFactory?.createMediaSource(
                streamUrl = metadata.streamUrl,
                headers = metadata.headers,
                isLive = metadata.isLive,
                onRetry = { _streamRetryCount.value++ },
                transferListener = bandwidthMeter,
            ) ?: return

        player.setMediaSource(mediaSource)
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
        player.prepare()
    }

    private fun attemptLiveRetry() {
        val metadata = _currentMetadata.value
        if (!metadata.isLive || liveRetryCount >= MAX_LIVE_RETRIES) {
            if (metadata.isLive && liveRetryCount >= MAX_LIVE_RETRIES) {
                _playbackState.value =
                    PlaybackState.Error(
                        "Live stream unavailable after $MAX_LIVE_RETRIES retries. " +
                            "The channel may be offline.",
                    )
            }
            return
        }

        // Hard retry should use fast-startup settings
        setRecycling(false)

        liveRetryCount++
        _streamRetryCount.value++
        val delayMs = LIVE_RETRY_BASE_DELAY_MS * liveRetryCount
        Log.i(TAG, "Live stream retry $liveRetryCount/$MAX_LIVE_RETRIES in ${delayMs}ms")

        val retryRunnable =
            Runnable {
                val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return@Runnable
                playerListener?.resetErrorState()

                val mediaSource =
                    mediaSourceFactory?.createMediaSource(
                        streamUrl = metadata.streamUrl,
                        headers = metadata.headers,
                        isLive = metadata.isLive,
                        onRetry = { _streamRetryCount.value++ },
                    ) ?: return@Runnable

                player.setMediaSource(mediaSource)
                player.playWhenReady = true
                player.prepare()
            }
        pendingRetry = retryRunnable
        mainHandler.postDelayed(retryRunnable, delayMs)
    }

    private fun cancelPendingRetry() {
        pendingRetry?.let { mainHandler.removeCallbacks(it) }
        pendingRetry = null
    }

    private fun handleStreamEndedOrError(errorMessage: String?) {
        val metadata = _currentMetadata.value
        if (metadata.isLive) {
            attemptLiveRetry()
        } else {
            if (errorMessage != null) {
                _playbackState.value = PlaybackState.Error(errorMessage)
            } else {
                _playbackState.value = PlaybackState.Ended
            }
        }
    }

    fun pause() {
        mediaSession?.player?.pause()
        releaseWakeLock()
    }

    fun resume() {
        mediaSession?.player?.play()
        acquireWakeLock()
    }

    fun stop() {
        cancelPendingRetry()
        mediaSession?.player?.stop()
        _playbackState.value = PlaybackState.Idle
        releaseWakeLock()
    }

    fun seekTo(position: Long) {
        mediaSession?.player?.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaSession?.player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
    }

    fun getAudioTracks(): List<org.njarasoa.fijerena.core.player.model.AudioTrackInfo> {
        val p = mediaSession?.player ?: return emptyList()
        val tracks = p.currentTracks
        val audioTracks = mutableListOf<org.njarasoa.fijerena.core.player.model.AudioTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)

                    audioTracks.add(
                        org.njarasoa.fijerena.core.player.model.AudioTrackInfo(
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

    fun selectAudioTrack(consolidatedIndex: Int) {
        val tracks = getAudioTracks()
        if (consolidatedIndex >= 0 && consolidatedIndex < tracks.size) {
            val track = tracks[consolidatedIndex]
            selectAudioTrack(track.groupIndex, track.trackIndex)
        }
    }

    fun getSubtitleTracks(): List<org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo> {
        val p = mediaSession?.player ?: return emptyList()
        val tracks = p.currentTracks
        val subtitleTracks = mutableListOf<org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)

                    subtitleTracks.add(
                        org.njarasoa.fijerena.core.player.model.SubtitleTrackInfo(
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

    fun selectSubtitleTrack(consolidatedIndex: Int) {
        if (consolidatedIndex == -1) {
            disableSubtitles()
            return
        }
        val tracks = getSubtitleTracks()
        if (consolidatedIndex >= 0 && consolidatedIndex < tracks.size) {
            val track = tracks[consolidatedIndex]
            selectSubtitleTrack(track.groupIndex, track.trackIndex)
        }
    }

    fun selectAudioTrack(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        val trackSelectionOverride =
            androidx.media3.common.TrackSelectionOverride(
                trackGroup.mediaTrackGroup,
                listOf(trackIndex),
            )

        val parameters =
            trackSelector.parameters
                .buildUpon()
                .setOverrideForType(trackSelectionOverride)
                .build()

        trackSelector.parameters = parameters

        // Save choice immediately
        onPositionSaveListener?.invoke(player.currentPosition, player.duration, !player.isPlaying, trackIndex, null)
    }

    fun selectSubtitleTrack(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        val trackSelectionOverride =
            androidx.media3.common.TrackSelectionOverride(
                trackGroup.mediaTrackGroup,
                listOf(trackIndex),
            )

        val parameters =
            trackSelector.parameters
                .buildUpon()
                .setOverrideForType(trackSelectionOverride)
                .build()

        trackSelector.parameters = parameters

        // Save choice immediately
        onPositionSaveListener?.invoke(player.currentPosition, player.duration, !player.isPlaying, null, trackIndex)
    }

    fun disableSubtitles() {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val parameters =
            trackSelector.parameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()

        trackSelector.parameters = parameters

        // Save choice immediately (-1 for disabled)
        onPositionSaveListener?.invoke(player.currentPosition, player.duration, !player.isPlaying, null, -1)
    }

    fun selectVideoQuality(
        groupIndex: Int,
        trackIndex: Int,
    ) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        val trackSelectionOverride =
            androidx.media3.common.TrackSelectionOverride(
                trackGroup.mediaTrackGroup,
                listOf(trackIndex),
            )

        val parameters =
            trackSelector.parameters
                .buildUpon()
                .setOverrideForType(trackSelectionOverride)
                .build()

        trackSelector.parameters = parameters
    }

    fun enableAutoQuality() {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val parameters =
            trackSelector.parameters
                .buildUpon()
                .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                .build()

        trackSelector.parameters = parameters
    }

    fun getPlayer(): androidx.media3.common.Player? = mediaSession?.player

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock =
                powerManager
                    .newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "StreamingPlayback:WakeLock",
                    ).apply {
                        setReferenceCounted(false)
                    }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onDestroy() {
        cancelPendingRetry()
        mediaSession?.player?.let {
            if (it.isPlaying || it.playbackState == Player.STATE_READY) {
                onPositionSaveListener?.invoke(it.currentPosition, it.duration, !it.isPlaying, null, null)
            }
        }

        mediaSession?.run {
            player.removeListener(playerListener!!)
            player.release()
            release()
        }
        mediaSession = null
        releaseWakeLock()
        wakeLock = null
        analyticsListener = null
        serviceScope?.cancel()
        serviceScope = null
        adaptiveLoadControl = null
        NetworkMonitor.release()
        instance = null
        super.onDestroy()
    }

    private class PlayerListener(
        private val onStateChanged: (PlaybackState) -> Unit,
        private val onWakeLockRequired: () -> Unit,
        private val player: Player,
        private val onPositionSave: ((Long, Long, Boolean, Int?, Int?) -> Unit)? = null,
        private val onStreamEndedOrError: (errorMessage: String?) -> Unit = {},
    ) : Player.Listener {
        private var isInErrorState = false
        private var lastSavedPosition = 0L
        private val saveIntervalMs = 10_000L

        fun resetErrorState() {
            isInErrorState = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val currentPosition = player.currentPosition
                val duration = player.duration
                val isPaused = !player.isPlaying

                // Periodic save while playing or immediate save on state change (including pause)
                if (kotlin.math.abs(currentPosition - lastSavedPosition) >= saveIntervalMs || isPaused) {
                    onPositionSave?.invoke(currentPosition, duration, isPaused, null, null)
                    lastSavedPosition = currentPosition
                }
            }

            updatePlaybackState()
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (playWhenReady) {
                onWakeLockRequired()
            }
            // Trigger immediate save when play/pause state changes
            if (player.playbackState == Player.STATE_READY) {
                onPositionSave?.invoke(player.currentPosition, player.duration, !playWhenReady, null, null)
            }
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onWakeLockRequired()
            }
            // Trigger immediate save when play/pause state changes
            if (player.playbackState == Player.STATE_READY) {
                onPositionSave?.invoke(player.currentPosition, player.duration, !isPlaying, null, null)
            }
            updatePlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            isInErrorState = true
            val errorMessage = parsePlaybackError(error)
            onStreamEndedOrError(errorMessage)
        }

        private fun parsePlaybackError(error: PlaybackException): String =
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                -> {
                    val codecInfo = extractCodecInfo(error.message ?: "")
                    if (codecInfo.isNotEmpty()) {
                        "Video codec not supported on this device: $codecInfo"
                    } else {
                        "Video format not supported on this device"
                    }
                }
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                -> {
                    "Network connection failed. Check your internet connection."
                }
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    val code = extractHttpStatusCode(error)
                    when (code) {
                        401 -> "HTTP 401 — Authentication failed. Check your credentials."
                        403 -> "HTTP 403 — Access denied. Your subscription may have expired."
                        404 -> "HTTP 404 — Stream not found. The channel may be offline."
                        458 -> "HTTP 458 — Connection limit reached. Close other active streams on this account."
                        502, 503, 504 -> "HTTP $code — Server unavailable. Try again shortly."
                        in 500..599 -> "HTTP $code — Server error. Try again later."
                        in 400..499 -> "HTTP $code — Stream access denied."
                        else -> if (code != null) "HTTP $code — Stream unavailable." else "Stream unavailable."
                    }
                }
                PlaybackException.ERROR_CODE_TIMEOUT -> {
                    "Playback timeout. The stream may be too slow or unavailable."
                }
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> {
                    "Stream not found or access denied."
                }
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                -> {
                    "Invalid stream format. The stream may be corrupted."
                }
                else -> {
                    "Playback error: ${error.errorCodeName}"
                }
            }

        private fun extractHttpStatusCode(error: PlaybackException): Int? {
            var cause: Throwable? = error.cause
            while (cause != null) {
                val match = HTTP_STATUS_REGEX.find(cause.message ?: "")
                if (match != null) return match.groupValues[1].toIntOrNull()
                cause = cause.cause
            }
            return null
        }

        private fun extractCodecInfo(message: String): String {
            val match = CODEC_REGEX.find(message)
            return match?.value?.replace("video/", "")?.uppercase() ?: ""
        }

        companion object {
            // Pre-compiled regexes — avoid recompiling on every error event
            private val HTTP_STATUS_REGEX = Regex("Response code: (\\d{3})")
            private val CODEC_REGEX = Regex("video/(\\w+)|format=(\\w+)")
        }

        private fun updatePlaybackState() {
            if (isInErrorState) return

            val state =
                when (player.playbackState) {
                    Player.STATE_IDLE -> PlaybackState.Idle
                    Player.STATE_BUFFERING -> PlaybackState.Buffering
                    Player.STATE_READY -> {
                        if (player.playWhenReady) {
                            PlaybackState.Playing(
                                position = player.currentPosition,
                                duration = player.duration.coerceAtLeast(0L),
                            )
                        } else {
                            PlaybackState.Paused(
                                position = player.currentPosition,
                                duration = player.duration.coerceAtLeast(0L),
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        onStreamEndedOrError(null)
                        return
                    }
                    else -> PlaybackState.Idle
                }
            onStateChanged(state)
        }
    }

    private class PerformanceAnalyticsListener(
        private val onMetricsUpdate: (droppedFrames: Long, totalFrames: Long) -> Unit,
        private val onRebuffer: (count: Int, totalTimeMs: Long) -> Unit,
        private val onExhaustionRebuffer: (count: Int) -> Unit,
        private val onBandwidthUpdate: (bitrateEstimate: Long) -> Unit,
        private val onQualitySwitch: (count: Int) -> Unit,
        private val onFpsUpdate: (fps: Float) -> Unit,
    ) : androidx.media3.exoplayer.analytics.AnalyticsListener {
        private var droppedFrames = 0L
        private var totalFrames = 0L
        private var rebufferCount = 0
        private var exhaustionRebufferCount = 0
        private var totalRebufferTimeMs = 0L
        private var rebufferStartTimeMs = 0L
        private var wasPlaying = false
        private var seekPending = false
        private var qualitySwitchCount = 0
        private var lastVideoHeight = -1

        private var fpsLastTimeMs = 0L
        private var fpsLastFrameCount = 0L

        override fun onVideoFrameProcessingOffset(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            totalProcessingOffsetUs: Long,
            frameCount: Int,
        ) {
            totalFrames += frameCount
            val currentTime = SystemClock.elapsedRealtime()
            if (fpsLastTimeMs == 0L) {
                fpsLastTimeMs = currentTime
                fpsLastFrameCount = totalFrames
            } else {
                val delta = currentTime - fpsLastTimeMs
                if (delta >= 1000) {
                    val frames = totalFrames - fpsLastFrameCount
                    val fps = (frames * 1000f) / delta
                    onFpsUpdate(fps)
                    fpsLastTimeMs = currentTime
                    fpsLastFrameCount = totalFrames
                }
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            this.droppedFrames += droppedFrames
            onMetricsUpdate(this.droppedFrames, totalFrames)
        }

        override fun onPositionDiscontinuity(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                seekPending = true
            }
        }

        override fun onPlaybackStateChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            state: Int,
        ) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    if (wasPlaying) {
                        rebufferCount++
                        rebufferStartTimeMs = SystemClock.elapsedRealtime()
                        if (!seekPending) {
                            exhaustionRebufferCount++
                            onExhaustionRebuffer(exhaustionRebufferCount)
                        }
                    }
                    seekPending = false
                }
                Player.STATE_READY -> {
                    seekPending = false
                    if (rebufferStartTimeMs > 0) {
                        totalRebufferTimeMs += SystemClock.elapsedRealtime() - rebufferStartTimeMs
                        rebufferStartTimeMs = 0L
                        onRebuffer(rebufferCount, totalRebufferTimeMs)
                    }
                    wasPlaying = true
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    wasPlaying = false
                    seekPending = false
                    rebufferStartTimeMs = 0L
                }
            }
            
            // Feed health monitor on every state change
            val service = instance
            val player = service?.getPlayer()
            if (service != null && player != null) {
                service.healthMonitor?.updateMetrics(
                    bufferedDurationMs = player.bufferedPosition - player.currentPosition,
                    droppedFramesPerSecond = service._measuredFps.value, 
                    hasReadTimeout = false // ExoPlayer reports timeouts via exceptions
                )
            }
        }

        override fun onBandwidthEstimate(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            onBandwidthUpdate(bitrateEstimate)
        }

        override fun onDownstreamFormatChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
        ) {
            if (mediaLoadData.trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                val newHeight = mediaLoadData.trackFormat?.height ?: return
                if (lastVideoHeight > 0 && newHeight != lastVideoHeight) {
                    qualitySwitchCount++
                    onQualitySwitch(qualitySwitchCount)
                }
                lastVideoHeight = newHeight
            }
        }
    }

    companion object {
        private const val TAG = "StreamingPlaybackService"
        private const val MAX_LIVE_RETRIES = 3
        private const val LIVE_RETRY_BASE_DELAY_MS = 2000L

        @Volatile
        private var instance: StreamingPlaybackService? = null

        private val instanceReady = kotlinx.coroutines.CompletableDeferred<StreamingPlaybackService>()

        fun getInstance(): StreamingPlaybackService? = instance

        suspend fun awaitInstance(): StreamingPlaybackService = instanceReady.await()

        fun getPlaybackState(service: StreamingPlaybackService): StateFlow<PlaybackState> = service.playbackState

        fun getCurrentMetadata(service: StreamingPlaybackService): StateFlow<PlayerMetadata> = service.currentMetadata
    }
}
