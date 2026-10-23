package org.njarasoa.fijerena.core.ui.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A release or air date as the app language writes it ("17 Feb 2028" in English, which uses the
 * UK locale; see [LocaleManager]). Sources and TMDB send ISO days ("2028-02-17", sometimes with a
 * time after them); any other text is shown as it is.
 */
fun displayDate(
    raw: String,
    locale: Locale = Locale.getDefault(),
): String {
    val day = runCatching { LocalDate.parse(raw.trim().take(ISO_DAY_LENGTH)) }.getOrNull() ?: return raw
    return day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

private const val ISO_DAY_LENGTH = 10
