package org.njarasoa.fijerena.core.ui.guide

import org.njarasoa.fijerena.core.player.model.EpgProgram

/**
 * The time-to-pixel engine behind the TV Guide grid, shared by both platforms (UX overhaul plan
 * Part III, GD2). Pure Kotlin: the caller turns dp into px once ([pxPerMinute]) and this maps
 * programme times onto a horizontal canvas whose origin is [windowStartSec].
 *
 * The invariants that fix G-T1 and G-T2 live here: every x comes from the same scale, so a
 * programme's left edge sits under the header tick of its start time; a cell's width is its true
 * duration, never stretched to fit its label (a 5-minute programme is 5 minutes wide and its label
 * truncates or disappears — [GuideCell.labelFits]); and a row composes only the cells that overlap
 * the range the caller asks for ([cellsIn]), so a day of 50 channels is not 50 × N cards.
 */
class GuideLayout(
    /** Horizontal scale: pixels per minute (for 1 h = 240 dp, `4 * density`). */
    val pxPerMinute: Float,
    /** Epoch seconds at x = 0 — the start of the day the grid shows. */
    val windowStartSec: Long,
    /** Epoch seconds at the right edge of the canvas. */
    val windowEndSec: Long,
    /** Below this cell width the label is not worth drawing; the cell itself keeps its width. */
    val minLabelWidthPx: Float,
) {
    init {
        require(pxPerMinute > 0f) { "pxPerMinute must be positive" }
        require(windowEndSec > windowStartSec) { "window must not be empty" }
    }

    /** Width of the whole canvas, in px. */
    val totalWidthPx: Float get() = xFor(windowEndSec)

    /** Canvas x of [epochSec]; negative before the window, past [totalWidthPx] after it. */
    fun xFor(epochSec: Long): Float = (epochSec - windowStartSec) / SECONDS_PER_MINUTE * pxPerMinute

    /** The epoch second under canvas x [xPx]. */
    fun timeAt(xPx: Float): Long = windowStartSec + (xPx / pxPerMinute * SECONDS_PER_MINUTE).toLong()

    /**
     * True width of a programme running [startSec]..[endSec], floored at [MIN_CELL_WIDTH_PX] so a
     * zero-length listing still exists on screen. Never widened for its label.
     */
    fun widthFor(
        startSec: Long,
        endSec: Long,
    ): Float = ((endSec - startSec) / SECONDS_PER_MINUTE * pxPerMinute).coerceAtLeast(MIN_CELL_WIDTH_PX)

    /** The time span visible in a viewport of [viewportPx] scrolled to [scrollPx], clipped to the window. */
    fun visibleRange(
        scrollPx: Float,
        viewportPx: Float,
    ): LongRange {
        val from = timeAt(scrollPx.coerceAtLeast(0f)).coerceIn(windowStartSec, windowEndSec)
        val to = timeAt((scrollPx + viewportPx).coerceAtLeast(0f)).coerceIn(windowStartSec, windowEndSec)
        return from..to
    }

    /**
     * The span a row should keep composed: [visibleRange] widened by one viewport on each side, and
     * quantised to half-viewport steps so the result changes only every half viewport of scrolling —
     * a `derivedStateOf` over it leaves rows alone while the canvas animates. Empty (nothing to
     * compose) until the viewport has been measured.
     */
    fun composeRange(
        scrollPx: Float,
        viewportPx: Float,
    ): LongRange {
        val range =
            if (viewportPx <= 0f) {
                windowStartSec..windowStartSec
            } else {
                val step = viewportPx / 2f
                val page = (scrollPx.coerceAtLeast(0f) / step).toInt()
                val from = page * step - viewportPx
                val to = (page + 1) * step + viewportPx
                LongRange(
                    timeAt(from.coerceAtLeast(0f)).coerceIn(windowStartSec, windowEndSec),
                    timeAt(to).coerceIn(windowStartSec, windowEndSec),
                )
            }
        return range
    }

    /**
     * The cells of one channel row that overlap [fromSec]..[toSec], placed on the canvas. A
     * programme running past either window edge is clipped to the window, so its visible part is
     * what gets a card; one entirely outside the window yields nothing.
     */
    fun cellsIn(
        programs: List<EpgProgram>,
        fromSec: Long,
        toSec: Long,
    ): List<GuideCell> {
        val cells = ArrayList<GuideCell>()
        for (program in programs) {
            val start = program.startTime.coerceAtLeast(windowStartSec)
            val end = program.endTime.coerceAtMost(windowEndSec)
            if (end <= start || end <= fromSec || start >= toSec) continue
            val width = widthFor(start, end)
            cells.add(
                GuideCell(
                    program = program,
                    startSec = start,
                    endSec = end,
                    x = xFor(start),
                    width = width,
                    labelFits = width >= minLabelWidthPx,
                ),
            )
        }
        return cells
    }

    /** Header ticks every [stepMin] minutes from the first whole step at or after [fromSec] up to [toSec]. */
    fun tickMarks(
        fromSec: Long,
        toSec: Long,
        stepMin: Int = DEFAULT_TICK_STEP_MIN,
    ): List<GuideTick> {
        require(stepMin > 0) { "stepMin must be positive" }
        val stepSec = stepMin * SECONDS_PER_MINUTE.toLong()
        val from = fromSec.coerceAtLeast(windowStartSec)
        val to = toSec.coerceAtMost(windowEndSec)
        // Ticks are aligned on the window start, which is a local midnight, so they fall on the
        // clock's whole half-hours in the device's zone.
        val offset = Math.floorMod(from - windowStartSec, stepSec)
        var tick = if (offset == 0L) from else from + (stepSec - offset)
        val ticks = ArrayList<GuideTick>()
        while (tick < to) {
            ticks.add(GuideTick(epochSec = tick, x = xFor(tick)))
            tick += stepSec
        }
        return ticks
    }

    companion object {
        const val SECONDS_PER_MINUTE = 60f
        const val DEFAULT_TICK_STEP_MIN = 30

        /** 1 hour = 240 dp. */
        const val DP_PER_MINUTE = 4f

        /** A hairline, so a zero-length listing is still a card and not a gap. */
        const val MIN_CELL_WIDTH_PX = 2f

        /**
         * Picks in [programs] the one on air at [atSec], else the one nearest to it in time (the
         * later one on a tie: what comes next beats what has ended). Null for an empty row. This is
         * the Up/Down rule (G-T4): a move to another row lands on the cell under the same time.
         */
        fun programAt(
            programs: List<EpgProgram>,
            atSec: Long,
        ): EpgProgram? {
            var best: EpgProgram? = null
            var bestDistance = Long.MAX_VALUE
            for (program in programs) {
                val distance =
                    when {
                        atSec < program.startTime -> program.startTime - atSec
                        atSec >= program.endTime -> atSec - program.endTime
                        else -> 0L
                    }
                val closer = distance < bestDistance
                val laterTie = distance == bestDistance && best != null && program.startTime > best.startTime
                if (closer || laterTie) {
                    best = program
                    bestDistance = distance
                }
            }
            return best
        }
    }
}

/** One placed card: [x] and [width] in canvas px, [startSec]/[endSec] clipped to the window. */
data class GuideCell(
    val program: EpgProgram,
    val startSec: Long,
    val endSec: Long,
    val x: Float,
    val width: Float,
    /** False when the cell is too narrow for any text; the card is still drawn at its true width. */
    val labelFits: Boolean,
)

/** One header tick, at canvas [x]. */
data class GuideTick(
    val epochSec: Long,
    val x: Float,
)
