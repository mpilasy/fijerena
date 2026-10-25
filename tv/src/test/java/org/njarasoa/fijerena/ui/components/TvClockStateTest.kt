package org.njarasoa.fijerena.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screens' clock shows unless full-screen playback hides it or Show Clock is off. */
class TvClockStateTest {
    private val clock = TvClockState()

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
    fun `Show Clock off hides the clock`() {
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
