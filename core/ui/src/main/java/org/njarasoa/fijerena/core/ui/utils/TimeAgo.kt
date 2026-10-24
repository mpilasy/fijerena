package org.njarasoa.fijerena.core.ui.utils

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * "2 hours ago" in the app's language. DateUtils.getRelativeTimeSpanString uses the device's
 * language, so a French app on an English phone said "Mis à jour 1 hour ago".
 */
@Composable
fun timeAgo(
    atMs: Long,
    now: Long = System.currentTimeMillis(),
): String = timeAgo(atMs, now, LocalConfiguration.current.locales[0])

/** [timeAgo] in [locale]: minutes under an hour, hours under a day, then days. */
fun timeAgo(
    atMs: Long,
    now: Long,
    locale: Locale,
): String {
    val elapsed = (now - atMs).coerceAtLeast(0L)
    val (count, unit) =
        when {
            elapsed < DateUtils.HOUR_IN_MILLIS -> elapsed / DateUtils.MINUTE_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.MINUTES
            elapsed < DateUtils.DAY_IN_MILLIS -> elapsed / DateUtils.HOUR_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.HOURS
            else -> elapsed / DateUtils.DAY_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.DAYS
        }
    return RelativeDateTimeFormatter
        .getInstance(ULocale.forLocale(locale))
        .format(count.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}
