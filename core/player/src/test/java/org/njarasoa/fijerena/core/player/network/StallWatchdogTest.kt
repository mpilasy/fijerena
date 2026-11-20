package org.njarasoa.fijerena.core.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** the playback capability errors plan → P4. */
class StallWatchdogTest {
    private val watchdog = StallWatchdog(limitMs = 60_000L)

    @Test
    fun `buffering with no data for the limit is a stall`() {
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 0L))
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 59_999L))
        assertTrue(watchdog.isStalled(isBuffering = true, nowMs = 60_000L))
    }

    @Test
    fun `data arriving restarts the minute`() {
        watchdog.isStalled(isBuffering = true, nowMs = 0L)
        watchdog.onData(nowMs = 50_000L)
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 100_000L))
        assertTrue(watchdog.isStalled(isBuffering = true, nowMs = 110_000L))
    }

    @Test
    fun `data from before buffering began doesn't shorten the minute`() {
        watchdog.onData(nowMs = 0L)
        watchdog.isStalled(isBuffering = false, nowMs = 100_000L)
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 100_000L))
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 159_999L))
        assertTrue(watchdog.isStalled(isBuffering = true, nowMs = 160_000L))
    }

    @Test
    fun `leaving buffering (playing, paused, idle between retries) starts over`() {
        watchdog.isStalled(isBuffering = true, nowMs = 0L)
        assertFalse(watchdog.isStalled(isBuffering = false, nowMs = 50_000L))
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 60_000L))
        assertTrue(watchdog.isStalled(isBuffering = true, nowMs = 120_000L))
    }

    @Test
    fun `reset forgets the stall in progress`() {
        watchdog.isStalled(isBuffering = true, nowMs = 0L)
        watchdog.reset()
        assertFalse(watchdog.isStalled(isBuffering = true, nowMs = 60_000L))
    }

    @Test
    fun `quiet time counts only after data has arrived`() {
        // A channel still starting: no data yet, so no reconnect.
        watchdog.isStalled(isBuffering = true, nowMs = 0L)
        assertEquals(0L, watchdog.quietMs(nowMs = 30_000L))
        // Data, then a stall: quiet since the last data.
        watchdog.onData(nowMs = 31_000L)
        watchdog.isStalled(isBuffering = true, nowMs = 45_000L)
        assertEquals(14_000L, watchdog.quietMs(nowMs = 45_000L))
        // Playing again.
        watchdog.isStalled(isBuffering = false, nowMs = 46_000L)
        assertEquals(0L, watchdog.quietMs(nowMs = 46_000L))
    }
}
