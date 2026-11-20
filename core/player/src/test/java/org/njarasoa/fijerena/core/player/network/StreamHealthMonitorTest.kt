package org.njarasoa.fijerena.core.player.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mobile data playback plan → Phase 3: stalls by movement, no buffer-depth rule. */
class StreamHealthMonitorTest {
    private val reasons = mutableListOf<String>()
    private val monitor = StreamHealthMonitor(onStreamRecycleRequired = { reasons += it })

    @Test
    fun `a small buffer that moves is healthy`() {
        // Ticks with no stall and no dropped frames: what a live MPEG-TS stream on mobile data
        // with 1-2 s buffered sends. The old rule reconnected it after 20 s.
        repeat(20) { monitor.updateMetrics(droppedFramesPerSecond = 0f, stallReason = null) }
        assertTrue(reasons.isEmpty())
    }

    @Test
    fun `a stall reconnects at once, with its reason`() {
        monitor.updateMetrics(droppedFramesPerSecond = 0f, stallReason = "no data for 10 s")
        assertEquals(listOf("no data for 10 s (attempt 1)"), reasons)
    }

    @Test
    fun `healthy ticks between stalls don't clear the backoff`() {
        repeat(3) {
            monitor.updateMetrics(droppedFramesPerSecond = 0f, stallReason = null)
            monitor.updateMetrics(droppedFramesPerSecond = 0f, stallReason = "the position stopped moving")
            monitor.reset()
        }
        assertEquals(3, monitor.state.value.recycleAttempts)
    }
}
