package org.njarasoa.fijerena.core.player.config

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * Adaptive LoadControl that swaps buffer profiles based on network type.
 * Optimized for low-latency starts on high-speed networks and deep buffers on cellular.
 */
@OptIn(UnstableApi::class)
class AdaptiveLoadControl(
    private val allocator: DefaultAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    private val contentType: PlayerConfigFactory.ContentType = PlayerConfigFactory.ContentType.VOD,
    private val cellularLiveMultiplier: Float = 1.0f,
    private val cellularVodMultiplier: Float = 1.0f
) : LoadControl {

    enum class BufferProfile(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int
    ) {
        HighSpeed(15_000, 50_000, 2_500, 5_000),
        Cellular(30_000, 100_000, 5_000, 10_000),
        UltraLowLatency(5_000, 15_000, 1_000, 2_500)
    }

    private var currentProfile: BufferProfile = BufferProfile.HighSpeed
    private var isBuffering = false

    fun updateForNetwork(networkType: NetworkType) {
        val newProfile = when (networkType) {
            NetworkType.CELLULAR -> BufferProfile.Cellular
            NetworkType.WIFI -> {
                if (contentType == PlayerConfigFactory.ContentType.LIVE_TV) BufferProfile.UltraLowLatency 
                else BufferProfile.HighSpeed
            }
            else -> BufferProfile.HighSpeed
        }
        
        if (currentProfile != newProfile) {
            currentProfile = newProfile
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepared() {
        // No-op
    }

    @Deprecated("Deprecated in Java")
    override fun onTracksSelected(
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>
    ) {
        // No-op
    }

    @Deprecated("Deprecated in Java")
    override fun onStopped() {
        // No-op
    }

    @Deprecated("Deprecated in Java")
    override fun onReleased() {
        // No-op
    }

    override fun getAllocator(): Allocator = allocator

    @Deprecated("Deprecated in Java")
    override fun getBackBufferDurationUs(): Long = 0

    @Deprecated("Deprecated in Java")
    override fun retainBackBufferFromKeyframe(): Boolean = false

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float
    ): Boolean {
        val multiplier = if (NetworkMonitor.currentNetworkType == NetworkType.CELLULAR) {
            if (contentType == PlayerConfigFactory.ContentType.LIVE_TV) cellularLiveMultiplier else cellularVodMultiplier
        } else 1.0f

        val minBufferUs = (currentProfile.minBufferMs * 1000L * multiplier).toLong()
        val maxBufferUs = (currentProfile.maxBufferMs * 1000L * multiplier).toLong()
        
        val targetBufferUs = if (isBuffering) maxBufferUs else minBufferUs
        val shouldLoad = bufferedDurationUs < targetBufferUs
        if (!shouldLoad) isBuffering = false
        return shouldLoad
    }

    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean {
        val minBufferUs = if (rebuffering) {
            currentProfile.bufferForPlaybackAfterRebufferMs * 1000L
        } else {
            currentProfile.bufferForPlaybackMs * 1000L
        }
        
        val canStart = bufferedDurationUs >= minBufferUs
        if (canStart) isBuffering = false
        return canStart
    }
}
