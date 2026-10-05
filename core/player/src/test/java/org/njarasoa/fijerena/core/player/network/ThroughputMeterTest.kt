package org.njarasoa.fijerena.core.player.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/plans/archive/20261004_playback-capability-errors-plan.md → P4. */
class ThroughputMeterTest {
    private val meter = ThroughputMeter(windowMs = 10_000L)

    @Test
    fun `steady 20 Mbit per second reads as 20 Mbit per second`() {
        // 250 kB every 100 ms = 2.5 MB/s = 20 Mbit/s, for 15 s.
        for (t in 0L until 15_000L step 100L) meter.onBytes(t, 250_000)
        assertEquals(20_000_000.0, meter.bitsPerSecond(15_000L).toDouble(), 500_000.0)
    }

    @Test
    fun `a short burst is averaged over at least a second`() {
        meter.onBytes(0L, 125_000)
        assertEquals(1_000_000L, meter.bitsPerSecond(100L))
    }

    @Test
    fun `nothing within the window reads as zero`() {
        meter.onBytes(0L, 1_000_000)
        assertEquals(0L, meter.bitsPerSecond(20_000L))
    }

    @Test
    fun `reset forgets everything`() {
        meter.onBytes(0L, 1_000_000)
        meter.reset()
        assertEquals(0L, meter.bitsPerSecond(500L))
    }
}
