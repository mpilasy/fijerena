package org.njarasoa.fijerena.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone screens' clock shows unless full-screen playback hides it or it is off (the setting, a child profile). */
class MobileClockStateTest {
    private val clock = MobileClockState()

    @Test
    fun `the clock shows by default`() {
        assertTrue(clock.visible)
    }

    @Test
    fun `the clock hides while a player holds it, and comes back after the last one`() {
        clock.hide()
        clock.hide()
        clock.show()
        assertFalse(clock.visible)
        clock.show()
        assertTrue(clock.visible)
    }

    @Test
    fun `the clock is off for a child profile or with the setting off`() {
        clock.off = true
        assertFalse(clock.visible)
    }

    @Test
    fun `an extra show does not leave the clock hidden later`() {
        clock.show()
        clock.hide()
        assertFalse(clock.visible)
    }
}
