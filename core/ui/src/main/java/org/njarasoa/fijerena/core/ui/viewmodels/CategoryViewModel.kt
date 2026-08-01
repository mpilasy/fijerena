package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.di.AppContainer

class CategoryViewModel(
    private val context: Context,
    private val contentType: String,
    private val initialCategoryId: String? = null,
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
            val streamsPayloadSize: String? = null,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    fun getPayloadSize(categoryId: String): String? {
        val key =
            when {
                categoryId.startsWith("vod_") -> "category_$categoryId"
                categoryId.startsWith("series_") -> "category_$categoryId"
                contentType == ContentType.MOVIES -> "category_vod_$categoryId"
                contentType == ContentType.TV_SHOWS -> "category_series_$categoryId"
                else -> "category_$categoryId"
            }
        return repository.getPayloadSize(key)
    }

    fun getCategoriesPayloadSize(): String? =
        when (contentType) {
            ContentType.LIVE_TV -> repository.getPayloadSize("live_categories")
            ContentType.MOVIES -> repository.getPayloadSize("vod_categories")
            ContentType.TV_SHOWS -> repository.getPayloadSize("series_categories")
            else -> null
        }

    fun getFetchTime(categoryId: String): String? {
        val key =
            when {
                categoryId.startsWith("vod_") -> "category_$categoryId"
                categoryId.startsWith("series_") -> "category_$categoryId"
                contentType == ContentType.MOVIES -> "category_vod_$categoryId"
                contentType == ContentType.TV_SHOWS -> "category_series_$categoryId"
                else -> "category_$categoryId"
            }
        return repository.getFetchTimeFormatted(key)
    }

    fun getCategoriesFetchTime(): String? =
        when (contentType) {
            ContentType.LIVE_TV -> repository.getFetchTimeFormatted("live_categories")
            ContentType.MOVIES -> repository.getFetchTimeFormatted("vod_categories")
            ContentType.TV_SHOWS -> repository.getFetchTimeFormatted("series_categories")
            else -> null
        }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val nowPlaying: StateFlow<Map<String, EpgProgram>> = _nowPlaying.asStateFlow()

    private val _supportsNativeEpg = MutableStateFlow(false)
    val supportsNativeEpg: StateFlow<Boolean> = _supportsNativeEpg.asStateFlow()

    // Pre-computed per-item data — avoids calling ViewModel methods inline per visible item
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _favoriteCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteCategoryIds: StateFlow<Set<String>> = _favoriteCategoryIds.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress: StateFlow<Map<String, Float>> = _watchProgress.asStateFlow()

    // Lazily initialized in init coroutine to avoid blocking the UI thread
    private lateinit var repository: MediaRepository

    private var categories: List<MediaCategory> = emptyList()
    private var currentStreams: List<MediaItem> = emptyList()
    private var currentCategoryId: String? = null
    private var isInitialLoad = true
    private var initialLoadRetried = false
    private var categoriesRetried = false

    init {
        viewModelScope.launch {
            repository = AppContainer.getInstance(context).getMediaRepository()
            loadCategoriesInternal()
        }
        // Refresh pre-computed per-item data only when the actual stream list changes
        viewModelScope.launch {
            var lastStreams: List<MediaItem>? = null
            _uiState.collect { state ->
                if (state is UiState.Success && ::repository.isInitialized) {
                    val streams = state.streams
                    if (streams !== lastStreams) {
                        lastStreams = streams
                        refreshPerItemData()
                    }
                }
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            if (!::repository.isInitialized) {
                repository = AppContainer.getInstance(context).getMediaRepository()
            }
            loadCategoriesInternal()
        }
    }

    private suspend fun loadCategoriesInternal() {
        _uiState.value = UiState.Loading

        if (!repository.isConnected()) {
            val connectResult = repository.connect()
            if (connectResult.isFailure) {
                val reason = connectResult.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.value = UiState.Error("Connection failed: $reason")
                return
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
                        onFailure = { buildAndShowCategories(emptyList()) },
                    )
                    return
                }
                buildAndShowCategories(fetchedCategories)
            },
            onFailure = { error ->
                _uiState.value = UiState.Error(error.message ?: "Failed to load categories")
            },
        )
    }

    private fun buildAndShowCategories(fetchedCategories: List<MediaCategory>) {
        categories = rebuildVirtualCategories(fetchedCategories)

        val lastItemId = repository.getLastItemId(contentType)
        _uiState.value =
            UiState.Success(
                categories = categories,
                selectedCategoryId = null,
                streams = null,
                streamsLoading = false,
                categoriesRefreshing = false,
                lastPlayedItemId = lastItemId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null,
            )

        if (categories.isNotEmpty()) {
            val categoryToLoad =
                if (initialCategoryId != null &&
                    categories.any { it.id == initialCategoryId }
                ) {
                    initialCategoryId
                } else if (contentType == ContentType.MOVIES || contentType == ContentType.TV_SHOWS) {
                    // Movies/TV Shows: default to Continue Watching so the landing page shows
                    // what's in progress, not just the single most-recently-touched item.
                    CONTINUE_WATCHING_CATEGORY_ID
                } else {
                    // Live TV: default to "Last Watched" so focus lands on the last played stream
                    LAST_WATCHED_CATEGORY_ID
                }
            loadStreams(categoryToLoad)
        }
    }

    fun loadStreams(categoryId: String) {
        viewModelScope.launch {
            loadStreamsInternal(categoryId, isRetryEnabled = true)
        }
    }

    /**
     * Shared implementation for loading streams by category. Handles both initial load
     * and refresh paths, eliminating ~130 lines of duplicated code.
     * @param isRetryEnabled when true, retries once on empty/failed initial load (loadStreams path)
     */
    private suspend fun loadStreamsInternal(
        categoryId: String,
        isRetryEnabled: Boolean,
    ) {
        currentCategoryId = categoryId
        val lastItemId = repository.getLastItemId(contentType)

        _uiState.value =
            UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = null,
                streamsLoading = true,
                categoriesRefreshing = false,
                lastPlayedItemId = lastItemId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null,
            )

        // Helper to emit a success state with the given streams
        fun emitStreams(
            streams: List<MediaItem>,
            payloadSize: String? = null,
        ) {
            currentStreams = streams
            _uiState.value =
                UiState.Success(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    streams = streams,
                    streamsLoading = false,
                    categoriesRefreshing = false,
                    lastPlayedItemId = lastItemId,
                    categoriesPayloadSize = getCategoriesPayloadSize(),
                    streamsPayloadSize = payloadSize,
                )
        }

        // Handle virtual categories
        when (categoryId) {
            CONTINUE_WATCHING_CATEGORY_ID -> {
                emitStreams(repository.getInProgressItemsSuspend(contentType))
                loadNowPlaying(currentStreams)
                return
            }
            LAST_WATCHED_CATEGORY_ID -> {
                emitStreams(repository.getWatchHistoryForContentTypeSuspend(contentType))
                loadNowPlaying(currentStreams)
                return
            }
            FAVORITES_CATEGORY_ID -> {
                emitStreams(repository.getFavoritesForContentTypeSuspend(contentType))
                loadNowPlaying(currentStreams)
                return
            }
            FAVORITE_CATEGORIES_ID -> {
                val favCategories = repository.getFavoriteCategoriesForContentType(contentType)
                emitStreams(
                    favCategories.map { cat ->
                        MediaItem(
                            id = "fav_cat_${cat.id}",
                            name = cat.name,
                            mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                            categoryId = FAVORITE_CATEGORIES_ID,
                            providerData = mapOf("isCategoryRef" to "true", "categoryId" to cat.id),
                        )
                    },
                )
                return
            }
            RECENTLY_VIEWED_CATEGORIES_ID -> {
                val recentCategories = repository.getRecentlyViewedCategories(contentType)
                emitStreams(
                    recentCategories.map { recent ->
                        MediaItem(
                            id = "recent_cat_${recent.categoryId}",
                            name = recent.categoryName,
                            mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                            categoryId = RECENTLY_VIEWED_CATEGORIES_ID,
                            providerData = mapOf("isCategoryRef" to "true", "categoryId" to recent.categoryId),
                        )
                    },
                )
                return
            }
        }

        // Track non-virtual category views
        val categoryName = categories.firstOrNull { it.id == categoryId }?.name
        if (categoryName != null) {
            repository.addToCategoryHistory(categoryId, categoryName, contentType)
        }

        val result = repository.getItems(categoryId, contentType)

        result.fold(
            onSuccess = { items ->
                emitStreams(items, getPayloadSize(categoryId))
                loadNowPlaying(items)
                // Retry once if initial load returned empty for a non-virtual category
                if (isRetryEnabled && isInitialLoad && items.isEmpty() && !initialLoadRetried) {
                    initialLoadRetried = true
                    delay(1500)
                    loadStreamsInternal(categoryId, isRetryEnabled = true)
                }
                isInitialLoad = false
            },
            onFailure = {
                emitStreams(emptyList(), getPayloadSize(categoryId))
                // Retry once on initial load failure after a short delay
                if (isRetryEnabled && isInitialLoad && !initialLoadRetried) {
                    initialLoadRetried = true
                    delay(2000)
                    loadStreamsInternal(categoryId, isRetryEnabled = true)
                }
            },
        )
    }

    private fun loadNowPlaying(items: List<MediaItem>) {
        if (contentType != ContentType.LIVE_TV) return
        viewModelScope.launch {
            // Phase 1: Fast SQLite query for indexed channels
            val indexResult = repository.getNowPlayingFromIndex(items.take(50))
            if (indexResult.isNotEmpty()) {
                _nowPlaying.value = indexResult
            }

            // Phase 2: Xtream API fallback for unmatched items
            val caps = repository.getCapabilities()
            if (caps?.supportsEpg != true) return@launch

            val unmatchedItems = items.take(50).filter { it.id !in indexResult }
            if (unmatchedItems.isEmpty()) return@launch

            val now = System.currentTimeMillis() / 1000
            for (chunk in unmatchedItems.chunked(10)) {
                val streamIds = chunk.map { it.id }
                val epgResult = repository.getEpgBulk(streamIds)?.getOrNull() ?: continue
                val batchNowPlaying =
                    epgResult
                        .mapValues { (_, resp) ->
                            resp.listings.firstOrNull { now in it.startTime..it.endTime }
                        }.filterValues { it != null }
                        .mapValues { it.value!! }

                if (batchNowPlaying.isNotEmpty()) {
                    _nowPlaying.value = _nowPlaying.value + batchNowPlaying
                }
            }

            // Fire-and-forget: ingest Xtream EPG into index for next time
            launch { repository.ingestXtreamEpgIfNeeded() }
        }
    }

    fun getPlaybackPosition(
        itemId: String,
        contentType: String,
    ) = repository.getPlaybackPosition(itemId, contentType)

    fun isFavorite(
        itemId: String,
        contentType: String,
    ) = repository.isFavorite(itemId, contentType)

    fun isFavoriteCategory(
        categoryId: String,
        contentType: String,
    ) = repository.isFavoriteCategory(categoryId, contentType)

    /**
     * Refresh pre-computed per-item data (favorites, watch progress) for current streams.
     * Called after streams change or favorites are toggled.
     */
    private suspend fun refreshPerItemData() {
        if (!::repository.isInitialized) return
        val streams = currentStreams
        val ct = contentType
        val cats = categories

        withContext(Dispatchers.Default) {
            // Build favorite IDs set
            _favoriteIds.value =
                streams
                    .filter { repository.isFavorite(it.id, ct) }
                    .mapTo(HashSet()) { it.id }

            // Build favorite category IDs set
            _favoriteCategoryIds.value =
                cats
                    .filter { repository.isFavoriteCategory(it.id, ct) }
                    .mapTo(HashSet()) { it.id }

            // Build watch progress map (optimized bulk lookup)
            val itemIds = streams.map { it.id }
            val positions = repository.getPlaybackPositions(itemIds, ct)

            val progressMap = HashMap<String, Float>(positions.size)
            for ((id, watched) in positions) {
                if (watched.duration > 0) {
                    progressMap[id] = (watched.playbackPosition.toFloat() / watched.duration.toFloat()).coerceIn(0f, 1f)
                }
            }
            _watchProgress.value = progressMap
        }
    }

    fun toggleFavoriteCategory(
        categoryId: String,
        categoryName: String,
        contentType: String,
    ) {
        if (repository.isFavoriteCategory(categoryId, contentType)) {
            repository.removeFavoriteCategory(categoryId, contentType)
        } else {
            repository.addFavoriteCategory(categoryId, categoryName, contentType)
        }
        viewModelScope.launch { refreshPerItemData() }
        // Local rebuild only — no network fetch needed for a local favorite change
        refreshCategoriesLocal()
    }

    fun toggleFavoriteStream(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
    ) {
        if (repository.isFavorite(itemId, contentType)) {
            repository.removeFavorite(itemId, contentType)
        } else {
            repository.addFavorite(itemId, itemName, categoryId, contentType)
        }
        viewModelScope.launch { refreshPerItemData() }
        // Local rebuild only — no network fetch needed for a local favorite change
        refreshCategoriesLocal()
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
        if (contentType != ContentType.LIVE_TV) {
            virtualCats.add(
                MediaCategory(
                    id = CONTINUE_WATCHING_CATEGORY_ID,
                    name = "Continue Watching",
                    isVirtual = true,
                ),
            )
        }
        virtualCats.add(
            MediaCategory(
                id = FAVORITES_CATEGORY_ID,
                name = "Favorites",
                isVirtual = true,
            ),
        )
        val favCategories = repository.getFavoriteCategoriesForContentType(contentType)
        if (favCategories.isNotEmpty()) {
            virtualCats.add(
                MediaCategory(
                    id = FAVORITE_CATEGORIES_ID,
                    name = "Favorite Categories",
                    isVirtual = true,
                ),
            )
        }
        virtualCats.add(
            MediaCategory(
                id = LAST_WATCHED_CATEGORY_ID,
                name = "Last Watched",
                isVirtual = true,
            ),
        )
        val recentCategories = repository.getRecentlyViewedCategories(contentType)
        if (recentCategories.isNotEmpty()) {
            virtualCats.add(
                MediaCategory(
                    id = RECENTLY_VIEWED_CATEGORIES_ID,
                    name = "Recent Categories",
                    isVirtual = true,
                ),
            )
        }
        return virtualCats + regularCategories
    }

    /** Rebuild virtual categories from local data only — no network I/O. */
    private fun refreshCategoriesLocal() {
        val regularCategories = categories.filter { !it.isVirtual }
        categories = rebuildVirtualCategories(regularCategories)
        val lastItemId = repository.getLastItemId(contentType)
        _uiState.value =
            UiState.Success(
                categories = categories,
                selectedCategoryId = currentCategoryId,
                streams = currentStreams,
                streamsLoading = false,
                categoriesRefreshing = false,
                lastPlayedItemId = lastItemId,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = getPayloadSize(currentCategoryId ?: ""),
            )
    }

    fun refreshCategories() {
        // Immediately rebuild virtual categories from local data so UI updates instantly
        refreshCategoriesLocal()

        // Also refresh from network in the background
        viewModelScope.launch {
            val result = repository.getFilteredCategories(contentType)
            result.onSuccess { fetchedCategories ->
                categories = rebuildVirtualCategories(fetchedCategories)
                val freshLastItemId = repository.getLastItemId(contentType)
                _uiState.value =
                    UiState.Success(
                        categories = categories,
                        selectedCategoryId = currentCategoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = freshLastItemId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(currentCategoryId ?: ""),
                    )
            }
        }
    }

    fun refreshStreams(categoryId: String) {
        viewModelScope.launch {
            loadStreamsInternal(categoryId, isRetryEnabled = false)
        }
    }

    /**
     * Watch history fetch that bypasses [uiState] entirely — for callers (the Live TV preview
     * pane) that need the history list independent of whatever category is actually selected/
     * browsed, without disturbing that selection or its own streams list.
     */
    suspend fun getLastWatchedSnapshot(): List<MediaItem> {
        if (!::repository.isInitialized) return emptyList()
        return repository.getWatchHistoryForContentTypeSuspend(contentType)
    }
}
