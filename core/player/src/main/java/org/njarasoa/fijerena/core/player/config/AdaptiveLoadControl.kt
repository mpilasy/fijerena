@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.config

import androidx.media3.common.C
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
class AdaptiveLoadControl(
    private var contentType: PlayerConfigFactory.ContentType,
    private val cellularLiveMultiplier: Float = 1.0f,
    private val cellularVodMultiplier: Float = 1.0f
) : LoadControl {

    private val sharedAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    @Volatile
    private var currentNetworkType: NetworkType = NetworkMonitor.currentNetworkType

    @Volatile
    private var delegate: DefaultLoadControl = buildDelegate(currentNetworkType)

    fun updateForNetwork(networkType: NetworkType) {
        currentNetworkType = networkType
        delegate = buildDelegate(networkType)
    }

    fun updateForContentType(contentType: PlayerConfigFactory.ContentType) {
        this.contentType = contentType
        delegate = buildDelegate(currentNetworkType)
    }

    private fun buildDelegate(networkType: NetworkType): DefaultLoadControl {
        // Standard, proven buffer settings used by most top-tier IPTV apps
        return DefaultLoadControl.Builder()
            .setAllocator(sharedAllocator)
            .setBufferDurationsMs(
                15_000, // minBufferMs
                50_000, // maxBufferMs
                2_500,  // bufferForPlaybackMs
                5_000   // bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(10_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // ── LoadControl implementation (FULL DELEGATION TO SATISFY 1.5.1 / 1.7.1 RUNTIME) ──

    // Basic lifecycle methods (required by 1.5.1 interface)
    override fun onPrepared() { delegate.onPrepared() }
    override fun onStopped() { delegate.onStopped() }
    override fun onReleased() { delegate.onReleased() }

    // Analytics-aware lifecycle methods (often used by 1.7.1+ internal logic)
    override fun onPrepared(playerId: PlayerId) { delegate.onPrepared(playerId) }
    override fun onStopped(playerId: PlayerId) { delegate.onStopped(playerId) }
    override fun onReleased(playerId: PlayerId) { delegate.onReleased(playerId) }

    // Allocator (signature varies, implemented both via override where possible)
    override fun getAllocator(): Allocator = delegate.allocator

    // Properties / Methods for back buffer duration
    override fun getBackBufferDurationUs(): Long {
        return delegate.backBufferDurationUs
    }
    override fun getBackBufferDurationUs(playerId: PlayerId): Long {
        return delegate.getBackBufferDurationUs(playerId)
    }

    // Properties / Methods for retaining back buffer from keyframe
    override fun retainBackBufferFromKeyframe(): Boolean {
        return delegate.retainBackBufferFromKeyframe()
    }
    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean {
        return delegate.retainBackBufferFromKeyframe(playerId)
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        return delegate.shouldStartPlayback(parameters)
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>
    ) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }
}
