package org.njarasoa.fijerena.core.player.config

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * A delegating [LoadControl] that swaps the inner [DefaultLoadControl] atomically
 * when the network type changes, sharing a single [DefaultAllocator] across swaps
 * to avoid memory churn.
 */
@OptIn(UnstableApi::class)
class AdaptiveLoadControl(
    private var contentType: PlayerConfigFactory.ContentType,
    private val cellularLiveMultiplier: Float = 1.0f,
    private val cellularVodMultiplier: Float = 1.0f,
) : LoadControl {
    private val sharedAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    private var networkType: NetworkType = NetworkMonitor.currentNetworkType

    // DefaultLoadControl is stateful across its lifecycle: shouldContinueLoading()/
    // shouldStartPlayback() rely on internal state that onTracksSelected() sets up. A freshly
    // built delegate hasn't seen that call, so swapping mid-playback used to crash the very
    // next shouldContinueLoading() with a NullPointerException. Caching the most recent
    // onPrepared/onTracksSelected calls lets a new delegate be brought up to the same state
    // immediately after a swap.
    private var lastPreparedPlayerId: PlayerId? = null
    private var lastTracksSelected: TracksSelectedCall? = null

    // When true, the delegate was swapped and needs lifecycle state replayed on the
    // playback thread. Cleared either by replayIfPending() (mid-playback swap) or by
    // a natural onPrepared() call from ExoPlayer (retry / new prepare).
    @Volatile
    private var pendingReplay = false

    // Swapped on network/content-type changes; LoadControl methods below always read through
    // this reference, so a swap takes effect on the very next call. Rebuilding just constructs
    // a fresh value object — it doesn't touch the player, the allocator, or buffered data — so
    // this is safe to do immediately rather than deferring to the next prepare()/recycle.
    @Volatile
    private var delegate: DefaultLoadControl = buildDelegate(networkType, contentType)

    fun updateForNetwork(networkType: NetworkType) {
        this.networkType = networkType
        delegate = buildDelegate(networkType, contentType)
        pendingReplay = true
    }

    fun updateContentType(contentType: PlayerConfigFactory.ContentType) {
        this.contentType = contentType
        delegate = buildDelegate(networkType, contentType)
        pendingReplay = true
    }

    /**
     * Replays cached [onPrepared]/[onTracksSelected] state on the current delegate.
     * Called from [shouldContinueLoading]/[shouldStartPlayback] which ExoPlayer always
     * invokes on the playback looper thread, satisfying Media3 1.7.1's thread assertion
     * in [DefaultLoadControl.onPrepared].
     */
    private fun replayIfPending() {
        if (pendingReplay) {
            pendingReplay = false
            lastPreparedPlayerId?.let { delegate.onPrepared(it) }
            lastTracksSelected?.let { (parameters, trackGroups, trackSelections) ->
                delegate.onTracksSelected(parameters, trackGroups, trackSelections)
            }
        }
    }

    private data class TracksSelectedCall(
        val parameters: LoadControl.Parameters,
        val trackGroups: TrackGroupArray,
        val trackSelections: Array<out ExoTrackSelection?>,
    )

    private fun buildDelegate(networkType: NetworkType, contentType: PlayerConfigFactory.ContentType): DefaultLoadControl {
        // WIFI and UNKNOWN both use the WiFi profile, matching NetworkMonitor's own default
        // (currentNetworkType starts as WIFI; UNKNOWN only follows an explicit network loss,
        // not a degraded-but-present connection) — written explicitly per branch rather than
        // an `else` fallthrough so the mapping stays obvious to the next reader.
        val durations =
            when (networkType) {
                NetworkType.CELLULAR ->
                    when (contentType) {
                        PlayerConfigFactory.ContentType.LIVE_TV ->
                            BufferDurations(
                                NetworkBufferProfile.getCellularLiveMinBuffer(cellularLiveMultiplier),
                                NetworkBufferProfile.getCellularLiveMaxBuffer(cellularLiveMultiplier),
                                NetworkBufferProfile.getCellularLivePlayback(cellularLiveMultiplier),
                                NetworkBufferProfile.getCellularLiveRebuffer(cellularLiveMultiplier),
                                NetworkBufferProfile.CELLULAR_LIVE_BACK_BUFFER_MS,
                            )
                        PlayerConfigFactory.ContentType.VOD ->
                            BufferDurations(
                                NetworkBufferProfile.getCellularVodMinBuffer(cellularVodMultiplier),
                                NetworkBufferProfile.getCellularVodMaxBuffer(cellularVodMultiplier),
                                NetworkBufferProfile.getCellularVodPlayback(cellularVodMultiplier),
                                NetworkBufferProfile.getCellularVodRebuffer(cellularVodMultiplier),
                                NetworkBufferProfile.CELLULAR_VOD_BACK_BUFFER_MS,
                            )
                    }
                NetworkType.WIFI, NetworkType.UNKNOWN ->
                    when (contentType) {
                        PlayerConfigFactory.ContentType.LIVE_TV ->
                            BufferDurations(
                                NetworkBufferProfile.WIFI_LIVE_MIN_BUFFER_MS,
                                NetworkBufferProfile.WIFI_LIVE_MAX_BUFFER_MS,
                                NetworkBufferProfile.WIFI_LIVE_PLAYBACK_MS,
                                NetworkBufferProfile.WIFI_LIVE_REBUFFER_MS,
                                NetworkBufferProfile.WIFI_LIVE_BACK_BUFFER_MS,
                            )
                        PlayerConfigFactory.ContentType.VOD ->
                            BufferDurations(
                                NetworkBufferProfile.WIFI_VOD_MIN_BUFFER_MS,
                                NetworkBufferProfile.WIFI_VOD_MAX_BUFFER_MS,
                                NetworkBufferProfile.WIFI_VOD_PLAYBACK_MS,
                                NetworkBufferProfile.WIFI_VOD_REBUFFER_MS,
                                NetworkBufferProfile.WIFI_VOD_BACK_BUFFER_MS,
                            )
                    }
            }

        return DefaultLoadControl.Builder()
            .setAllocator(sharedAllocator)
            .setBufferDurationsMs(durations.minBufferMs, durations.maxBufferMs, durations.playbackMs, durations.rebufferMs)
            .setBackBuffer(durations.backBufferMs, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    private data class BufferDurations(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val playbackMs: Int,
        val rebufferMs: Int,
        val backBufferMs: Int,
    )

    // ── LoadControl implementation (FULL DELEGATION TO SATISFY 1.5.1 / 1.7.1 RUNTIME) ──

    // Basic lifecycle methods (required by 1.5.1 interface)
    @Deprecated("Use onPrepared(PlayerId) instead")
    override fun onPrepared() {
        delegate.onPrepared()
    }

    @Deprecated("Use onStopped(PlayerId) instead")
    override fun onStopped() {
        delegate.onStopped()
    }

    @Deprecated("Use onReleased(PlayerId) instead")
    override fun onReleased() {
        delegate.onReleased()
    }

    // Analytics-aware lifecycle methods (often used by 1.7.1+ internal logic)
    override fun onPrepared(playerId: PlayerId) {
        pendingReplay = false  // Natural lifecycle call from ExoPlayer supersedes deferred replay
        lastPreparedPlayerId = playerId
        delegate.onPrepared(playerId)
    }

    override fun onStopped(playerId: PlayerId) {
        delegate.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        delegate.onReleased(playerId)
    }

    // Allocator (signature varies, implemented both via override where possible)
    override fun getAllocator(): Allocator = delegate.allocator

    @Deprecated("Use getBackBufferDurationUs(PlayerId) instead")
    override fun getBackBufferDurationUs(): Long = delegate.backBufferDurationUs

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    @Deprecated("Use retainBackBufferFromKeyframe(PlayerId) instead")
    override fun retainBackBufferFromKeyframe(): Boolean = delegate.retainBackBufferFromKeyframe()

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        replayIfPending()
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        replayIfPending()
        return delegate.shouldStartPlayback(parameters)
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        lastTracksSelected = TracksSelectedCall(parameters, trackGroups, trackSelections)
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }
}
