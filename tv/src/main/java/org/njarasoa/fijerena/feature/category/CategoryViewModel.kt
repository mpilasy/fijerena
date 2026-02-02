package org.njarasoa.fijerena.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.FavoriteStream
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.WatchedStream
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

    companion object {
        const val FAVORITES_CATEGORY_ID = "favorites"
        const val LAST_WATCHED_CATEGORY_ID = "last_watched"
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val categories: List<XtreamCategory>,
            val selectedCategoryId: String?,
            val streams: List<XtreamStream>?,
            val streamsLoading: Boolean,
            val categoriesRefreshing: Boolean = false,
            val lastPlayedStreamId: Int? = null,
            val categoriesPayloadSize: String? = null,
            val streamsPayloadSize: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun getPayloadSize(categoryId: String): String? {
        // Handle different category key formats based on content type
        val key = when {
            categoryId.startsWith("vod_") -> "category_$categoryId"
            categoryId.startsWith("series_") -> "category_$categoryId"
            contentType == "MOVIES" -> "category_vod_$categoryId"
            contentType == "TV_SHOWS" -> "category_series_$categoryId"
            else -> "category_$categoryId"
        }
        return repository.getPayloadSize(key)
    }

    fun getCategoriesPayloadSize(): String? {
        return when (contentType) {
            "LIVE_TV" -> repository.getPayloadSize("live_categories")
            "MOVIES" -> repository.getPayloadSize("vod_categories")
            "TV_SHOWS" -> repository.getPayloadSize("series_categories")
            else -> null
        }
    }

    fun getFetchTime(categoryId: String): String? {
        // Handle different category key formats based on content type
        val key = when {
            categoryId.startsWith("vod_") -> "category_$categoryId"
            categoryId.startsWith("series_") -> "category_$categoryId"
            contentType == "MOVIES" -> "category_vod_$categoryId"
            contentType == "TV_SHOWS" -> "category_series_$categoryId"
            else -> "category_$categoryId"
        }
        return repository.getFetchTimeFormatted(key)
    }

    fun getCategoriesFetchTime(): String? {
        return when (contentType) {
            "LIVE_TV" -> repository.getFetchTimeFormatted("live_categories")
            "MOVIES" -> repository.getFetchTimeFormatted("vod_categories")
            "TV_SHOWS" -> repository.getFetchTimeFormatted("series_categories")
            else -> null
        }
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
                    // Add "Last Watched" virtual category at the top
                    val lastWatchedCategory = XtreamCategory(
                        categoryId = LAST_WATCHED_CATEGORY_ID,
                        categoryName = "Last Watched",
                        parentId = 0
                    )
                    categories = listOf(lastWatchedCategory) + result.data

                    val lastStreamId = repository.getLastStreamId(contentType)
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = null,
                        streams = null,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedStreamId = lastStreamId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = null
                    )

                    // Restore last selected category, or auto-select first category
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
                    val errorMessage = result.message ?: "Failed to load categories"
                    _uiState.value = UiState.Error(errorMessage)
                }
            }
        }
    }

    fun loadStreams(categoryId: String) {
        viewModelScope.launch {
            currentCategoryId = categoryId

            val lastStreamId = repository.getLastStreamId(contentType)

            // Update state to show loading for streams
            _uiState.value = UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = currentStreams,
                streamsLoading = true,
                categoriesRefreshing = false,
                lastPlayedStreamId = lastStreamId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null
            )

            // Handle "Last Watched" virtual category
            if (categoryId == LAST_WATCHED_CATEGORY_ID) {
                val watchHistory = repository.getWatchHistory()
                    .filter { it.contentType == contentType } // Only show history for current content type
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
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedStreamId = lastStreamId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null // Last Watched doesn't have a payload
                )
                return@launch
            }

            val result = when (contentType) {
                "LIVE_TV" -> repository.getStreams(categoryId)
                "MOVIES" -> repository.getVodStreams(categoryId)
                "TV_SHOWS" -> repository.getSeries(categoryId)
                else -> repository.getStreams(categoryId)
            }

            println("CategoryViewModel: loadStreams result for $contentType, categoryId=$categoryId: ${if (result is Result.Success) "Success with ${result.data.size} items" else "Error: ${(result as? Result.Error)?.message}"}")

            when (result) {
                is Result.Success -> {
                    currentStreams = result.data
                    println("CategoryViewModel: Loaded ${currentStreams.size} streams for category $categoryId")
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedStreamId = lastStreamId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                }
                is Result.Error -> {
                    // Show error but keep UI usable
                    println("CategoryViewModel: ERROR loading streams: ${result.message}")
                    currentStreams = emptyList()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = emptyList(),
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedStreamId = lastStreamId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                }
            }
        }
    }

    fun retry() {
        loadCategories()
    }

    fun refreshCategories() {
        viewModelScope.launch {
            // Set refreshing state
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                _uiState.value = currentState.copy(categoriesRefreshing = true)
            }

            // Clear cache and reload
            repository.clearCategoriesCache(contentType)

            // Reload categories
            val result = when (contentType) {
                "LIVE_TV" -> repository.getCategories()
                "MOVIES" -> repository.getVodCategories()
                "TV_SHOWS" -> repository.getSeriesCategories()
                else -> repository.getCategories()
            }

            when (result) {
                is Result.Success -> {
                    // Add virtual categories at the top: Favorites, then Last Watched
                    val favoritesCategory = XtreamCategory(
                        categoryId = FAVORITES_CATEGORY_ID,
                        categoryName = "Favorites",
                        parentId = 0
                    )
                    val lastWatchedCategory = XtreamCategory(
                        categoryId = LAST_WATCHED_CATEGORY_ID,
                        categoryName = "Last Watched",
                        parentId = 0
                    )
                    categories = listOf(favoritesCategory, lastWatchedCategory) + result.data

                    val lastStreamId = repository.getLastStreamId(contentType)
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = currentCategoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedStreamId = lastStreamId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(currentCategoryId ?: "")
                    )
                }
                is Result.Error -> {
                    // Keep current data, just stop refreshing
                    if (currentState is UiState.Success) {
                        _uiState.value = currentState.copy(categoriesRefreshing = false)
                    }
                }
            }
        }
    }

    fun refreshStreams(categoryId: String) {
        viewModelScope.launch {
            currentCategoryId = categoryId

            val lastStreamId = repository.getLastStreamId(contentType)

            // Set loading state but keep current data
            _uiState.value = UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = currentStreams,
                streamsLoading = true,
                categoriesRefreshing = false,
                lastPlayedStreamId = lastStreamId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null
            )

            // Clear cache
            repository.clearStreamsCache(categoryId)

            // Handle "Favorites" virtual category
            if (categoryId == FAVORITES_CATEGORY_ID) {
                val favorites = repository.getFavorites()
                    .filter { it.contentType == contentType }
                currentStreams = favorites.map { favorite ->
                    XtreamStream(
                        num = 0,
                        name = favorite.streamName,
                        streamType = contentType.lowercase(),
                        streamId = favorite.streamId,
                        streamIcon = null,
                        epgChannelId = null,
                        added = null,
                        categoryId = favorite.categoryId,
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
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedStreamId = lastStreamId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = getPayloadSize(categoryId)
                )
                return@launch
            }

            // Handle "Last Watched" virtual category
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
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedStreamId = lastStreamId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = getPayloadSize(categoryId)
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
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedStreamId = lastStreamId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                }
                is Result.Error -> {
                    // Keep empty list but stop loading
                    currentStreams = emptyList()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = emptyList(),
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedStreamId = lastStreamId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                }
            }
        }
    }
}
