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
// FFmpeg library from Jellyfin's pre-built Media3 decoder
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

class StreamingPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var playerListener: PlayerListener? = null
    private var analyticsListener: PerformanceAnalyticsListener? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMetadata = MutableStateFlow(PlayerMetadata())
    val currentMetadata: StateFlow<PlayerMetadata> = _currentMetadata.asStateFlow()

    // Performance metrics
    private val _droppedFrames = MutableStateFlow(0L)
    val droppedFrames: StateFlow<Long> = _droppedFrames.asStateFlow()

    private val _totalFrames = MutableStateFlow(0L)
    val totalFrames: StateFlow<Long> = _totalFrames.asStateFlow()

    // Stream stats: retry count (load-level + live-level) and stream start time
    private val _streamRetryCount = MutableStateFlow(0)
    val streamRetryCount: StateFlow<Int> = _streamRetryCount.asStateFlow()

    private val _streamStartTimeMs = MutableStateFlow(0L)
    val streamStartTimeMs: StateFlow<Long> = _streamStartTimeMs.asStateFlow()

    // Network stutter metrics
    private val _rebufferCount = MutableStateFlow(0)
    val rebufferCount: StateFlow<Int> = _rebufferCount.asStateFlow()

    private val _totalRebufferTimeMs = MutableStateFlow(0L)
    val totalRebufferTimeMs: StateFlow<Long> = _totalRebufferTimeMs.asStateFlow()

    private val _bandwidthEstimate = MutableStateFlow(0L)
    val bandwidthEstimate: StateFlow<Long> = _bandwidthEstimate.asStateFlow()

    private val _qualitySwitchCount = MutableStateFlow(0)
    val qualitySwitchCount: StateFlow<Int> = _qualitySwitchCount.asStateFlow()

    private var onPositionSaveListener: ((Long, Long) -> Unit)? = null

    // Live stream auto-retry
    private var liveRetryCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRetry: Runnable? = null

    // Adaptive load control for network-aware buffering
    private var adaptiveLoadControl: AdaptiveLoadControl? = null
    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        NetworkMonitor.init(this)
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
                updateTrackSelectionForNetwork(networkType)
            }
        }
    }

    private fun updateTrackSelectionForNetwork(networkType: org.njarasoa.fijerena.core.player.config.NetworkType) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return
        val isCellular = networkType == org.njarasoa.fijerena.core.player.config.NetworkType.CELLULAR

        val maxBitrate = if (isCellular) {
            // Aggressive cap for cellular to prevent buffering/stuttering (1.5 Mbps)
            1_500_000
        } else {
            // Restore device-specific limits on WiFi
            val capabilities = org.njarasoa.fijerena.core.player.device.DeviceDetector.detect()
            when (capabilities.deviceType) {
                org.njarasoa.fijerena.core.player.device.DeviceType.NVIDIA_SHIELD -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                org.njarasoa.fijerena.core.player.device.DeviceType.SONY_BRAVIA -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                org.njarasoa.fijerena.core.player.device.DeviceType.CHROMECAST_TV -> if (capabilities.supports4K) 20_000_000 else 10_000_000
                org.njarasoa.fijerena.core.player.device.DeviceType.GENERIC_TV -> 10_000_000
                org.njarasoa.fijerena.core.player.device.DeviceType.GENERIC_MOBILE -> 5_000_000
            }
        }

        val parameters = trackSelector.parameters
            .buildUpon()
            .setMaxVideoBitrate(maxBitrate)
            .build()

        trackSelector.parameters = parameters
        Log.i(TAG, "Updated track selector for network $networkType. Max bitrate: $maxBitrate")
    }

    private fun initializePlayer(contentType: PlayerConfigFactory.ContentType = PlayerConfigFactory.ContentType.VOD) {
        // Check and log FFmpeg availability
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        Log.i(TAG, "FFmpeg library available: $ffmpegAvailable")

        if (ffmpegAvailable) {
            // Log supported decoders
            val supportedDecoders = listOf("ac3", "eac3", "mlp", "truehd", "dts", "dts_express")
            supportedDecoders.forEach { codec ->
                val supported = FfmpegLibrary.supportsFormat(codec)
                Log.i(TAG, "FFmpeg supports $codec: $supported")
            }
        }

        // Create RenderersFactory that prioritizes extension decoders (FFmpeg) for audio
        // EXTENSION_RENDERER_MODE_PREFER: Use FFmpeg decoders over platform decoders when available
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioFloatOutput(true) // Better audio quality if hardware supports it

        // Use AdaptiveLoadControl for network-aware buffer management
        // Read cellular buffer multipliers from SharedPreferences (avoids circular dependency)
        val prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val cellularLiveMultiplier = prefs.getFloat("cellular_live_multiplier", 1.0f)
        val cellularVodMultiplier = prefs.getFloat("cellular_vod_multiplier", 1.0f)

        val loadControl = AdaptiveLoadControl(
            contentType = contentType,
            cellularLiveMultiplier = cellularLiveMultiplier,
            cellularVodMultiplier = cellularVodMultiplier
        )
        adaptiveLoadControl = loadControl

        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(PlayerConfigFactory.createTrackSelector(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        playerListener = PlayerListener(
            onStateChanged = { newState ->
                // Reset retry counter on successful playback
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

        // Add analytics listener for performance monitoring
        analyticsListener = PerformanceAnalyticsListener(
            onMetricsUpdate = { dropped, total ->
                _droppedFrames.value = dropped
                _totalFrames.value = total
            },
            onRebuffer = { count, totalTimeMs ->
                _rebufferCount.value = count
                _totalRebufferTimeMs.value = totalTimeMs
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
        // Release old player and create new one with different LoadControl
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

    fun playStream(metadata: PlayerMetadata) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        // Cancel any pending retry
        cancelPendingRetry()
        // Reset error state and retry counter on new stream
        playerListener?.resetErrorState()
        liveRetryCount = 0
        _streamRetryCount.value = 0
        _rebufferCount.value = 0
        _totalRebufferTimeMs.value = 0L
        _bandwidthEstimate.value = 0L
        _qualitySwitchCount.value = 0
        _streamStartTimeMs.value = SystemClock.elapsedRealtime()
        _currentMetadata.value = metadata

        // Use StreamingMediaSourceFactory for proper HLS/DASH/MPEG-TS detection
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
        _playbackState.value = PlaybackState.Buffering
    }

    private fun attemptLiveRetry() {
        val metadata = _currentMetadata.value
        if (!metadata.isLive || liveRetryCount >= MAX_LIVE_RETRIES) {
            // Exceeded max retries, show error to user
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
            // Live stream: attempt auto-retry
            attemptLiveRetry()
        } else {
            // VOD: show ended or error state to user
            if (errorMessage != null) {
                _playbackState.value = PlaybackState.Error(errorMessage)
            } else {
                _playbackState.value = PlaybackState.Ended
            }
        }
    }

    fun pause() {
        mediaSession?.player?.pause()
        // Release wake lock when paused to save battery
        // User can pause long movies and put device to sleep
        releaseWakeLock()
    }

    fun resume() {
        mediaSession?.player?.play()
        // Re-acquire wake lock when resuming playback
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

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return

        val currentTracks = player.currentTracks
        if (groupIndex < 0 || groupIndex >= currentTracks.groups.size) return

        val trackGroup = currentTracks.groups[groupIndex]
        if (trackIndex < 0 || trackIndex >= trackGroup.length) return

        // Build track selection parameters to override the audio track
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

        // Build track selection parameters to override the subtitle track
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

        // Disable all text tracks
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

        // Build track selection parameters to override the video quality
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

        // Clear all video overrides to enable adaptive bitrate
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
        // Acquire without timeout for long-form content (movies can be 2+ hours)
        // Will be released when playback stops or service is destroyed
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
        // Don't set to null, keep instance for reuse
    }

    override fun onDestroy() {
        cancelPendingRetry()
        // Save final position before cleanup
        mediaSession?.player?.let {
            if (it.isPlaying || it.playbackState == Player.STATE_READY) {
                onPositionSaveListener?.invoke(it.currentPosition, it.duration)
            }
        }

        mediaSession?.run {
            player.removeListener(playerListener!!)
            // Analytics listener will be cleaned up automatically when player is released
            player.release()
            release()
        }
        mediaSession = null
        releaseWakeLock()
        // Clean up wake lock reference
        wakeLock = null
        analyticsListener = null
        // Clean up network monitoring and coroutine scope
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
        private val saveIntervalMs = 10_000L // Save every 10 seconds

        fun resetErrorState() {
            isInErrorState = false
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            // Auto-save position when playing
            if (playbackState == Player.STATE_READY && player.isPlaying) {
                val currentPosition = player.currentPosition
                val duration = player.duration

                // Save if 10 seconds elapsed since last save
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
                    // Codec/decoder errors
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
                val match = Regex("Response code: (\\d{3})").find(cause.message ?: "")
                if (match != null) return match.groupValues[1].toIntOrNull()
                cause = cause.cause
            }
            return null
        }

        private fun extractCodecInfo(message: String): String {
            // Extract codec info from error message like "video/hevc" or "hvc1.2.4.H150.B0"
            val codecRegex = Regex("video/(\\w+)|format=(\\w+)")
            val match = codecRegex.find(message)
            return match?.value?.replace("video/", "")?.uppercase() ?: ""
        }

        private fun updatePlaybackState() {
            // Don't overwrite error state
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
                    // For live streams, attempt auto-retry instead of showing "ended"
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
        private val onBandwidthUpdate: (bitrateEstimate: Long) -> Unit,
        private val onQualitySwitch: (count: Int) -> Unit
    ) : androidx.media3.exoplayer.analytics.AnalyticsListener {
        private var droppedFrames = 0L
        private var totalFrames = 0L
        private var rebufferCount = 0
        private var totalRebufferTimeMs = 0L
        private var rebufferStartTimeMs = 0L
        private var wasPlaying = false
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

        override fun onPlaybackStateChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            state: Int
        ) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    // Only count as rebuffer if we were previously playing (not initial buffer)
                    if (wasPlaying) {
                        rebufferCount++
                        rebufferStartTimeMs = SystemClock.elapsedRealtime()
                    }
                }
                Player.STATE_READY -> {
                    if (rebufferStartTimeMs > 0) {
                        totalRebufferTimeMs += SystemClock.elapsedRealtime() - rebufferStartTimeMs
                        rebufferStartTimeMs = 0L
                        onRebuffer(rebufferCount, totalRebufferTimeMs)
                    }
                    wasPlaying = true
                }
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    wasPlaying = false
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
            // Track video quality switches (ignore audio/subtitle format changes)
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

        fun getInstance(): StreamingPlaybackService? = instance

        fun getPlaybackState(service: StreamingPlaybackService): StateFlow<PlaybackState> {
            return service.playbackState
        }

        fun getCurrentMetadata(service: StreamingPlaybackService): StateFlow<PlayerMetadata> {
            return service.currentMetadata
        }
    }
}
