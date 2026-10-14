package org.njarasoa.fijerena.core.ui.viewmodels

import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.CategorySearchResult
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult

// Search's source filter on TV and phone (docs/plans/20261014_search-source-filter-plan.md).

/** A source among a search's results, and how many of them are from it. */
data class SearchSourceCount(
    val providerId: Long,
    val name: String,
    val count: Int,
)

/**
 * Each source of [categories] and [results] with its count, sorted by name so the row doesn't
 * reorder as results stream in; empty when they come from fewer than two sources.
 */
fun searchSourceCounts(
    categories: List<CategorySearchResult>,
    results: List<SearchResult>,
): List<SearchSourceCount> {
    // A result's source name is null when the search covers a single source.
    val tagged = categories.map { it.providerId to it.sourceName } + results.map { it.providerId to it.sourceName }
    val counts =
        tagged
            .mapNotNull { (providerId, name) -> name?.let { providerId to it } }
            .groupBy({ it.first }, { it.second })
            .map { (providerId, names) -> SearchSourceCount(providerId, names.first(), names.size) }
    return if (counts.size < 2) emptyList() else counts.sortedBy { it.name.lowercase() }
}

/** The [selected] source while it still has results, else null (every source): a new query without it shows them all. */
fun effectiveSourceFilter(
    selected: Long?,
    sources: List<SearchSourceCount>,
): Long? = selected?.takeIf { id -> sources.any { it.providerId == id } }

/** The categories from [providerId], or all of them when null. */
fun List<CategorySearchResult>.fromSource(providerId: Long?): List<CategorySearchResult> =
    if (providerId == null) this else filter { it.providerId == providerId }

/** The results from [providerId], or all of them when null. */
@JvmName("resultsFromSource")
fun List<SearchResult>.fromSource(providerId: Long?): List<SearchResult> =
    if (providerId == null) this else filter { it.providerId == providerId }
