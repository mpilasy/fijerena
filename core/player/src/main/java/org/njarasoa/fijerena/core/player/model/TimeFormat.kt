package org.njarasoa.fijerena.core.player.model

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Locale-aware time formatting that respects the OS 24h/12h setting.
 *
 * For composable callers, use the wrappers in [org.njarasoa.fijerena.core.ui.theme.TimeFormat]
 * which grab context automatically.
 */
object TimeFormat {
    private val formatter24 = DateTimeFormatter.ofPattern("HH:mm")
    private val formatter12 = DateTimeFormatter.ofPattern("h:mm a")

    fun formatTime(context: Context, epochSeconds: Long): String {
        val localTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(epochSeconds),
            ZoneId.systemDefault()
        )
        return localTime.format(if (DateFormat.is24HourFormat(context)) formatter24 else formatter12)
    }

    fun formatTimeRange(context: Context, startEpochSeconds: Long, endEpochSeconds: Long): String {
        return "${formatTime(context, startEpochSeconds)} - ${formatTime(context, endEpochSeconds)}"
    }

    fun formatClockTime(context: Context, date: Date): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }
}
