package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.ui.utils.AdminTaps.Outcome

class AdminTapsTest {
    @Test
    fun `with no Admin, seven taps turn Admin on, the last three count down`() {
        val outcomes = (0 until AdminTaps.TAPS_TO_ENABLE).map { AdminTaps.onTap(it, adminExists = false) }
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
    fun `while an Admin exists, no tap does or says anything`() {
        val outcomes = (0..AdminTaps.TAPS_TO_ENABLE).map { AdminTaps.onTap(it, adminExists = true) }
        assertTrue(outcomes.all { it == Outcome.Nothing })
    }

    @Test
    fun `an Admin exists when any profile is one`() {
        assertTrue(AdminTaps.adminExists(listOf("a", "b")) { it == "b" })
        assertFalse(AdminTaps.adminExists(listOf("a", "b")) { false })
    }

    @Test
    fun `a list not loaded yet counts as having an Admin`() {
        assertTrue(AdminTaps.adminExists(null) { false })
    }
}
