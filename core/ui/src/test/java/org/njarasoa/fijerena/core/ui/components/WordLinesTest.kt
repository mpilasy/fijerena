package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WordLinesTest {
    /** A line fits 10 characters. */
    private fun lines(text: String) = wordLines(text, maxLines = 2) { it.length <= 10 }

    @Test
    fun `a long first word keeps its own line and is not broken`() {
        assertEquals(listOf("Manchester", "City"), lines("Manchester City"))
        assertEquals(listOf("Wolverhampton", "Wanderers"), lines("Wolverhampton Wanderers"))
    }

    @Test
    fun `the words that fit share a line, the rest go on the last line`() {
        assertEquals(listOf("Kansas", "City Chiefs"), lines("Kansas City Chiefs"))
        assertEquals(listOf("FC Bayern", "München Frauen"), lines("FC  Bayern München Frauen"))
    }

    @Test
    fun `a short name stays on one line`() {
        assertEquals(listOf("Ajax"), lines("Ajax"))
    }
}
