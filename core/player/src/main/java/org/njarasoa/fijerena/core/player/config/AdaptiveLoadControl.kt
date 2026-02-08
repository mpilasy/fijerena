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
 *
 * Thread safety: [delegate] is @Volatile — written from the main thread (StateFlow
 * collection), read from ExoPlayer's loading thread. Single-writer/multi-reader.
 */
class AdaptiveLoadControl(
    private val contentType: PlayerConfigFactory.ContentType
) : LoadControl {

    private val sharedAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    @Volatile
    private var delegate: DefaultLoadControl = buildDelegate(NetworkMonitor.currentNetworkType)

    /**
     * Swap the inner delegate to match the given network type.
     * Called from the main-thread StateFlow collector in StreamingPlaybackService.
     */
    fun updateForNetwork(networkType: NetworkType) {
        delegate = buildDelegate(networkType)
    }

    private fun buildDelegate(networkType: NetworkType): DefaultLoadControl {
        val isWifi = networkType != NetworkType.CELLULAR
        val isLive = contentType == PlayerConfigFactory.ContentType.LIVE_TV

        val minBuffer: Int
        val maxBuffer: Int
        val playback: Int
        val rebuffer: Int
        val backBuffer: Int
        val retainKeyframe: Boolean

        if (isLive) {
            if (isWifi) {
                minBuffer = NetworkBufferProfile.WIFI_LIVE_MIN_BUFFER_MS
                maxBuffer = NetworkBufferProfile.WIFI_LIVE_MAX_BUFFER_MS
                playback = NetworkBufferProfile.WIFI_LIVE_PLAYBACK_MS
                rebuffer = NetworkBufferProfile.WIFI_LIVE_REBUFFER_MS
                backBuffer = NetworkBufferProfile.WIFI_LIVE_BACK_BUFFER_MS
            } else {
                minBuffer = NetworkBufferProfile.CELLULAR_LIVE_MIN_BUFFER_MS
                maxBuffer = NetworkBufferProfile.CELLULAR_LIVE_MAX_BUFFER_MS
                playback = NetworkBufferProfile.CELLULAR_LIVE_PLAYBACK_MS
                rebuffer = NetworkBufferProfile.CELLULAR_LIVE_REBUFFER_MS
                backBuffer = NetworkBufferProfile.CELLULAR_LIVE_BACK_BUFFER_MS
            }
            retainKeyframe = false
        } else {
            if (isWifi) {
                minBuffer = NetworkBufferProfile.WIFI_VOD_MIN_BUFFER_MS
                maxBuffer = NetworkBufferProfile.WIFI_VOD_MAX_BUFFER_MS
                playback = NetworkBufferProfile.WIFI_VOD_PLAYBACK_MS
                rebuffer = NetworkBufferProfile.WIFI_VOD_REBUFFER_MS
                backBuffer = NetworkBufferProfile.WIFI_VOD_BACK_BUFFER_MS
            } else {
                minBuffer = NetworkBufferProfile.CELLULAR_VOD_MIN_BUFFER_MS
                maxBuffer = NetworkBufferProfile.CELLULAR_VOD_MAX_BUFFER_MS
                playback = NetworkBufferProfile.CELLULAR_VOD_PLAYBACK_MS
                rebuffer = NetworkBufferProfile.CELLULAR_VOD_REBUFFER_MS
                backBuffer = NetworkBufferProfile.CELLULAR_VOD_BACK_BUFFER_MS
            }
            retainKeyframe = true
        }

        val builder = DefaultLoadControl.Builder()
            .setAllocator(sharedAllocator)
            .setBufferDurationsMs(minBuffer, maxBuffer, playback, rebuffer)
            .setBackBuffer(backBuffer, retainKeyframe)

        if (!isWifi) {
            builder.setPrioritizeTimeOverSizeThresholds(true)
        }

        return builder.build()
    }

    // ── LoadControl delegation (Media3 1.9 API) ────────────────────

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = sharedAllocator

    override fun getBackBufferDurationUs(playerId: PlayerId): Long =
        delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldStartPlayback(parameters)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)
}
