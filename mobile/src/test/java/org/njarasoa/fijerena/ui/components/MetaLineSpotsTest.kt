package org.njarasoa.fijerena.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MetaLineSpotsTest {
    // Dot 4 wide, gap 2 each side: a dot and its gaps take 8.

    @Test
    fun `facts that fit share one line with a dot between them`() {
        assertEquals(
            listOf(MetaSpot(0, 0, null), MetaSpot(0, 18, 12)),
            metaLineSpots(listOf(10, 10), dotWidth = 4, gap = 2, maxWidth = 100),
        )
    }

    @Test
    fun `a fact that starts a line has no dot`() {
        assertEquals(
            listOf(MetaSpot(0, 0, null), MetaSpot(0, 18, 12), MetaSpot(1, 0, null), MetaSpot(1, 18, 12)),
            metaLineSpots(listOf(10, 10, 10, 10), dotWidth = 4, gap = 2, maxWidth = 30),
        )
    }

    @Test
    fun `a fact wider than the line still gets its own line`() {
        assertEquals(
            listOf(MetaSpot(0, 0, null), MetaSpot(1, 0, null)),
            metaLineSpots(listOf(10, 50), dotWidth = 4, gap = 2, maxWidth = 30),
        )
    }
}
