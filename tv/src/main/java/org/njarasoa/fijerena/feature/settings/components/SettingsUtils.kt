package org.njarasoa.fijerena.feature.settings.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatTimestamp(millis: Long): String {
    val format = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    format.timeZone = TimeZone.getDefault()
    return format.format(Date(millis))
}

fun formatProgrammeCount(count: Int): String =
    when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
