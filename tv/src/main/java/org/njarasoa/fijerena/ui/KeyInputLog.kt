package org.njarasoa.fijerena.ui

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Show Key Input (Admin → This Device): the last [MAX_LINES] keys the activity received, for
 * finding presses that seem to go missing. MainActivity records each key after it dispatched
 * it; KeyInputOverlay shows the lines while [on].
 */
object KeyInputLog {
    const val MAX_LINES = 10

    /** KEYCODE_DPAD_CENTER's length: the D-pad keys' columns line up. */
    private const val KEY_NAME_WIDTH = 19

    /** Set by KeyInputOverlay while Show Key Input lasts; off, nothing is recorded. */
    @Volatile var on: Boolean = false

    /** Oldest first. Main thread only: key dispatch and composition. */
    val lines = mutableStateListOf<String>()

    /** After the activity dispatched [event]: [handled] is what dispatch returned. */
    fun record(
        event: KeyEvent,
        handled: Boolean,
    ) {
        if (!on) return
        // The event's uptime as a wall-clock time, so lines match a log or a note of the time.
        val wallTime = System.currentTimeMillis() - (android.os.SystemClock.uptimeMillis() - event.eventTime)
        lines += line(KeyEvent.keyCodeToString(event.keyCode), wallTime, event.action, event.repeatCount, handled)
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    fun clear() {
        lines.clear()
    }

    /** One line: "12:03:41.207 KEYCODE_DPAD_DOWN   DOWN r0 handled"; an UP has no "handled". */
    internal fun line(
        keyName: String,
        timeMs: Long,
        action: Int,
        repeatCount: Int,
        handled: Boolean,
    ): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(timeMs))
        val actionName =
            when (action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> "ACTION_$action"
            }
        // Only a press says whether it was handled: the screens act on DOWN, so an UP is mostly
        // not handled, and saying so for each one hid the presses that were not.
        val result =
            if (action != KeyEvent.ACTION_DOWN) {
                ""
            } else if (handled) {
                " handled"
            } else {
                " NOT handled"
            }
        return "$time ${keyName.padEnd(KEY_NAME_WIDTH)} ${actionName.padEnd(4)} r$repeatCount$result"
    }
}
