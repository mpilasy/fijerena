package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.ParsedQuery
import org.njarasoa.fijerena.core.network.SearchUtils
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer

class SearchViewModel(
    private val context: android.content.Context,
    private val contentType: String,
) : ViewModel() {
    private var repository: org.njarasoa.fijerena.core.network.MediaRepository? = null

    private suspend fun ensureRepo(): org.njarasoa.fijerena.core.network.MediaRepository {
        val repo = repository ?: AppContainer.getInstance(context).getMediaRepository().also { repository = it }
        return repo
    }

    sealed class UiState {
        data class Loading(val message: String? = null) : UiState()

        data class Success(
            val categoryResults: List<CategorySearchResult> = emptyList(),
            val allResults: List<SearchResult>,
            val filteredResults: List<SearchResult>,
            val query: String,
            /** Per content type, matches hidden because their category is excluded by the provider's category filters. */
            val excludedCountByType: Map<String, Int> = emptyMap(),
            val isSearching: Boolean = false,
            val searchProgress: String? = null,
            val searchDataSize: String? = null,
            val totalDuration: String? = null,
            val networkWallDuration: String? = null,
            val networkAccumDuration: String? = null,
            val networkCalls: Int = 0,
            val failedCalls: Int = 0,
            val firstError: String? = null,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    data class SearchResult(
        val itemId: String,
        val streamName: String,
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val thumbnailUrl: String? = null,
        val mediaType: org.njarasoa.fijerena.core.player.domain.MediaType? = null,
    )

    data class CategorySearchResult(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
    )

    private data class SearchableCategory(
        val category: org.njarasoa.fijerena.core.player.domain.MediaCategory,
        val contentType: String,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val appSettings = org.njarasoa.fijerena.core.network.AppSettings(context)

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null

    companion object {
        private const val PARALLEL_BATCH_SIZE = 20
        private const val TARGET_RESULTS = 200
        const val CONTENT_TYPE_ALL = "ALL"
    }

    // Pre-fetched categories (loaded in background on init)
    private var prefetchedCategories: List<SearchableCategory>? = null

    init {
        _uiState.value =
            UiState.Success(
                allResults = emptyList(),
                filteredResults = emptyList(),
                query = "",
            )
        _searchHistory.value = appSettings.getSearchHistory()
        // Pre-fetch category list + all missing/stale category items in background.
        viewModelScope.launch(Dispatchers.IO) {
            val repo =
                try {
                    ensureRepo()
                } catch (_: Exception) {
                    return@launch
                }

            if (!repo.isConnected()) {
                repo.connect()
            }

            val capabilities = repo.getCapabilities()
            val targetContentTypes =
                if (contentType == CONTENT_TYPE_ALL) {
                    capabilities?.supportedContentTypes?.toList() ?: emptyList()
                } else {
                    listOf(contentType)
                }

            val allCategories = mutableListOf<SearchableCategory>()
            val semaphore = Semaphore(PARALLEL_BATCH_SIZE)

            targetContentTypes.forEach { type ->
                val result = repo.getFilteredCategories(type)
                result.onSuccess { categories ->
                    val realCategories = categories.filter { !it.isVirtual }
                    realCategories.forEach { cat ->
                        allCategories.add(SearchableCategory(cat, type))
                    }

                    // Launch prefetch for this batch only if search is not supported natively via database/server
                    if (capabilities?.supportsSearch != true) {
                        realCategories.map { category ->
                            launch {
                                if (!repo.getItemsIfCached(category.id, type).isNullOrEmpty()) return@launch
                                semaphore.withPermit {
                                    try {
                                        repo.getItemsForSearch(category.id, type)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SearchViewModel", "Failed to get items for search category ${category.id}", e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            prefetchedCategories = allCategories
        }
    }

    /** Called when the user presses the Search button or keyboard search action. */
    fun performSearch(query: String) {
        if (query.isBlank() || query.length < 2) return
        // Save to history
        appSettings.addSearchHistory(query)
        _searchHistory.value = appSettings.getSearchHistory()
        // Cancel previous search, start new one
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch(Dispatchers.IO) {
                doSearch(this, query)
            }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value =
            UiState.Success(
                query = "",
                categoryResults = emptyList(),
                allResults = emptyList(),
                filteredResults = emptyList(),
                isSearching = false,
                searchProgress = null,
                searchDataSize = null,
                networkCalls = 0,
                failedCalls = 0,
                totalDuration = "0ms",
                networkWallDuration = "0ms",
                networkAccumDuration = "0ms",
                firstError = null,
            )
    }

    fun removeSearchHistoryEntry(query: String) {
        appSettings.removeSearchHistory(query)
        _searchHistory.value = appSettings.getSearchHistory()
    }

    fun clearSearchHistory() {
        appSettings.clearSearchHistory()
        _searchHistory.value = emptyList()
    }

    private fun formatSeconds(ms: Long): String = "%.1fs".format(ms / 1000.0)

    private suspend fun doSearch(
        scope: kotlinx.coroutines.CoroutineScope,
        query: String,
    ) {
        try {
            val startTime = System.currentTimeMillis()

            val repo = ensureRepo()

            if (!repo.isConnected()) {
                val connectResult = repo.connect()
                if (connectResult.isFailure) {
                    _uiState.value = UiState.Error(context.getString(R.string.search_error_session_expired))
                    return
                }
            }

            val targetContentTypes =
                if (contentType == CONTENT_TYPE_ALL) {
                    repo.getCapabilities()?.supportedContentTypes?.toList() ?: emptyList()
                } else {
                    listOf(contentType)
                }

            _uiState.value = UiState.Loading()

            // The background prefetch may not have populated the category list yet (a search fired
            // right after the screen opened). Fall back to loading categories now — they're
            // DB-cached and cheap — so category matching never silently sees an empty list.
            val realCategories =
                prefetchedCategories?.takeIf { it.isNotEmpty() }
                    ?: targetContentTypes.flatMap { type ->
                        repo.getFilteredCategories(type).getOrNull()
                            ?.filter { !it.isVirtual }
                            ?.map { SearchableCategory(it, type) }
                            ?: emptyList()
                    }
            // Build the id→name map from the UNFILTERED category list: a stream can belong to a
            // category hidden from browse by the provider's script/prefix filter (e.g. names with
            // non-Latin unicode like "FR| PRIME ᴿᴬᵂ"), and we still want its name shown on results
            // instead of a blank "Category:" line.
            val categoryNameById =
                targetContentTypes
                    .flatMap { type -> repo.getCategories(type).getOrNull().orEmpty() }
                    .associate { it.id to it.name }
            val normalizedQuery = query.trim().lowercase()
            val parsedQuery = SearchUtils.parseQuery(normalizedQuery)

            val matchingCategories =
                realCategories.mapNotNull {
                    if (SearchUtils.matchesQuery(it.category.name, parsedQuery)) {
                        CategorySearchResult(it.category.id, it.category.name, it.contentType)
                    } else {
                        null
                    }
                }

            val excludedCountByType =
                targetContentTypes
                    .associateWith { repo.countExcludedSearchMatches(query, it) }
                    .filterValues { it > 0 }

            // Try server-side search first (e.g., Jellyfin)
            val serverResults = mutableListOf<SearchResult>()
            var serverSearchSuccess = false

            for (type in targetContentTypes) {
                val serverResult = repo.search(query, type)
                if (serverResult != null) {
                    serverResult.fold(
                        onSuccess = { items ->
                            serverSearchSuccess = true

                            items.forEach { item ->
                                serverResults.add(
                                    SearchResult(
                                        itemId = item.id,
                                        streamName = item.name,
                                        categoryId = item.categoryId,
                                        categoryName = categoryNameById[item.categoryId] ?: "",
                                        contentType = type,
                                        thumbnailUrl = item.thumbnailUrl,
                                        mediaType = item.mediaType,
                                    ),
                                )
                            }
                        },
                        onFailure = { },
                    )
                }
            }

            if (serverSearchSuccess) {
                // Return server results
                val elapsed = System.currentTimeMillis() - startTime
                val sortedResults = sortResults(serverResults, normalizedQuery, parsedQuery)
                _uiState.value =
                    UiState.Success(
                        categoryResults = matchingCategories,
                        allResults = sortedResults,
                        filteredResults = sortedResults,
                        query = query,
                        excludedCountByType = excludedCountByType,
                        totalDuration = formatSeconds(elapsed),
                        networkCalls = 1,
                    )
                return
            }

            // Fall back to client-side search
            _uiState.value = UiState.Loading(context.getString(org.njarasoa.fijerena.core.ui.R.string.search_fallback_local))
            kotlinx.coroutines.delay(50) // Allow UI to render the new loading message
            
            val results = mutableListOf<SearchResult>()

            // Phase 1: Local cache scan
            for (sc in realCategories) {
                currentCoroutineContext().job.ensureActive()
                val cached = repo.getItemsIfCached(sc.category.id, sc.contentType)
                if (!cached.isNullOrEmpty()) {
                    val categoryMatches = SearchUtils.matchesQuery(sc.category.name, parsedQuery)
                    cached.mapNotNullTo(results) { item ->
                        if (categoryMatches || SearchUtils.matchesQuery(item.name, parsedQuery)) {
                            SearchResult(
                                item.id,
                                item.name,
                                sc.category.id,
                                sc.category.name,
                                sc.contentType,
                                item.thumbnailUrl,
                                item.mediaType,
                            )
                        } else {
                            null
                        }
                    }
                }
            }

            val finalResults = sortResults(results, normalizedQuery, parsedQuery).take(TARGET_RESULTS)
            val elapsed = System.currentTimeMillis() - startTime
            _uiState.value =
                UiState.Success(
                    categoryResults = matchingCategories,
                    allResults = finalResults,
                    filteredResults = finalResults,
                    query = query,
                    excludedCountByType = excludedCountByType,
                    totalDuration = formatSeconds(elapsed),
                )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.value = UiState.Error(e.message ?: context.getString(R.string.search_error_failed))
        }
    }

    // ⚡ Bolt: Performance Optimization
    // Replaced O(N log N) dynamic evaluation of expensive string matching in `compareBy`
    // with an O(N) bucketing approach. The string matching logic is now evaluated exactly once
    // per item, and the simple string sorting happens only within the smaller buckets.
    private fun sortResults(
        results: List<SearchResult>,
        normalizedQuery: String,
        parsedQuery: ParsedQuery,
    ): List<SearchResult> {
        val exactMatches = ArrayList<SearchResult>()
        val startsWithMatches = ArrayList<SearchResult>()
        val queryMatches = ArrayList<SearchResult>()
        val others = ArrayList<SearchResult>()

        for (result in results) {
            val name = result.streamName
            if (name.equals(normalizedQuery, ignoreCase = true)) {
                exactMatches.add(result)
            } else if (name.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithMatches.add(result)
            } else if (!parsedQuery.isEmpty && SearchUtils.matchesQuery(name, parsedQuery)) {
                queryMatches.add(result)
            } else {
                others.add(result)
            }
        }

        val comparator = Comparator<SearchResult> { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.streamName, b.streamName) }
        exactMatches.sortWith(comparator)
        startsWithMatches.sortWith(comparator)
        queryMatches.sortWith(comparator)
        others.sortWith(comparator)

        val sorted = ArrayList<SearchResult>(results.size)
        sorted.addAll(exactMatches)
        sorted.addAll(startsWithMatches)
        sorted.addAll(queryMatches)
        sorted.addAll(others)
        return sorted
    }

    fun isFavorite(
        itemId: String,
        contentType: String,
    ): Boolean = repository?.isFavorite(itemId, contentType) ?: false

    fun isFavoriteCategory(
        categoryId: String,
        contentType: String,
    ): Boolean = repository?.isFavoriteCategory(categoryId, contentType) ?: false

    fun toggleFavorite(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        isFavorite: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = ensureRepo()
            if (isFavorite) {
                repo.removeFavorite(itemId, contentType)
            } else {
                repo.addFavorite(itemId, itemName, categoryId, contentType)
            }
        }
    }

    fun toggleFavoriteCategory(
        categoryId: String,
        categoryName: String,
        contentType: String,
        isFavorite: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = ensureRepo()
            if (isFavorite) {
                repo.removeFavoriteCategory(categoryId, contentType)
            } else {
                repo.addFavoriteCategory(categoryId, categoryName, contentType)
            }
        }
    }

    /**
     * Manual watched/unwatched mark (Phase 6, docs/plans/watch-state-durable-storage-plan.md). Unlike
     * [isFavorite], this has no synchronous in-memory cache to read — `watch_state` reads are
     * suspend since Phase 3 — so building the long-press menu target for a search result means an
     * explicit fetch rather than an inline call.
     */
    suspend fun isWatchedSuspend(
        itemId: String,
        contentType: String,
    ): Boolean = ensureRepo().getPlaybackPositionSuspend(itemId, contentType)?.isCompleted == true

    fun toggleWatched(
        itemId: String,
        contentType: String,
        isWatched: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ensureRepo().setWatched(itemId, contentType, !isWatched)
        }
    }
}

/**
 * Groups search results by content type, with Live TV/Movies/TV Shows sorted first
 * (in that order) and any other types appended after.
 */
fun buildGroupedSearchResults(
    categoryResults: List<SearchViewModel.CategorySearchResult>,
    results: List<SearchViewModel.SearchResult>,
): List<Triple<String, List<SearchViewModel.CategorySearchResult>, List<SearchViewModel.SearchResult>>> {
    val catsByType = categoryResults.groupBy { it.contentType }
    val streamsByType = results.groupBy { it.contentType }
    val allTypes = (catsByType.keys + streamsByType.keys).distinct()
    val priorityTypes = listOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS)
    val sortedTypes = priorityTypes + (allTypes - priorityTypes.toSet())
    return sortedTypes.map { type -> Triple(type, catsByType[type].orEmpty(), streamsByType[type].orEmpty()) }
}

/** Adds [item] if absent, removes it if present. */
fun Set<String>.toggled(item: String): Set<String> = if (contains(item)) this - item else this + item
