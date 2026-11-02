package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.content.res.Resources
import org.njarasoa.fijerena.core.ui.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Numbers, sizes and durations on screen, written the app language's way ("195 300", "19,3 Mo",
 * "1 337 ms" in French; "195,300", "19.3 MB", "1,337 ms" in English). Every screen on TV and phone
 * uses these, with the locale of its Resources ([appLocale]). A unit follows its number after a
 * no-break space, so a line never wraps between them.
 */
object NumberUtils {
    /** A whole count, grouped the [locale]'s way: "195,300" in English, "195 300" in French. */
    fun formatCount(
        count: Long,
        locale: Locale,
    ): String = NumberFormat.getIntegerInstance(locale).format(count)

    fun formatCount(
        count: Int,
        locale: Locale,
    ): String = formatCount(count.toLong(), locale)

    /** [formatCount] in the app language of [resources]. */
    fun formatCount(
        resources: Resources,
        count: Long,
    ): String = formatCount(count, resources.appLocale())

    fun formatCount(
        resources: Resources,
        count: Int,
    ): String = formatCount(count.toLong(), resources.appLocale())

    /** [value] with one decimal, the [locale]'s decimal mark and grouping: "19.3" in English, "19,3" in French. */
    fun formatDecimal(
        value: Double,
        locale: Locale,
    ): String =
        NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }.format(value)

    /** "1,337 ms" under 10 seconds, "45 s" under a minute, else "2 min 5 s" ("1 337 ms" in French). */
    fun formatDuration(
        durationMs: Long,
        locale: Locale,
    ): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            durationMs < 10_000 -> "${formatCount(durationMs, locale)}${NBSP}ms"
            minutes > 0 -> "${formatCount(minutes, locale)}${NBSP}min $seconds${NBSP}s"
            else -> "$seconds${NBSP}s"
        }
    }

    /** [formatDuration] in the app language of [resources]. */
    fun formatDuration(
        resources: Resources,
        durationMs: Long,
    ): String = formatDuration(durationMs, resources.appLocale())

    /** A size in the app language: "19.3 MB" in English, "19,3 Mo" in French. */
    fun formatBytes(
        resources: Resources,
        bytes: Long,
    ): String = formatBytes(bytes, resources.appLocale()) { unit, number -> resources.getString(unit, number) }

    /** [formatBytes] with the unit's words from [unitText] (a size string resource and the number). */
    internal fun formatBytes(
        bytes: Long,
        locale: Locale,
        unitText: (Int, String) -> String,
    ): String =
        when {
            bytes >= GB -> unitText(R.string.size_gigabytes, formatDecimal(bytes / GB.toDouble(), locale))
            bytes >= MB -> unitText(R.string.size_megabytes, formatDecimal(bytes / MB.toDouble(), locale))
            bytes >= KB -> unitText(R.string.size_kilobytes, formatDecimal(bytes / KB.toDouble(), locale))
            else -> unitText(R.string.size_bytes, formatCount(bytes, locale))
        }

    /** A timestamp as a medium date and time ("3 Nov 2026, 14:30"), see [DateText.dateTime]. */
    fun formatTimestamp(
        context: Context,
        millis: Long,
    ): String = DateText.dateTime(context, millis)

    private const val NBSP = ' '
    private const val KB = 1_024L
    private const val MB = 1_048_576L
    private const val GB = 1_073_741_824L
}
