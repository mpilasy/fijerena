package org.njarasoa.fijerena.ui

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A dead D-pad — presses in several directions that nothing handles — goes to Diagnostics once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeadKeyDetectorTest {
    private val reports = mutableListOf<String>()

    @Before
    fun setUp() {
        DeadKeyDetector.report = { _, message -> reports += message }
        DeadKeyDetector.screen = "LiveTvTab"
        DeadKeyDetector.layer = "Live TV preview"
        press(KeyEvent.KEYCODE_BACK, handled = true) // a clean streak
        reports.clear()
    }

    @After
    fun tearDown() {
        DeadKeyDetector.layer = null
    }

    private fun press(
        code: Int,
        handled: Boolean,
    ) {
        DeadKeyDetector.onKey(KeyEvent(KeyEvent.ACTION_DOWN, code), handled)
        DeadKeyDetector.onKey(KeyEvent(KeyEvent.ACTION_UP, code), handled)
    }

    @Test
    fun `three unhandled presses in two directions are reported once, with the screen`() {
        press(KeyEvent.KEYCODE_DPAD_DOWN, handled = false)
        press(KeyEvent.KEYCODE_DPAD_DOWN, handled = false)
        press(KeyEvent.KEYCODE_DPAD_LEFT, handled = false)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, handled = false)

        assertEquals(1, reports.size)
        assertTrue(reports.single(), reports.single().contains("screen=LiveTvTab · Live TV preview"))
        assertTrue(reports.single(), reports.single().contains("DPAD_DOWN, DPAD_DOWN, DPAD_LEFT"))
    }

    @Test
    fun `focus given back after a loss is noted with the screen and the key`() {
        DeadKeyDetector.focusRestored(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))

        assertEquals(1, reports.size)
        assertTrue(reports.single(), reports.single().contains("screen=LiveTvTab · Live TV preview"))
        assertTrue(reports.single(), reports.single().contains("key=DPAD_UP"))
    }

    @Test
    fun `pressing against an edge is not a dead D-pad`() {
        repeat(5) { press(KeyEvent.KEYCODE_DPAD_UP, handled = false) }

        assertTrue(reports.isEmpty())
    }

    @Test
    fun `a handled key ends the streak`() {
        press(KeyEvent.KEYCODE_DPAD_DOWN, handled = false)
        press(KeyEvent.KEYCODE_DPAD_LEFT, handled = false)
        press(KeyEvent.KEYCODE_DPAD_DOWN, handled = true)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, handled = false)

        assertTrue(reports.isEmpty())
    }
}
