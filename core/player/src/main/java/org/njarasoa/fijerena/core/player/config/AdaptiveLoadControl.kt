package org.njarasoa.fijerena.core.player.config

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * A delegating [LoadControl] that swaps the inner [DefaultLoadControl] when the network type or
 * the content type changes, sharing a single [DefaultAllocator] across swaps to avoid memory churn.
 */
@OptIn(UnstableApi::class)
class AdaptiveLoadControl(
    private var contentType: PlayerConfigFactory.ContentType,
    private val cellularLiveMultiplier: Float = 1.0f,
    private val cellularVodMultiplier: Float = 1.0f,
    // From NetworkBufferProfile.vodTargetBufferBytes(largeMemoryClass) — the device-scaled cap.
    private val vodTargetBufferBytes: Int = NetworkBufferProfile.VOD_TARGET_BUFFER_BYTES,
) : LoadControl {
    private val sharedAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    // Written on Main only.
    private var networkType: NetworkType = NetworkMonitor.currentNetworkType

    // DefaultLoadControl keeps per-player state that onPrepared() sets up and that
    // shouldContinueLoading()/shouldStartPlayback()/onTracksSelected() read (checkNotNull). A
    // freshly built delegate has no such state, so its first use before onPrepared() throws a
    // NullPointerException. The cached calls below bring a new delegate up to the same state.
    // Read and written on the playback thread only.
    private var lastPreparedPlayerId: PlayerId? = null
    private var lastTracksSelected: TracksSelectedCall? = null

    // A swap has two threads: Main builds the new delegate and puts it here; the playback thread
    // takes it in install(), replays the cached calls on it, and only then uses it. So the
    // playback thread never uses a delegate that has not had onPrepared().
    private val pending = AtomicReference<Delegate?>(null)

    // The delegate in use. Written on the playback thread only (after construction); volatile for
    // the reads on Main in startThresholdMs().
    @Volatile
    private var current: Delegate = buildDelegate(networkType, contentType)

    // True when the byte cap stopped loading. Loading starts again below the minimum buffer, as in
    // DefaultLoadControl. Playback thread only.
    private var stoppedBySize = false

    /**
     * Video, ms, the player holds before it plays: at a start or after a seek, or after a stall
     * ([rebuffering]). For the Expert-mode buffering line; the size cap can start playback sooner.
     * Uses the newest delegate, also when the playback thread has not installed it yet.
     */
    fun startThresholdMs(rebuffering: Boolean): Int {
        val durations = (pending.get() ?: current).durations
        return if (rebuffering) durations.rebufferMs else durations.playbackMs
    }

    fun updateForNetwork(networkType: NetworkType) {
        this.networkType = networkType
        pending.set(buildDelegate(networkType, contentType))
    }

    fun updateContentType(contentType: PlayerConfigFactory.ContentType) {
        this.contentType = contentType
        pending.set(buildDelegate(networkType, contentType))
    }

    /**
     * Installs a pending delegate. Call it on the playback thread before each use of the delegate's
     * per-player state. With [replay], it first gives the new delegate the cached onPrepared and
     * onTracksSelected calls (Media3 asserts that onPrepared runs on the playback thread).
     */
    private fun install(replay: Boolean = true) {
        val next = pending.getAndSet(null) ?: return
        if (replay) {
            lastPreparedPlayerId?.let { next.control.onPrepared(it) }
            lastTracksSelected?.let { (parameters, trackGroups, trackSelections) ->
                next.control.onTracksSelected(parameters, trackGroups, trackSelections)
            }
        }
        stoppedBySize = false
        current = next
    }

    private data class TracksSelectedCall(
        val parameters: LoadControl.Parameters,
        val trackGroups: TrackGroupArray,
        val trackSelections: Array<out ExoTrackSelection?>,
    )

    private class Delegate(
        val control: DefaultLoadControl,
        val durations: BufferDurations,
        val targetBufferBytes: Int,
        val prioritizeTimeOverSize: Boolean,
    )

    private fun buildDelegate(
        networkType: NetworkType,
        contentType: PlayerConfigFactory.ContentType,
    ): Delegate {
        // WIFI and UNKNOWN both use the WiFi profile, matching NetworkMonitor's own default
        // (currentNetworkType starts as WIFI; UNKNOWN only follows an explicit network loss,
        // not a degraded-but-present connection) — written explicitly per branch rather than
        // an `else` fallthrough so the mapping stays obvious to the next reader.
        val durations =
            when (networkType) {
                NetworkType.CELLULAR -> {
                    when (contentType) {
                        PlayerConfigFactory.ContentType.LIVE_TV -> {
                            BufferDurations(
                                NetworkBufferProfile.getCellularLiveMinBuffer(cellularLiveMultiplier),
                                NetworkBufferProfile.getCellularLiveMaxBuffer(cellularLiveMultiplier),
                                NetworkBufferProfile.getCellularLivePlayback(cellularLiveMultiplier),
                                NetworkBufferProfile.getCellularLiveRebuffer(cellularLiveMultiplier),
                                NetworkBufferProfile.CELLULAR_LIVE_BACK_BUFFER_MS,
                            )
                        }

                        PlayerConfigFactory.ContentType.VOD -> {
                            BufferDurations(
                                NetworkBufferProfile.getCellularVodMinBuffer(cellularVodMultiplier),
                                NetworkBufferProfile.getCellularVodMaxBuffer(cellularVodMultiplier),
                                NetworkBufferProfile.getCellularVodPlayback(cellularVodMultiplier),
                                NetworkBufferProfile.getCellularVodRebuffer(cellularVodMultiplier),
                                NetworkBufferProfile.CELLULAR_VOD_BACK_BUFFER_MS,
                            )
                        }
                    }
                }

                NetworkType.WIFI, NetworkType.UNKNOWN -> {
                    when (contentType) {
                        PlayerConfigFactory.ContentType.LIVE_TV -> {
                            BufferDurations(
                                NetworkBufferProfile.WIFI_LIVE_MIN_BUFFER_MS,
                                NetworkBufferProfile.WIFI_LIVE_MAX_BUFFER_MS,
                                NetworkBufferProfile.WIFI_LIVE_PLAYBACK_MS,
                                NetworkBufferProfile.WIFI_LIVE_REBUFFER_MS,
                                NetworkBufferProfile.WIFI_LIVE_BACK_BUFFER_MS,
                            )
                        }

                        PlayerConfigFactory.ContentType.VOD -> {
                            BufferDurations(
                                NetworkBufferProfile.WIFI_VOD_MIN_BUFFER_MS,
                                NetworkBufferProfile.WIFI_VOD_MAX_BUFFER_MS,
                                NetworkBufferProfile.WIFI_VOD_PLAYBACK_MS,
                                NetworkBufferProfile.WIFI_VOD_REBUFFER_MS,
                                NetworkBufferProfile.WIFI_VOD_BACK_BUFFER_MS,
                            )
                        }
                    }
                }
            }

        val targetBufferBytes =
            when (contentType) {
                PlayerConfigFactory.ContentType.LIVE_TV -> NetworkBufferProfile.LIVE_TARGET_BUFFER_BYTES
                PlayerConfigFactory.ContentType.VOD -> vodTargetBufferBytes
            }

        // VOD deliberately prioritizes the size cap over the time target (see
        // NetworkBufferProfile.VOD_TARGET_BUFFER_BYTES's kdoc) to bound native memory on a
        // high-bitrate 4K VOD stream — keep that as-is. LIVE_TV needs the opposite: its time
        // targets (15-50s depending on network) are what a smooth live handover actually depends
        // on, but the same false setting let its much smaller byte cap cut buffering short well
        // before those targets on high-bitrate streams (16MB fills in ~3-5s at 25-40Mbps 4K),
        // repeatedly stalling and eventually exhausting recovery. Prioritizing time for LIVE_TV
        // only, plus the larger cap below, fixes that without reopening the VOD memory risk.
        val prioritizeTimeOverSize = contentType == PlayerConfigFactory.ContentType.LIVE_TV

        val control =
            DefaultLoadControl
                .Builder()
                .setAllocator(sharedAllocator)
                .setBufferDurationsMs(durations.minBufferMs, durations.maxBufferMs, durations.playbackMs, durations.rebufferMs)
                .setTargetBufferBytes(targetBufferBytes)
                .setBackBuffer(durations.backBufferMs, true)
                .setPrioritizeTimeOverSizeThresholds(prioritizeTimeOverSize)
                .build()
        return Delegate(control, durations, targetBufferBytes, prioritizeTimeOverSize)
    }

    private data class BufferDurations(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val playbackMs: Int,
        val rebufferMs: Int,
        val backBufferMs: Int,
    )

    // ── LoadControl implementation ──

    // Media3 never calls the deprecated methods; they forward to the delegate in use.
    @Suppress("DEPRECATION")
    @Deprecated("Use onPrepared(PlayerId) instead")
    override fun onPrepared() {
        current.control.onPrepared()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use onStopped(PlayerId) instead")
    override fun onStopped() {
        current.control.onStopped()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use onReleased(PlayerId) instead")
    override fun onReleased() {
        current.control.onReleased()
    }

    override fun onPrepared(playerId: PlayerId) {
        // No replay: this call prepares the new delegate, and Media3 calls onTracksSelected next.
        install(replay = false)
        stoppedBySize = false
        lastPreparedPlayerId = playerId
        current.control.onPrepared(playerId)
    }

    override fun onStopped(playerId: PlayerId) {
        // Install first, so that the delegate in use holds the player and resets the allocator.
        install()
        // A stopped player has no state in the delegate, so a later swap must not replay it.
        lastPreparedPlayerId = null
        lastTracksSelected = null
        current.control.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        install()
        // This also stops pinning the released player's TrackGroupArray/ExoTrackSelection objects.
        lastPreparedPlayerId = null
        lastTracksSelected = null
        current.control.onReleased(playerId)
    }

    // The allocator every delegate is built with: one pool for the player across swaps. Media3
    // calls this once, on the app thread, when it builds the player.
    override fun getAllocator(playerId: PlayerId): Allocator = sharedAllocator

    // The back buffer calls read only the delegate's settings, not its per-player state, and
    // Media3 makes one of them on the app thread, so they do not install.
    @Suppress("DEPRECATION")
    @Deprecated("Use getBackBufferDurationUs(PlayerId) instead")
    override fun getBackBufferDurationUs(): Long = current.control.backBufferDurationUs

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = current.control.getBackBufferDurationUs(playerId)

    @Suppress("DEPRECATION")
    @Deprecated("Use retainBackBufferFromKeyframe(PlayerId) instead")
    override fun retainBackBufferFromKeyframe(): Boolean = current.control.retainBackBufferFromKeyframe()

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = current.control.retainBackBufferFromKeyframe(playerId)

    // The byte cap. DefaultLoadControl counts a player's bytes only through the allocator that its
    // own getAllocator(playerId) gives. We give the shared allocator instead (Media3 takes the
    // allocator once, so a delegate's own allocator would stay with the first delegate after a
    // swap), so the delegate's count stays 0 and its cap never trips. We apply the cap here with
    // the shared allocator's total: for our one player it is the same number, and it survives a
    // swap. These are DefaultLoadControl's size rules; the delegate applies the time rules.
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        install()
        val delegate = current
        val loading = delegate.control.shouldContinueLoading(parameters)
        val sizeReached = sizeReached(delegate)
        var minBufferUs = Util.msToUs(delegate.durations.minBufferMs.toLong())
        if (parameters.playbackSpeed > 1) {
            minBufferUs =
                min(
                    Util.getMediaDurationForPlayoutDuration(minBufferUs, parameters.playbackSpeed),
                    Util.msToUs(delegate.durations.maxBufferMs.toLong()),
                )
        }
        minBufferUs = max(minBufferUs, MIN_BUFFER_FLOOR_US)
        if (parameters.bufferedDurationUs < minBufferUs) {
            stoppedBySize = sizeReached && !delegate.prioritizeTimeOverSize
        } else if (sizeReached) {
            stoppedBySize = true
        }
        return loading && !stoppedBySize
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        install()
        val delegate = current
        return delegate.control.shouldStartPlayback(parameters) ||
            (!delegate.prioritizeTimeOverSize && sizeReached(delegate))
    }

    private fun sizeReached(delegate: Delegate): Boolean = sharedAllocator.totalBytesAllocated >= delegate.targetBufferBytes

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        install()
        lastTracksSelected = TracksSelectedCall(parameters, trackGroups, trackSelections)
        current.control.onTracksSelected(parameters, trackGroups, trackSelections)
    }

    private companion object {
        // DefaultLoadControl's floor, so that playback cannot get stuck on a tiny minimum buffer.
        const val MIN_BUFFER_FLOOR_US = 500_000L
    }
}
