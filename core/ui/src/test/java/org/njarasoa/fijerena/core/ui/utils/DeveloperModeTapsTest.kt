package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.ui.utils.DeveloperModeTaps.Outcome

class DeveloperModeTapsTest {
    @Test
    fun `seven taps turn developer mode on, the last three count down`() {
        val outcomes = (0 until DeveloperModeTaps.TAPS_TO_ENABLE).map { DeveloperModeTaps.onTap(it, alreadyOn = false) }
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
    fun `when it is already on, a tap only says so`() {
        assertEquals(Outcome.AlreadyOn, DeveloperModeTaps.onTap(0, alreadyOn = true))
    }
}
