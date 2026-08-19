package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import org.njarasoa.fijerena.core.player.model.TimeFormat as CoreTimeFormat

/**
 * Composable time formatting wrappers. Context is grabbed automatically —
 * callers just pass the value to format.
 *
 * Every wrapper memoizes on its inputs. The underlying core call runs
 * `DateFormat.is24HourFormat(context)` plus an `Instant`/`ZoneId` conversion, and the EPG grid
 * calls these once per visible cell — without the `remember` that whole chain re-ran on every
 * recomposition of every cell, for values (a programme's start time) that never change.
 *
 * Trade-off: if the OS 12h/24h setting is flipped while a screen is composed, already-formatted
 * strings hold their old shape until their inputs change or they leave and re-enter composition.
 * Formatting was never re-driven by that setting anyway — only by incidental recomposition — so
 * this narrows an existing gap rather than opening a new one.
 */
object TimeFormat {
    @Composable
    fun formatTime(epochSeconds: Long): String {
        val context = LocalContext.current
        return remember(context, epochSeconds) { CoreTimeFormat.formatTime(context, epochSeconds) }
    }

    @Composable
    fun formatTimeRange(
        startEpochSeconds: Long,
        endEpochSeconds: Long,
    ): String {
        val context = LocalContext.current
        return remember(context, startEpochSeconds, endEpochSeconds) {
            CoreTimeFormat.formatTimeRange(context, startEpochSeconds, endEpochSeconds)
        }
    }

    /** Not memoized — its caller is a wall-clock tick, so [date] is a new value on every call. */
    @Composable
    fun formatClockTime(date: Date): String = CoreTimeFormat.formatClockTime(LocalContext.current, date)
}
