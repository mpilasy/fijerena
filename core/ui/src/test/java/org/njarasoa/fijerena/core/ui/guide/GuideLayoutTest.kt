package org.njarasoa.fijerena.core.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EpgProgram

/** GD2: the one scale that makes the TV Guide align (G-T1, G-T2) and compose only what shows. */
class GuideLayoutTest {
    // Window: a 24-hour "day" starting at epoch 1 000 000 (any midnight works the same way).
    private val dayStart = 1_000_000L
    private val dayEnd = dayStart + 24 * 3600
    private val pxPerMinute = 4f // 1 h = 240 px at density 1
    private val layout =
        GuideLayout(
            pxPerMinute = pxPerMinute,
            windowStartSec = dayStart,
            windowEndSec = dayEnd,
            minLabelWidthPx = 48f,
        )

    private fun program(
        id: String,
        startMin: Long,
        endMin: Long,
    ) = EpgProgram(
        id = id,
        title = id,
        start = (dayStart + startMin * 60).toString(),
        end = (dayStart + endMin * 60).toString(),
    )

    @Test
    fun a13hProgrammeStartsUnderThe13hTick() {
        val journal = program("journal", startMin = 13 * 60, endMin = 13 * 60 + 40)

        val cell = layout.cellsIn(listOf(journal), dayStart, dayEnd).single()
        val tick13 = layout.tickMarks(dayStart + 12 * 3600, dayStart + 14 * 3600).first { it.epochSec == journal.startTime }

        assertEquals(tick13.x, cell.x, 0f)
        assertEquals(13 * 60 * pxPerMinute, cell.x, 0f)
        assertEquals(40 * pxPerMinute, cell.width, 0f)
        assertEquals(journal.startTime, layout.timeAt(cell.x))
    }

    @Test
    fun ticksFallOnWholeHalfHoursEvenWhenTheRangeStartsBetweenThem() {
        val ticks = layout.tickMarks(dayStart + 10 * 3600 + 7 * 60, dayStart + 11 * 3600 + 1)

        assertEquals(listOf(10 * 60 + 30L, 11 * 60L), ticks.map { (it.epochSec - dayStart) / 60 })
        assertEquals((10 * 60 + 30) * pxPerMinute, ticks.first().x, 0f)
    }

    @Test
    fun aShortProgrammeKeepsItsTrueWidthAndDropsItsLabel() {
        val flash = program("flash", startMin = 600, endMin = 605)
        val film = program("film", startMin = 605, endMin = 725)

        val cells = layout.cellsIn(listOf(flash, film), dayStart, dayEnd)

        assertEquals(5 * pxPerMinute, cells[0].width, 0f)
        assertFalse(cells[0].labelFits)
        assertTrue(cells[1].labelFits)
        // The next programme still starts where its time says, not after a stretched neighbour.
        assertEquals(cells[0].x + cells[0].width, cells[1].x, 0f)
        assertEquals(605 * pxPerMinute, cells[1].x, 0f)
    }

    @Test
    fun aZeroLengthListingIsStillAHairline() {
        assertEquals(GuideLayout.MIN_CELL_WIDTH_PX, layout.widthFor(dayStart, dayStart), 0f)
    }

    @Test
    fun cellsOutsideTheAskedRangeAreCulled() {
        val programs =
            listOf(
                program("early", 0, 60),
                program("late-morning", 600, 660),
                program("noon", 660, 780),
                program("evening", 1200, 1260),
            )

        val visible = layout.cellsIn(programs, dayStart + 640 * 60, dayStart + 700 * 60)

        assertEquals(listOf("late-morning", "noon"), visible.map { it.program.id })
        // A cell crossing the range edge is placed by its own time, not clipped to the range.
        assertEquals(600 * pxPerMinute, visible[0].x, 0f)
        assertEquals(60 * pxPerMinute, visible[0].width, 0f)
    }

    @Test
    fun aProgrammeStraddlingMidnightIsClippedToTheWindow() {
        val overnight = program("tfou", startMin = -70, endMin = 50) // 22:50 yesterday → 00:50

        val cell = layout.cellsIn(listOf(overnight), dayStart, dayEnd).single()

        assertEquals(0f, cell.x, 0f)
        assertEquals(50 * pxPerMinute, cell.width, 0f)
        assertEquals(dayStart, cell.startSec)
        assertEquals(emptyList<GuideCell>(), layout.cellsIn(listOf(program("yesterday", -120, -60)), dayStart, dayEnd))
    }

    @Test
    fun visibleRangeFollowsTheScrollAndStopsAtTheWindow() {
        val viewport = 2 * 240f // two hours on screen

        assertEquals(dayStart..(dayStart + 2 * 3600), layout.visibleRange(0f, viewport))
        assertEquals((dayStart + 10 * 3600)..(dayStart + 12 * 3600), layout.visibleRange(10 * 240f, viewport))
        assertEquals((dayStart + 23 * 3600)..dayEnd, layout.visibleRange(23 * 240f, viewport))
    }

    @Test
    fun composeRangeWidensByAViewportAndMovesInHalfViewportSteps() {
        val viewport = 2 * 240f
        val at10h = layout.composeRange(10 * 240f, viewport)

        assertEquals(dayStart + 8 * 3600, at10h.first)
        assertEquals(dayStart + 13 * 3600, at10h.last)
        // Scrolling less than half a viewport leaves the composed span alone.
        assertEquals(at10h, layout.composeRange(10 * 240f + 200f, viewport))
        assertEquals(dayStart + 9 * 3600, layout.composeRange(11 * 240f, viewport).first)
        // Before the viewport is measured nothing is composed.
        val unmeasured = layout.composeRange(0f, 0f)
        assertEquals(emptyList<GuideCell>(), layout.cellsIn(listOf(program("first", 0, 60)), unmeasured.first, unmeasured.last))
    }

    @Test
    fun programAtPicksTheCellUnderTheTimeElseTheNearest() {
        val programs = listOf(program("a", 0, 60), program("b", 60, 120), program("d", 180, 240))

        assertEquals("b", GuideLayout.programAt(programs, dayStart + 90 * 60)?.id)
        assertEquals("b", GuideLayout.programAt(programs, dayStart + 60 * 60)?.id)
        // In a gap: the nearest in time, the later one on a tie.
        assertEquals("b", GuideLayout.programAt(programs, dayStart + 130 * 60)?.id)
        assertEquals("d", GuideLayout.programAt(programs, dayStart + 170 * 60)?.id)
        assertEquals("d", GuideLayout.programAt(programs, dayStart + 150 * 60)?.id)
        // Before the start of the row: its first programme.
        assertEquals("a", GuideLayout.programAt(programs, dayStart - 30 * 60)?.id)
        // Past the end of the row: its last programme.
        assertEquals("d", GuideLayout.programAt(programs, dayStart + 300 * 60)?.id)
        assertNull(GuideLayout.programAt(emptyList(), dayStart))
    }

    @Test
    fun thePhoneScalePutsOneHourAt160Dp() {
        val density = 2.75f
        val phone =
            GuideLayout(
                pxPerMinute = GuideLayout.PHONE_DP_PER_MINUTE * density,
                windowStartSec = dayStart,
                windowEndSec = dayEnd,
                minLabelWidthPx = 48f * density,
            )

        assertEquals(160f * density, phone.xFor(dayStart + 3600), 0.01f)
        assertEquals(24 * 160f * density, phone.totalWidthPx, 0.1f)
    }
}
