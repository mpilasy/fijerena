package org.njarasoa.fijerena.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class KeyInputLogTest {
    private val time =
        Calendar
            .getInstance()
            .apply {
                set(2026, Calendar.NOVEMBER, 5, 12, 3, 41)
                set(Calendar.MILLISECOND, 207)
            }.timeInMillis

    @Test
    fun `a handled press shows its time to the ms, key, action and repeat count`() {
        assertEquals(
            "12:03:41.207 KEYCODE_DPAD_DOWN   DOWN r0 handled",
            KeyInputLog.line("KEYCODE_DPAD_DOWN", time, KeyEvent.ACTION_DOWN, 0, handled = true),
        )
    }

    @Test
    fun `an unhandled press says so`() {
        assertEquals(
            "12:03:41.207 KEYCODE_BACK        DOWN r2 NOT handled",
            KeyInputLog.line("KEYCODE_BACK", time, KeyEvent.ACTION_DOWN, 2, handled = false),
        )
    }

    @Test
    fun `a key up does not say whether it was handled`() {
        assertEquals(
            "12:03:41.207 KEYCODE_DPAD_UP     UP   r0",
            KeyInputLog.line("KEYCODE_DPAD_UP", time, KeyEvent.ACTION_UP, 0, handled = false),
        )
    }

    @Test
    fun `a line goes after 30 seconds on the screen`() {
        KeyInputLog.lines.clear()
        KeyInputLog.lines += KeyInputLog.Line("old", addedAtMs = 1_000L)
        KeyInputLog.lines += KeyInputLog.Line("new", addedAtMs = 20_000L)
        KeyInputLog.removeOld(nowMs = 30_999L)
        assertEquals(listOf("old", "new"), KeyInputLog.lines.map { it.text })
        KeyInputLog.removeOld(nowMs = 31_000L)
        assertEquals(listOf("new"), KeyInputLog.lines.map { it.text })
        KeyInputLog.lines.clear()
    }
}
