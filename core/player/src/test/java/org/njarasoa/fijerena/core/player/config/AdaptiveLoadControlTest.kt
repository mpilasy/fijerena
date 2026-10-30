package org.njarasoa.fijerena.core.player.config

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A swap of the inner load control must not reach the playback thread before the new control has
 * the player's state, and the byte cap must stop loading, also after a swap.
 */
class AdaptiveLoadControlTest {
    private val playerId = PlayerId.UNSET
    private val timeline =
        SinglePeriodTimeline(
            C.TIME_UNSET,
            false,
            false,
            false,
            null,
            MediaItem.EMPTY,
        )
    private val periodId = MediaPeriodId(timeline.getUidOfPeriod(0))

    private fun params(bufferedMs: Long) =
        LoadControl.Parameters(
            playerId,
            timeline,
            periodId,
            0L,
            bufferedMs * 1000,
            1f,
            true,
            false,
            C.TIME_UNSET,
            C.TIME_UNSET,
        )

    private fun prepared(control: AdaptiveLoadControl): AdaptiveLoadControl {
        control.onPrepared(playerId)
        control.onTracksSelected(params(0), TrackGroupArray.EMPTY, arrayOf<ExoTrackSelection?>())
        return control
    }

    @Test
    fun `a swap then a load check on the playback thread does not throw`() {
        val control = prepared(AdaptiveLoadControl(PlayerConfigFactory.ContentType.LIVE_TV))

        control.updateContentType(PlayerConfigFactory.ContentType.VOD)

        assertTrue(control.shouldContinueLoading(params(1_000)))
        assertFalse(control.shouldStartPlayback(params(0)))
    }

    @Test
    fun `a pending control installs before onTracksSelected and the back buffer reads`() {
        val control = prepared(AdaptiveLoadControl(PlayerConfigFactory.ContentType.VOD))

        control.updateForNetwork(NetworkType.CELLULAR)
        control.onTracksSelected(params(0), TrackGroupArray.EMPTY, arrayOf<ExoTrackSelection?>())

        assertEquals(0L, control.getBackBufferDurationUs(playerId))
        assertTrue(control.shouldContinueLoading(params(1_000)))
        control.onStopped(playerId)
    }

    @Test
    fun `a swap before prepare installs without a double prepare`() {
        val control = AdaptiveLoadControl(PlayerConfigFactory.ContentType.VOD)
        control.updateContentType(PlayerConfigFactory.ContentType.LIVE_TV)
        prepared(control)

        assertTrue(control.shouldContinueLoading(params(1_000)))
        // One prepare, so one stop removes the player; a later swap replays nothing.
        control.onStopped(playerId)
        control.updateContentType(PlayerConfigFactory.ContentType.VOD)
        prepared(control)
        assertTrue(control.shouldContinueLoading(params(1_000)))
    }

    @Test
    fun `the start threshold follows a pending swap`() {
        val control = AdaptiveLoadControl(PlayerConfigFactory.ContentType.VOD)
        assertEquals(NetworkBufferProfile.WIFI_VOD_PLAYBACK_MS, control.startThresholdMs(false))

        control.updateContentType(PlayerConfigFactory.ContentType.LIVE_TV)

        assertEquals(NetworkBufferProfile.WIFI_LIVE_PLAYBACK_MS, control.startThresholdMs(false))
        assertEquals(NetworkBufferProfile.WIFI_LIVE_REBUFFER_MS, control.startThresholdMs(true))
    }

    @Test
    fun `the VOD byte cap stops loading, also after a swap`() {
        val cap = 1024 * 1024
        val control = prepared(AdaptiveLoadControl(PlayerConfigFactory.ContentType.VOD, vodTargetBufferBytes = cap))
        val allocator = control.getAllocator(playerId)
        assertTrue(control.shouldContinueLoading(params(1_000)))
        assertTrue(control.shouldContinueLoading(params(40_000)))

        val allocations = mutableListOf<Allocation>()
        while (allocations.size * allocator.individualAllocationLength < cap) {
            allocations += allocator.allocate()
        }

        // Above the minimum buffer: the cap stops loading.
        assertFalse(control.shouldContinueLoading(params(40_000)))
        // The size cap also lets playback start before the time target (VOD puts size first).
        assertTrue(control.shouldStartPlayback(params(0)))

        control.updateForNetwork(NetworkType.CELLULAR)
        assertFalse(control.shouldContinueLoading(params(50_000)))
        // Below the minimum buffer, VOD still puts the size first.
        assertFalse(control.shouldContinueLoading(params(1_000)))

        allocations.forEach(allocator::release)
        // Below the cap, loading starts again only under the minimum buffer, as in Media3.
        assertFalse(control.shouldContinueLoading(params(50_000)))
        assertTrue(control.shouldContinueLoading(params(1_000)))
    }

    @Test
    fun `live TV puts time first below the minimum buffer`() {
        val control = prepared(AdaptiveLoadControl(PlayerConfigFactory.ContentType.LIVE_TV))
        val allocator = control.getAllocator(playerId)
        val allocations = mutableListOf<Allocation>()
        while (allocations.size * allocator.individualAllocationLength < NetworkBufferProfile.LIVE_TARGET_BUFFER_BYTES) {
            allocations += allocator.allocate()
        }

        assertTrue(control.shouldContinueLoading(params(1_000)))
        assertFalse(control.shouldContinueLoading(params(20_000)))
        allocations.forEach(allocator::release)
    }
}
