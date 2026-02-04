package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository

/**
 * ViewModel for search functionality across all categories.
 *
 * Handles:
 * - Loading all categories and streams for a content type
 * - Client-side search filtering with debouncing
 * - Search result management
 */
class SearchViewModel(
    private val repository: XtreamRepository,
    private val contentType: String
) : ViewModel() {

    /**
     * UI state for search screen
     */
    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val allResults: List<SearchResult>,
            val filteredResults: List<SearchResult>,
            val query: String,
            val isSearching: Boolean = false,
            val searchProgress: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    /**
     * Represents a single search result
     */
    data class SearchResult(
        val streamId: Int,
        val streamName: String,
        val categoryId: String,
        val categoryName: String,
        val contentType: String
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        observeSearchQuery()
        // Start with an empty success state
        _uiState.value = UiState.Success(
            allResults = emptyList(),
            filteredResults = emptyList(),
            query = ""
        )
    }

    /**
     * Update the search query
     */
    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    /**
     * Perform efficient sequential search with early stopping
     * - Searches categories one at a time (memory efficient)
     * - Stops after finding enough matches (fast)
     * - Shows results as they arrive (responsive)
     * - Prioritizes exact/prefix matches
     */
    private fun performIncrementalSearch(query: String) {
        viewModelScope.launch {
            try {
                // Ensure we have an authenticated session
                if (!repository.isAuthenticated()) {
                    when (val restoreResult = repository.restoreSession()) {
                        is Result.Error -> {
                            _uiState.value = UiState.Error("Session expired. Please login again.")
                            return@launch
                        }
                        is Result.Success -> {
                            // Session restored successfully, continue
                        }
                    }
                }

                // Show loading state
                _uiState.value = UiState.Loading

                // 1. Load categories based on content type
                val categoriesResult = when (contentType) {
                    "LIVE_TV" -> repository.getCategories()
                    "MOVIES" -> repository.getVodCategories()
                    "TV_SHOWS" -> repository.getSeriesCategories()
                    else -> {
                        _uiState.value = UiState.Error("Invalid content type")
                        return@launch
                    }
                }

                val categories = when (categoriesResult) {
                    is Result.Success -> categoriesResult.data
                    is Result.Error -> {
                        _uiState.value = UiState.Error(categoriesResult.message ?: "Failed to load categories")
                        return@launch
                    }
                }

                // Filter out "Last Watched" virtual category
                val realCategories = categories.filter { it.categoryId != "last_watched" }

                // 2. Search sequentially with early stopping
                val results = mutableListOf<SearchResult>()
                val normalizedQuery = query.trim().lowercase()
                val targetResults = 200 // Increased limit for better search results

                println("SearchViewModel: Starting search for query='$normalizedQuery', contentType=$contentType, categories=${realCategories.size}")

                // Show initial searching state
                _uiState.value = UiState.Success(
                    allResults = emptyList(),
                    filteredResults = emptyList(),
                    query = query,
                    isSearching = true,
                    searchProgress = "Searching categories..."
                )

                var categoriesSearched = 0
                for (category in realCategories) {
                    categoriesSearched++
                    // Stop if we have enough results
                    if (results.size >= targetResults) {
                        println("SearchViewModel: Reached target of $targetResults results, stopping search")
                        break
                    }

                    // Check if search query has changed (user kept typing)
                    if (searchQuery.value != query) {
                        println("SearchViewModel: Query changed, stopping search")
                        break
                    }

                    // Load streams for this category (use search cache to avoid overwriting catalog cache)
                    val streamsResult = when (contentType) {
                        "LIVE_TV" -> repository.getStreams(category.categoryId, forSearch = true)
                        "MOVIES" -> repository.getVodStreams(category.categoryId, forSearch = true)
                        "TV_SHOWS" -> repository.getSeries(category.categoryId, forSearch = true)
                        else -> continue
                    }

                    when (streamsResult) {
                        is Result.Success -> {
                            println("SearchViewModel: Category ${category.categoryName} has ${streamsResult.data.size} streams")

                            // Filter streams that match the query
                            val matchingStreams = streamsResult.data
                                .filter {
                                    val matches = it.name.lowercase().contains(normalizedQuery)
                                    if (matches) println("SearchViewModel: Found match: ${it.name}")
                                    matches
                                }
                                .map { stream ->
                                    SearchResult(
                                        streamId = stream.streamId,
                                        streamName = stream.name,
                                        categoryId = category.categoryId,
                                        categoryName = category.categoryName,
                                        contentType = contentType
                                    )
                                }

                            println("SearchViewModel: Found ${matchingStreams.size} matches in category ${category.categoryName}")
                            results.addAll(matchingStreams)

                            // Sort by relevance: exact match > starts with > contains
                            val sortedResults = results.sortedWith(compareBy<SearchResult> {
                                when {
                                    it.streamName.lowercase() == normalizedQuery -> 0
                                    it.streamName.lowercase().startsWith(normalizedQuery) -> 1
                                    else -> 2
                                }
                            }.thenBy { it.streamName })

                            val finalResults = sortedResults.take(targetResults)
                            println("SearchViewModel: Updating UI state with ${finalResults.size} results for query='$query'")
                            if (finalResults.isNotEmpty()) {
                                println("SearchViewModel: First result - streamName='${finalResults.first().streamName}', streamId=${finalResults.first().streamId}, contentType='${finalResults.first().contentType}'")
                            }

                            // Update UI incrementally (every category) with progress
                            _uiState.value = UiState.Success(
                                allResults = finalResults,
                                filteredResults = finalResults,
                                query = query,
                                isSearching = true,
                                searchProgress = "Found ${finalResults.size} results (searched $categoriesSearched/${realCategories.size} categories)"
                            )

                            println("SearchViewModel: UI state updated. Current state: ${_uiState.value}")
                        }
                        is Result.Error -> {
                            // Skip failed categories, continue loading others
                        }
                    }
                }

                // Search complete - update final state
                val sortedFinalResults = results.sortedWith(compareBy<SearchResult> {
                    when {
                        it.streamName.lowercase() == normalizedQuery -> 0
                        it.streamName.lowercase().startsWith(normalizedQuery) -> 1
                        else -> 2
                    }
                }.thenBy { it.streamName })

                val finalResults = sortedFinalResults.take(targetResults)
                println("SearchViewModel: Search complete. Found ${finalResults.size} total results")

                _uiState.value = UiState.Success(
                    allResults = finalResults,
                    filteredResults = finalResults,
                    query = query,
                    isSearching = false,
                    searchProgress = null
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to search")
            }
        }
    }

    /**
     * Observe search query changes with debouncing
     */
    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        searchQuery
            .debounce(500) // Increased debounce to reduce API calls
            .onEach { query ->
                if (query.isBlank()) {
                    // Empty query - show empty results
                    _uiState.value = UiState.Success(
                        allResults = emptyList(),
                        filteredResults = emptyList(),
                        query = ""
                    )
                } else if (query.length >= 2) {
                    // Only search when query is at least 2 characters
                    performIncrementalSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Filter results by query (case-insensitive substring match)
     */
    private fun filterResults(query: String, allResults: List<SearchResult>): List<SearchResult> {
        val normalizedQuery = query.trim().lowercase()
        return allResults.filter { result ->
            result.streamName.lowercase().contains(normalizedQuery)
        }
    }
}
