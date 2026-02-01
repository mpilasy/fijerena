package org.njarasoa.fijerena.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream

/**
 * ViewModel for managing category and stream data from Xtream API.
 *
 * Handles:
 * - Loading categories from XtreamRepository
 * - Loading streams for selected category
 * - Loading, Success, and Error states
 * - Category selection tracking
 * - Content type selection (Live TV, Movies, TV Shows)
 */
class CategoryViewModel(
    private val repository: XtreamRepository,
    private val contentType: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val categories: List<XtreamCategory>,
            val selectedCategoryId: String?,
            val streams: List<XtreamStream>?,
            val streamsLoading: Boolean,
            val lastPlayedStreamId: Int? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var categories: List<XtreamCategory> = emptyList()
    private var currentStreams: List<XtreamStream> = emptyList()
    private var currentCategoryId: String? = null

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // First, ensure we have an authenticated session
            if (!repository.isAuthenticated()) {
                when (val restoreResult = repository.restoreSession()) {
                    is Result.Error -> {
                        val errorMessage = "Session expired. Please login again."
                        _uiState.value = UiState.Error(errorMessage)
                        return@launch
                    }
                    is Result.Success -> {
                        // Session restored successfully, continue
                    }
                }
            }

            // Now fetch categories based on content type
            val result = when (contentType) {
                "LIVE_TV" -> repository.getCategories()
                "MOVIES" -> repository.getVodCategories()
                "TV_SHOWS" -> repository.getSeriesCategories()
                else -> repository.getCategories()
            }

            when (result) {
                is Result.Success -> {
                    categories = result.data
                    val lastStreamId = repository.getLastStreamId()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = null,
                        streams = null,
                        streamsLoading = false,
                        lastPlayedStreamId = lastStreamId
                    )

                    // Restore last selected category, or auto-select first category
                    if (categories.isNotEmpty()) {
                        val lastCategoryId = repository.getLastCategoryId()
                        val categoryToLoad = if (lastCategoryId != null &&
                            categories.any { it.categoryId == lastCategoryId }) {
                            lastCategoryId
                        } else {
                            categories.first().categoryId
                        }
                        loadStreams(categoryToLoad)
                    }
                }
                is Result.Error -> {
                    val errorMessage = result.message ?: "Failed to load categories"
                    _uiState.value = UiState.Error(errorMessage)
                }
            }
        }
    }

    fun loadStreams(categoryId: String) {
        viewModelScope.launch {
            currentCategoryId = categoryId

            // Save the selected category
            repository.saveLastCategory(categoryId)

            val lastStreamId = repository.getLastStreamId()

            // Update state to show loading for streams
            _uiState.value = UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = currentStreams,
                streamsLoading = true,
                lastPlayedStreamId = lastStreamId
            )

            val result = when (contentType) {
                "LIVE_TV" -> repository.getStreams(categoryId)
                "MOVIES" -> repository.getVodStreams(categoryId)
                "TV_SHOWS" -> repository.getSeries(categoryId)
                else -> repository.getStreams(categoryId)
            }

            when (result) {
                is Result.Success -> {
                    currentStreams = result.data
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        lastPlayedStreamId = lastStreamId
                    )
                }
                is Result.Error -> {
                    // Show error but keep UI usable
                    currentStreams = emptyList()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = emptyList(),
                        streamsLoading = false,
                        lastPlayedStreamId = lastStreamId
                    )
                }
            }
        }
    }

    fun retry() {
        loadCategories()
    }
}
