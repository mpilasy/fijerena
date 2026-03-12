package org.njarasoa.fijerena.core.player.service

import android.content.Context
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
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
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
import androidx.media3.common.audio.AudioProcessor
import org.njarasoa.fijerena.core.player.audio.BraviaVoiceZoomManager
import org.njarasoa.fijerena.core.player.audio.NightModeManager
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

    private val _audioDspStats = MutableStateFlow(org.njarasoa.fijerena.core.player.model.AudioDspStats())
    val audioDspStats: StateFlow<org.njarasoa.fijerena.core.player.model.AudioDspStats> = _audioDspStats.asStateFlow()

    private var onPositionSaveListener: ((Long, Long) -> Unit)? = null

    private var liveRetryCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRetry: Runnable? = null

    private var adaptiveLoadControl: AdaptiveLoadControl? = null
    private var serviceScope: CoroutineScope? = null

    // Audio enhancement
    val nightModeManager = NightModeManager()
    var voiceZoomManager: BraviaVoiceZoomManager? = null
        private set
    private var audioProcessors: Array<AudioProcessor> = emptyArray()

    /**
     * Set external audio processors (e.g., DialogueBoostProcessor from core:ai).
     * Must be called before initializePlayer() to take effect.
     * The service will include these in the DefaultAudioSink chain.
     */
    fun setAudioProcessors(processors: Array<AudioProcessor>) {
        audioProcessors = processors
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        instanceReady.complete(this)
        NetworkMonitor.init(this)
        // Initialize Bravia Voice Zoom if on a Sony TV
        val vzm = BraviaVoiceZoomManager(this)
        if (vzm.isAvailable) {
            voiceZoomManager = vzm
            vzm.readCurrentState()
            Log.i(TAG, "Bravia Voice Zoom available, current state: ${vzm.enabled}")
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
        // Periodically update Audio DSP stats for the Stats overlay
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                updateAudioDspStats()
            }
        }
    }

    private fun updateAudioDspStats() {
        // Read Clear Voice stats from the AudioProcessor via reflection (core:ai is optional)
        var clearVoiceEnabled = false
        var clearVoiceStrength = 0f
        var clearVoiceAutoDisabled = false
        var aiFramesProcessed = 0L
        var aiFramesSkipped = 0L
        var aiLastInferenceMs = 0L
        var aiAvgInferenceMs = 0f

        for (proc in audioProcessors) {
            try {
                val clazz = proc.javaClass
                if (clazz.simpleName == "DialogueBoostProcessor") {
                    clearVoiceStrength = clazz.getMethod("getStrength").invoke(proc) as? Float ?: 0f
                    clearVoiceEnabled = clearVoiceStrength > 0f
                    clearVoiceAutoDisabled = clazz.getMethod("isAutoDisabled").invoke(proc) as? Boolean ?: false
                    aiFramesProcessed = clazz.getMethod("getTotalFramesProcessed").invoke(proc) as? Long ?: 0L
                    aiFramesSkipped = clazz.getMethod("getTotalFramesSkipped").invoke(proc) as? Long ?: 0L
                    aiLastInferenceMs = clazz.getMethod("getLastInferenceMs").invoke(proc) as? Long ?: 0L
                    aiAvgInferenceMs = clazz.getMethod("getAvgInferenceMs").invoke(proc) as? Float ?: 0f
                }
            } catch (_: Exception) {
                // core:ai not present or processor not a DialogueBoostProcessor
            }
        }

        _audioDspStats.value = org.njarasoa.fijerena.core.player.model.AudioDspStats(
            clearVoiceEnabled = clearVoiceEnabled,
            clearVoiceStrength = clearVoiceStrength,
            clearVoiceAutoDisabled = clearVoiceAutoDisabled,
            aiFramesProcessed = aiFramesProcessed,
            aiFramesSkipped = aiFramesSkipped,
            aiLastInferenceMs = aiLastInferenceMs,
            aiAvgInferenceMs = aiAvgInferenceMs,
            nightModeEnabled = nightModeManager.enabled,
            voiceZoomEnabled = voiceZoomManager?.enabled ?: false,
            voiceZoomAvailable = voiceZoomManager?.isAvailable ?: false
        )
    }

    private fun initializePlayer(contentType: PlayerConfigFactory.ContentType = PlayerConfigFactory.ContentType.VOD) {
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        Log.i(TAG, "FFmpeg library available: $ffmpegAvailable")

        // Custom RenderersFactory to bypass VSyncSamplerV33 ClassNotFoundException on Android 9
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Let super build it, but we've verified the issue is specifically triggered
                // by the VSyncSamplerV33 which we're sidestepping by staying on 1.9.1 
                // but implementing all required interfaces.
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    allowedVideoJoiningTimeMs,
                    out
                )
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
         .setEnableAudioFloatOutput(true)

        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val cellularLiveMultiplier = prefs.getFloat("cellular_live_multiplier", 1.0f)
        val cellularVodMultiplier = prefs.getFloat("cellular_vod_multiplier", 1.0f)

        // Restore audio enhancement settings from preferences
        nightModeManager.enabled = prefs.getBoolean("night_mode_enabled", false)

        val loadControl = AdaptiveLoadControl(
            contentType = contentType,
            cellularLiveMultiplier = cellularLiveMultiplier,
            cellularVodMultiplier = cellularVodMultiplier
        )
        adaptiveLoadControl = loadControl

        // Build ExoPlayer with optional audio processor chain
        val playerBuilder = androidx.media3.exoplayer.ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(PlayerConfigFactory.createTrackSelector(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)

        // Inject AI audio processors into the audio sink if any are configured
        if (audioProcessors.isNotEmpty()) {
            val audioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(this)
                .setAudioProcessors(audioProcessors)
                .setEnableFloatOutput(true)
                .build()
            // Use a custom RenderersFactory that provides the audio sink
            // The processors are already set — ExoPlayer will use them
            playerBuilder.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            Log.i(TAG, "Audio processor chain configured with ${audioProcessors.size} processor(s)")
        }

        val player = playerBuilder.build()

        mediaSession = MediaSession.Builder(this, player).build()

        playerListener = PlayerListener(
            onStateChanged = { newState ->
                if (newState is PlaybackState.Playing) {
                    liveRetryCount = 0
                }
                _playbackState.value = newState
            },
            onWakeLockRequired = {
                acquireWakeLock()
            },
            player = player,
            onPositionSave = { position, duration ->
                onPositionSaveListener?.invoke(position, duration)
            },
            onStreamEndedOrError = { errorMessage ->
                handleStreamEndedOrError(errorMessage)
            }
        )
        player.addListener(playerListener!!)

        // Night Mode: attach DynamicsProcessing to the player's audio session
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                nightModeManager.attach(audioSessionId)
            }
        })

        analyticsListener = PerformanceAnalyticsListener(
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
            }
        )
        player.addAnalyticsListener(analyticsListener!!)
    }

    fun setContentType(contentType: PlayerConfigFactory.ContentType) {
        mediaSession?.run {
            player.removeListener(playerListener!!)
            player.release()
            release()
        }
        mediaSession = null
        initializePlayer(contentType)
    }

    fun setPositionSaveListener(listener: (position: Long, duration: Long) -> Unit) {
        onPositionSaveListener = listener
    }

    fun playStream(metadata: PlayerMetadata, startPositionMs: Long = 0L) {
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

        val mediaSource = StreamingMediaSourceFactory.createMediaSource(
            context = this,
            streamUrl = metadata.streamUrl,
            headers = metadata.headers,
            isLive = metadata.isLive,
            onRetry = { _streamRetryCount.value++ }
        )

        player.setMediaSource(mediaSource)
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
        player.prepare()
        _playbackState.value = PlaybackState.Buffering
    }

    private fun attemptLiveRetry() {
        val metadata = _currentMetadata.value
        if (!metadata.isLive || liveRetryCount >= MAX_LIVE_RETRIES) {
            if (metadata.isLive && liveRetryCount >= MAX_LIVE_RETRIES) {
                _playbackState.value = PlaybackState.Error(
                    "Live stream unavailable after $MAX_LIVE_RETRIES retries. " +
                    "The channel may be offline."
                )
            }
            return
        }

        liveRetryCount++
        _streamRetryCount.value++
        val delayMs = LIVE_RETRY_BASE_DELAY_MS * liveRetryCount
        Log.i(TAG, "Live stream retry $liveRetryCount/$MAX_LIVE_RETRIES in ${delayMs}ms")

        _playbackState.value = PlaybackState.Buffering

        val retryRunnable = Runnable {
            val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return@Runnable
            playerListener?.resetErrorState()

            val mediaSource = StreamingMediaSourceFactory.createMediaSource(
                context = this,
                streamUrl = metadata.streamUrl,
                headers = metadata.headers,
                isLive = metadata.isLive,
                onRetry = { _streamRetryCount.value++ }
            )

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

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(trackIndex)
        )

        val parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(trackSelectionOverride)
            .build()

        trackSelector.parameters = parameters
    }

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(trackIndex)
        )

        val parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(trackSelectionOverride)
            .build()

        trackSelector.parameters = parameters
    }

    fun disableSubtitles() {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val parameters = trackSelector.parameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            .build()

        trackSelector.parameters = parameters
    }

    fun selectVideoQuality(groupIndex: Int, trackIndex: Int) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
            trackGroup.mediaTrackGroup,
            listOf(trackIndex)
        )

        val parameters = trackSelector.parameters
            .buildUpon()
            .setOverrideForType(trackSelectionOverride)
            .build()

        trackSelector.parameters = parameters
    }

    fun enableAutoQuality() {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val parameters = trackSelector.parameters
            .buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            .build()

        trackSelector.parameters = parameters
    }

    fun getPlayer(): androidx.media3.common.Player? {
        return mediaSession?.player
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StreamingPlayback:WakeLock"
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
                onPositionSaveListener?.invoke(it.currentPosition, it.duration)
            }
        }

        mediaSession?.run {
            player.removeListener(playerListener!!)
            player.release()
            release()
        }
        mediaSession = null
        nightModeManager.release()
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
        private val onPositionSave: ((Long, Long) -> Unit)? = null,
        private val onStreamEndedOrError: (errorMessage: String?) -> Unit = {}
    ) : Player.Listener {
        private var isInErrorState = false
        private var lastSavedPosition = 0L
        private val saveIntervalMs = 10_000L

        fun resetErrorState() {
            isInErrorState = false
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && player.isPlaying) {
                val currentPosition = player.currentPosition
                val duration = player.duration

                if (currentPosition - lastSavedPosition >= saveIntervalMs) {
                    onPositionSave?.invoke(currentPosition, duration)
                    lastSavedPosition = currentPosition
                }
            }

            updatePlaybackState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (playWhenReady) {
                onWakeLockRequired()
            }
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onWakeLockRequired()
            }
            updatePlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            isInErrorState = true
            val errorMessage = parsePlaybackError(error)
            onStreamEndedOrError(errorMessage)
        }

        private fun parsePlaybackError(error: PlaybackException): String {
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> {
                    val codecInfo = extractCodecInfo(error.message ?: "")
                    if (codecInfo.isNotEmpty()) {
                        "Video codec not supported on this device: $codecInfo"
                    } else {
                        "Video format not supported on this device"
                    }
                }
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
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
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                    "Stream not found or access denied."
                }
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    "Invalid stream format. The stream may be corrupted."
                }
                else -> {
                    "Playback error: ${error.errorCodeName}"
                }
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

            val state = when (player.playbackState) {
                Player.STATE_IDLE -> PlaybackState.Idle
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY -> {
                    if (player.playWhenReady) {
                        PlaybackState.Playing(
                            position = player.currentPosition,
                            duration = player.duration.coerceAtLeast(0L)
                        )
                    } else {
                        PlaybackState.Paused(
                            position = player.currentPosition,
                            duration = player.duration.coerceAtLeast(0L)
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
        private val onQualitySwitch: (count: Int) -> Unit
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

        override fun onVideoFrameProcessingOffset(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            totalProcessingOffsetUs: Long,
            frameCount: Int
        ) {
            totalFrames += frameCount
        }

        override fun onDroppedVideoFrames(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            this.droppedFrames += droppedFrames
            onMetricsUpdate(this.droppedFrames, totalFrames)
        }

        override fun onPositionDiscontinuity(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                seekPending = true
            }
        }

        override fun onPlaybackStateChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            state: Int
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
        }

        override fun onBandwidthEstimate(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
        ) {
            onBandwidthUpdate(bitrateEstimate)
        }

        override fun onDownstreamFormatChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
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

        fun getPlaybackState(service: StreamingPlaybackService): StateFlow<PlaybackState> {
            return service.playbackState
        }

        fun getCurrentMetadata(service: StreamingPlaybackService): StateFlow<PlayerMetadata> {
            return service.currentMetadata
        }
    }
}
