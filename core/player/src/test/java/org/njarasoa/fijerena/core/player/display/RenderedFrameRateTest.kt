package org.njarasoa.fijerena.core.player.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderedFrameRateTest {
    /** Feeds frames at [fps] for [seconds] from [startMs] and returns each rate that a window gave. */
    private fun RenderedFrameRate.feed(
        fps: Int,
        seconds: Int,
        startMs: Long = 0L,
    ): List<Float> = (0..fps * seconds).mapNotNull { onFrame(startMs + it * 1_000L / fps) }

    @Test
    fun `a 10 fps film reads 10 fps`() {
        val rates = RenderedFrameRate().feed(fps = 10, seconds = 6)

        assertEquals(listOf(10f, 10f, 10f), rates)
    }

    @Test
    fun `25 fps reads 25 fps`() {
        assertEquals(25f, RenderedFrameRate().feed(fps = 25, seconds = 4).last(), 0.01f)
    }

    @Test
    fun `no rate before a full window of frames`() {
        val counter = RenderedFrameRate()

        (0 until 49).forEach { assertNull(counter.onFrame(it * 40L)) }
    }

    @Test
    fun `frames released on 60 Hz refreshes still read 10 fps`() {
        val counter = RenderedFrameRate()

        // Each frame of a 10 fps film shows on the next refresh of a 60 Hz display.
        val rates = (0..40).mapNotNull { counter.onFrame(((it * 100L) / 16 + 1) * 16) }

        assertEquals(10f, rates.first(), 0.1f)
    }

    @Test
    fun `a pause does not make the rate lower`() {
        val counter = RenderedFrameRate()
        counter.feed(fps = 25, seconds = 4)

        // Paused for 5 seconds, then 25 fps again.
        val rates = counter.feed(fps = 25, seconds = 4, startMs = 9_000L)

        assertEquals(listOf(25f, 25f), rates)
    }

    @Test
    fun `dropped frames make the rate lower`() {
        val counter = RenderedFrameRate()

        // 25 fps with one frame of each five not shown: 20 frames each second.
        val rates = (0..100).filter { it % 5 != 4 }.mapNotNull { counter.onFrame(it * 40L) }

        assertEquals(20f, rates.first(), 0.01f)
    }
}
