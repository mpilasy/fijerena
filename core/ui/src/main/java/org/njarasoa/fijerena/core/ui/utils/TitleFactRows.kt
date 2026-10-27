package org.njarasoa.fijerena.core.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.TitleFacts
import org.njarasoa.fijerena.core.player.domain.cleanTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.languageLabel
import org.njarasoa.fijerena.core.ui.viewmodels.regionLabel
import java.util.Locale

/** One label/value row of a details tab. */
data class FactRow(
    val label: String,
    val value: String,
)

/**
 * The details-tab rows for [facts], in the app language: original title (only when it is not
 * [shownName]), original language, countries, spoken languages, studios or networks, creators
 * and a show's status. The tagline is not a row: the page shows it above the plot.
 */
@Composable
fun titleFactRows(
    facts: TitleFacts,
    shownName: String,
    isShow: Boolean,
): List<FactRow> {
    val locale = Locale.getDefault()
    val rows = mutableListOf<FactRow>()
    facts.originalTitle?.takeUnless { it.equals(cleanTitle(shownName).trim(), ignoreCase = true) }?.let {
        rows += FactRow(stringResource(R.string.details_label_original_title), it)
    }
    facts.originalLanguage?.takeIf { it != NO_LANGUAGE }?.let {
        rows += FactRow(stringResource(R.string.details_label_original_language), languageLabel(it, locale))
    }
    if (facts.countries.isNotEmpty()) {
        rows +=
            FactRow(
                pluralStringResource(R.plurals.details_label_countries, facts.countries.size),
                facts.countries.joinToString(", ") { countryName(it, locale) },
            )
    }
    val languages = facts.spokenLanguages.filter { it != NO_LANGUAGE }
    if (languages.isNotEmpty()) {
        rows +=
            FactRow(
                pluralStringResource(R.plurals.details_label_languages, languages.size),
                languages.joinToString(", ") { languageLabel(it, locale) },
            )
    }
    if (facts.studios.isNotEmpty()) {
        val label = if (isShow) R.plurals.details_label_networks else R.plurals.details_label_studios
        rows += FactRow(pluralStringResource(label, facts.studios.size), facts.studios.joinToString(", "))
    }
    if (facts.creators.isNotEmpty()) {
        rows += FactRow(stringResource(R.string.details_label_creators), facts.creators.joinToString(", "))
    }
    val status =
        listOfNotNull(
            facts.status?.let { showStatus(it) },
            facts.seasonCount?.let { seasons ->
                facts.episodeCount?.let { episodes -> stringResource(R.string.series_seasons_and_episodes_format, seasons, episodes) }
            },
        )
    if (status.isNotEmpty()) rows += FactRow(stringResource(R.string.details_label_status), status.joinToString(" · "))
    return rows
}

// TMDB's code for "no spoken language" (a silent film, a documentary of music).
private const val NO_LANGUAGE = "xx"

private val COUNTRY_CODE = Regex("[A-Z]{2}")

/** A TMDB country code's name in [locale]; a source's own name ("United States") as it is. */
private fun countryName(
    country: String,
    locale: Locale,
): String = if (COUNTRY_CODE.matches(country)) regionLabel(country, locale) { null } else country

/** A show status as TMDB or Jellyfin sends it, in the app language; one the app doesn't know as it is. */
@Composable
private fun showStatus(status: String): String =
    when (status.lowercase(Locale.ROOT)) {
        "returning series", "continuing" -> stringResource(R.string.details_status_returning)
        "ended" -> stringResource(R.string.details_status_ended)
        "canceled", "cancelled" -> stringResource(R.string.details_status_cancelled)
        "in production" -> stringResource(R.string.details_status_in_production)
        "planned" -> stringResource(R.string.details_status_planned)
        "pilot" -> stringResource(R.string.details_status_pilot)
        else -> status
    }
