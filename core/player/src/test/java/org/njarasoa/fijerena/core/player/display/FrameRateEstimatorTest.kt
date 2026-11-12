package org.njarasoa.fijerena.core.player.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random
import kotlin.math.roundToLong

class FrameRateEstimatorTest {
    /** The presentation times of [count] frames at [rate] from [startUs], each moved by [jitter]. */
    private fun times(
        rate: Float,
        count: Int,
        startUs: Long = 0L,
        jitter: (Int) -> Long = { 0L },
    ): List<Long> = (0 until count).map { startUs + (it * 1_000_000.0 / rate).roundToLong() + jitter(it) }

    private fun FrameRateEstimator.feed(times: List<Long>): Float? = times.map { onFrame(it) }.last()

    private fun rateOf(times: List<Long>): Float? = FrameRateEstimator().feed(times)

    private fun assertRate(
        expected: Float,
        actual: Float?,
    ) = assertEquals(expected, actual ?: 0f, 0.0001f)

    @Test
    fun `each common rate is found from its steps`() {
        listOf(FrameRateEstimator.RATE_23_976, 24f, 25f, FrameRateEstimator.RATE_29_97, 50f, FrameRateEstimator.RATE_59_94)
            .forEach { rate -> assertRate(rate, rateOf(times(rate, FRAMES))) }
    }

    @Test
    fun `23_976 and 24 stay apart`() {
        assertRate(FrameRateEstimator.RATE_23_976, rateOf(times(FrameRateEstimator.RATE_23_976, FRAMES)))
        assertRate(24f, rateOf(times(24f, FRAMES)))
    }

    @Test
    fun `MPEG-TS times in 90 kHz ticks give 23_976`() {
        // 23.976 fps is 3753.75 ticks a frame: the steps are 3753 or 3754 ticks.
        val ticks = (0 until FRAMES).map { (it * 90_000.0 * 1001 / 24_000).toLong() }
        assertRate(FrameRateEstimator.RATE_23_976, rateOf(ticks.map { it * 1_000_000 / 90_000 }))
    }

    @Test
    fun `times rounded to whole milliseconds give the exact rate`() {
        // MKV stores times in milliseconds: the steps of 23.976 are 41 or 42 ms, the median 42 ms.
        val rounded = times(FrameRateEstimator.RATE_23_976, FRAMES).map { (it / 1000.0).roundToLong() * 1000 }
        assertRate(FrameRateEstimator.RATE_23_976, rateOf(rounded))
        assertRate(24f, rateOf(times(24f, FRAMES).map { (it / 1000.0).roundToLong() * 1000 }))
    }

    @Test
    fun `jitter of up to 1 ms in the times keeps the rate`() {
        (1L..20L).forEach { seed ->
            val random = Random(seed)
            val jitter = { _: Int -> random.nextInt(2_001).toLong() - 1_000 }
            assertRate(FrameRateEstimator.RATE_23_976, rateOf(times(FrameRateEstimator.RATE_23_976, FRAMES, jitter = jitter)))
            assertRate(24f, rateOf(times(24f, FRAMES, jitter = jitter)))
            assertRate(FrameRateEstimator.RATE_59_94, rateOf(times(FrameRateEstimator.RATE_59_94, FRAMES, jitter = jitter)))
        }
    }

    @Test
    fun `a gap in the stream is not a frame step`() {
        val before = times(25f, 30)
        val after = times(25f, 40, startUs = before.last() + 5_000_000L)
        assertRate(25f, rateOf(before + after))
    }

    @Test
    fun `a step back in the times is not a frame step`() {
        val before = times(50f, 30, startUs = 10_000_000L)
        val after = times(50f, 120)
        assertRate(50f, rateOf(before + after))
    }

    @Test
    fun `frames the decoder did not give do not change the rate`() {
        val missing = setOf(10, 11, 30, 50)
        val frames = times(FrameRateEstimator.RATE_23_976, FRAMES + missing.size).filterIndexed { i, _ -> i !in missing }
        assertRate(FrameRateEstimator.RATE_23_976, rateOf(frames))
    }

    @Test
    fun `25i given as 50 steps a second reads as 50`() {
        assertRate(50f, rateOf(times(50f, FRAMES)))
    }

    @Test
    fun `no rate before the window is full`() {
        val estimator = FrameRateEstimator()
        val frames = times(25f, FrameRateEstimator.DEFAULT_WINDOW + 1)
        assertNull(estimator.feed(frames.dropLast(1)))
        assertRate(25f, estimator.onFrame(frames.last()))
        // At 60 fps, 60 steps cover 1 second only: the steps must also cover 2 seconds.
        val fast = times(60f, 121)
        assertNull(rateOf(fast.take(61)))
        assertNull(rateOf(fast.take(120)))
        assertRate(60f, rateOf(fast))
    }

    @Test
    fun `a rate that is not a known rate gives null`() {
        assertNull(rateOf(times(15f, FRAMES)))
        assertNull(rateOf(times(27f, FRAMES)))
    }

    @Test
    fun `reset forgets the frames of the stream before`() {
        val estimator = FrameRateEstimator()
        assertRate(25f, estimator.feed(times(25f, FRAMES, startUs = 50_000_000L)))
        estimator.reset()
        assertNull(estimator.rate)
        // The next stream starts earlier and at another rate: no step crosses the two.
        val next = times(FrameRateEstimator.RATE_59_94, FRAMES, startUs = 50_010_000L)
        assertNull(estimator.feed(next.take(30)))
        assertRate(FrameRateEstimator.RATE_59_94, estimator.feed(next.drop(30)))
    }

    @Test
    fun `content rate takes the format rate for VOD and the frames for live`() {
        val content = ContentFrameRate()
        content.newStream(isLive = false)
        content.onFrame(0L, 24f)
        assertRate(24f, content.rate.value)

        content.newStream(isLive = true)
        assertNull(content.rate.value)
        times(50f, FRAMES).forEach { content.onFrame(it, 25f) }
        assertRate(50f, content.rate.value)

        content.newStream(isLive = false)
        times(25f, FRAMES).forEach { content.onFrame(it, -1f) }
        assertRate(25f, content.rate.value)
        content.onSeek()
        assertNull(content.rate.value)
    }

    @Test
    fun `display mode texts`() {
        val current = ModeSpec(1, 3840, 2160, 23.976025f)
        val modes =
            listOf(
                current,
                ModeSpec(2, 3840, 2160, 60f),
                ModeSpec(3, 3840, 2160, 59.94006f),
                ModeSpec(4, 3840, 2160, 24f),
                ModeSpec(5, 3840, 2160, 50f),
                ModeSpec(6, 1920, 1080, 30f),
            )
        assertEquals("3840×2160 · 23.976 Hz", displayModeText(current))
        assertEquals("23.976, 24, 50, 59.94, 60 Hz", refreshRatesText(modes, current))
        assertEquals("29.97", rateText(FrameRateEstimator.RATE_29_97))
    }

    private companion object {
        const val FRAMES = 150
    }
}
