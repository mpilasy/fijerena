package org.njarasoa.fijerena.core.player.display

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Finds the frame rate of a stream from the presentation times of its frames (the match frame
 * rate plan, Phase 1). Live MPEG-TS and HLS without a FRAME-RATE attribute give no rate in their
 * format, so the frames are the only source.
 *
 * It keeps the steps between the last [window] frames, and more while they cover less than
 * [MIN_SPAN_US] (a 50 or 60 fps stream keeps 100 or 120). A step back or a step longer than
 * [MAX_STEP_US] (a seek, a stream gap) is not kept. The median step is the nominal step: each step
 * counts as the number of nominal steps nearest to it, so a frame that the decoder did not give
 * counts as two. A straight line fitted (least squares) through the frame counts and the times
 * gives the step. The line takes the mean of the jitter in the times, so the result can tell
 * 23.976 from 24, which are only 0.1 % apart, where the median step alone cannot. The result is
 * snapped to a known rate ([KNOWN_RATES]) within [SNAP_TOLERANCE], or null.
 *
 * Interlaced 25i comes from the decoder as 50 frames per second, so it reads as 50: the 50 Hz
 * family, as a 50 Hz display mode wants.
 *
 * Not thread-safe: the caller locks.
 */
class FrameRateEstimator(
    private val window: Int = DEFAULT_WINDOW,
) {
    private val steps = ArrayDeque<Long>()
    private var spanUs = 0L
    private var stepsSinceEstimate = ESTIMATE_EVERY - 1
    private var lastTimeUs: Long? = null

    /** The rate the steps show, or null while there are too few of them or no known rate fits. */
    var rate: Float? = null
        private set

    /** Adds the frame shown at [presentationTimeUs] and returns [rate]. */
    fun onFrame(presentationTimeUs: Long): Float? {
        val step = lastTimeUs?.let { presentationTimeUs - it }
        lastTimeUs = presentationTimeUs
        // A step back or a long jump is a discontinuity or a seek, not a frame step.
        if (step != null && step in 1..MAX_STEP_US) {
            steps.addLast(step)
            spanUs += step
            while (steps.size > window && spanUs - steps.first() >= MIN_SPAN_US) {
                spanUs -= steps.removeFirst()
            }
            val ready = steps.size >= window && spanUs >= MIN_SPAN_US
            // The first full window gives a rate at once; after it, a new estimate every
            // ESTIMATE_EVERY steps keeps the playback thread from making garbage at each frame.
            stepsSinceEstimate = if (ready) stepsSinceEstimate + 1 else ESTIMATE_EVERY - 1
            if (!ready) rate = null
            if (stepsSinceEstimate >= ESTIMATE_EVERY) {
                rate = estimate(steps.toList())
                stepsSinceEstimate = 0
            }
        }
        return rate
    }

    /** Forgets every frame: a new stream or a seek starts again. */
    fun reset() {
        steps.clear()
        spanUs = 0L
        stepsSinceEstimate = ESTIMATE_EVERY - 1
        lastTimeUs = null
        rate = null
    }

    companion object {
        /** The number of steps between two estimates once the window is full: about 1 second at 30 fps. */
        const val ESTIMATE_EVERY = 30

        /** The fewest steps for a rate: about 2 seconds of video at 25 to 30 fps. */
        const val DEFAULT_WINDOW = 60

        /** The shortest time the steps cover for a rate: 2 seconds. */
        const val MIN_SPAN_US = 2_000_000L

        /** The longest step that counts as one frame: 200 ms, 5 fps. */
        const val MAX_STEP_US = 200_000L

        /** The largest distance from a known rate, as a fraction of it, that still snaps to it. */
        const val SNAP_TOLERANCE = 0.003

        const val RATE_23_976 = 24_000f / 1001f
        const val RATE_29_97 = 30_000f / 1001f
        const val RATE_59_94 = 60_000f / 1001f

        /** The rates a stream can have. 23.976 and 24 (and the other NTSC pairs) stay apart. */
        val KNOWN_RATES = listOf(RATE_23_976, 24f, 25f, RATE_29_97, 30f, 50f, RATE_59_94, 60f)

        private const val MICROS_PER_SECOND = 1_000_000.0

        /** The known rate that [steps] show, or null. */
        internal fun estimate(steps: List<Long>): Float? {
            val median = steps.sorted()[steps.size / 2].toDouble()
            val frames = steps.runningFold(0L) { count, step -> count + (step / median).roundToLong() }
            val times = steps.runningFold(0L) { time, step -> time + step }
            return snap(MICROS_PER_SECOND / slope(frames, times))
        }

        /** The slope of the least-squares line through the points ([xs], [ys]). */
        private fun slope(
            xs: List<Long>,
            ys: List<Long>,
        ): Double {
            val meanX = xs.average()
            val meanY = ys.average()
            val covariance = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
            val variance = xs.sumOf { (it - meanX) * (it - meanX) }
            return covariance / variance
        }

        /** The known rate nearest to [measured] when it is within [SNAP_TOLERANCE], else null. */
        internal fun snap(measured: Double): Float? {
            val nearest = KNOWN_RATES.minBy { abs(it - measured) }
            return nearest.takeIf { abs(it - measured) <= it * SNAP_TOLERANCE }
        }
    }
}
