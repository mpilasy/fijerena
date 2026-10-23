package org.njarasoa.fijerena.core.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacetChoice
import org.njarasoa.fijerena.core.ui.viewmodels.SearchFacets
import org.njarasoa.fijerena.core.ui.viewmodels.filterSummary
import org.njarasoa.fijerena.core.ui.viewmodels.languageLabel
import org.njarasoa.fijerena.core.ui.viewmodels.regionLabel

/** How Search's filter names a decade, a language and a region, in the app's language (TV and phone). */
class SearchFacetLabels(
    val decade: (Int) -> String,
    val language: (String) -> String,
    val region: (String) -> String,
) {
    /** The active filter in a few words ("panel-a · 1990s · Drama · English · Greece"), or null with none. */
    fun summary(
        sourceName: String?,
        facets: SearchFacets,
        choice: SearchFacetChoice,
    ): String? = filterSummary(sourceName, facets, choice, decade, language, region)
}

@Composable
fun rememberSearchFacetLabels(): SearchFacetLabels {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) {
        val custom =
            mapOf(
                "AF" to resources.getString(R.string.search_region_africa),
                "EX" to resources.getString(R.string.search_region_ex_yugoslavia),
                "LAT" to resources.getString(R.string.search_region_latin_america),
                "AS" to resources.getString(R.string.search_region_asia),
                "SC" to resources.getString(R.string.search_region_nordic),
            )
        SearchFacetLabels(
            decade = { resources.getString(R.string.search_decade_format, it) },
            language = { languageLabel(it, locale) },
            region = { key -> regionLabel(key, locale) { custom[it] } },
        )
    }
}
