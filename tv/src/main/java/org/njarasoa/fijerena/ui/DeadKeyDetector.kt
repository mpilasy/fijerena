package org.njarasoa.fijerena.ui

import android.view.KeyEvent
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * Notices the D-pad doing nothing — focus lost, every press unhandled until Back — and writes it
 * to Settings → Diagnostics with the screen it happened on, so the case can be found and fixed.
 * A streak of [STREAK] unhandled D-pad presses in at least two directions: an edge (Up at the top
 * of a list) refuses only one. Once per streak; any handled key ends it.
 */
object DeadKeyDetector {
    private const val STREAK = 3

    /** The nav destination on screen (TvNavHost). */
    @Volatile var screen: String = "?"

    /** A layer on that destination, e.g. Live TV's preview or full screen (null: none). */
    @Volatile var layer: String? = null

    /** Where an entry goes: Diagnostics; a seam for tests. */
    internal var report: (source: String, message: String) -> Unit = CrashLog::note

    private val streak = mutableListOf<Int>()
    private var reported = false

    /** After the activity dispatched [event]: [handled] is what dispatch returned. */
    fun onKey(
        event: KeyEvent,
        handled: Boolean,
    ) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        if (handled || event.keyCode !in DPAD_KEYS.keys) {
            streak.clear()
            reported = false
            return
        }
        streak += event.keyCode
        if (!reported && streak.size >= STREAK && streak.toSet().size >= 2) {
            reported = true
            val where = screen + (layer?.let { " · $it" } ?: "")
            val keys = streak.joinToString { DPAD_KEYS[it] ?: "$it" }
            report("D-pad did nothing", "screen=$where\nunhandled keys=$keys")
        }
    }

    /**
     * MainActivity gave focus back after [event] found none in the window (the focused control had
     * left the screen): noted, as a dead D-pad would have been, so the screen can still be fixed.
     */
    fun focusRestored(event: KeyEvent) {
        streak.clear()
        reported = false
        val where = screen + (layer?.let { " · $it" } ?: "")
        report("Focus lost, given back", "screen=$where\nkey=${DPAD_KEYS[event.keyCode] ?: event.keyCode}")
    }

    private val DPAD_KEYS =
        mapOf(
            KeyEvent.KEYCODE_DPAD_UP to "DPAD_UP",
            KeyEvent.KEYCODE_DPAD_DOWN to "DPAD_DOWN",
            KeyEvent.KEYCODE_DPAD_LEFT to "DPAD_LEFT",
            KeyEvent.KEYCODE_DPAD_RIGHT to "DPAD_RIGHT",
            KeyEvent.KEYCODE_DPAD_CENTER to "DPAD_CENTER",
        )
}
