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
        const val LAST_WATCHED_CATEGORY_ID = "last_watched"
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
        val virtualCategories = mutableListOf<MediaCategory>()

        if (contentType != "LIVE_TV") {
            virtualCategories.add(MediaCategory(
                id = CONTINUE_WATCHING_CATEGORY_ID,
                name = "Continue Watching",
                isVirtual = true
            ))
        }

        virtualCategories.add(MediaCategory(
            id = FAVORITES_CATEGORY_ID,
            name = "Favorites",
            isVirtual = true
        ))

        virtualCategories.add(MediaCategory(
            id = LAST_WATCHED_CATEGORY_ID,
            name = "Last Watched",
            isVirtual = true
        ))

        categories = virtualCategories + fetchedCategories

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
            val lastCategoryId = repository.getLastCategoryId(contentType)
            val categoryToLoad = if (initialCategoryId != null &&
                categories.any { it.id == initialCategoryId }) {
                initialCategoryId
            } else if (lastCategoryId != null &&
                categories.any { it.id == lastCategoryId }) {
                lastCategoryId
            } else {
                val firstRegularCategory = categories.firstOrNull {
                    it.id != FAVORITES_CATEGORY_ID &&
                    it.id != LAST_WATCHED_CATEGORY_ID &&
                    it.id != CONTINUE_WATCHING_CATEGORY_ID
                }
                firstRegularCategory?.id ?: categories.first().id
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
                        categoryId != LAST_WATCHED_CATEGORY_ID) {
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
            val hasExternalEpg = repository.getProviderSettings().epgUrl.isNotBlank()
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

    fun retry() {
        loadCategories()
    }

    fun refreshCategories() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                _uiState.value = currentState.copy(categoriesRefreshing = true)
            }

            val result = repository.getFilteredCategories(contentType)

            result.fold(
                onSuccess = { fetchedCategories ->
                    val favoritesCategory = MediaCategory(
                        id = FAVORITES_CATEGORY_ID,
                        name = "Favorites",
                        isVirtual = true
                    )
                    val lastWatchedCategory = MediaCategory(
                        id = LAST_WATCHED_CATEGORY_ID,
                        name = "Last Watched",
                        isVirtual = true
                    )
                    categories = listOf(favoritesCategory, lastWatchedCategory) + fetchedCategories

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
                },
                onFailure = {
                    if (currentState is UiState.Success) {
                        _uiState.value = currentState.copy(categoriesRefreshing = false)
                    }
                }
            )
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
