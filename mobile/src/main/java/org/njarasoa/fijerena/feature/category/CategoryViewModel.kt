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

class CategoryViewModel(
    private val repository: XtreamRepository,
    private val contentType: String
) : ViewModel() {

    companion object {
        const val LAST_WATCHED_CATEGORY_ID = "last_watched"
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val categories: List<XtreamCategory>,
            val selectedCategoryId: String?,
            val streams: List<XtreamStream>?,
            val streamsLoading: Boolean
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

            val result = when (contentType) {
                "LIVE_TV" -> repository.getCategories()
                "MOVIES" -> repository.getVodCategories()
                "TV_SHOWS" -> repository.getSeriesCategories()
                else -> repository.getCategories()
            }

            when (result) {
                is Result.Success -> {
                    val lastWatchedCategory = XtreamCategory(
                        categoryId = LAST_WATCHED_CATEGORY_ID,
                        categoryName = "Last Watched",
                        parentId = 0
                    )
                    categories = listOf(lastWatchedCategory) + result.data

                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = null,
                        streams = null,
                        streamsLoading = false
                    )

                    if (categories.isNotEmpty()) {
                        val lastCategoryId = repository.getLastCategoryId(contentType)
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
                    _uiState.value = UiState.Error(result.message ?: "Failed to load categories")
                }
            }
        }
    }

    fun loadStreams(categoryId: String) {
        viewModelScope.launch {
            currentCategoryId = categoryId

            _uiState.value = UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = currentStreams,
                streamsLoading = true
            )

            if (categoryId == LAST_WATCHED_CATEGORY_ID) {
                val watchHistory = repository.getWatchHistory()
                    .filter { it.contentType == contentType }
                currentStreams = watchHistory.map { watched ->
                    XtreamStream(
                        num = 0,
                        name = watched.streamName,
                        streamType = contentType.lowercase(),
                        streamId = watched.streamId,
                        streamIcon = null,
                        epgChannelId = null,
                        added = null,
                        categoryId = watched.categoryId,
                        customSid = null,
                        tvArchive = 0,
                        directSource = null,
                        tvArchiveDuration = 0
                    )
                }
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false
                )
                return@launch
            }

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
                        streamsLoading = false
                    )
                }
                is Result.Error -> {
                    currentStreams = emptyList()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = emptyList(),
                        streamsLoading = false
                    )
                }
            }
        }
    }

    fun retry() {
        loadCategories()
    }
}
