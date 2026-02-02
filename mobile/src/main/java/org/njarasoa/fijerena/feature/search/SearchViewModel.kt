package org.njarasoa.fijerena.feature.search

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

class SearchViewModel(
    private val repository: XtreamRepository,
    private val contentType: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val allResults: List<SearchResult>,
            val filteredResults: List<SearchResult>,
            val query: String
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

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
        loadAllData()
        observeSearchQuery()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun loadAllData() {
        viewModelScope.launch {
            try {
                if (!repository.isAuthenticated()) {
                    when (val restoreResult = repository.restoreSession()) {
                        is Result.Error -> {
                            _uiState.value = UiState.Error("Session expired. Please login again.")
                            return@launch
                        }
                        is Result.Success -> {
                            // Session restored successfully
                        }
                    }
                }

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

                val realCategories = categories.filter { it.categoryId != "last_watched" }

                val allResults = mutableListOf<SearchResult>()

                coroutineScope {
                    realCategories.forEach { category ->
                        launch {
                            val streamsResult = when (contentType) {
                                "LIVE_TV" -> repository.getStreams(category.categoryId)
                                "MOVIES" -> repository.getVodStreams(category.categoryId)
                                "TV_SHOWS" -> repository.getSeries(category.categoryId)
                                else -> return@launch
                            }

                            when (streamsResult) {
                                is Result.Success -> {
                                    val streams = streamsResult.data.map { stream ->
                                        SearchResult(
                                            streamId = stream.streamId,
                                            streamName = stream.name,
                                            categoryId = category.categoryId,
                                            categoryName = category.categoryName,
                                            contentType = contentType
                                        )
                                    }
                                    synchronized(allResults) {
                                        allResults.addAll(streams)
                                    }
                                }
                                is Result.Error -> {
                                    // Skip failed categories
                                }
                            }
                        }
                    }
                }

                _uiState.value = UiState.Success(
                    allResults = allResults,
                    filteredResults = allResults,
                    query = ""
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load data")
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        searchQuery
            .debounce(300)
            .onEach { query ->
                val currentState = _uiState.value
                if (currentState is UiState.Success) {
                    val filtered = if (query.isBlank()) {
                        currentState.allResults
                    } else {
                        filterResults(query, currentState.allResults)
                    }

                    _uiState.value = currentState.copy(
                        filteredResults = filtered.take(200),
                        query = query
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun filterResults(query: String, allResults: List<SearchResult>): List<SearchResult> {
        val normalizedQuery = query.trim().lowercase()
        return allResults.filter { result ->
            result.streamName.lowercase().contains(normalizedQuery)
        }
    }
}
