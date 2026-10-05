package org.njarasoa.fijerena.core.player.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/plans/20261004_playback-capability-errors-plan.md → P4. */
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
}
