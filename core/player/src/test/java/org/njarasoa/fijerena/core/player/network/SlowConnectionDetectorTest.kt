package org.njarasoa.fijerena.core.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.SlowConnection

/** docs/plans/archive/20261004_playback-capability-errors-plan.md → P4. */
class SlowConnectionDetectorTest {
    private val detector = SlowConnectionDetector(unknownBitrateWindowMs = 120_000L, clearAfterMs = 60_000L)

    @Test
    fun `a rebuffer below a known bitrate shows the numbers at once`() {
        assertEquals(SlowConnection(60_000_000L, 20_000_000L), detector.onRebuffer(0L, 60_000_000, 20_000_000L))
    }

    @Test
    fun `a rebuffer with enough bandwidth, or no estimate yet, shows nothing`() {
        assertNull(detector.onRebuffer(0L, 60_000_000, 80_000_000L))
        assertNull(detector.onRebuffer(1_000L, 60_000_000, 0L))
    }

    @Test
    fun `with an unknown bitrate the second rebuffer within the window shows the banner without numbers`() {
        assertNull(detector.onRebuffer(0L, -1, 5_000_000L))
        assertEquals(SlowConnection(null, null), detector.onRebuffer(120_000L, -1, 5_000_000L))
    }

    @Test
    fun `with an unknown bitrate rebuffers further apart show nothing`() {
        assertNull(detector.onRebuffer(0L, -1, 5_000_000L))
        assertNull(detector.onRebuffer(120_001L, -1, 5_000_000L))
    }

    @Test
    fun `the banner goes after a minute without a rebuffer`() {
        detector.onRebuffer(0L, 60_000_000, 20_000_000L)
        assertEquals(SlowConnection(60_000_000L, 20_000_000L), detector.onTick(59_999L))
        assertNull(detector.onTick(60_000L))
    }

    @Test
    fun `reset clears the banner`() {
        detector.onRebuffer(0L, 60_000_000, 20_000_000L)
        detector.reset()
        assertNull(detector.onTick(1L))
    }
}
