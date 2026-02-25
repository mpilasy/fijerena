package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.njarasoa.fijerena.core.network.MediaRepository

class SearchViewModel(
    private val repository: MediaRepository,
    private val contentType: String
) : ViewModel() {

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
            val firstError: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    data class SearchResult(
        val itemId: String,
        val streamName: String,
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val thumbnailUrl: String? = null,
        val mediaType: org.njarasoa.fijerena.core.player.domain.MediaType? = null
    )

    data class CategorySearchResult(
        val categoryId: String,
        val categoryName: String,
        val contentType: String
    )

    private data class SearchableCategory(
        val category: org.njarasoa.fijerena.core.player.domain.MediaCategory,
        val contentType: String
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    companion object {
        private const val PARALLEL_BATCH_SIZE = 20
        private const val TARGET_RESULTS = 200
        const val CONTENT_TYPE_ALL = "ALL"
    }

    // Pre-fetched categories (loaded in background on init)
    private var prefetchedCategories: List<SearchableCategory>? = null

    init {
        _uiState.value = UiState.Success(
            allResults = emptyList(),
            filteredResults = emptyList(),
            query = ""
        )
        // Pre-fetch category list + all missing/stale category items in background.
        // This job runs independently — never cancelled by search.
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.isConnected()) {
                repository.connect()
            }

            val targetContentTypes = if (contentType == CONTENT_TYPE_ALL) {
                repository.getCapabilities()?.supportedContentTypes?.toList() ?: emptyList()
            } else {
                listOf(contentType)
            }

            val allCategories = mutableListOf<SearchableCategory>()
            val semaphore = Semaphore(PARALLEL_BATCH_SIZE)

            targetContentTypes.forEach { type ->
                val result = repository.getFilteredCategories(type)
                result.onSuccess { categories ->
                    val realCategories = categories.filter { it.id != "last_watched" && !it.isVirtual }
                    realCategories.forEach { cat ->
                        allCategories.add(SearchableCategory(cat, type))
                    }

                    // Launch prefetch for this batch
                    realCategories.map { category ->
                        launch {
                            if (repository.getItemsIfCached(category.id, type) != null) return@launch
                            semaphore.withPermit {
                                try {
                                    repository.getItemsForSearch(category.id, type)
                                } catch (_: Exception) { }
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
        // Cancel previous search, start new one
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            doSearch(this, query)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatSeconds(ms: Long): String {
        return "%.1fs".format(ms / 1000.0)
    }

    private suspend fun doSearch(scope: kotlinx.coroutines.CoroutineScope, query: String) {
        try {
            val startTime = System.currentTimeMillis()
            var networkBytes = 0L
            var networkCalls = 0
            var failedCalls = 0
            var accumulatedNetworkMs = 0L
            var firstError: String? = null

            if (!repository.isConnected()) {
                val connectResult = repository.connect()
                if (connectResult.isFailure) {
                    _uiState.value = UiState.Error("Session expired. Please login again.")
                    return
                }
            }

            val targetContentTypes = if (contentType == CONTENT_TYPE_ALL) {
                repository.getCapabilities()?.supportedContentTypes?.toList() ?: emptyList()
            } else {
                listOf(contentType)
            }

            _uiState.value = UiState.Loading

            // Try server-side search first (e.g., Jellyfin)
            val serverResults = mutableListOf<SearchResult>()
            var serverSearchSuccess = false
            var serverError: String? = null

            for (type in targetContentTypes) {
                val serverResult = repository.search(query, type)
                if (serverResult != null) {
                    serverResult.fold(
                        onSuccess = { items ->
                            serverSearchSuccess = true
                            val bulkDataSize = repository.getLastSearchDataSize(type)
                            networkBytes += bulkDataSize
                                ?: items.sumOf { it.name.length.toLong() * 2 + 64 }

                            items.forEach { item ->
                                serverResults.add(
                                    SearchResult(
                                        itemId = item.id,
                                        streamName = item.name,
                                        categoryId = item.categoryId,
                                        categoryName = "",
                                        contentType = type,
                                        thumbnailUrl = item.thumbnailUrl,
                                        mediaType = item.mediaType
                                    )
                                )
                            }
                        },
                        onFailure = {
                            serverError = it.message
                        }
                    )
                }
            }

            if (serverSearchSuccess) {
                val elapsed = System.currentTimeMillis() - startTime
                val normalizedQuery = query.trim().lowercase()
                val queryWords = SearchUtils.getQueryWords(normalizedQuery)
                val sortedResults = sortResults(serverResults, normalizedQuery, queryWords)

                val matchingCategories = mutableListOf<CategorySearchResult>()
                for (type in targetContentTypes) {
                    val serverCategories = repository.getFilteredCategories(type)
                    serverCategories.getOrDefault(emptyList())
                        .filter { !it.isVirtual }
                        .filter { SearchUtils.matchesQuery(it.name, queryWords) }
                        .forEach {
                            matchingCategories.add(CategorySearchResult(it.id, it.name, type))
                        }
                }

                _uiState.value = UiState.Success(
                    categoryResults = matchingCategories,
                    allResults = sortedResults,
                    filteredResults = sortedResults,
                    query = query,
                    isSearching = false,
                    searchProgress = "Search complete",
                    searchDataSize = formatBytes(networkBytes),
                    totalDuration = formatSeconds(elapsed),
                    networkWallDuration = formatSeconds(elapsed),
                    networkAccumDuration = formatSeconds(elapsed),
                    networkCalls = 1 // Simplified
                )
                return
            }

            // Fall back to parallel client-side category iteration
            val realCategories = prefetchedCategories ?: run {
                val allFetched = mutableListOf<SearchableCategory>()
                for (type in targetContentTypes) {
                    val categoriesResult = repository.getFilteredCategories(type)
                    val categories = categoriesResult.getOrElse {
                        // continue to next type on error
                        continue
                    }
                    categories.filter { it.id != "last_watched" && !it.isVirtual }.forEach {
                        allFetched.add(SearchableCategory(it, type))
                    }
                }
                if (allFetched.isEmpty() && targetContentTypes.isNotEmpty()) {
                    _uiState.value = UiState.Error("Failed to load categories")
                    return
                }
                allFetched
            }

            val results = mutableListOf<SearchResult>()
            val normalizedQuery = query.trim().lowercase()
            val queryWords = SearchUtils.getQueryWords(normalizedQuery)

            val matchingCategories = realCategories
                .filter { SearchUtils.matchesQuery(it.category.name, queryWords) }
                .map { CategorySearchResult(it.category.id, it.category.name, it.contentType) }

            _uiState.value = UiState.Success(
                categoryResults = matchingCategories,
                allResults = emptyList(),
                filteredResults = emptyList(),
                query = query,
                isSearching = true,
                searchProgress = "Searching categories..."
            )

            var categoriesSearched = 0

            // Phase 1: Sweep all categories from cache (instant, no network)
            val uncachedCategories = mutableListOf<SearchableCategory>()
            for (sc in realCategories) {
                currentCoroutineContext().job.ensureActive() // respect cancellation
                val cached = repository.getItemsIfCached(sc.category.id, sc.contentType)
                if (cached != null) {
                    categoriesSearched++
                    val matchingItems = cached
                        .filter { SearchUtils.matchesQuery(it.name, queryWords) }
                        .map { item ->
                            SearchResult(
                                itemId = item.id,
                                streamName = item.name,
                                categoryId = sc.category.id,
                                categoryName = sc.category.name,
                                contentType = sc.contentType,
                                thumbnailUrl = item.thumbnailUrl,
                                mediaType = item.mediaType
                            )
                        }
                    results.addAll(matchingItems)
                } else {
                    uncachedCategories.add(sc)
                }
            }

            // Show cached results immediately
            if (categoriesSearched > 0) {
                val sortedCached = sortResults(results, normalizedQuery, queryWords)
                val displayCached = sortedCached.take(TARGET_RESULTS)
                val elapsed = System.currentTimeMillis() - startTime
                _uiState.value = UiState.Success(
                    categoryResults = matchingCategories,
                    allResults = displayCached,
                    filteredResults = displayCached,
                    query = query,
                    isSearching = uncachedCategories.isNotEmpty(),
                    searchProgress = "Found ${displayCached.size} results (searched $categoriesSearched/${realCategories.size} categories)",
                    searchDataSize = formatBytes(networkBytes),
                    totalDuration = formatSeconds(elapsed),
                    networkWallDuration = formatSeconds(0),
                    networkAccumDuration = formatSeconds(0),
                    networkCalls = 0
                )
            }

            var networkStartTime = System.currentTimeMillis()

            // Phase 2: Fetch uncached categories from network with concurrency limit
            if (uncachedCategories.isNotEmpty() && results.size < TARGET_RESULTS) {
                currentCoroutineContext().job.ensureActive()
                networkStartTime = System.currentTimeMillis()
                val semaphore = Semaphore(PARALLEL_BATCH_SIZE)

                data class FetchResult(
                    val category: org.njarasoa.fijerena.core.player.domain.MediaCategory,
                    val contentType: String,
                    val items: List<org.njarasoa.fijerena.core.player.domain.MediaItem>?,
                    val callDurationMs: Long,
                    val error: String? = null
                )
                val fetchChannel = Channel<FetchResult>(Channel.UNLIMITED)

                val producerJob = scope.launch(Dispatchers.IO) {
                    val uncachedByContentType = uncachedCategories.groupBy { it.contentType }

                    for ((type, categories) in uncachedByContentType) {
                        // Try batch fetch first to avoid N+1 queries
                        val tBatch = System.currentTimeMillis()
                        val batchResult = repository.getAllItems(type)

                        if (batchResult.isSuccess) {
                            val allItems = batchResult.getOrNull() ?: emptyList()
                            val itemsByCategory = allItems.groupBy { it.categoryId }
                            val duration = System.currentTimeMillis() - tBatch
                            // Distribute duration roughly among categories to keep stats somewhat meaningful,
                            // though networkCalls will appear higher than reality.
                            val durationPerCat = if (categories.isNotEmpty()) duration / categories.size else 0L

                            categories.forEach { sc ->
                                val catItems = itemsByCategory[sc.category.id] ?: emptyList()
                                fetchChannel.send(FetchResult(sc.category, sc.contentType, catItems, durationPerCat))
                            }
                        } else {
                            // Fallback to individual parallel fetching if batch not supported/failed
                            for (sc in categories) {
                                launch {
                                    semaphore.withPermit {
                                        val t0 = System.currentTimeMillis()
                                        var items: List<org.njarasoa.fijerena.core.player.domain.MediaItem>? = null
                                        var error: String? = null
                                        try {
                                            val result = repository.getItemsForSearch(sc.category.id, sc.contentType)
                                            items = result.getOrNull()
                                            if (items == null) {
                                                error = result.exceptionOrNull()?.message ?: "Unknown failure"
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: e.javaClass.simpleName
                                        }
                                        val dt = System.currentTimeMillis() - t0
                                        fetchChannel.send(FetchResult(sc.category, sc.contentType, items, dt, error))
                                    }
                                }
                            }
                        }
                    }
                }

                var lastUiUpdateTime = 0L
                repeat(uncachedCategories.size) {
                    if (results.size >= TARGET_RESULTS) {
                        categoriesSearched++
                        fetchChannel.receive()
                        return@repeat
                    }
                    currentCoroutineContext().job.ensureActive()

                    val fetch = fetchChannel.receive()
                    categoriesSearched++
                    networkCalls++
                    accumulatedNetworkMs += fetch.callDurationMs
                    val items = fetch.items
                    if (items != null) {
                        networkBytes += items.sumOf { it.name.length.toLong() * 2 + 64 }
                        val matchingItems = items
                            .filter { SearchUtils.matchesQuery(it.name, queryWords) }
                            .map { item ->
                                SearchResult(
                                    itemId = item.id,
                                    streamName = item.name,
                                    categoryId = fetch.category.id,
                                    categoryName = fetch.category.name,
                                    contentType = fetch.contentType,
                                    thumbnailUrl = item.thumbnailUrl
                                )
                            }
                        results.addAll(matchingItems)
                    } else {
                        failedCalls++
                        if (firstError == null && fetch.error != null) {
                            firstError = fetch.error
                            android.util.Log.w("SearchVM", "First fetch failure for cat=${fetch.category.id} (${fetch.category.name}): ${fetch.error}")
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateTime > 100 || categoriesSearched == realCategories.size) {
                        lastUiUpdateTime = now
                        val sorted = sortResults(results, normalizedQuery, queryWords)
                        val display = sorted.take(TARGET_RESULTS)
                        val now2 = System.currentTimeMillis()
                        _uiState.value = UiState.Success(
                            categoryResults = matchingCategories,
                            allResults = display,
                            filteredResults = display,
                            query = query,
                            isSearching = true,
                            searchProgress = "Found ${display.size} results (searched $categoriesSearched/${realCategories.size} categories)",
                            searchDataSize = formatBytes(networkBytes),
                            totalDuration = formatSeconds(now2 - startTime),
                            networkWallDuration = formatSeconds(now2 - networkStartTime),
                            networkAccumDuration = formatSeconds(accumulatedNetworkMs),
                            networkCalls = networkCalls,
                            failedCalls = failedCalls,
                            firstError = firstError
                        )
                    }
                }
                fetchChannel.close()
            }

            val finalResults = sortResults(results, normalizedQuery, queryWords).take(TARGET_RESULTS)
            val endTime = System.currentTimeMillis()
            val networkEndTime = if (uncachedCategories.isNotEmpty()) endTime else startTime
            _uiState.value = UiState.Success(
                categoryResults = matchingCategories,
                allResults = finalResults,
                filteredResults = finalResults,
                query = query,
                isSearching = false,
                searchProgress = "Search complete",
                searchDataSize = formatBytes(networkBytes),
                totalDuration = formatSeconds(endTime - startTime),
                networkWallDuration = if (networkCalls > 0) formatSeconds(networkEndTime - networkStartTime) else formatSeconds(0),
                networkAccumDuration = formatSeconds(accumulatedNetworkMs),
                networkCalls = networkCalls,
                failedCalls = failedCalls,
                firstError = firstError
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.value = UiState.Error(e.message ?: "Failed to search")
        }
    }

    private fun sortResults(results: List<SearchResult>, normalizedQuery: String, queryWords: List<String>): List<SearchResult> {
        return results.sortedWith(compareBy<SearchResult> { it.categoryName.lowercase() }
            .thenBy {
                val lowerName = it.streamName.lowercase()
                when {
                    lowerName == normalizedQuery -> 0
                    lowerName.startsWith(normalizedQuery) -> 1
                    else -> {
                        if (queryWords.isNotEmpty() && queryWords.all { w -> lowerName.contains(w) }) {
                            if (lowerName.startsWith(queryWords[0])) 2 else 3
                        } else 4
                    }
                }
            }
            .thenBy { it.streamName })
    }
}
