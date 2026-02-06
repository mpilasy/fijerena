package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
            val searchProgress: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    data class SearchResult(
        val itemId: String,
        val streamName: String,
        val categoryId: String,
        val categoryName: String,
        val contentType: String
    )

    data class CategorySearchResult(
        val categoryId: String,
        val categoryName: String,
        val contentType: String
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        observeSearchQuery()
        _uiState.value = UiState.Success(
            allResults = emptyList(),
            filteredResults = emptyList(),
            query = ""
        )
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun performIncrementalSearch(query: String) {
        viewModelScope.launch {
            try {
                if (!repository.isConnected()) {
                    val connectResult = repository.connect()
                    if (connectResult.isFailure) {
                        _uiState.value = UiState.Error("Session expired. Please login again.")
                        return@launch
                    }
                }

                _uiState.value = UiState.Loading

                // Try server-side search first (e.g., Jellyfin)
                val serverResult = repository.search(query, contentType)
                if (serverResult != null) {
                    serverResult.fold(
                        onSuccess = { items ->
                            val normalizedQuery = query.trim().lowercase()
                            val results = items.map { item ->
                                SearchResult(
                                    itemId = item.id,
                                    streamName = item.name,
                                    categoryId = item.categoryId,
                                    categoryName = "",
                                    contentType = contentType
                                )
                            }.sortedWith(compareBy<SearchResult> { it.categoryName.lowercase() }
                                .thenBy {
                                    when {
                                        it.streamName.lowercase() == normalizedQuery -> 0
                                        it.streamName.lowercase().startsWith(normalizedQuery) -> 1
                                        else -> 2
                                    }
                                }
                                .thenBy { it.streamName })

                            // Also search categories for server-side providers
                            val serverCategories = repository.getFilteredCategories(contentType)
                            val matchingCategories = serverCategories.getOrDefault(emptyList())
                                .filter { !it.isVirtual }
                                .filter { it.name.lowercase().contains(normalizedQuery) }
                                .map { CategorySearchResult(it.id, it.name, contentType) }

                            _uiState.value = UiState.Success(
                                categoryResults = matchingCategories,
                                allResults = results,
                                filteredResults = results,
                                query = query,
                                isSearching = false,
                                searchProgress = "Search complete"
                            )
                        },
                        onFailure = {
                            _uiState.value = UiState.Error(it.message ?: "Search failed")
                        }
                    )
                    return@launch
                }

                // Fall back to client-side category iteration (uses filtered categories)
                val categoriesResult = repository.getFilteredCategories(contentType)
                val categories = categoriesResult.getOrElse {
                    _uiState.value = UiState.Error(it.message ?: "Failed to load categories")
                    return@launch
                }

                val realCategories = categories.filter { it.id != "last_watched" && !it.isVirtual }

                val results = mutableListOf<SearchResult>()
                val normalizedQuery = query.trim().lowercase()
                val targetResults = 200

                // Filter categories by name match
                val matchingCategories = realCategories
                    .filter { it.name.lowercase().contains(normalizedQuery) }
                    .map { CategorySearchResult(it.id, it.name, contentType) }

                _uiState.value = UiState.Success(
                    categoryResults = matchingCategories,
                    allResults = emptyList(),
                    filteredResults = emptyList(),
                    query = query,
                    isSearching = true,
                    searchProgress = "Searching categories..."
                )

                var categoriesSearched = 0
                for (category in realCategories) {
                    categoriesSearched++
                    if (results.size >= targetResults) break
                    if (searchQuery.value != query) break

                    val itemsResult = repository.getItemsForSearch(category.id, contentType)

                    itemsResult.fold(
                        onSuccess = { items ->
                            val matchingItems = items
                                .filter { it.name.lowercase().contains(normalizedQuery) }
                                .map { item ->
                                    SearchResult(
                                        itemId = item.id,
                                        streamName = item.name,
                                        categoryId = category.id,
                                        categoryName = category.name,
                                        contentType = contentType
                                    )
                                }

                            results.addAll(matchingItems)

                            val sortedResults = results.sortedWith(compareBy<SearchResult> { it.categoryName.lowercase() }
                                .thenBy {
                                    when {
                                        it.streamName.lowercase() == normalizedQuery -> 0
                                        it.streamName.lowercase().startsWith(normalizedQuery) -> 1
                                        else -> 2
                                    }
                                }
                                .thenBy { it.streamName })

                            val finalResults = sortedResults.take(targetResults)

                            _uiState.value = UiState.Success(
                                categoryResults = matchingCategories,
                                allResults = finalResults,
                                filteredResults = finalResults,
                                query = query,
                                isSearching = true,
                                searchProgress = "Found ${finalResults.size} results (searched $categoriesSearched/${realCategories.size} categories)"
                            )
                        },
                        onFailure = {
                            // Skip failed categories
                        }
                    )
                }

                val sortedFinalResults = results.sortedWith(compareBy<SearchResult> { it.categoryName.lowercase() }
                    .thenBy {
                        when {
                            it.streamName.lowercase() == normalizedQuery -> 0
                            it.streamName.lowercase().startsWith(normalizedQuery) -> 1
                            else -> 2
                        }
                    }
                    .thenBy { it.streamName })

                val finalResults = sortedFinalResults.take(targetResults)

                _uiState.value = UiState.Success(
                    categoryResults = matchingCategories,
                    allResults = finalResults,
                    filteredResults = finalResults,
                    query = query,
                    isSearching = false,
                    searchProgress = "Search complete"
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to search")
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        searchQuery
            .debounce(500)
            .onEach { query ->
                if (query.isBlank()) {
                    _uiState.value = UiState.Success(
                        allResults = emptyList(),
                        filteredResults = emptyList(),
                        query = ""
                    )
                } else if (query.length >= 2) {
                    performIncrementalSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }
}
