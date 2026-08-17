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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.njarasoa.fijerena.core.player.R
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.config.AdaptiveLoadControl
import org.njarasoa.fijerena.core.player.config.NetworkType
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

    private val _measuredDroppedFps = MutableStateFlow(0f)
    val measuredDroppedFps: StateFlow<Float> = _measuredDroppedFps.asStateFlow()

    // Percentage of frames dropped over the last ~10s. The cumulative rate averages a bad burst
    // away against however long the stream has been clean — half an hour of perfect playback
    // makes a 15-second stall look like a rounding error.
    private val _recentDropRate = MutableStateFlow(0f)
    val recentDropRate: StateFlow<Float> = _recentDropRate.asStateFlow()

    private val _streamHealthState = MutableStateFlow(org.njarasoa.fijerena.core.player.network.StreamHealthState())
    val streamHealthState: StateFlow<org.njarasoa.fijerena.core.player.network.StreamHealthState> = _streamHealthState.asStateFlow()

    private var onPositionSaveListener: ((Long, Long, Boolean, Int?, Int?) -> Unit)? = null

    private var retryCount = 0
    private var autoRetryAttempted = false
    private var lastErrorMessage: String? = null

    // Position to resume at on a VOD retry/reconnect — captured the moment a fault is first
    // observed, since the player's position is still valid then even though playback state is
    // about to flip to Error/Idle. Live retries reconnect to the live edge instead, so they
    // never read this.
    private var pendingResumePositionMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRetry: Runnable? = null

    private var adaptiveLoadControl: AdaptiveLoadControl? = null
    private var bandwidthMeter: androidx.media3.exoplayer.upstream.DefaultBandwidthMeter? = null
    private var serviceScope: CoroutineScope? = null
    private var healthMonitor: org.njarasoa.fijerena.core.player.network.StreamHealthMonitor? = null
    private var lastHealthCheckPosition: Long = -1L

    private val _isRecycling = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRecyclingFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRecycling.asStateFlow()

    private var recycleStartTimeMs: Long = 0L

    // A seek (esp. VOD scrubbing into an unbuffered region) briefly looks identical to real
    // degradation (low buffered margin) to the health monitor. This grace window suppresses
    // health-metric feeding right after a seek so scrubbing never triggers a spurious recycle.
    // Live streams don't seek, so this is a no-op for them.
    private var lastSeekTimeMs: Long = 0L

    private fun isWithinSeekGrace(): Boolean = (SystemClock.elapsedRealtime() - lastSeekTimeMs) < SEEK_HEALTH_GRACE_MS

    fun isRecycling(): Boolean = _isRecycling.value

    // While a recycle is in flight, non-Playing states are suppressed to keep it seamless.
    // On a healthy connection that gap is a few seconds; on a degraded one it can run much
    // longer, so the suppression only holds for this long before a real Buffering state is
    // allowed through instead of leaving the screen black with no indicator.
    private fun isWithinSeamlessGrace(): Boolean =
        isRecycling() && (SystemClock.elapsedRealtime() - recycleStartTimeMs) < SEAMLESS_RECYCLE_GRACE_MS

    private fun setRecycling(recycling: Boolean) {
        _isRecycling.value = recycling
        if (recycling) {
            recycleStartTimeMs = SystemClock.elapsedRealtime()
        }
    }

    private val recycleHandler = Runnable {
        val metadata = _currentMetadata.value
        val player = getPlayer()
        if (player != null) {
            val currentPos = player.currentPosition
            Log.i(TAG, "Executing silent stream recycle at position: $currentPos (isLive=${metadata.isLive})")

            // 1. Evict network connection pool to bypass ISP/CDN shaping.
            // Closing pooled connections can block on socket I/O, so this must not run on
            // the main thread (recycleHandler is posted via mainHandler.post).
            serviceScope?.launch(Dispatchers.IO) {
                org.njarasoa.fijerena.core.player.network.NetworkModule.evictConnectionPool()
            }

            // 2. Restart stream seamlessly without clearing the screen
            performSeamlessRecycle(metadata, currentPos)

            // 3. Reset monitor for the fresh connection
            healthMonitor?.reset()
        }
    }

    private fun performSeamlessRecycle(metadata: PlayerMetadata, currentPos: Long) {
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer
            ?: run {
                Log.w(TAG, "performSeamlessRecycle: no-op, mediaSession/player unavailable.")
                return
            }

        // A hard retry scheduled for the same underlying error must not be allowed to fire
        // later with a screen-clearing setMediaSource() call on top of this seamless swap.
        cancelPendingRetry()

        // Temporarily increase buffer requirement for recycling to ensure a 100% smooth handover
        setRecycling(true)

        val mediaSource = mediaSourceFactory?.createMediaSource(
            streamUrl = metadata.streamUrl,
            headers = metadata.headers,
            isLive = metadata.isLive,
            onRetry = { _streamRetryCount.value++ },
            transferListener = bandwidthMeter,
        ) ?: run {
            Log.w(TAG, "performSeamlessRecycle: no-op, mediaSourceFactory unavailable or createMediaSource() returned null.")
            return
        }

        // setMediaSource(source, resetPosition=false) keeps the current frame on screen
        // while the new source prepares in the background.
        player.setMediaSource(mediaSource, false)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onCreate() {
        super.onCreate()
        NetworkMonitor.init(this)
        mediaSourceFactory = StreamingMediaSourceFactory(this)

        healthMonitor = org.njarasoa.fijerena.core.player.network.StreamHealthMonitor(
            onStreamRecycleRequired = {
                mainHandler.post(recycleHandler)
            },
            onRecoveryExhausted = {
                mainHandler.post {
                    val isLive = _currentMetadata.value.isLive
                    Log.w(TAG, "Recovery exhausted: giving up after repeated recycle attempts (isLive=$isLive).")
                    stop()
                    val label = if (isLive) getString(R.string.player_error_stream_type_live) else getString(R.string.player_error_stream_type_video)
                    _playbackState.value =
                        PlaybackState.Error(
                            getString(R.string.player_error_recovery_exhausted_format, label),
                        )
                }
            },
        )

        initializePlayer()
        // Only publish the instance after initializePlayer() so getInstance()/awaitInstance()
        // callers (e.g. PlaybackViewModel.playStream(), and TvPlayerScreen's setContentType()/
        // setPositionSaveListener() calls fired from a LaunchedEffect right as the player
        // screen mounts) can't resolve to a service whose mediaSession/adaptiveLoadControl is
        // still null. Those callers silently no-op on null (`?: return`, `?.`) with no log, so
        // the previous early-publish made failures invisible — playStream()'s case produced a
        // black screen stuck in Idle forever, with no diagnostic trail.
        instance = this
        instanceReady.complete(this)
        acquireWakeLock()
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        serviceScope = scope
        scope.launch {
            healthMonitor?.state?.collect {
                _streamHealthState.value = it
            }
        }
        scope.launch {
            var previousNetworkType = NetworkMonitor.currentNetworkType
            NetworkMonitor.networkType.collect { networkType ->
                adaptiveLoadControl?.updateForNetwork(networkType)

                val connectivityRestored =
                    previousNetworkType == NetworkType.UNKNOWN && networkType != NetworkType.UNKNOWN
                previousNetworkType = networkType

                if (connectivityRestored &&
                    !autoRetryAttempted &&
                    _currentMetadata.value.streamUrl.isNotEmpty() &&
                    _playbackState.value is PlaybackState.Error
                ) {
                    autoRetryAttempted = true
                    val metadata = _currentMetadata.value
                    val resumePosition = if (metadata.isLive) 0L else pendingResumePositionMs
                    Log.i(TAG, "Connectivity restored after recovery exhaustion — auto-retrying once (isLive=${metadata.isLive}).")
                    playStream(metadata, resumePosition)
                }
            }
        }
        startHealthMonitorLoop(scope)
        startPositionSaveLoop(scope)
    }

    // Wall-clock-driven, unlike PlayerListener's save-on-state-change: a long stretch of
    // smooth, uninterrupted playback fires none of onPlaybackStateChanged/onIsPlayingChanged/
    // onPlayWhenReadyChanged, so without this loop nothing gets saved for as long as that
    // stretch lasts — resume position can end up stale by however long since the last real
    // event (pause/seek/rebuffer/track-change). This guarantees a save at least every
    // POSITION_SAVE_INTERVAL_MS regardless of whether any such event ever fires.
    private fun startPositionSaveLoop(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                val player = getPlayer() ?: continue
                if (player.isPlaying && player.playbackState == Player.STATE_READY) {
                    onPositionSaveListener?.invoke(player.currentPosition, player.duration, false, null, null)
                }
            }
        }
    }

    private fun startHealthMonitorLoop(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(healthMonitor?.config?.evaluationIntervalMs ?: 5000L)
                val player = getPlayer()
                val metadata = _currentMetadata.value
                if (player != null && metadata.streamUrl.isNotEmpty() && !isWithinSeekGrace()) {
                    val state = player.playbackState
                    if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                        val position = player.currentPosition
                        // Position not moving while ExoPlayer claims READY+playing isn't caught by
                        // the buffered-margin check below: bufferedPosition stalls right alongside
                        // currentPosition, so their difference still looks like a healthy buffer.
                        val isStalled =
                            state == Player.STATE_READY &&
                                player.isPlaying &&
                                lastHealthCheckPosition == position
                        lastHealthCheckPosition = position
                        healthMonitor?.updateMetrics(
                            bufferedDurationMs = player.bufferedPosition - player.currentPosition,
                            droppedFramesPerSecond = _measuredDroppedFps.value,
                            hasReadTimeout = isStalled
                        )
                    }
                }
            }
        }
    }

    private fun initializePlayer(contentType: PlayerConfigFactory.ContentType = PlayerConfigFactory.ContentType.VOD) {
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        Log.i(TAG, "FFmpeg library available: $ffmpegAvailable")

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

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
                context = this,
                onStateChanged = { newState ->
                    val isRecycling = isRecycling()
                    Log.d(TAG, "onStateChanged: newState=$newState, isRecycling=$isRecycling")
                    
                    if (newState is PlaybackState.Playing) {
                        retryCount = 0
                        healthMonitor?.notifyStablePlayback()
                        // Reset recycling mode once we are successfully playing the new stream
                        if (isRecycling) {
                            Log.i(TAG, "Successfully resumed after recycle. Resetting recycling flag.")
                            setRecycling(false)
                        }
                    }

                    // Suppress any non-final state during a silent recycle to keep it seamless.
                    // The player can pass through Buffering/Paused/Idle with not-yet-valid
                    // (e.g. negative) position data while the new source's timeline resolves;
                    // only a confirmed Playing or a terminal Error should reach the UI. This
                    // only holds for SEAMLESS_RECYCLE_GRACE_MS — past that, let the real state
                    // (almost always Buffering) through so a slow recovery shows a spinner
                    // instead of leaving the screen black with no indicator at all.
                    if (isWithinSeamlessGrace() && newState !is PlaybackState.Playing && newState !is PlaybackState.Error) {
                        Log.d(TAG, "Suppressing $newState during silent recycle.")
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
                onDroppedFpsUpdate = { droppedFps ->
                    _measuredDroppedFps.value = droppedFps
                },
                onRecentDropRateUpdate = { dropRatePercent ->
                    _recentDropRate.value = dropRatePercent
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
        val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer
            ?: run {
                Log.w(TAG, "playStream: no-op, mediaSession/player unavailable.")
                return
            }
        cancelPendingRetry()
        playerListener?.resetErrorState()
        retryCount = 0
        autoRetryAttempted = false
        lastErrorMessage = null
        pendingResumePositionMs = 0L
        healthMonitor?.notifyStablePlayback()
        healthMonitor?.reset()
        // A new stream has no position history yet — without this, the stall check in
        // startHealthMonitorLoop() compares the new stream's first sampled position against
        // whatever the PREVIOUS stream's position happened to be, and a coincidental match
        // false-positives an immediate recycle.
        lastHealthCheckPosition = -1L
        _streamRetryCount.value = 0
        _rebufferCount.value = 0
        _exhaustionRebufferCount.value = 0
        _totalRebufferTimeMs.value = 0L
        _bandwidthEstimate.value = 0L
        _qualitySwitchCount.value = 0
        analyticsListener?.reset()
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
            ) ?: run {
                Log.w(TAG, "playStream: no-op, mediaSourceFactory unavailable or createMediaSource() returned null.")
                return
            }

        // Fully release the outgoing renderer/decoder session before starting the next one.
        // setMediaSource() alone replaces the playlist but can reuse the existing renderer
        // rather than releasing it, which on this emulator's decoder leaves the old codec
        // session still delivering in-flight frames to its Surface after the new one starts —
        // rendering two channels overlapping. An explicit stop() forces a clean teardown first.
        player.stop()
        player.setMediaSource(mediaSource)
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
        player.prepare()
    }

    private fun attemptStreamRetry(metadata: PlayerMetadata) {
        val maxRetries = if (metadata.isLive) MAX_LIVE_RETRIES else MAX_VOD_RETRIES
        if (retryCount >= maxRetries) {
            val detail = lastErrorMessage ?: if (metadata.isLive) getString(R.string.player_error_channel_offline) else getString(R.string.player_error_check_connection)
            val label = if (metadata.isLive) getString(R.string.player_error_stream_type_live) else getString(R.string.player_error_stream_type_video)
            _playbackState.value =
                PlaybackState.Error(
                    getString(R.string.player_error_retry_exhausted_format, label, maxRetries, detail),
                )
            return
        }

        // A seamless recycle is already handling recovery for this disruption; don't race it
        // with a hard, screen-clearing retry.
        if (isRecycling()) {
            Log.i(TAG, "Skipping hard retry: seamless recycle already in progress.")
            return
        }

        // Hard retry should use fast-startup settings
        setRecycling(false)

        retryCount++
        _streamRetryCount.value++
        val baseDelay = if (metadata.isLive) LIVE_RETRY_BASE_DELAY_MS else VOD_RETRY_BASE_DELAY_MS
        val delayMs = baseDelay * retryCount
        Log.i(TAG, "Stream retry $retryCount/$maxRetries in ${delayMs}ms (isLive=${metadata.isLive})")

        // updatePlaybackState() already pushed the player's raw STATE_IDLE through before
        // onPlayerError ran, so without this the screen goes silently blank for the whole
        // retry delay — indistinguishable from a dead app. Show buffering instead.
        _playbackState.value = PlaybackState.Buffering

        // Captured now, not read from the field inside the closure — a later fault (before this
        // delayed retry fires) could overwrite pendingResumePositionMs with a different value.
        val resumePosition = pendingResumePositionMs

        val retryRunnable =
            Runnable {
                val player = mediaSession?.player as? androidx.media3.exoplayer.ExoPlayer ?: return@Runnable
                playerListener?.resetErrorState()
                healthMonitor?.reset()

                val mediaSource =
                    mediaSourceFactory?.createMediaSource(
                        streamUrl = metadata.streamUrl,
                        headers = metadata.headers,
                        isLive = metadata.isLive,
                        onRetry = { _streamRetryCount.value++ },
                        transferListener = bandwidthMeter,
                    ) ?: return@Runnable

                player.setMediaSource(mediaSource)
                // Live reconnects at the live edge; VOD must resume where it failed.
                if (!metadata.isLive && resumePosition > 0L) {
                    player.seekTo(resumePosition)
                }
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
        if (errorMessage == null && !metadata.isLive) {
            // Natural end of VOD content — not a fault, never retried.
            _playbackState.value = PlaybackState.Ended
            return
        }
        lastErrorMessage = errorMessage
        pendingResumePositionMs = getPlayer()?.currentPosition?.coerceAtLeast(0L) ?: pendingResumePositionMs
        attemptStreamRetry(metadata)
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
        // A pending autonomous recycle (StreamHealthMonitor) must not be allowed to silently
        // resurrect playback right after a deliberate stop.
        mainHandler.removeCallbacks(recycleHandler)
        setRecycling(false)
        healthMonitor?.reset()
        mediaSession?.player?.stop()
        _playbackState.value = PlaybackState.Idle
        releaseWakeLock()
    }

    fun seekTo(position: Long) {
        lastSeekTimeMs = SystemClock.elapsedRealtime()
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
                            language = format.language ?: getString(R.string.player_track_language_unknown),
                            label = format.label ?: getString(R.string.player_track_audio_fallback_label_format, format.language ?: getString(R.string.player_track_generic_label), format.channelCount),
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
                            language = format.language ?: getString(R.string.player_track_language_unknown),
                            label = format.label ?: format.language ?: getString(R.string.player_track_subtitle_fallback_label_format, trackIndex + 1),
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

        // Save choice immediately. Persist the consolidated (flattened, cross-group) index —
        // not the raw in-group trackIndex — since selectAudioTrack(consolidatedIndex) is what
        // resumes it on restore, and that overload indexes into getAudioTracks()'s flattened
        // list, not per-group.
        val consolidatedIndex = getAudioTracks().indexOfFirst { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
        onPositionSaveListener?.invoke(
            player.currentPosition,
            player.duration,
            !player.isPlaying,
            consolidatedIndex.takeIf { it >= 0 },
            null,
        )
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

        // Save choice immediately. Persist the consolidated (flattened, cross-group) index —
        // not the raw in-group trackIndex — since selectSubtitleTrack(consolidatedIndex) is
        // what resumes it on restore, and that overload indexes into getSubtitleTracks()'s
        // flattened list, not per-group.
        val consolidatedIndex = getSubtitleTracks().indexOfFirst { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
        onPositionSaveListener?.invoke(
            player.currentPosition,
            player.duration,
            !player.isPlaying,
            null,
            consolidatedIndex.takeIf { it >= 0 },
        )
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
            (player as? androidx.media3.exoplayer.ExoPlayer)?.removeAnalyticsListener(analyticsListener!!)
            player.release()
            release()
        }
        mediaSession = null
        releaseWakeLock()
        wakeLock = null
        playerListener = null
        analyticsListener = null
        serviceScope?.cancel()
        serviceScope = null
        adaptiveLoadControl = null
        NetworkMonitor.release()
        instance = null
        // If Android recreates this service later in the same process (e.g. after
        // reclaiming it during long standby), the next onCreate() needs a fresh,
        // not-yet-completed deferred to publish into — instanceReady.complete() is a
        // silent no-op once already completed, so without this reset awaitInstance()
        // would hand out this now-destroyed instance forever.
        instanceReady = kotlinx.coroutines.CompletableDeferred()
        super.onDestroy()
    }

    private class PlayerListener(
        private val context: Context,
        private val onStateChanged: (PlaybackState) -> Unit,
        private val onWakeLockRequired: () -> Unit,
        private val player: Player,
        private val onPositionSave: ((Long, Long, Boolean, Int?, Int?) -> Unit)? = null,
        private val onStreamEndedOrError: (errorMessage: String?) -> Unit = {},
    ) : Player.Listener {
        private var isInErrorState = false
        private val saveIntervalMs = POSITION_SAVE_INTERVAL_MS
        private var lastSavedPosition = -saveIntervalMs

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
                        context.getString(R.string.player_error_codec_unsupported_format, codecInfo)
                    } else {
                        context.getString(R.string.player_error_format_unsupported)
                    }
                }
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                -> {
                    context.getString(R.string.player_error_network_failed)
                }
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                    val code = extractHttpStatusCode(error)
                    when (code) {
                        401 -> context.getString(R.string.player_error_http_401)
                        403 -> context.getString(R.string.player_error_http_403)
                        404 -> context.getString(R.string.player_error_http_404)
                        456, 458 -> context.getString(R.string.player_error_http_connection_limit_format, code)
                        502, 503, 504 -> context.getString(R.string.player_error_http_server_unavailable_format, code)
                        in 500..599 -> context.getString(R.string.player_error_http_server_error_format, code)
                        in 400..499 -> context.getString(R.string.player_error_http_access_denied_format, code)
                        else -> if (code != null) context.getString(R.string.player_error_http_unavailable_format, code) else context.getString(R.string.player_error_stream_unavailable)
                    }
                }
                PlaybackException.ERROR_CODE_TIMEOUT -> {
                    context.getString(R.string.player_error_playback_timeout)
                }
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> {
                    context.getString(R.string.player_error_stream_not_found)
                }
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                -> {
                    context.getString(R.string.player_error_invalid_stream_format)
                }
                else -> {
                    context.getString(R.string.player_error_generic_format, error.errorCodeName)
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
        private val onDroppedFpsUpdate: (droppedFps: Float) -> Unit,
        private val onRecentDropRateUpdate: (dropRatePercent: Float) -> Unit,
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
        private var fpsLastDroppedFrameCount = 0L

        /** Per-second (rendered, dropped) samples, trimmed to the last [RECENT_WINDOW_SAMPLES]. */
        private val recentSamples = ArrayDeque<Pair<Long, Long>>()

        // playStream() zeroes the published StateFlow counters on every channel switch, but this
        // listener instance lives for the whole service lifetime — without resetting these too,
        // the next real rebuffer republishes a stale, session-wide count in one jump instead of
        // a small per-stream delta, which used to trip the exhaustion-toast threshold instantly.
        fun reset() {
            droppedFrames = 0L
            totalFrames = 0L
            rebufferCount = 0
            exhaustionRebufferCount = 0
            totalRebufferTimeMs = 0L
            rebufferStartTimeMs = 0L
            wasPlaying = false
            seekPending = false
            qualitySwitchCount = 0
            lastVideoHeight = -1
            fpsLastTimeMs = 0L
            fpsLastFrameCount = 0L
            fpsLastDroppedFrameCount = 0L
            recentSamples.clear()
            onRecentDropRateUpdate(0f)
        }

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
                fpsLastDroppedFrameCount = droppedFrames
            } else {
                val delta = currentTime - fpsLastTimeMs
                if (delta >= 1000) {
                    val frames = totalFrames - fpsLastFrameCount
                    val fps = (frames * 1000f) / delta
                    onFpsUpdate(fps)
                    
                    val dropped = droppedFrames - fpsLastDroppedFrameCount
                    val droppedFps = (dropped * 1000f) / delta
                    onDroppedFpsUpdate(droppedFps)

                    recentSamples.addLast(frames to dropped)
                    while (recentSamples.size > RECENT_WINDOW_SAMPLES) recentSamples.removeFirst()
                    val windowFrames = recentSamples.sumOf { it.first }
                    val windowDropped = recentSamples.sumOf { it.second }
                    onRecentDropRateUpdate(
                        if (windowFrames > 0) (windowDropped * 100f) / windowFrames else 0f,
                    )

                    fpsLastTimeMs = currentTime
                    fpsLastFrameCount = totalFrames
                    fpsLastDroppedFrameCount = droppedFrames
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
            
            // Feed health monitor on every state change. Skipped right after a seek (VOD
            // scrubbing) — a seek briefly looks like a low-buffer degradation event otherwise.
            val service = instance
            val player = service?.getPlayer()
            if (service != null && player != null && !service.isWithinSeekGrace()) {
                service.healthMonitor?.updateMetrics(
                    bufferedDurationMs = player.bufferedPosition - player.currentPosition,
                    droppedFramesPerSecond = service._measuredDroppedFps.value, 
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

        private companion object {
            /** One sample per second, so this is a ~10s trailing window. */
            const val RECENT_WINDOW_SAMPLES = 10
        }
    }

    companion object {
        private const val TAG = "StreamingPlaybackService"
        private const val MAX_LIVE_RETRIES = 3
        private const val LIVE_RETRY_BASE_DELAY_MS = 2000L
        private const val MAX_VOD_RETRIES = 3
        private const val VOD_RETRY_BASE_DELAY_MS = 3000L
        private const val SEEK_HEALTH_GRACE_MS = 6000L
        private const val SEAMLESS_RECYCLE_GRACE_MS = 7000L
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L

        @Volatile
        private var instance: StreamingPlaybackService? = null

        @Volatile
        private var instanceReady = kotlinx.coroutines.CompletableDeferred<StreamingPlaybackService>()

        fun getInstance(): StreamingPlaybackService? = instance

        suspend fun awaitInstance(): StreamingPlaybackService = instanceReady.await()

        fun getPlaybackState(service: StreamingPlaybackService): StateFlow<PlaybackState> = service.playbackState

        fun getCurrentMetadata(service: StreamingPlaybackService): StateFlow<PlayerMetadata> = service.currentMetadata
    }
}
