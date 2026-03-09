package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

/**
 * Utility for formatting numbers and durations for the UI.
 */
object NumberUtils {

    /**
     * Format a count (e.g. programs, channels) to a short string (e.g. 1.2k, 5m).
     */
    fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> "%.1fm".format(count / 1_000_000.0)
            count >= 1_000 -> "%.1fk".format(count / 1_000.0)
            else -> count.toString()
        }
    }

    /**
     * Format a duration in milliseconds to a human-readable string.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs < 10_000) return "${durationMs}ms"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    /**
     * Format bytes to a human-readable string (KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Format a timestamp to a medium date/time string.
     */
    fun formatTimestamp(context: Context, millis: Long): String {
        val dateFormat = DateFormat.getMediumDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val date = Date(millis)
        return "${dateFormat.format(date)}, ${timeFormat.format(date)}"
    }

    /**
     * Format epoch seconds to a medium date/time string.
     */
    fun formatEpochDate(context: Context, epochSeconds: Long): String {
        return formatTimestamp(context, epochSeconds * 1000L)
    }
}
