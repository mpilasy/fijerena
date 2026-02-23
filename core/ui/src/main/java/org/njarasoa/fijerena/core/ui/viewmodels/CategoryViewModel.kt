package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram

class CategoryViewModel(
    private val repository: MediaRepository,
    private val contentType: String,
    private val initialCategoryId: String? = null
) : ViewModel() {

    companion object {
        const val CONTINUE_WATCHING_CATEGORY_ID = "continue_watching"
        const val FAVORITES_CATEGORY_ID = "favorites"
        const val FAVORITE_CATEGORIES_ID = "favorite_categories"
        const val LAST_WATCHED_CATEGORY_ID = "last_watched"
        const val RECENTLY_VIEWED_CATEGORIES_ID = "recently_viewed_categories"
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val categories: List<MediaCategory>,
            val selectedCategoryId: String?,
            val streams: List<MediaItem>?,
            val streamsLoading: Boolean,
            val categoriesRefreshing: Boolean = false,
            val lastPlayedItemId: String? = null,
            val categoriesPayloadSize: String? = null,
            val streamsPayloadSize: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun getPayloadSize(categoryId: String): String? {
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

    private val _nowPlaying = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val nowPlaying: StateFlow<Map<String, EpgProgram>> = _nowPlaying.asStateFlow()

    private val _supportsNativeEpg = MutableStateFlow(false)
    val supportsNativeEpg: StateFlow<Boolean> = _supportsNativeEpg.asStateFlow()

    private var categories: List<MediaCategory> = emptyList()
    private var currentStreams: List<MediaItem> = emptyList()
    private var currentCategoryId: String? = null
    private var isInitialLoad = true
    private var initialLoadRetried = false
    private var categoriesRetried = false

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            if (!repository.isConnected()) {
                val connectResult = repository.connect()
                if (connectResult.isFailure) {
                    val reason = connectResult.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.value = UiState.Error("Connection failed: $reason")
                    return@launch
                }
            }

            _supportsNativeEpg.value = repository.getCapabilities()?.supportsEpg == true

            val result = repository.getFilteredCategories(contentType)

            result.fold(
                onSuccess = { fetchedCategories ->
                    // Retry once if provider returned no categories (server session may not be ready)
                    if (fetchedCategories.isEmpty() && !categoriesRetried) {
                        categoriesRetried = true
                        delay(1500)
                        val retryResult = repository.getFilteredCategories(contentType)
                        retryResult.fold(
                            onSuccess = { buildAndShowCategories(it) },
                            onFailure = { buildAndShowCategories(emptyList()) }
                        )
                        return@launch
                    }
                    buildAndShowCategories(fetchedCategories)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load categories")
                }
            )
        }
    }

    private fun buildAndShowCategories(fetchedCategories: List<MediaCategory>) {
        categories = rebuildVirtualCategories(fetchedCategories)

        val lastItemId = repository.getLastItemId(contentType)
        _uiState.value = UiState.Success(
            categories = categories,
            selectedCategoryId = null,
            streams = null,
            streamsLoading = false,
            categoriesRefreshing = false,
            lastPlayedItemId = lastItemId,
            categoriesPayloadSize = getCategoriesPayloadSize(),
            streamsPayloadSize = null
        )

        if (categories.isNotEmpty()) {
            val categoryToLoad = if (initialCategoryId != null &&
                categories.any { it.id == initialCategoryId }) {
                initialCategoryId
            } else {
                // Default to "Last Watched" on startup so focus lands on the last played stream
                LAST_WATCHED_CATEGORY_ID
            }
            loadStreams(categoryToLoad)
        }
    }

    fun loadStreams(categoryId: String) {
        viewModelScope.launch {
            currentCategoryId = categoryId

            val lastItemId = repository.getLastItemId(contentType)

            _uiState.value = UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = null,
                streamsLoading = true,
                categoriesRefreshing = false,
                lastPlayedItemId = lastItemId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null
            )

            // Handle virtual categories
            if (categoryId == CONTINUE_WATCHING_CATEGORY_ID) {
                currentStreams = repository.getInProgressItemsSuspend(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            if (categoryId == LAST_WATCHED_CATEGORY_ID) {
                currentStreams = repository.getWatchHistoryForContentTypeSuspend(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            if (categoryId == FAVORITES_CATEGORY_ID) {
                currentStreams = repository.getFavoritesForContentTypeSuspend(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            if (categoryId == FAVORITE_CATEGORIES_ID) {
                val favCategories = repository.getFavoriteCategoriesForContentType(contentType)
                currentStreams = favCategories.map { cat ->
                    MediaItem(
                        id = "fav_cat_${cat.id}",
                        name = cat.name,
                        mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                        categoryId = FAVORITE_CATEGORIES_ID,
                        providerData = mapOf(
                            "isCategoryRef" to "true",
                            "categoryId" to cat.id
                        )
                    )
                }
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            if (categoryId == RECENTLY_VIEWED_CATEGORIES_ID) {
                val recentCategories = repository.getRecentlyViewedCategories(contentType)
                currentStreams = recentCategories.map { recent ->
                    MediaItem(
                        id = "recent_cat_${recent.categoryId}",
                        name = recent.categoryName,
                        mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                        categoryId = RECENTLY_VIEWED_CATEGORIES_ID,
                        providerData = mapOf(
                            "isCategoryRef" to "true",
                            "categoryId" to recent.categoryId
                        )
                    )
                }
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            // Track non-virtual category views
            val categoryName = categories.firstOrNull { it.id == categoryId }?.name
            if (categoryName != null) {
                repository.addToCategoryHistory(categoryId, categoryName, contentType)
            }

            val result = repository.getItems(categoryId, contentType)

            result.fold(
                onSuccess = { items ->
                    currentStreams = items
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = lastItemId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                    // Load "What's On Now" for Live TV channels
                    loadNowPlaying(items)
                    // Retry once if initial load returned empty for a non-virtual category
                    if (isInitialLoad && items.isEmpty() && !initialLoadRetried &&
                        categoryId != CONTINUE_WATCHING_CATEGORY_ID &&
                        categoryId != FAVORITES_CATEGORY_ID &&
                        categoryId != FAVORITE_CATEGORIES_ID &&
                        categoryId != LAST_WATCHED_CATEGORY_ID &&
                        categoryId != RECENTLY_VIEWED_CATEGORIES_ID) {
                        initialLoadRetried = true
                        delay(1500)
                        loadStreams(categoryId)
                    }
                    isInitialLoad = false
                },
                onFailure = {
                    currentStreams = emptyList()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = emptyList(),
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = lastItemId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                    // Retry once on initial load failure after a short delay
                    if (isInitialLoad && !initialLoadRetried) {
                        initialLoadRetried = true
                        delay(2000)
                        loadStreams(categoryId)
                    }
                }
            )
        }
    }

    private fun loadNowPlaying(items: List<MediaItem>) {
        if (contentType != "LIVE_TV") return
        viewModelScope.launch {
            val caps = repository.getCapabilities()
            val hasExternalEpg = repository.hasIndexedEpgData()
            if (caps?.supportsEpg != true && !hasExternalEpg) return@launch
            val epgData = repository.getEpgBulkForItems(
                items.take(50)
            ).getOrNull() ?: return@launch
            val now = System.currentTimeMillis() / 1000
            _nowPlaying.value = epgData.mapValues { (_, resp) ->
                resp.listings.firstOrNull { now in it.startTime..it.endTime }
            }.filterValues { it != null }.mapValues { it.value!! }
        }
    }

    fun getPlaybackPosition(itemId: String, contentType: String) =
        repository.getPlaybackPosition(itemId, contentType)

    fun isFavorite(itemId: String, contentType: String) =
        repository.isFavorite(itemId, contentType)

    fun isFavoriteCategory(categoryId: String, contentType: String) =
        repository.isFavoriteCategory(categoryId, contentType)

    fun toggleFavoriteCategory(categoryId: String, categoryName: String, contentType: String) {
        if (repository.isFavoriteCategory(categoryId, contentType)) {
            repository.removeFavoriteCategory(categoryId, contentType)
        } else {
            repository.addFavoriteCategory(categoryId, categoryName, contentType)
        }
        // Refresh to update virtual categories list
        refreshCategories()
    }

    fun toggleFavoriteStream(itemId: String, itemName: String, categoryId: String, contentType: String) {
        if (repository.isFavorite(itemId, contentType)) {
            repository.removeFavorite(itemId, contentType)
        } else {
            repository.addFavorite(itemId, itemName, categoryId, contentType)
        }
        refreshCategories()
    }

    fun retry() {
        loadCategories()
    }

    /**
     * Refreshes the lastPlayedItemId in the current UI state.
     * Called when returning from the player screen to update focus target.
     */
    fun refreshLastPlayedItem() {
        val current = _uiState.value
        if (current is UiState.Success) {
            val lastItemId = repository.getLastItemId(contentType)
            _uiState.value = current.copy(lastPlayedItemId = lastItemId)
        }
    }

    private fun rebuildVirtualCategories(regularCategories: List<MediaCategory>): List<MediaCategory> {
        val virtualCats = mutableListOf<MediaCategory>()
        if (contentType != "LIVE_TV") {
            virtualCats.add(MediaCategory(
                id = CONTINUE_WATCHING_CATEGORY_ID,
                name = "Continue Watching",
                isVirtual = true
            ))
        }
        virtualCats.add(MediaCategory(
            id = FAVORITES_CATEGORY_ID,
            name = "Favorites",
            isVirtual = true
        ))
        val favCategories = repository.getFavoriteCategoriesForContentType(contentType)
        if (favCategories.isNotEmpty()) {
            virtualCats.add(MediaCategory(
                id = FAVORITE_CATEGORIES_ID,
                name = "Favorite Categories",
                isVirtual = true
            ))
        }
        virtualCats.add(MediaCategory(
            id = LAST_WATCHED_CATEGORY_ID,
            name = "Last Watched",
            isVirtual = true
        ))
        val recentCategories = repository.getRecentlyViewedCategories(contentType)
        if (recentCategories.isNotEmpty()) {
            virtualCats.add(MediaCategory(
                id = RECENTLY_VIEWED_CATEGORIES_ID,
                name = "Recent Categories",
                isVirtual = true
            ))
        }
        return virtualCats + regularCategories
    }

    fun refreshCategories() {
        // Immediately rebuild virtual categories from local data so UI updates instantly
        val regularCategories = categories.filter { !it.isVirtual }
        categories = rebuildVirtualCategories(regularCategories)
        val lastItemId = repository.getLastItemId(contentType)
        _uiState.value = UiState.Success(
            categories = categories,
            selectedCategoryId = currentCategoryId,
            streams = currentStreams,
            streamsLoading = false,
            categoriesRefreshing = false,
            lastPlayedItemId = lastItemId,
            categoriesPayloadSize = getCategoriesPayloadSize(),
            streamsPayloadSize = getPayloadSize(currentCategoryId ?: "")
        )

        // Also refresh from network in the background
        viewModelScope.launch {
            val result = repository.getFilteredCategories(contentType)
            result.onSuccess { fetchedCategories ->
                categories = rebuildVirtualCategories(fetchedCategories)
                val freshLastItemId = repository.getLastItemId(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = currentCategoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = freshLastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = getPayloadSize(currentCategoryId ?: "")
                )
            }
        }
    }

    fun refreshStreams(categoryId: String) {
        viewModelScope.launch {
            currentCategoryId = categoryId

            val lastItemId = repository.getLastItemId(contentType)

            _uiState.value = UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = null,
                streamsLoading = true,
                categoriesRefreshing = false,
                lastPlayedItemId = lastItemId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null
            )

            // Handle virtual categories
            if (categoryId == CONTINUE_WATCHING_CATEGORY_ID) {
                currentStreams = repository.getInProgressItemsSuspend(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            if (categoryId == FAVORITES_CATEGORY_ID) {
                currentStreams = repository.getFavoritesForContentTypeSuspend(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = getPayloadSize(categoryId)
                )
                return@launch
            }

            if (categoryId == FAVORITE_CATEGORIES_ID) {
                val favCategories = repository.getFavoriteCategoriesForContentType(contentType)
                currentStreams = favCategories.map { cat ->
                    MediaItem(
                        id = "fav_cat_${cat.id}",
                        name = cat.name,
                        mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                        categoryId = FAVORITE_CATEGORIES_ID,
                        providerData = mapOf(
                            "isCategoryRef" to "true",
                            "categoryId" to cat.id
                        )
                    )
                }
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            if (categoryId == LAST_WATCHED_CATEGORY_ID) {
                currentStreams = repository.getWatchHistoryForContentTypeSuspend(contentType)
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = getPayloadSize(categoryId)
                )
                return@launch
            }

            if (categoryId == RECENTLY_VIEWED_CATEGORIES_ID) {
                val recentCategories = repository.getRecentlyViewedCategories(contentType)
                currentStreams = recentCategories.map { recent ->
                    MediaItem(
                        id = "recent_cat_${recent.categoryId}",
                        name = recent.categoryName,
                        mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                        categoryId = RECENTLY_VIEWED_CATEGORIES_ID,
                        providerData = mapOf(
                            "isCategoryRef" to "true",
                            "categoryId" to recent.categoryId
                        )
                    )
                }
                _uiState.value = UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = currentStreams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = null
                )
                return@launch
            }

            val result = repository.getItems(categoryId, contentType)

            result.fold(
                onSuccess = { items ->
                    currentStreams = items
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = lastItemId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                },
                onFailure = {
                    currentStreams = emptyList()
                    _uiState.value = UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = emptyList(),
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = lastItemId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(categoryId)
                    )
                }
            )
        }
    }
}
