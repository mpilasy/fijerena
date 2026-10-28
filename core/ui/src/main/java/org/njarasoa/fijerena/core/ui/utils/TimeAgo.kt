package org.njarasoa.fijerena.core.ui.utils

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import java.util.Locale

/**
 * "2 hours ago" in the app's language. DateUtils.getRelativeTimeSpanString uses the device's
 * language, so a French app on an English phone said "Mis à jour 1 hour ago".
 */
@Composable
fun timeAgo(
    atMs: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val locale = LocalConfiguration.current.locales[0]
    if (locale.language != MALAGASY) return timeAgo(atMs, now, locale)
    // Android's ICU has no Malagasy data: it fell back to the root form, "-36 min".
    val (count, unit) = elapsed(atMs, now)
    val res =
        when (unit) {
            RelativeDateTimeFormatter.RelativeUnit.MINUTES -> R.string.time_ago_minutes
            RelativeDateTimeFormatter.RelativeUnit.HOURS -> R.string.time_ago_hours
            else -> R.string.time_ago_days
        }
    return stringResource(res, count)
}

private const val MALAGASY = "mg"

/** How long ago [atMs] was at [now], in the unit [timeAgo] says it in. */
private fun elapsed(
    atMs: Long,
    now: Long,
): Pair<Long, RelativeDateTimeFormatter.RelativeUnit> {
    val elapsed = (now - atMs).coerceAtLeast(0L)
    return when {
        elapsed < DateUtils.HOUR_IN_MILLIS -> elapsed / DateUtils.MINUTE_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        elapsed < DateUtils.DAY_IN_MILLIS -> elapsed / DateUtils.HOUR_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.HOURS
        else -> elapsed / DateUtils.DAY_IN_MILLIS to RelativeDateTimeFormatter.RelativeUnit.DAYS
    }
}

/** [timeAgo] in [locale]: minutes under an hour, hours under a day, then days. */
fun timeAgo(
    atMs: Long,
    now: Long,
    locale: Locale,
): String {
    val (count, unit) = elapsed(atMs, now)
    return RelativeDateTimeFormatter
        .getInstance(ULocale.forLocale(locale))
        .format(count.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}
