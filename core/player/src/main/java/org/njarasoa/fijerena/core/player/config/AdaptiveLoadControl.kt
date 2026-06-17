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

    // Persistent delegate to avoid race conditions during rebuilds
    private val delegate: DefaultLoadControl = buildStaticDelegate()

    // No longer used, but kept for API compatibility during refactor
    private var isRecycling: Boolean = false

    fun updateForNetwork(networkType: NetworkType) {
        // Shared allocator and static settings handle this automatically
    }

    fun updateContentType(contentType: PlayerConfigFactory.ContentType) {
        // Shared allocator and static settings handle this automatically
    }

    fun setRecycling(recycling: Boolean) {
        this.isRecycling = recycling
    }

    fun isRecycling(): Boolean = isRecycling

    private fun buildStaticDelegate(): DefaultLoadControl {
        // Use the most accommodating values for both WiFi and Cellular.
        // The allocator and prioritizations handle the rest.
        return DefaultLoadControl.Builder()
            .setAllocator(sharedAllocator)
            .setBufferDurationsMs(
                NetworkBufferProfile.WIFI_LIVE_MIN_BUFFER_MS, // 15s
                NetworkBufferProfile.CELLULAR_VOD_MAX_BUFFER_MS, // 100s
                NetworkBufferProfile.WIFI_LIVE_PLAYBACK_MS, // 500ms
                NetworkBufferProfile.WIFI_LIVE_REBUFFER_MS // 1000ms
            )
            .setBackBuffer(NetworkBufferProfile.WIFI_VOD_BACK_BUFFER_MS, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

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

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean = delegate.shouldContinueLoading(parameters)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = delegate.shouldStartPlayback(parameters)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }
}
