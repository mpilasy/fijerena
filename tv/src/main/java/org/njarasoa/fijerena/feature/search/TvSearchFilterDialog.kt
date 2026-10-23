package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.search.SearchFacetLabels
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.FacetCount
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacetChoice
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacets
import org.njarasoa.fijerena.core.ui.viewmodels.SearchSourceCount
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.components.input.TvRadioRow
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Search's filter on TV (the search decade genre filter plan, Phase 3): a
 * section each for the results' sources (one choice; Expert mode, two or more sources), decades,
 * genres, languages and regions (several), with their counts, and **Clear filters** while one is active. A dialog
 * window: focus opens on the first chosen row, else the first choice (never on Clear filters);
 * OK changes a row and keeps focus on it; Clear filters, Close or Back closes it.
 */
@Composable
internal fun TvSearchFilterDialog(
    sources: List<SearchSourceCount>,
    selectedSource: Long?,
    onSelectSource: (Long?) -> Unit,
    facets: SearchFacets,
    choice: SearchFacetChoice,
    onChoiceChange: (SearchFacetChoice) -> Unit,
    labels: SearchFacetLabels,
    onDismiss: () -> Unit,
) {
    val entry = remember { FocusRequester() }
    val active = selectedSource != null || choice.isActive
    // The row that takes the first focus: the chosen source, else the first chosen decade or
    // genre, else the first choice.
    val entryKey =
        remember {
            when {
                selectedSource != null -> "source_$selectedSource"
                choice.decades.isNotEmpty() -> "decade_${facets.decades.first { it.value in choice.decades }.value}"
                choice.genres.isNotEmpty() -> "genre_${facets.genres.first { it.value in choice.genres }.value}"
                choice.languages.isNotEmpty() -> "language_${facets.languages.first { it.value in choice.languages }.value}"
                choice.regions.isNotEmpty() -> "region_${facets.regions.first { it.value in choice.regions }.value}"
                sources.isNotEmpty() -> "source_null"
                facets.decades.isNotEmpty() -> "decade_${facets.decades.first().value}"
                facets.genres.isNotEmpty() -> "genre_${facets.genres.first().value}"
                facets.languages.isNotEmpty() -> "language_${facets.languages.first().value}"
                else -> "region_${facets.regions.firstOrNull()?.value}"
            }
        }

    // A row stays while the dialog is open, with no results under the other section's choice: one
    // that went would take focus with it. Each row keeps its place by its key as the counts reorder.
    val seenDecades = remember { mutableSetOf<Int>() }
    val seenGenres = remember { mutableMapOf<String, String>() }
    facets.decades.forEach { seenDecades += it.value }
    facets.genres.forEach { seenGenres.putIfAbsent(it.value, it.label) }
    val decades =
        facets.decades + (seenDecades - facets.decades.map { it.value }.toSet()).sortedDescending().map { FacetCount(it, it.toString(), 0) }
    val genres =
        facets.genres + (seenGenres.keys - facets.genres.map { it.value }.toSet()).map { FacetCount(it, seenGenres.getValue(it), 0) }
    val seenLanguages = remember { mutableSetOf<String>() }
    facets.languages.forEach { seenLanguages += it.value }
    val languages =
        facets.languages + (seenLanguages - facets.languages.map { it.value }.toSet()).map { FacetCount(it, it, 0) }
    val seenRegions = remember { mutableSetOf<String>() }
    facets.regions.forEach { seenRegions += it.value }
    val regions =
        facets.regions + (seenRegions - facets.regions.map { it.value }.toSet()).map { FacetCount(it, it, 0) }

    fun Modifier.entryIf(key: String) = if (key == entryKey) focusRequester(entry) else this

    CinemaAlertDialog(
        initialFocus = entry,
        onDismissRequest = onDismiss,
        // The darkest surface behind the rows, as the other TV pickers (TV UI audit #3).
        containerColor = CinemaTheme.colors.background,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
        title = { CinemaText(stringResource(R.string.search_filter_label)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // It closes the dialog: the row goes once nothing is chosen, and focus would go with it.
                if (active) {
                    TvOptionRow(
                        title = stringResource(R.string.search_filter_clear),
                        selected = false,
                        onClick = {
                            onSelectSource(null)
                            onChoiceChange(SearchFacetChoice())
                            onDismiss()
                        },
                    )
                }
                if (sources.isNotEmpty()) {
                    Heading(stringResource(R.string.search_filter_source))
                    TvRadioRow(
                        selected = selectedSource == null,
                        onClick = { onSelectSource(null) },
                        label = "${stringResource(R.string.search_all_sources_label)} (${sources.sumOf { it.count }})",
                        modifier = Modifier.entryIf("source_null"),
                    )
                    for (source in sources) {
                        TvRadioRow(
                            selected = selectedSource == source.providerId,
                            onClick = { onSelectSource(source.providerId) },
                            label = "${source.name} (${source.count})",
                            modifier = Modifier.entryIf("source_${source.providerId}"),
                        )
                    }
                }
                if (decades.isNotEmpty()) {
                    Heading(stringResource(R.string.search_filter_decade))
                    for (decade in decades) {
                        key("decade_${decade.value}") {
                            val on = decade.value in choice.decades
                            TvCheckRow(
                                checked = on,
                                onCheckedChange = {
                                    onChoiceChange(
                                        choice.copy(
                                            decades =
                                                if (on) {
                                                    choice.decades - decade.value
                                                } else {
                                                    choice.decades +
                                                        decade.value
                                                },
                                        ),
                                    )
                                },
                                label = "${labels.decade(decade.value)} (${decade.count})",
                                modifier = Modifier.entryIf("decade_${decade.value}"),
                            )
                        }
                    }
                }
                if (genres.isNotEmpty()) {
                    Heading(stringResource(R.string.search_filter_genre))
                    for (genre in genres) {
                        key("genre_${genre.value}") {
                            val on = genre.value in choice.genres
                            TvCheckRow(
                                checked = on,
                                onCheckedChange = {
                                    onChoiceChange(
                                        choice.copy(genres = if (on) choice.genres - genre.value else choice.genres + genre.value),
                                    )
                                },
                                label = "${genre.label} (${genre.count})",
                                modifier = Modifier.entryIf("genre_${genre.value}"),
                            )
                        }
                    }
                }
                if (languages.isNotEmpty()) {
                    Heading(stringResource(R.string.search_filter_language))
                    for (language in languages) {
                        key("language_${language.value}") {
                            val on = language.value in choice.languages
                            TvCheckRow(
                                checked = on,
                                onCheckedChange = {
                                    onChoiceChange(
                                        choice.copy(
                                            languages = if (on) choice.languages - language.value else choice.languages + language.value,
                                        ),
                                    )
                                },
                                label = "${labels.language(language.value)} (${language.count})",
                                modifier = Modifier.entryIf("language_${language.value}"),
                            )
                        }
                    }
                }
                if (regions.isNotEmpty()) {
                    Heading(stringResource(R.string.search_filter_region))
                    for (region in regions) {
                        key("region_${region.value}") {
                            val on = region.value in choice.regions
                            TvCheckRow(
                                checked = on,
                                onCheckedChange = {
                                    onChoiceChange(
                                        choice.copy(regions = if (on) choice.regions - region.value else choice.regions + region.value),
                                    )
                                },
                                label = "${labels.region(region.value)} (${region.count})",
                                modifier = Modifier.entryIf("region_${region.value}"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_close), tone = TextTone.Inherit) }
        },
    )
}

/** A section's name: not focusable, it only names the rows under it. */
@Composable
private fun Heading(text: String) {
    CinemaText(
        text,
        modifier = Modifier.padding(top = Spacing.sm),
        role = TextRole.Label,
        tone = TextTone.Secondary,
    )
}
