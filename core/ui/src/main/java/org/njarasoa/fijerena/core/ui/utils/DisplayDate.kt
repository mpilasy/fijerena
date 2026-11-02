package org.njarasoa.fijerena.core.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import java.time.LocalDate
import java.util.Locale

/**
 * A release or air date as the app language writes it ("17 Feb 2028" in English, which uses the
 * UK locale; see [LocaleManager]). Sources and TMDB send ISO days ("2028-02-17", sometimes with a
 * time after them); any other text is shown as it is.
 */
fun displayDate(
    raw: String,
    locale: Locale,
): String {
    val day = runCatching { LocalDate.parse(raw.trim().take(ISO_DAY_LENGTH)) }.getOrNull() ?: return raw
    return DateText.mediumDate(day, locale)
}

/** [displayDate] in the app language of the screen's Resources. */
@Composable
fun displayDate(raw: String): String = displayDate(raw, LocalResources.current.appLocale())

private const val ISO_DAY_LENGTH = 10
