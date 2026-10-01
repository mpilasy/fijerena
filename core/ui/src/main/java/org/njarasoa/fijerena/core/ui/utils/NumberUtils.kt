package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for formatting numbers and durations for the UI.
 */
object NumberUtils {
    /**
     * Format a count (e.g. programs, channels) to a short string (e.g. 1.2k, 5m).
     * Locale.US, like the byte sizes below and PlaybackFormat's formatRating()/formatBitrate():
     * a device-locale decimal comma would make "1,2k" sit next to dot-formatted numbers.
     */
    fun formatCount(count: Int): String =
        when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fm", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
            else -> count.toString()
        }

    /**
     * Format a duration in milliseconds to a human-readable string.
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            durationMs < 10_000 -> "${durationMs}ms"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Format bytes to a human-readable string (KB, MB, GB). Always a decimal point, see formatCount.
     */
    fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }

    /**
     * Format a timestamp to a short time string (e.g. 14:30:05).
     */
    fun formatShortTime(millis: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(millis))

    /**
     * Format a timestamp to a medium date/time string.
     */
    fun formatTimestamp(
        context: Context,
        millis: Long,
    ): String {
        val dateFormat = DateFormat.getMediumDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val date = Date(millis)
        return "${dateFormat.format(date)}, ${timeFormat.format(date)}"
    }

    /**
     * Format epoch seconds to a medium date/time string.
     */
    fun formatEpochDate(
        context: Context,
        epochSeconds: Long,
    ): String = formatTimestamp(context, epochSeconds * 1000L)
}
