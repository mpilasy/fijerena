package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.RecentRemoval
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.categoryKey
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

class CategoryViewModel(
    private val context: Context,
    private val contentType: String,
    private val initialCategoryId: String? = null,
    // The source the list was opened on (a category from Search, a guide or another source's
    // route); 0 = a section's list. Either way the list holds every Xtream source's categories of
    // the profile (multi-source plan, Phase 6), [initialCategoryId] on this one.
    val providerId: Long = 0L,
) : ViewModel() {
    private val appSettings = AppSettings(context.applicationContext)

    companion object {
        const val RECENT_CATEGORY_ID = "recent"
        const val FAVORITES_CATEGORY_ID = "favorites"
        const val FAVORITE_CATEGORIES_ID = "favorite_categories"

        /** Id prefix of the Favorite Categories list's rows — each one stands for a category, not a stream. */
        private const val FAVORITE_CATEGORY_ROW_PREFIX = "fav_cat_"

        /** Id prefix of the Recent Categories list's rows, which stand for a category too. */
        private const val RECENT_CATEGORY_ROW_PREFIX = "recent_cat_"
        const val RECENTLY_VIEWED_CATEGORIES_ID = "recently_viewed_categories"
        private const val TAG = "CategoryViewModel"

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

        /**
         * The rows of Recent or Favourites for [contentType], or null when [categoryId] is a real
         * category the source knows. Shared with the TV Guide, which otherwise sends the virtual
         * id to the source and gets the catalogue's first page back.
         */
        suspend fun virtualCategoryItems(
            repo: MediaRepository,
            categoryId: String,
            contentType: String,
        ): List<MediaItem>? =
            when (categoryId) {
                RECENT_CATEGORY_ID -> repo.refreshRecentItems(contentType)
                FAVORITES_CATEGORY_ID -> repo.getFavoritesForContentTypeSuspend(contentType)
                else -> null
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
            // The selected category's source; 0 for a virtual one (Recent, Favourites…).
            val selectedCategorySource: Long = 0L,
        ) : UiState() {
            /** The selection as a [MediaCategory.key]: what the category lists compare and focus by. */
            val selectedCategoryKey: String? get() = selectedCategoryId?.let { categoryKey(it, selectedCategorySource) }

            /** The selected category itself; null with none, or one no longer listed. */
            val selectedCategory: MediaCategory? get() = selectedCategoryKey?.let { key -> categories.firstOrNull { it.key == key } }
        }

        data class Error(
            val message: String,
        ) : UiState()
    }

    fun getPayloadSize(categoryId: String): String? {
        val repo = categoryRepoOrNull(categoryId) ?: return null
        val key =
            when {
                categoryId.startsWith("vod_") -> "category_$categoryId"
                categoryId.startsWith("series_") -> "category_$categoryId"
                contentType == ContentType.MOVIES -> "category_vod_$categoryId"
                contentType == ContentType.TV_SHOWS -> "category_series_$categoryId"
                else -> "category_$categoryId"
            }
        return repo.getPayloadSize(key)
    }

    // [categoryId]'s source's repository when it is the selected category, else this list's own.
    private fun categoryRepoOrNull(categoryId: String): MediaRepository? =
        if (categoryId == currentCategoryId && currentCategorySource != 0L) {
            mergedSnapshot?.repoFor(currentCategorySource)
        } else {
            repositoryOrNull
        }

    fun getCategoriesPayloadSize(): String? {
        val repo = repositoryOrNull ?: return null
        return when (contentType) {
            ContentType.LIVE_TV -> repo.getPayloadSize("live_categories")
            ContentType.MOVIES -> repo.getPayloadSize("vod_categories")
            ContentType.TV_SHOWS -> repo.getPayloadSize("series_categories")
            else -> null
        }
    }

    fun getFetchTime(categoryId: String): String? {
        val repo = categoryRepoOrNull(categoryId) ?: return null
        val key =
            when {
                categoryId.startsWith("vod_") -> "category_$categoryId"
                categoryId.startsWith("series_") -> "category_$categoryId"
                contentType == ContentType.MOVIES -> "category_vod_$categoryId"
                contentType == ContentType.TV_SHOWS -> "category_series_$categoryId"
                else -> "category_$categoryId"
            }
        return repo.getFetchTimeFormatted(key)
    }

    fun getCategoriesFetchTime(): String? {
        val repo = repositoryOrNull ?: return null
        return when (contentType) {
            ContentType.LIVE_TV -> repo.getFetchTimeFormatted("live_categories")
            ContentType.MOVIES -> repo.getFetchTimeFormatted("vod_categories")
            ContentType.TV_SHOWS -> repo.getFetchTimeFormatted("series_categories")
            else -> null
        }
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val nowPlaying: StateFlow<Map<String, EpgProgram>> = _nowPlaying.asStateFlow()

    private val _supportsNativeEpg = MutableStateFlow(false)
    val supportsNativeEpg: StateFlow<Boolean> = _supportsNativeEpg.asStateFlow()

    // Mirrors the shared Recent list — every source of the profile's, merged (multi-source plan,
    // Phase 4) — so the Live TV preview panels can render it without owning a fetch of their own.
    // null until the first load lands.
    private val _recentItems = MutableStateFlow<List<MediaItem>?>(null)
    val recentItems: StateFlow<List<MediaItem>?> = _recentItems.asStateFlow()

    val supportsRemoveFromRecent: Boolean
        get() = repositoryOrNull?.supportsRemoveFromRecent ?: true

    // Each source's name by id while the Recent and Favourites lists mix several (their rows show
    // it, SourceBadge); empty with one.
    private val _sourceNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sourceNames: StateFlow<Map<Long, String>> = _sourceNames.asStateFlow()

    /** This list's own source, resolved (the active one's id for 0) once the repository is built. */
    val sourceId: Long
        get() = repositorySnapshot?.getProviderId() ?: providerId

    /**
     * Whether a row of [rowSource] is on a source this list doesn't hold (its route opens there).
     * The list holds every Xtream source of the profile (Phase 6), so a Recent Categories row of
     * any of them opens in place.
     */
    fun isOtherSource(rowSource: Long): Boolean =
        rowSource != 0L && (mergedSnapshot?.inProfileOrder?.none { it.providerId == rowSource } ?: (rowSource != sourceId))

    // Pre-computed per-item data — avoids calling ViewModel methods inline per visible item. Keyed
    // by the row's sourceKey, a category by its key (and [nowPlaying] too): the same id on two
    // sources is two rows, each with its own star, progress and now line (Phase 6).
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    // Bumped on every favourite change made here, so a list held outside [uiState] (the Live TV
    // panel's Favourites tab) re-reads it: a removed row goes, an Undo puts it back.
    private val _favoritesVersion = MutableStateFlow(0)
    val favoritesVersion: StateFlow<Int> = _favoritesVersion.asStateFlow()

    private val _favoriteCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteCategoryIds: StateFlow<Set<String>> = _favoriteCategoryIds.asStateFlow()

    private val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress: StateFlow<Map<String, Float>> = _watchProgress.asStateFlow()

    // Finished items, drawn with a watched check instead of a progress bar. Disjoint from
    // [watchProgress] by construction: resumeProgress() returns null once isCompleted is set.
    private val _watchedIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedIds: StateFlow<Set<String>> = _watchedIds.asStateFlow()

    // Resolved in the init coroutine to avoid blocking the UI thread. Every suspend call site
    // awaits this, so a call made before it is set (loadStreams(), favorite/watched toggles,
    // removeFromRecent() — see its own comment on why that race is real) waits instead of being
    // dropped. The handful of synchronous call sites Compose reads directly during composition
    // (which cannot suspend) go through [repositoryOrNull] instead and degrade to defaults.
    private val repositoryDeferred = CompletableDeferred<MediaRepository>()

    // Mirrors repositoryDeferred's result once completed — set in the same init coroutine, right
    // alongside repositoryDeferred.complete(). CompletableDeferred.getCompleted() would do the
    // same thing but is @ExperimentalCoroutinesApi; this avoids that opt-in for no real benefit.
    @Volatile
    private var repositorySnapshot: MediaRepository? = null

    private suspend fun awaitRepository(): MediaRepository = repositoryDeferred.await()

    private val repositoryOrNull: MediaRepository?
        get() = repositorySnapshot

    // The Recent, Favourites and Recent Categories lists of every source of the profile (Phase 4),
    // built with the repository; this list's own source is its primary.
    private val mergedDeferred = CompletableDeferred<MergedLists>()

    @Volatile
    private var mergedSnapshot: MergedLists? = null

    private suspend fun awaitMerged(): MergedLists = mergedDeferred.await()

    /** [rowSource]'s repository (a merged list's row), this list's own for 0. */
    private suspend fun repoFor(rowSource: Long): MediaRepository = awaitMerged().repoFor(rowSource)

    private var categories: List<MediaCategory> = emptyList()
    private var currentStreams: List<MediaItem> = emptyList()
    private var currentCategoryId: String? = null

    // The selected category's source (0 for a virtual list): its rows, guide and Refresh are its.
    private var currentCategorySource: Long = 0L

    // Live TV's Recent keeps its order for the visit (docs/plans/archive/20261009_tv-recents-favorites-plan.md
    // → B): the row ids in display order, removed ones included so an Undo puts a row back in its
    // place. Reset on Refresh and when the section is entered again ([reloadRecentOnEntry]).
    private var liveRecentOrder: List<String> = emptyList()
    private var isInitialLoad = true
    private var initialLoadRetried = false
    private var categoriesRetried = false

    // Guards against category-switch races (rapid D-pad/tap navigation launching overlapping
    // queries): each new load cancels whatever's still in flight for the previous category.
    private var loadStreamsJob: Job? = null
    private var nowPlayingJob: Job? = null

    // The init coroutine. If building the repository fails (Keystore, credential store,
    // providers.db) the screen shows the error, and Retry ([loadCategories]) runs it again —
    // repositoryDeferred stays pending until it succeeds, so nothing awaiting it sees a failure.
    private var repositoryJob: Job? = null

    init {
        repositoryJob = startRepository()
        // Refresh pre-computed per-item data only when the actual stream list changes
        viewModelScope.launchGuarded("CategoryViewModel.perItemData") {
            var lastStreams: List<MediaItem>? = null
            _uiState.collect { state ->
                if (state is UiState.Success) {
                    val streams = state.streams
                    if (streams !== lastStreams) {
                        lastStreams = streams
                        refreshPerItemData()
                    }
                }
            }
        }
    }

    private fun startRepository(): Job =
        viewModelScope.launchGuarded("CategoryViewModel.init", onError = ::showError) {
            val container = AppContainer.getInstance(context)
            // A section's list is built around the profile's first Xtream source, not the active one.
            val repo = container.getMediaRepository(MergedLists.browsingProviderId(container, providerId))
            repositorySnapshot = repo
            repositoryDeferred.complete(repo)
            if (!mergedDeferred.isCompleted) {
                val merged = MergedLists.withOthers(container, repo)
                mergedSnapshot = merged
                mergedDeferred.complete(merged)
                _sourceNames.value = merged.sourceNames
            }
            val merged = awaitMerged()
            val ownSource = repo.getProviderId()
            launch { merged.recentItems(contentType).collect { _recentItems.value = it?.onSources(ownSource) } }
            loadCategoriesInternal()
            // Entering on a real category (from the EPG, search, or a saved selection) never
            // loads the Recent row, but the Live TV preview panel shows that list regardless of
            // what was browsed into — without this warm-up it would sit on its spinner forever.
            merged.loadRecentItems(contentType)
        }

    private fun showError(e: Throwable) {
        _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
    }

    fun loadCategories() {
        if (repositoryDeferred.isCompleted || repositoryJob?.isActive == true) {
            viewModelScope.launchGuarded("CategoryViewModel.loadCategories", onError = ::showError) {
                loadCategoriesInternal()
            }
        } else {
            repositoryJob = startRepository()
        }
    }

    private suspend fun loadCategoriesInternal() {
        val repo = awaitRepository()
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

        val connectError =
            if (repo.isConnected()) {
                null
            } else {
                val connectResult = repo.connect()
                if (connectResult.isFailure) {
                    // Never a Room/HTTP/serialization exception's own message verbatim (e.g. a JSON
                    // parse error from an EOF response body): friendlyErrorMessage maps it to
                    // something a viewer can act on, with the raw text appended only in dev mode.
                    val reason =
                        connectResult.exceptionOrNull()?.let { friendlyErrorMessage(it, context, appSettings.isDevMode) }
                            ?: context.getString(R.string.error_generic_unknown)
                    context.getString(R.string.category_error_connection_failed_format, reason)
                } else {
                    null
                }
            }

        if (connectError != null) {
            _uiState.value = UiState.Error(connectError)
        } else {
            _supportsNativeEpg.value = repo.getCapabilities()?.supportsEpg == true

            val result = mergedCategories(repo)

            result.fold(
                onSuccess = { fetchedCategories ->
                    // Retry once if provider returned no categories (server session may not be ready)
                    if (fetchedCategories.isEmpty() && !categoriesRetried) {
                        categoriesRetried = true
                        delay(1500)
                        val retryResult = mergedCategories(repo)
                        retryResult.fold(
                            onSuccess = { buildAndShowCategories(repo, it) },
                            onFailure = { buildAndShowCategories(repo, emptyList()) },
                        )
                    } else {
                        buildAndShowCategories(repo, fetchedCategories)
                    }
                },
                onFailure = { error ->
                    // Same reasoning as the connect-failure branch above: raw exception text
                    // (e.g. "executor rejected", a Room/HTTP internal) is meaningless to a user
                    // and must go through friendlyErrorMessage, not straight to the UI.
                    _uiState.value = UiState.Error(friendlyErrorMessage(error, context, appSettings.isDevMode))
                },
            )
        }
    }

    /**
     * Every source's categories (each its own filters and hidden categories), one list: by source in
     * the profile's order, then each source's own order, each tagged with its source (Phase 6). This
     * list's own source failing fails it; another one failing is left out.
     */
    private suspend fun mergedCategories(repo: MediaRepository): kotlin.Result<List<MediaCategory>> {
        val ownSource = repo.getProviderId()
        val reads =
            coroutineScope {
                awaitMerged()
                    .inProfileOrder
                    .map { source ->
                        async {
                            val read =
                                if (source.providerId == ownSource) {
                                    repo.getFilteredCategories(contentType)
                                } else {
                                    suspendRunCatching { source.repo.getFilteredCategories(contentType).getOrThrow() }
                                        .onFailure { android.util.Log.w(TAG, "Source ${source.providerId} left out of the categories", it) }
                                }
                            source.providerId to read
                        }
                    }.awaitAll()
            }
        val ownFailure = reads.firstOrNull { it.first == ownSource }?.second?.exceptionOrNull()
        return if (ownFailure != null) {
            kotlin.Result.failure(ownFailure)
        } else {
            val lists = reads.map { (source, read) -> source to read.getOrNull().orEmpty() }
            kotlin.Result.success(withContext(Dispatchers.Default) { mergeCategories(lists) })
        }
    }

    private fun buildAndShowCategories(
        repo: MediaRepository,
        fetchedCategories: List<MediaCategory>,
    ) {
        categories = rebuildVirtualCategories(repo, fetchedCategories)

        val lastItemId = repo.getLastItemId(contentType)
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
            // The requested category on the source the list was opened on (route), else on any.
            val requested = canonicalCategoryId(initialCategoryId)?.let { findCategory(it, repo.getProviderId()) }
            // Every content type lands on Recent otherwise: what's in progress first, then the
            // rest of the history — for Live TV that's the last played channels.
            loadStreams(requested?.id ?: RECENT_CATEGORY_ID, requested?.providerId ?: 0L)
        }
    }

    /**
     * The category [categoryId] of [source] in the list; with no source (0), this list's own
     * source's, else the first one listed. Virtual categories have no source.
     */
    private fun findCategory(
        categoryId: String,
        source: Long,
    ): MediaCategory? {
        val matches = categories.filter { it.id == categoryId }
        return when {
            source != 0L -> matches.firstOrNull { it.isVirtual || it.providerId == source }
            else -> matches.firstOrNull { it.isVirtual || it.providerId == sourceId } ?: matches.firstOrNull()
        }
    }

    /** Opens [categoryId] of [source] (0: this list's own, or a virtual list). */
    fun loadStreams(
        categoryId: String,
        source: Long = 0L,
    ) {
        loadStreamsJob?.cancel()
        nowPlayingJob?.cancel()
        loadStreamsJob =
            viewModelScope.launchGuarded("CategoryViewModel.loadStreams") {
                loadStreamsInternal(categoryId, source, isRetryEnabled = true)
            }
    }

    /**
     * Shared implementation for loading streams by category. Handles both initial load
     * and refresh paths.
     * @param isRetryEnabled when true, retries once on empty/failed initial load (loadStreams path)
     */
    private suspend fun loadStreamsInternal(
        categoryId: String,
        requestedSource: Long,
        isRetryEnabled: Boolean,
    ) {
        val ownRepo = awaitRepository()
        val ownSource = ownRepo.getProviderId()
        // A real category plays, lists and records on its own source; a virtual list has none.
        val category = findCategory(categoryId, requestedSource)
        val categorySource =
            when {
                category?.isVirtual == true -> 0L
                category != null -> category.providerId.takeIf { it != 0L } ?: ownSource
                categoryId in VIRTUAL_CATEGORY_IDS -> 0L
                else -> requestedSource.takeIf { it != 0L } ?: ownSource
            }
        val repo = if (categorySource == 0L) ownRepo else repoFor(categorySource)
        currentCategoryId = categoryId
        currentCategorySource = categorySource
        val lastItemId = ownRepo.getLastItemId(contentType)

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
                selectedCategorySource = categorySource,
            )

        fun emitStreams(
            streams: List<MediaItem>,
            payloadSize: String? = null,
        ) {
            // A slower query for a category the user has since navigated away from must not
            // clobber the state of whatever is now selected.
            if (categoryId == currentCategoryId && categorySource == currentCategorySource) {
                // Every row on its source, so its key and per-row state agree (Phase 6).
                currentStreams = streams.onSources(categorySource.takeIf { it != 0L } ?: ownSource)
                _uiState.value =
                    UiState.Success(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = lastItemId,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = payloadSize,
                        selectedCategorySource = categorySource,
                    )
            }
        }

        val handledVirtual =
            when (categoryId) {
                // Every source of the profile's (Phase 4).
                RECENT_CATEGORY_ID -> {
                    emitStreams(inRecentOrder(awaitMerged().refreshRecentItems(contentType)))
                    loadNowPlaying(currentStreams)
                    true
                }

                FAVORITES_CATEGORY_ID -> {
                    emitStreams(awaitMerged().favorites(contentType))
                    loadNowPlaying(currentStreams)
                    true
                }

                FAVORITE_CATEGORIES_ID -> {
                    emitStreams(favoriteCategoryRows(awaitMerged()))
                    true
                }

                RECENTLY_VIEWED_CATEGORIES_ID -> {
                    // Every source's, newest first; each row opens its category on its source. The
                    // id carries the source too, so the same category id on two sources is two rows.
                    val recentCategories = awaitMerged().recentCategories(contentType)
                    emitStreams(
                        recentCategories.map { (source, recent) ->
                            MediaItem(
                                id = RECENT_CATEGORY_ROW_PREFIX + categoryKey(recent.categoryId, source),
                                name = recent.categoryName,
                                mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                                categoryId = RECENTLY_VIEWED_CATEGORIES_ID,
                                target = BrowseTarget.CategoryRef(recent.categoryId),
                                providerId = source,
                            )
                        },
                    )
                    true
                }

                else -> {
                    false
                }
            }

        if (!handledVirtual) {
            val categoryName = category?.name
            if (categoryName != null) {
                repo.addToCategoryHistory(categoryId, categoryName, contentType)
            }

            val result = repo.getItems(categoryId, contentType)

            result.fold(
                onSuccess = { fetched ->
                    val items = withContext(Dispatchers.Default) { fetched.onSources(categorySource) }
                    emitStreams(items, getPayloadSize(categoryId))
                    loadNowPlaying(items)
                    // Retry once if initial load returned empty for a non-virtual category
                    if (isRetryEnabled && isInitialLoad && items.isEmpty() && !initialLoadRetried) {
                        initialLoadRetried = true
                        delay(1500)
                        loadStreamsInternal(categoryId, categorySource, isRetryEnabled = true)
                    }
                    isInitialLoad = false
                },
                onFailure = {
                    emitStreams(emptyList(), getPayloadSize(categoryId))
                    // Retry once on initial load failure after a short delay
                    if (isRetryEnabled && isInitialLoad && !initialLoadRetried) {
                        initialLoadRetried = true
                        delay(2000)
                        loadStreamsInternal(categoryId, categorySource, isRetryEnabled = true)
                    }
                },
            )
        }
    }

    private fun loadNowPlaying(items: List<MediaItem>) {
        if (contentType != ContentType.LIVE_TV) return
        nowPlayingJob?.cancel()
        nowPlayingJob =
            viewModelScope.launchGuarded("CategoryViewModel.loadNowPlaying") {
                val ownSource = awaitRepository().getProviderId()
                // A merged list's rows each from their own source's guide (Phase 4).
                val bySource = items.take(50).groupBy { it.sourceOr(ownSource) }
                // Phase 1: Fast SQLite query for indexed channels. Keyed by the row's sourceKey.
                val indexResult = HashMap<String, EpgProgram>()
                for ((source, rows) in bySource) {
                    for ((id, program) in repoFor(source).getNowPlayingFromIndex(rows)) indexResult[sourceKey(source, id)] = program
                }
                if (indexResult.isNotEmpty()) {
                    _nowPlaying.value = indexResult
                }

                // Phase 2: Xtream API fallback for unmatched items
                // Accumulate across chunks and emit once. Emitting per chunk published a fresh map up
                // to five times per list load (50 items / 10 per request), and every one of those is a
                // new ImmutableNowPlaying that recomposes the whole channel list — interleaved with the
                // network waits between chunks, so the list churned for as long as the fallback ran.
                val collected = mutableMapOf<String, EpgProgram>()
                val now = System.currentTimeMillis() / 1000
                for ((source, rows) in bySource) {
                    val repo = repoFor(source)
                    if (repo.getCapabilities()?.supportsEpg != true) continue
                    for (chunk in rows.filter { sourceKey(source, it.id) !in indexResult }.chunked(10)) {
                        val streamIds = chunk.map { it.id }
                        val epgResult = repo.getEpgBulk(streamIds)?.getOrNull() ?: continue
                        for ((itemId, resp) in epgResult) {
                            val airing = resp.listings.firstOrNull { now in it.startTime..it.endTime }
                            if (airing != null) collected[sourceKey(source, itemId)] = airing
                        }
                    }
                }
                if (collected.isNotEmpty()) {
                    _nowPlaying.value = _nowPlaying.value + collected
                }

                // No catalogue-wide EPG ingest from here: that is EpgSyncWorker's job. Run from a
                // list load, a whole-catalogue fetch competed with video decode in the same
                // process, which stuttered playback and eventually ANR'd.
            }
    }

    /**
     * Archive days of the channels among [items] that keep one, for the list's catch-up badge
     * ([MediaRepository.getCatchupDaysForRows]). Asked by the list itself, so every list of
     * channels (a category, Recent, Favourites, the Live TV panel's tabs) gets it. Empty off Live TV.
     * Keyed by the row's sourceKey.
     */
    suspend fun catchupDays(items: List<MediaItem>): Map<String, Int> {
        val days = HashMap<String, Int>()
        if (contentType == ContentType.LIVE_TV) {
            val ownSource = awaitRepository().getProviderId()
            // A merged list's rows each from their own source (Phase 4).
            for ((source, rows) in items.groupBy { it.sourceOr(ownSource) }) {
                for ((id, archive) in repoFor(source).getCatchupDaysForRows(rows)) days[sourceKey(source, id)] = archive
            }
        }
        return days
    }

    /** Whether [itemId] is a favourite on [rowSource] (a merged list's row; 0: this list's source). */
    fun isFavorite(
        itemId: String,
        contentType: String,
        rowSource: Long = 0L,
    ): Boolean = (mergedSnapshot?.repoFor(rowSource) ?: repositoryOrNull)?.isFavorite(itemId, contentType) ?: false

    /** Whether category [categoryId] of [source] is a favourite (0: this list's source). */
    fun isFavoriteCategory(
        categoryId: String,
        contentType: String,
        source: Long = 0L,
    ): Boolean = (mergedSnapshot?.repoFor(source) ?: repositoryOrNull)?.isFavoriteCategory(categoryId, contentType) ?: false

    /**
     * Refresh pre-computed per-item data (favorites, watch progress) for current streams.
     * Called after streams change or favorites are toggled.
     */
    private suspend fun refreshPerItemData() {
        val ownRepo = awaitRepository()
        val merged = awaitMerged()
        val ownSource = ownRepo.getProviderId()
        val streams = currentStreams
        val ct = contentType
        val cats = categories

        withContext(Dispatchers.Default) {
            val favoriteIds = HashSet<String>()
            val progressMap = HashMap<String, Float>()
            val watched = HashSet<String>()
            // A merged list's rows (Recent, Favourites) each read their own source (Phase 4).
            for ((source, rows) in streams.groupBy { it.sourceOr(ownSource) }) {
                val repo = merged.repoFor(source)
                // Build favorite IDs set using single-lock batch lookups. A Favorite Categories row
                // is starred by its category, not by its own id.
                val favItemIds = repo.getFavoriteItemIds(ct)
                val favCatIds = repo.getFavoriteCategoryIds(ct)
                rows
                    .filter { it.id in favItemIds || (it.target as? BrowseTarget.CategoryRef)?.categoryId in favCatIds }
                    .mapTo(favoriteIds) { sourceKey(source, it.id) }

                val positions = repo.getPlaybackPositions(rows.map { it.id }, ct)
                for ((id, item) in positions) {
                    // Resumable band only, so a card's bar means the same thing everywhere:
                    // barely-started and finished items get no bar rather than a sliver or a full one.
                    item.resumeProgress()?.let { progressMap[sourceKey(source, id)] = it }
                    if (item.isCompleted) watched.add(sourceKey(source, id))
                }
                // Series rows track episodes completed, not minutes: watch history is keyed by
                // episode, so a series id never resolves through the lookup above. A fully watched
                // series drops out of the bar map and gets the check instead, matching movies.
                if (ct == ContentType.TV_SHOWS) {
                    val seriesProgress = repo.getSeriesWatchProgress()
                    for (item in rows) {
                        val fraction = seriesProgress[item.id]
                        if (fraction != null) {
                            if (fraction >= 1f) {
                                watched.add(sourceKey(source, item.id))
                            } else {
                                progressMap[sourceKey(source, item.id)] = fraction
                            }
                        }
                    }
                }
                // TMDB dedup (Phase 5): a different catalogue entry for the same title — a second
                // language track, a 4K re-rip — completed under its own id. Movies only for now; a
                // sibling only ever adds a check, never a resume bar, so it can't undo a progress
                // entry this item already has above.
                if (ct == ContentType.MOVIES) {
                    repo.getSiblingCompletedMovieIds().mapTo(watched) { sourceKey(source, it) }
                }
            }
            _favoriteIds.value = favoriteIds

            // Each category starred by its own source's favourites, by key.
            val favCatIds = merged.inProfileOrder.associate { it.providerId to it.repo.getFavoriteCategoryIds(ct) }
            _favoriteCategoryIds.value =
                cats
                    .filter { !it.isVirtual && it.id in favCatIds[it.providerId.takeIf { source -> source != 0L } ?: ownSource].orEmpty() }
                    .mapTo(HashSet()) { it.key }

            _watchProgress.value = progressMap
            _watchedIds.value = watched
        }
    }

    /** Favourite category or not, on [source] (the category's; 0: this list's source). */
    fun toggleFavoriteCategory(
        categoryId: String,
        categoryName: String,
        contentType: String,
        source: Long = 0L,
    ) {
        val repo = mergedSnapshot?.repoFor(source) ?: repositoryOrNull
        if (repo != null) {
            if (repo.isFavoriteCategory(categoryId, contentType)) {
                repo.removeFavoriteCategory(categoryId, contentType)
            } else {
                repo.addFavoriteCategory(categoryId, categoryName, contentType)
            }
            viewModelScope.launchGuarded("CategoryViewModel.refreshPerItemData") { refreshPerItemData() }
            // Local rebuild only — no network fetch needed for a local favorite change
            refreshCategoriesLocal()
        }
    }

    /** Favourite or not, on [rowSource] (a merged list's row; 0: this list's source). */
    fun toggleFavoriteStream(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        rowSource: Long = 0L,
    ) {
        val repo = mergedSnapshot?.repoFor(rowSource) ?: repositoryOrNull
        val categoryRef = categoryRefRow(itemId, rowSource)
        if (categoryRef != null) {
            // A Favorite or Recent Categories row: its star is its category's, on the row's source,
            // never a stream favourite.
            toggleFavoriteCategory(categoryRef.categoryId, itemName, contentType, rowSource)
        } else if (repo != null) {
            if (repo.isFavorite(itemId, contentType)) {
                repo.removeFavorite(itemId, contentType)
                // The Favourites list loses the row at once, the rest staying where they are.
                if (currentCategoryId == FAVORITES_CATEGORY_ID) {
                    val source = repo.getProviderId()
                    currentStreams = currentStreams.filterNot { it.id == itemId && it.sourceOr(sourceId) == source }
                }
            } else {
                repo.addFavorite(itemId, itemName, categoryId, contentType)
            }
            _favoritesVersion.value++
            viewModelScope.launchGuarded("CategoryViewModel.refreshPerItemData") { refreshPerItemData() }
            // Local rebuild only — no network fetch needed for a local favorite change
            refreshCategoriesLocal()
        }
    }

    /**
     * Removes [item] from the favourites (a stream, or the category of a Favorite Categories row),
     * its row leaving the Favourites list in place, and returns the Undo: the favourite back as it
     * was, and its row back where it was on that list (docs/plans/archive/20261009_tv-recents-favorites-plan.md → A).
     */
    fun removeFavorite(
        item: MediaItem,
        contentType: String,
    ): () -> Unit {
        val index = currentStreams.indexOfFirst { it.id == item.id && it.providerId == item.providerId }
        val listId = currentCategoryId
        // A category row's favourite is its category's, on its source; read from the row itself,
        // which its Undo no longer finds on the list.
        val categoryRef = item.target as? BrowseTarget.CategoryRef
        val toggle = {
            if (categoryRef != null) {
                toggleFavoriteCategory(categoryRef.categoryId, item.name, contentType, item.providerId)
            } else {
                toggleFavoriteStream(item.id, item.name, item.categoryId, contentType, item.providerId)
            }
        }
        if (isFavoriteRow(item, contentType)) toggle()
        return {
            if (!isFavoriteRow(item, contentType)) {
                if (listId == FAVORITES_CATEGORY_ID && currentCategoryId == listId && index >= 0 &&
                    currentStreams.none { it.id == item.id && it.providerId == item.providerId }
                ) {
                    currentStreams = currentStreams.toMutableList().apply { add(index.coerceAtMost(size), item) }
                }
                toggle()
            }
        }
    }

    private fun isFavoriteRow(
        item: MediaItem,
        contentType: String,
    ): Boolean {
        val categoryRef = item.target as? BrowseTarget.CategoryRef
        return if (categoryRef != null) {
            isFavoriteCategory(categoryRef.categoryId, contentType, item.providerId)
        } else {
            isFavorite(item.id, contentType, item.providerId)
        }
    }

    /**
     * The category a Favorite or Recent Categories row [itemId] of [rowSource] stands for; null
     * for any other row. Read from the row itself (the list on screen), not parsed from its id.
     */
    private fun categoryRefRow(
        itemId: String,
        rowSource: Long,
    ): BrowseTarget.CategoryRef? {
        val source = rowSource.takeIf { it != 0L } ?: sourceId
        val isCategoryRow = itemId.startsWith(FAVORITE_CATEGORY_ROW_PREFIX) || itemId.startsWith(RECENT_CATEGORY_ROW_PREFIX)
        val row = if (isCategoryRow) currentStreams.firstOrNull { it.id == itemId && it.sourceOr(sourceId) == source } else null
        return row?.target as? BrowseTarget.CategoryRef
    }

    /**
     * Manual watched/unwatched mark (Phase 6, docs/plans/archive/20260828_watch-state-durable-storage-plan.md), same
     * refresh discipline as [toggleFavoriteStream]: [setWatched][org.njarasoa.fijerena.core.network
     * .MediaRepository.setWatched] then [refreshPerItemData] republishes [watchedIds], costing
     * nothing extra since watch state is looked up separately from the catalogue rather than
     * joined into it — no catalogue re-query, no list invalidation.
     */
    fun toggleWatchedStream(
        itemId: String,
        contentType: String,
        rowSource: Long = 0L,
    ) {
        val nowWatched = sourceKey(rowSource.takeIf { it != 0L } ?: sourceId, itemId) !in _watchedIds.value
        viewModelScope.launchGuarded("CategoryViewModel.toggleWatchedStream") {
            val repo = repoFor(rowSource)
            repo.setWatched(itemId, contentType, nowWatched)
            refreshPerItemData()
        }
    }

    /**
     * Whether rows offer "Mark watched / unwatched" (and a show row "Mark show watched / unwatched"):
     * not where the source keeps watched state on its server.
     */
    val supportsWatchedMark: Boolean
        get() = repositoryOrNull?.supportsMarkWatched ?: false

    /**
     * Whether every episode of [seriesId] is watched — the wording of the show row's mark; null
     * when not known here (or the read failed: the menu then offers "watched").
     */
    suspend fun showWatchState(
        seriesId: String,
        rowSource: Long = 0L,
    ): Boolean? = suspendRunCatching { repoFor(rowSource).episodesWatchState(seriesId, null) }.getOrNull()

    /**
     * Marks every episode of [seriesId] watched or unwatched
     * (docs/plans/archive/20261011_season-show-watched-plan.md Phase 3), then republishes the rows'
     * watched checks and progress as [toggleWatchedStream] does. A failure (a show never opened
     * here, offline) reaches [onError] as a plain-words message.
     */
    fun setShowWatched(
        seriesId: String,
        watched: Boolean,
        rowSource: Long = 0L,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launchGuarded("CategoryViewModel.setShowWatched") {
            repoFor(rowSource)
                .setEpisodesWatched(seriesId, null, watched)
                .onSuccess { refreshPerItemData() }
                .onFailure { e ->
                    onError(
                        context.getString(R.string.watched_mark_many_failed) + "\n" +
                            friendlyErrorMessage(e, context, appSettings.isDevMode),
                    )
                }
        }
    }

    /**
     * Removes an item (or all episodes of a series) from the Recent list.
     * Updates the local UI state streams list if the current category is RECENT_CATEGORY_ID.
     * Returns the Undo, which puts back what was removed (docs/plans/archive/20261009_tv-recents-favorites-plan.md → A).
     */
    fun removeFromRecent(
        itemId: String,
        contentType: String,
        seriesId: String? = null,
        // The row's source (a merged list's row, Phase 4); 0: this list's.
        rowSource: Long = 0L,
    ): () -> Unit {
        var removal: RecentRemoval? = null
        val removing =
            viewModelScope.launchGuarded("CategoryViewModel.removeFromRecent") {
                // The Recent row (unlike category streams) can be visible before loadCategories()
                // has run — e.g. the Live TV preview panel shows it regardless of what was browsed
                // into — so this can race ahead of the repository's assignment.
                // awaitMerged() below waits for it rather than dropping the removal.
                val merged = awaitMerged()
                removal = merged.removeFromRecent(rowSource, itemId, contentType, seriesId)
                showRecentChange(merged)
            }
        return {
            viewModelScope.launchGuarded("CategoryViewModel.undoRemoveFromRecent") {
                removing.join()
                val merged = awaitMerged()
                removal?.let { merged.restoreRecent(it) }
                showRecentChange(merged)
            }
        }
    }

    /**
     * Clear Recent (edit mode): this section's whole Recent list — on every source of the profile —
     * after the screen's one confirmation. Progress and watched marks stay.
     */
    fun clearRecent() {
        viewModelScope.launchGuarded("CategoryViewModel.clearRecent") {
            val merged = awaitMerged()
            merged.clearRecent(contentType)
            showRecentChange(merged)
        }
    }

    /** Recent on screen after a removal, an Undo or a clear: the sources' lists, in place for Live TV. */
    private fun showRecentChange(merged: MergedLists) {
        val currentState = _uiState.value
        if (currentState is UiState.Success && currentState.selectedCategoryId == RECENT_CATEGORY_ID) {
            val updatedStreams = inRecentOrder(merged.publishedRecent(contentType).onSources(sourceId))
            currentStreams = updatedStreams
            _uiState.value =
                currentState.copy(
                    streams = updatedStreams,
                    streamsLoading = false,
                )
        }
    }

    /**
     * Live TV's Recent in the order it had on screen this visit (new rows first); other sections'
     * Recent as the repository orders it.
     */
    private fun inRecentOrder(items: List<MediaItem>): List<MediaItem> =
        if (contentType == ContentType.LIVE_TV) {
            liveRecentOrder = items.stableOrder(liveRecentOrder)
            items.inStableOrder(liveRecentOrder)
        } else {
            items
        }

    /**
     * The Live TV section entered again (back from another section, Home, the TV Guide or Search):
     * Recent on screen reloads in the repository's order, last watched first. Not on returning from
     * the preview layer, which keeps the order ([loadStreams] on Recent). The rows are replaced
     * in place, with no loading state: the screen's own focus hand-back (a header button, a row)
     * has already run on the list it found.
     */
    fun reloadRecentOnEntry() {
        val state = _uiState.value
        val onRecent = state is UiState.Success && state.selectedCategoryId == RECENT_CATEGORY_ID && !state.streamsLoading
        if (contentType == ContentType.LIVE_TV && onRecent) {
            viewModelScope.launchGuarded("CategoryViewModel.reloadRecentOnEntry") {
                val merged = awaitMerged()
                merged.refreshRecentItems(contentType)
                liveRecentOrder = emptyList()
                showRecentChange(merged)
            }
        }
    }

    fun retry() {
        loadCategories()
    }

    /**
     * Refreshes the lastPlayedItemId in the current UI state.
     * Called when returning from the player screen to update focus target.
     */
    fun refreshLastPlayedItem() {
        val repo = repositoryOrNull ?: return
        val current = _uiState.value
        if (current is UiState.Success) {
            val lastItemId = repo.getLastItemId(contentType)
            _uiState.value = current.copy(lastPlayedItemId = lastItemId)
        }
    }

    /**
     * Re-reads favorites/watch progress/watched checks for whatever's currently on screen. Call
     * on resume alongside [refreshLastPlayedItem] — same reason: a favorite or watched mark made
     * on a screen this ViewModel doesn't own (movie details, an episode list, search) writes
     * straight to the repository with no way to reach this instance's cached [watchedIds]/
     * [favoriteIds]/[watchProgress], so without an explicit re-read on return they'd sit stale
     * until something *else* changes the stream list identity and trips [refreshPerItemData]'s
     * own guard.
     */
    fun refreshWatchStateOnResume() {
        // refreshPerItemData no-ops on its own until repository is initialized.
        viewModelScope.launchGuarded("CategoryViewModel.refreshPerItemData") { refreshPerItemData() }
    }

    /** The sources the list holds, in the profile's order; this list's own alone before they are known. */
    private fun browsingSources(): List<MergedLists.Source> =
        mergedSnapshot?.inProfileOrder
            ?: repositoryOrNull?.let { listOf(MergedLists.Source(it.getProviderId(), "", it)) }.orEmpty()

    /**
     * The Favorite Categories list's rows: one browse-only row per favourited category, every
     * source's (each in its own order), on its source. The id carries the source too.
     */
    private fun favoriteCategoryRows(sources: List<MergedLists.Source>): List<MediaItem> =
        sources.flatMap { source ->
            source.repo.getFavoriteCategoriesForContentType(contentType).map { cat ->
                MediaItem(
                    id = FAVORITE_CATEGORY_ROW_PREFIX + categoryKey(cat.id, source.providerId),
                    name = cat.name,
                    mediaType = org.njarasoa.fijerena.core.player.domain.MediaType.VIDEO_FILE,
                    categoryId = FAVORITE_CATEGORIES_ID,
                    target = BrowseTarget.CategoryRef(cat.id),
                    providerId = source.providerId,
                )
            }
        }

    private fun favoriteCategoryRows(merged: MergedLists): List<MediaItem> = favoriteCategoryRows(merged.inProfileOrder)

    private fun rebuildVirtualCategories(
        repo: MediaRepository,
        regularCategories: List<MediaCategory>,
    ): List<MediaCategory> {
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
        val hasFavoriteCategories = browsingSources().any { it.repo.getFavoriteCategoriesForContentType(contentType).isNotEmpty() }
        // Kept while it's the open list even once empty: dropping it left that list selected
        // but unnamed in the header ("Select a category") and its pane showing the old rows.
        if (hasFavoriteCategories || currentCategoryId == FAVORITE_CATEGORIES_ID) {
            virtualCats.add(
                MediaCategory(
                    id = FAVORITE_CATEGORIES_ID,
                    name = context.getString(R.string.category_favorite_categories_label),
                    isVirtual = true,
                ),
            )
        }
        val recentCategories = mergedSnapshot?.recentCategories(contentType) ?: repo.getRecentlyViewedCategories(contentType)
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
        val repo = repositoryOrNull ?: return
        val regularCategories = categories.filter { !it.isVirtual }
        categories = rebuildVirtualCategories(repo, regularCategories)
        // The Favorite Categories list is built from exactly what a category (un)favorite just
        // changed, so its rows have to follow along here — nothing else reloads them.
        if (currentCategoryId == FAVORITE_CATEGORIES_ID) {
            currentStreams = favoriteCategoryRows(browsingSources())
        }
        val lastItemId = repo.getLastItemId(contentType)
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
                selectedCategorySource = currentCategorySource,
            )
    }

    fun refreshCategories() {
        // Immediately rebuild virtual categories from local data so UI updates instantly
        refreshCategoriesLocal()

        // Also refresh from network in the background
        viewModelScope.launchGuarded("CategoryViewModel.refreshCategories") {
            val repo = awaitRepository()
            // A remote M3U loads its playlist once; without this Refresh re-served that copy.
            repo.refreshCatalog()
            val result = mergedCategories(repo)
            result.onSuccess { fetchedCategories ->
                categories = rebuildVirtualCategories(repo, fetchedCategories)
                val freshLastItemId = repo.getLastItemId(contentType)
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
                        selectedCategorySource = currentCategorySource,
                    )
            }
        }
    }

    /** Reloads [categoryId]: the selected category's source when it is the selected one. */
    fun refreshStreams(categoryId: String) {
        // Refresh is the viewer asking for the current truth: Recent re-sorts.
        if (categoryId == RECENT_CATEGORY_ID) liveRecentOrder = emptyList()
        val source = if (categoryId == currentCategoryId) currentCategorySource else 0L
        loadStreamsJob?.cancel()
        nowPlayingJob?.cancel()
        loadStreamsJob =
            viewModelScope.launchGuarded("CategoryViewModel.refreshStreams") {
                (if (source == 0L) awaitRepository() else repoFor(source)).refreshCatalog()
                loadStreamsInternal(categoryId, source, isRetryEnabled = false)
            }
    }

    /**
     * Reloads the shared Recent list — for callers (the Live TV preview panel) that show it
     * independently of whatever category is actually selected, without disturbing that
     * selection or its streams list. The result arrives via [recentItems].
     */
    suspend fun refreshRecentItems() {
        awaitMerged().refreshRecentItems(contentType)
    }

    /**
     * Favorites fetch, bypassing [uiState] the same way — for the same Live TV preview panel,
     * toggling between the two without disturbing the selected/browsed category. Every source's,
     * each row on its source.
     */
    suspend fun getFavoritesSnapshot(): List<MediaItem> = awaitMerged().favorites(contentType).onSources(awaitRepository().getProviderId())

    /**
     * Live TV's last channel watched: each of the profile's sources has its own, the newest of
     * them, on its source (multi-source plan, Phase 5). Null off Live TV or with none.
     */
    suspend fun lastWatchedChannel(): MediaItem? = if (contentType == ContentType.LIVE_TV) awaitMerged().lastWatchedChannel() else null
}

/** Splits into (virtual, regular) categories — single pass instead of two filters. */
fun List<MediaCategory>.partitionVirtual(): Pair<List<MediaCategory>, List<MediaCategory>> =
    partition { it.id in CategoryViewModel.VIRTUAL_CATEGORY_IDS }

/**
 * Each source's categories, tagged with it, one list in the order given (the profile's): Live TV,
 * Movies and TV Shows list every source's categories (multi-source plan, Phase 6).
 */
internal fun mergeCategories(lists: List<Pair<Long, List<MediaCategory>>>): List<MediaCategory> =
    lists.flatMap { (source, categories) -> categories.map { it.copy(providerId = source) } }

/**
 * [this] with every row on its source: a row that leaves it 0 is on [listSource]. A list's keys
 * (`sourceKey`) and its per-row state then agree whichever source a row came from.
 */
internal fun List<MediaItem>.onSources(listSource: Long): List<MediaItem> =
    if (none { it.providerId == 0L }) this else map { if (it.providerId == 0L) it.copy(providerId = listSource) else it }
