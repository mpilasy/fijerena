package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.ui.utils.DeveloperModeTaps.Outcome

class DeveloperModeTapsTest {
    @Test
    fun `with no Expert, seven taps turn Expert mode on, the last three count down`() {
        val outcomes = (0 until DeveloperModeTaps.TAPS_TO_ENABLE).map { DeveloperModeTaps.onTap(it, expertExists = false) }
        assertEquals(
            listOf(
                Outcome.Nothing,
                Outcome.Nothing,
                Outcome.Nothing,
                Outcome.TapsLeft(3),
                Outcome.TapsLeft(2),
                Outcome.TapsLeft(1),
                Outcome.Enable,
            ),
            outcomes,
        )
    }

    @Test
    fun `while an Expert exists, no tap does or says anything`() {
        val outcomes = (0..DeveloperModeTaps.TAPS_TO_ENABLE).map { DeveloperModeTaps.onTap(it, expertExists = true) }
        assertTrue(outcomes.all { it == Outcome.Nothing })
    }

    @Test
    fun `an Expert exists when any profile is one`() {
        assertTrue(DeveloperModeTaps.expertExists(listOf("a", "b")) { it == "b" })
        assertFalse(DeveloperModeTaps.expertExists(listOf("a", "b")) { false })
    }

    @Test
    fun `a list not loaded yet counts as having an Expert`() {
        assertTrue(DeveloperModeTaps.expertExists(null) { false })
    }
}
