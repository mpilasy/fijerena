package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.ui.utils.DateText
import java.util.Date
import org.njarasoa.fijerena.core.player.model.TimeFormat as CoreTimeFormat

/**
 * Composable time formatting wrappers; they read the context themselves.
 *
 * Each wrapper remembers its inputs: the core call runs `DateFormat.is24HourFormat(context)` plus an
 * `Instant`/`ZoneId` conversion, and the EPG grid calls it once per visible cell on every
 * recomposition, for values that never change.
 *
 * Trade-off: if the OS 12h/24h setting changes while a screen shows, formatted strings keep their
 * old shape until their inputs change or they leave and enter composition again.
 */
object TimeFormat {
    @Composable
    fun formatTime(epochSeconds: Long): String {
        val context = LocalContext.current
        return remember(context, epochSeconds) { CoreTimeFormat.formatTime(context, epochSeconds) }
    }

    /** "18:25 – 19:30", with the end's day when it ends on another day ([DateText.timeRange]). */
    @Composable
    fun formatTimeRange(
        startEpochSeconds: Long,
        endEpochSeconds: Long,
    ): String {
        val context = LocalContext.current
        return remember(context, startEpochSeconds, endEpochSeconds) {
            DateText.timeRange(context, startEpochSeconds, endEpochSeconds)
        }
    }

    /** Not memoized — its caller is a wall-clock tick, so [date] is a new value on every call. */
    @Composable
    fun formatClockTime(date: Date): String = CoreTimeFormat.formatClockTime(LocalContext.current, date)
}
