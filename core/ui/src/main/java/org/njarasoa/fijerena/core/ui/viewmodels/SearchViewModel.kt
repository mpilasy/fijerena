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
import org.njarasoa.fijerena.core.ui.di.AppContainer

class SearchViewModel(
    private val context: android.content.Context,
    private val contentType: String,
) : ViewModel() {
    private var repository: org.njarasoa.fijerena.core.network.MediaRepository? = null

    private suspend fun ensureRepo(): org.njarasoa.fijerena.core.network.MediaRepository {
        if (repository == null) {
            val container = AppContainer.getInstance(context)
            repository = container.getMediaRepository()
        }
        return repository!!
    }

    sealed class UiState {
        data object Loading : UiState()

        data class Success(
            val categoryResults: List<CategorySearchResult> = emptyList(),
            val allResults: List<SearchResult>,
            val filteredResults: List<SearchResult>,
            val query: String,
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

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
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
                    val realCategories = categories.filter { it.id != "last_watched" && !it.isVirtual }
                    realCategories.forEach { cat ->
                        allCategories.add(SearchableCategory(cat, type))
                    }

                    // Launch prefetch for this batch
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
                    _uiState.value = UiState.Error("Session expired. Please login again.")
                    return
                }
            }

            val targetContentTypes =
                if (contentType == CONTENT_TYPE_ALL) {
                    repo.getCapabilities()?.supportedContentTypes?.toList() ?: emptyList()
                } else {
                    listOf(contentType)
                }

            _uiState.value = UiState.Loading

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
                                        categoryName = "",
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
                val sortedResults = sortResults(serverResults, query.trim().lowercase(), SearchUtils.parseQuery(query))
                _uiState.value =
                    UiState.Success(
                        allResults = sortedResults,
                        filteredResults = sortedResults,
                        query = query,
                        totalDuration = formatSeconds(elapsed),
                        networkCalls = 1,
                    )
                return
            }

            // Fall back to client-side search
            val realCategories = prefetchedCategories ?: emptyList()
            val results = mutableListOf<SearchResult>()
            val normalizedQuery = query.trim().lowercase()
            val parsedQuery = SearchUtils.parseQuery(normalizedQuery)

            val matchingCategories =
                realCategories
                    .filter { SearchUtils.matchesQuery(it.category.name, parsedQuery) }
                    .map { CategorySearchResult(it.category.id, it.category.name, it.contentType) }

            // Phase 1: Local cache scan
            for (sc in realCategories) {
                currentCoroutineContext().job.ensureActive()
                val cached = repo.getItemsIfCached(sc.category.id, sc.contentType)
                if (!cached.isNullOrEmpty()) {
                    results.addAll(
                        cached.filter { SearchUtils.matchesQuery(it.name, parsedQuery) }.map { item ->
                            SearchResult(
                                item.id,
                                item.name,
                                sc.category.id,
                                sc.category.name,
                                sc.contentType,
                                item.thumbnailUrl,
                                item.mediaType,
                            )
                        },
                    )
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
                    totalDuration = formatSeconds(elapsed),
                )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.value = UiState.Error(e.message ?: "Failed to search")
        }
    }

    private fun sortResults(
        results: List<SearchResult>,
        normalizedQuery: String,
        parsedQuery: ParsedQuery,
    ): List<SearchResult> {
        return results
            .sortedWith(
                compareBy<SearchResult> {
                    when {
                        it.streamName.equals(normalizedQuery, ignoreCase = true) -> 0
                        it.streamName.startsWith(normalizedQuery, ignoreCase = true) -> 1
                        else -> if (!parsedQuery.isEmpty && SearchUtils.matchesQuery(it.streamName, parsedQuery)) 2 else 3
                    }
                }.thenBy { it.streamName },
            )
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
}
