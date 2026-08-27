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
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer

class CategoryViewModel(
    private val context: Context,
    private val contentType: String,
    private val initialCategoryId: String? = null,
) : ViewModel() {
    companion object {
        const val RECENT_CATEGORY_ID = "recent"
        const val FAVORITES_CATEGORY_ID = "favorites"
        const val FAVORITE_CATEGORIES_ID = "favorite_categories"
        const val RECENTLY_VIEWED_CATEGORIES_ID = "recently_viewed_categories"

        val VIRTUAL_CATEGORY_IDS =
            setOf(
                FAVORITES_CATEGORY_ID,
                FAVORITE_CATEGORIES_ID,
                RECENT_CATEGORY_ID,
                RECENTLY_VIEWED_CATEGORIES_ID,
            )

        /**
         * Maps the ids of the two rows Recent replaced onto it. Nothing persists a virtual
         * category id today, but a restored saved state or a hand-written deep link carrying
         * one would otherwise resolve to a category that no longer exists.
         */
        fun canonicalCategoryId(categoryId: String?): String? =
            when (categoryId) {
                "continue_watching", "last_watched" -> RECENT_CATEGORY_ID
                else -> categoryId
            }
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

    // Mirrors the repository's shared Recent list so the Live TV preview panels can render it
    // without owning a fetch of their own. null until the first load lands.
    private val _recentItems = MutableStateFlow<List<MediaItem>?>(null)
    val recentItems: StateFlow<List<MediaItem>?> = _recentItems.asStateFlow()

    // Pre-computed per-item data — avoids calling ViewModel methods inline per visible item
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _favoriteCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteCategoryIds: StateFlow<Set<String>> = _favoriteCategoryIds.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress: StateFlow<Map<String, Float>> = _watchProgress.asStateFlow()

    // Finished items, drawn with a watched check instead of a progress bar. Disjoint from
    // [watchProgress] by construction: resumeProgress() returns null once isCompleted is set.
    private val _watchedIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedIds: StateFlow<Set<String>> = _watchedIds.asStateFlow()

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
            launch { repository.recentItems(contentType).collect { _recentItems.value = it } }
            loadCategoriesInternal()
            // Entering on a real category (from the EPG, search, or a saved selection) never
            // loads the Recent row, but the Live TV preview panel shows that list regardless of
            // what was browsed into — without this warm-up it would sit on its spinner forever.
            if (repository.recentItems(contentType).value == null) {
                repository.refreshRecentItems(contentType)
            }
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
        // Keep whatever is already on screen and mark it refreshing, rather than blanking the
        // grid to a full-screen spinner on every reload. Only a genuinely empty screen (first
        // load, or a previous failure) falls back to Loading.
        val current = _uiState.value
        _uiState.value =
            if (current is UiState.Success && current.categories.isNotEmpty()) {
                current.copy(categoriesRefreshing = true)
            } else {
                UiState.Loading
            }

        if (!repository.isConnected()) {
            val connectResult = repository.connect()
            if (connectResult.isFailure) {
                val reason = connectResult.exceptionOrNull()?.message ?: context.getString(R.string.error_generic_unknown)
                _uiState.value = UiState.Error(context.getString(R.string.category_error_connection_failed_format, reason))
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
                _uiState.value = UiState.Error(error.message ?: context.getString(R.string.category_error_load_failed))
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
            val requestedCategoryId = canonicalCategoryId(initialCategoryId)
            val categoryToLoad =
                if (requestedCategoryId != null &&
                    categories.any { it.id == requestedCategoryId }
                ) {
                    requestedCategoryId
                } else {
                    // Every content type lands on Recent: what's in progress first, then the
                    // rest of the history — for Live TV that's the last played channels.
                    RECENT_CATEGORY_ID
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
            RECENT_CATEGORY_ID -> {
                emitStreams(repository.refreshRecentItems(contentType))
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
                            target = BrowseTarget.CategoryRef(cat.id),
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
                            target = BrowseTarget.CategoryRef(recent.categoryId),
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
            // Accumulate across chunks and emit once. Emitting per chunk published a fresh map up
            // to five times per list load (50 items / 10 per request), and every one of those is a
            // new ImmutableNowPlaying that recomposes the whole channel list — interleaved with the
            // network waits between chunks, so the list churned for as long as the fallback ran.
            val collected = mutableMapOf<String, EpgProgram>()
            for (chunk in unmatchedItems.chunked(10)) {
                val streamIds = chunk.map { it.id }
                val epgResult = repository.getEpgBulk(streamIds)?.getOrNull() ?: continue
                for ((itemId, resp) in epgResult) {
                    val airing = resp.listings.firstOrNull { now in it.startTime..it.endTime }
                    if (airing != null) collected[itemId] = airing
                }
            }
            if (collected.isNotEmpty()) {
                _nowPlaying.value = _nowPlaying.value + collected
            }

            // The catalogue-wide EPG ingest used to be fired off here. It is EpgSyncWorker's job
            // now — running it from a list load meant a whole-catalogue fetch competing with
            // video decode in the same process, which stuttered playback and eventually ANR'd.
        }
    }

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
            val watched = HashSet<String>()
            for ((id, item) in positions) {
                // Resumable band only, so a card's bar means the same thing everywhere:
                // barely-started and finished items get no bar rather than a sliver or a full one.
                item.resumeProgress()?.let { progressMap[id] = it }
                if (item.isCompleted) watched.add(id)
            }
            // Series rows track episodes completed, not minutes: watch history is keyed by
            // episode, so a series id never resolves through the lookup above. A fully watched
            // series drops out of the bar map and gets the check instead, matching movies.
            if (ct == ContentType.TV_SHOWS) {
                val seriesProgress = repository.getSeriesWatchProgress()
                for (item in streams) {
                    val fraction = seriesProgress[item.id] ?: continue
                    if (fraction >= 1f) {
                        watched.add(item.id)
                    } else {
                        progressMap[item.id] = fraction
                    }
                }
            }

            _watchProgress.value = progressMap
            _watchedIds.value = watched
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
        virtualCats.add(
            MediaCategory(
                id = RECENT_CATEGORY_ID,
                name = context.getString(R.string.category_recent_label),
                isVirtual = true,
            ),
        )
        virtualCats.add(
            MediaCategory(
                id = FAVORITES_CATEGORY_ID,
                name = context.getString(R.string.settings_import_favorites_label),
                isVirtual = true,
            ),
        )
        val favCategories = repository.getFavoriteCategoriesForContentType(contentType)
        if (favCategories.isNotEmpty()) {
            virtualCats.add(
                MediaCategory(
                    id = FAVORITE_CATEGORIES_ID,
                    name = context.getString(R.string.category_favorite_categories_label),
                    isVirtual = true,
                ),
            )
        }
        val recentCategories = repository.getRecentlyViewedCategories(contentType)
        if (recentCategories.isNotEmpty()) {
            virtualCats.add(
                MediaCategory(
                    id = RECENTLY_VIEWED_CATEGORIES_ID,
                    name = context.getString(R.string.category_recent_categories_label),
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
     * Reloads the shared Recent list — for callers (the Live TV preview panel) that show it
     * independently of whatever category is actually selected, without disturbing that
     * selection or its streams list. The result arrives via [recentItems].
     */
    suspend fun refreshRecentItems() {
        if (::repository.isInitialized) {
            repository.refreshRecentItems(contentType)
        }
    }

    /**
     * Favorites fetch, bypassing [uiState] the same way — for the same Live TV preview panel,
     * toggling between the two without disturbing the selected/browsed category.
     */
    suspend fun getFavoritesSnapshot(): List<MediaItem> {
        if (!::repository.isInitialized) return emptyList()
        return repository.getFavoritesForContentTypeSuspend(contentType)
    }
}

/** Splits into (virtual, regular) categories — single pass instead of two filters. */
fun List<MediaCategory>.partitionVirtual(): Pair<List<MediaCategory>, List<MediaCategory>> =
    partition { it.id in CategoryViewModel.VIRTUAL_CATEGORY_IDS }
