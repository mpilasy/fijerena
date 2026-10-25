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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.RecentRemoval
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.resumeProgress
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.network.wanted.GoneFavorite
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.categoryKey
import org.njarasoa.fijerena.core.player.domain.sourceKey
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.wanted.WantedCard
import org.njarasoa.fijerena.core.ui.wanted.cards
import org.njarasoa.fijerena.core.ui.wanted.gone
import org.njarasoa.fijerena.core.ui.wanted.statusLabel
import java.net.SocketTimeoutException

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

        /** The section's Wanted list (wanted titles plan, Phase 5): films under Movies, shows under TV Shows. */
        const val WANTED_CATEGORY_ID = "wanted"

        /** Id prefix of the Wanted list's rows, each a [WantedCard] by its key: no source item. */
        private const val WANTED_ROW_PREFIX = "wanted_row_"
        private const val TAG = "CategoryViewModel"

        // How long one source may take to list its categories before the list goes on without it.
        private const val SOURCE_LOAD_TIMEOUT_MS = 15_000L

        val VIRTUAL_CATEGORY_IDS =
            setOf(
                FAVORITES_CATEGORY_ID,
                FAVORITE_CATEGORIES_ID,
                RECENT_CATEGORY_ID,
                RECENTLY_VIEWED_CATEGORIES_ID,
                WANTED_CATEGORY_ID,
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
         * The rows of Recent or Favourites for [contentType], every source's of [lists] merged, or
         * null when [categoryId] is a real category the source knows. Shared with the TV Guide,
         * which otherwise sends the virtual id to the source and gets the catalogue's first page back.
         */
        suspend fun virtualCategoryItems(
            lists: MergedLists,
            categoryId: String,
            contentType: String,
        ): List<MediaItem>? =
            when (categoryId) {
                RECENT_CATEGORY_ID -> lists.refreshRecentItems(contentType)
                FAVORITES_CATEGORY_ID -> lists.favorites(contentType)
                else -> null
            }

        /** Whether [categoryId] is Recent or Favourites: a list merged across the profile's sources. */
        fun isMergedList(categoryId: String?): Boolean = categoryId == RECENT_CATEGORY_ID || categoryId == FAVORITES_CATEGORY_ID

        /**
         * Whether [categoryId]'s rows come from every source of the profile, so each carries its
         * source's badge: Recent, Favourites, Favourite Categories and Recent Categories.
         */
        fun mixesSources(categoryId: String?): Boolean =
            isMergedList(categoryId) || categoryId == FAVORITE_CATEGORIES_ID || categoryId == RECENTLY_VIEWED_CATEGORIES_ID

        /**
         * The lists a TV Guide of [categoryId] reads around [repo], the route's source: for Recent
         * or Favourites every Xtream source of the profile's, as the lists show them; a category
         * stays on [repo]'s own source.
         */
        suspend fun guideLists(
            container: AppContainer,
            repo: MediaRepository,
            categoryId: String,
        ): MergedLists =
            if (isMergedList(categoryId)) {
                MergedLists.withOthers(container, repo)
            } else {
                MergedLists(listOf(MergedLists.Source(repo.getProviderId(), "", repo)))
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
            // [lastPlayedItemId]'s source: the item played last across the list's sources (Phase 6).
            val lastPlayedSource: Long = 0L,
            val categoriesPayloadSize: String? = null,
            val streamsPayloadSize: String? = null,
            // The selected category's source; 0 for a virtual one (Recent, Favourites…).
            val selectedCategorySource: Long = 0L,
        ) : UiState() {
            /** The selection as a [MediaCategory.key]: what the category lists compare and focus by. */
            val selectedCategoryKey: String? get() = selectedCategoryId?.let { categoryKey(it, selectedCategorySource) }

            /** The item played last as a row key ([sourceKey]): what the lists mark as current. */
            val lastPlayedKey: String? get() = lastPlayedItemId?.let { sourceKey(lastPlayedSource, it) }

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

    /** Each listed category's name by its key ([MediaCategory.key]): the second line of a Recent or Favourites row names it. */
    val categoryNames: StateFlow<Map<String, String>> =
        uiState
            .map { state -> (state as? UiState.Success)?.categories.orEmpty() }
            // Rebuilt only when the categories themselves change (a new list, not every state
            // change: a selection, a load, the last played), and off the main thread: thousands
            // across a profile's sources.
            .distinctUntilChanged { old, new -> old === new }
            .map { categories -> categories.associate { it.key to it.name } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // The section's Wanted list: films under Movies, shows under TV Shows, none elsewhere (and none
    // on a child's profile, whose state is empty).
    private val wanted = WantedTitles.getInstance(context)
    private val wantedKind = contentType.takeIf { it == ContentType.MOVIES || it == ContentType.TV_SHOWS }

    /**
     * A short state per row (by [sourceKey]), shown first on its second line: Available or Not
     * available on a Wanted row, Gone from source on a favourite its source no longer lists.
     */
    val rowStatus: StateFlow<Map<String, String>> =
        combine(uiState, wanted.state) { state, wantedState -> rowStatusOf(state, wantedState) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun rowStatusOf(
        state: UiState,
        wantedState: WantedTitles.State,
    ): Map<String, String> {
        val success = state as? UiState.Success
        return when {
            success == null -> {
                emptyMap()
            }

            success.selectedCategoryId == WANTED_CATEGORY_ID -> {
                val cards = wantedState.cards(wantedKind).associateBy { WANTED_ROW_PREFIX + it.key }
                success.streams
                    .orEmpty()
                    .mapNotNull { row ->
                        cards[row.id]?.let { row.sourceKey to it.statusLabel(context.resources) }
                    }.toMap()
            }

            wantedState.goneFavorites.isEmpty() -> {
                emptyMap()
            }

            else -> {
                val gone = context.getString(R.string.favorite_gone_from_source)
                success.streams
                    .orEmpty()
                    .filter { wantedState.gone(it.providerId, it.id, contentType) != null }
                    .associate { it.sourceKey to gone }
            }
        }
    }

    /** The Wanted card a row of the Wanted list stands for; null for any other row. */
    fun wantedCardOf(item: MediaItem): WantedCard? =
        if (item.id.startsWith(WANTED_ROW_PREFIX)) {
            wanted.state.value
                .cards(wantedKind)
                .firstOrNull { WANTED_ROW_PREFIX + it.key == item.id }
        } else {
            null
        }

    /** [card] is opening: an available title's "new" badge goes. */
    fun openingWanted(card: WantedCard) {
        if (card.isNew) viewModelScope.launchGuarded("CategoryViewModel.wantedSeen") { wanted.markSeen(card.wantedKey) }
    }

    /** Removes [card] from the Wanted list; returns its Undo, which adds it back as it was. */
    fun removeWanted(card: WantedCard): () -> Unit {
        val removing = viewModelScope.launchGuarded("CategoryViewModel.removeWanted") { wanted.remove(card.wantedKey) }
        return {
            viewModelScope.launchGuarded("CategoryViewModel.undoRemoveWanted") {
                removing.join()
                wanted.add(card.entity)
            }
        }
    }

    /** [item]'s favourite when its source no longer lists it (wanted titles plan → Favourites gone from their source). */
    fun goneOf(item: MediaItem): GoneFavorite? = wanted.state.value.gone(item.sourceOr(sourceId), item.id, contentType)

    /**
     * The user's move of a gone favourite to [gone]'s other copy: the copy becomes the favourite,
     * then the old one goes (both sync). Never done by the app itself (CONTRIBUTING.md rule 12).
     */
    fun moveToCopy(
        item: MediaItem,
        gone: GoneFavorite,
    ) {
        val copy = gone.copy ?: return
        viewModelScope.launchGuarded("CategoryViewModel.moveToCopy") {
            val merged = awaitMerged()
            merged.repoFor(copy.providerId).addFavoriteSuspend(copy.itemId, copy.name, copy.categoryId, contentType)
            merged.repoFor(gone.providerId).removeFavoriteSuspend(item.id, contentType)
            wanted.refresh()
            currentCategoryId?.let { loadStreams(it, currentCategorySource) }
        }
    }

    private fun wantedRows(): List<MediaItem> =
        wanted.state.value.cards(wantedKind).map { card ->
            MediaItem(
                id = WANTED_ROW_PREFIX + card.key,
                name = card.entity.title,
                mediaType = if (card.entity.contentType == ContentType.TV_SHOWS) MediaType.SERIES else MediaType.MOVIE,
                categoryId = WANTED_CATEGORY_ID,
                thumbnailUrl = card.posterUrl,
                // A title not out yet already shows its date ("Coming 17 Feb 2028"): no year after it.
                metadata = MediaMetadata(year = card.entity.year.takeIf { card.isAvailable || card.release().date == null }),
            )
        }

    /** This list's own source (a section's: the profile's first Xtream source) once the repository is built. */
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
    // Replaced, already completed, when the list is built again on other sources ([followProfileSources]).
    @Volatile
    private var repositoryDeferred = CompletableDeferred<MediaRepository>()

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
    @Volatile
    private var mergedDeferred = CompletableDeferred<MergedLists>()

    // The profile's Xtream sources the list was last built on (null before the first build): a
    // change to them builds it again ([followProfileSources]).
    private val builtOn = MutableStateFlow<List<Long>?>(null)

    @Volatile
    private var mergedSnapshot: MergedLists? = null

    private suspend fun awaitMerged(): MergedLists = mergedDeferred.await()

    /** [rowSource]'s repository (a merged list's row), this list's own for 0. */
    private suspend fun repoFor(rowSource: Long): MediaRepository = awaitMerged().repoFor(rowSource)

    private var categories: List<MediaCategory> = emptyList()

    // The item played last across the list's sources, (source, id): the rows' current marker
    // (Phase 6). Read with each load ([readLastPlayed]); the synchronous rebuilds reuse it.
    @Volatile
    private var lastPlayed: Pair<Long, String>? = null
    private var currentStreams: List<MediaItem> = emptyList()
    private var currentCategoryId: String? = null

    // The selected category's source (0 for a virtual list): its rows, guide and Refresh are its.
    private var currentCategorySource: Long = 0L

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
        viewModelScope.launchGuarded("CategoryViewModel.followProfileSources") { followProfileSources() }
        // The Wanted entry and the Favourites entry's gone count follow the Wanted state, and an
        // open Wanted list follows it too.
        viewModelScope.launchGuarded("CategoryViewModel.followWanted") {
            wanted.state.drop(1).collect { refreshCategoriesLocal() }
        }
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
            builtOn.value = MergedLists.xtreamSourceIds(container)
            // A section's list is built around the profile's first Xtream source; with none there
            // is nothing to list (the sections aren't offered then).
            val ownId = MergedLists.browsingProviderId(container, providerId)
            if (ownId == null) {
                _uiState.value = UiState.Error(context.getString(R.string.home_no_sources))
                return@launchGuarded
            }
            // A section waits for no source's session: each connects when its categories are read,
            // so an offline first source doesn't hold the others up.
            val repo = if (providerId == 0L) container.getStoredDataRepository(ownId) else container.getMediaRepository(ownId)
            repositorySnapshot = repo
            repositoryDeferred = repositoryDeferred.completedWith(repo)
            val merged = MergedLists.withOthers(container, repo)
            mergedSnapshot = merged
            mergedDeferred = mergedDeferred.completedWith(merged)
            _sourceNames.value = merged.sourceNames
            val ownSource = repo.getProviderId()
            launch { merged.recentItems(contentType).collect { _recentItems.value = it?.onSources(ownSource) } }
            loadCategoriesInternal()
            // Entering on a real category (from the EPG, search, or a saved selection) never
            // loads the Recent row, but the Live TV preview panel shows that list regardless of
            // what was browsed into — without this warm-up it would sit on its spinner forever.
            merged.loadRecentItems(contentType)
        }

    /**
     * The profile's sources as they change (one turned on or off in its Sources, added or deleted):
     * the list is built again on them — categories, Recent, Favourites, their badges — keeping the
     * open list while it is still there. Nothing happens while they stay the same.
     */
    private suspend fun followProfileSources() {
        val container = AppContainer.getInstance(context)
        MergedLists.xtreamSourceIdChanges(container, appSettings.activeProfileId).collect { ids ->
            if (ids != builtOn.filterNotNull().first()) {
                repositoryJob?.cancel()
                repositoryJob = startRepository()
            }
        }
    }

    // [this] completed with [value], or a new deferred completed with it when [this] already was
    // (the list built again).
    private fun <T> CompletableDeferred<T>.completedWith(value: T): CompletableDeferred<T> =
        if (complete(value)) this else CompletableDeferred(value)

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

        val merged = awaitMerged()
        val result = mergedCategories(repo)
        // Every source of the list is Xtream, or the one Browse source: any with its own guide.
        _supportsNativeEpg.value = merged.inProfileOrder.any { it.repo.getCapabilities()?.supportsEpg == true }
        readLastPlayed()

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
                // Never raw exception text (a Room/HTTP internal, "executor rejected"):
                // friendlyErrorMessage maps it, with the raw text appended only in dev mode.
                _uiState.value = UiState.Error(sectionErrorMessage(error))
            },
        )
    }

    /**
     * Every source's categories (each its own filters and hidden categories), one list: by source in
     * the profile's order, then each source's own order, each tagged with its source (Phase 6). A
     * source that can't connect or read its categories is left out; the list fails only when none
     * of its sources loads ([loadedCategories]).
     */
    private suspend fun mergedCategories(repo: MediaRepository): kotlin.Result<List<MediaCategory>> {
        val reads =
            coroutineScope {
                awaitMerged()
                    .inProfileOrder
                    .map { source ->
                        async {
                            val read = suspendRunCatching { sourceCategories(source.repo) }
                            read.onFailure { android.util.Log.w(TAG, "Source ${source.providerId} left out of the categories", it) }
                            SourceRead(source.providerId, source.name, read)
                        }
                    }.awaitAll()
                    .ifEmpty { listOf(SourceRead(repo.getProviderId(), "", suspendRunCatching { sourceCategories(repo) })) }
            }
        return withContext(Dispatchers.Default) { loadedCategories(reads) }
    }

    /**
     * [repo]'s categories of this list's type. Not logged in yet, the ones it has saved, its login
     * left to run behind them — a server that doesn't answer no longer holds the list; with none
     * saved, after its login. A source that takes longer than [SOURCE_LOAD_TIMEOUT_MS] fails.
     */
    private suspend fun sourceCategories(repo: MediaRepository): List<MediaCategory> =
        withTimeoutOrNull(SOURCE_LOAD_TIMEOUT_MS) {
            if (!repo.isConnected()) {
                val saved = repo.getFilteredCategories(contentType).getOrNull()
                if (!saved.isNullOrEmpty()) {
                    viewModelScope.launchGuarded("CategoryViewModel.connectBehind") { repo.connect() }
                    return@withTimeoutOrNull saved
                }
                repo.connect().exceptionOrNull()?.let { throw SourceConnectException(it) }
            }
            repo.getFilteredCategories(contentType).getOrThrow()
        } ?: throw SourceConnectException(SocketTimeoutException("No answer in ${SOURCE_LOAD_TIMEOUT_MS / 1000} s"))

    /**
     * The message when no source of the list loads: the first failure's, friendly (a connection
     * failure says so). In developer mode with several sources, each source's own, raw detail
     * included (the dev-mode raw error pattern), one per line.
     */
    private fun sectionErrorMessage(error: Throwable): String {
        val devMode = appSettings.isDevMode

        fun messageOf(e: Throwable): String =
            if (e is SourceConnectException) {
                context.getString(R.string.category_error_connection_failed_format, friendlyErrorMessage(e.cause ?: e, context, devMode))
            } else {
                friendlyErrorMessage(e, context, devMode)
            }
        val failures = (error as? SectionLoadException)?.failures.orEmpty()
        return when {
            failures.isEmpty() -> messageOf(error)
            devMode && failures.size > 1 -> failures.joinToString("\n") { (name, e) -> "$name: ${messageOf(e)}" }
            else -> messageOf(failures.first().second)
        }
    }

    private fun buildAndShowCategories(
        repo: MediaRepository,
        fetchedCategories: List<MediaCategory>,
    ) {
        categories = rebuildVirtualCategories(repo, fetchedCategories)

        val last = lastPlayed
        _uiState.value =
            UiState.Success(
                categories = categories,
                selectedCategoryId = null,
                streams = null,
                streamsLoading = false,
                categoriesRefreshing = false,
                lastPlayedItemId = last?.second,
                lastPlayedSource = last?.first ?: 0L,
                categoriesPayloadSize = getCategoriesPayloadSize(),
                streamsPayloadSize = null,
            )

        if (categories.isNotEmpty()) {
            // The open list when the list is built again (its sources changed) and it is still
            // there; else the requested category on the source the list was opened on (route),
            // else on any.
            val requested =
                currentCategoryId?.let { findCategory(it, currentCategorySource) }
                    ?: canonicalCategoryId(initialCategoryId)?.let { findCategory(it, repo.getProviderId()) }
            // Every content type lands on Recent otherwise: the history, newest first — for Live TV
            // the last played channels.
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
        // Read before this load claims the selection below: a load started meanwhile must find it set.
        val last = readLastPlayed()
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

        _uiState.value =
            UiState.Success(
                categories = categories,
                selectedCategoryId = categoryId,
                streams = null,
                streamsLoading = true,
                categoriesRefreshing = false,
                lastPlayedItemId = last?.second,
                lastPlayedSource = last?.first ?: 0L,
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
                        lastPlayedItemId = last?.second,
                        lastPlayedSource = last?.first ?: 0L,
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
                    emitStreams(awaitMerged().refreshRecentItems(contentType))
                    loadNowPlaying(currentStreams)
                    true
                }

                FAVORITES_CATEGORY_ID -> {
                    emitStreams(awaitMerged().favorites(contentType))
                    loadNowPlaying(currentStreams)
                    true
                }

                WANTED_CATEGORY_ID -> {
                    emitStreams(wantedRows())
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
     * was, and its row back where it was on that list (the tv recents favorites plan → A).
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
     * Manual watched/unwatched mark (Phase 6, the watch state durable storage plan), same
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
     * (the season show watched plan Phase 3), then republishes the rows'
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
     * Returns the Undo, which puts back what was removed (the tv recents favorites plan → A).
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

    /** Recent on screen after a removal, an Undo or a clear: the sources' lists, newest first. */
    private fun showRecentChange(merged: MergedLists) {
        val currentState = _uiState.value
        if (currentState is UiState.Success && currentState.selectedCategoryId == RECENT_CATEGORY_ID) {
            val updatedStreams = merged.publishedRecent(contentType).onSources(sourceId)
            currentStreams = updatedStreams
            _uiState.value =
                currentState.copy(
                    streams = updatedStreams,
                    streamsLoading = false,
                )
        }
    }

    /**
     * The Live TV section entered again (back from another section, Home, the TV Guide or Search):
     * Recent on screen reloads, last watched first (closing the preview layer reloads it through
     * [loadStreams] on Recent). The rows are replaced
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
        if (!mergedDeferred.isCompleted) return
        viewModelScope.launchGuarded("CategoryViewModel.refreshLastPlayedItem") {
            val last = readLastPlayed()
            val current = _uiState.value
            if (current is UiState.Success) {
                _uiState.value = current.copy(lastPlayedItemId = last?.second, lastPlayedSource = last?.first ?: 0L)
            }
        }
    }

    /** The item played last across the list's sources, read again ([lastPlayed]). */
    private suspend fun readLastPlayed(): Pair<Long, String>? = awaitMerged().lastPlayed(contentType).also { lastPlayed = it }

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
        // With the count of favourites their source no longer lists, when there are some.
        val goneCount =
            wanted.state.value.goneFavorites
                .count { it.contentType == contentType }
        val favoritesName = context.getString(R.string.settings_import_favorites_label)
        virtualCats.add(
            MediaCategory(
                id = FAVORITES_CATEGORY_ID,
                name =
                    if (goneCount >
                        0
                    ) {
                        context.getString(R.string.favorite_gone_count_format, favoritesName, goneCount)
                    } else {
                        favoritesName
                    },
                isVirtual = true,
            ),
        )
        // Never empty (wanted titles plan, W-D21); kept while it is the open list, as Favorite Categories.
        val wantedCards = wanted.state.value.cards(wantedKind)
        if (wantedKind != null && (wantedCards.isNotEmpty() || currentCategoryId == WANTED_CATEGORY_ID)) {
            val newCount = wantedCards.count { it.isNew }
            val title = context.getString(R.string.wanted_title)
            virtualCats.add(
                MediaCategory(
                    id = WANTED_CATEGORY_ID,
                    name = if (newCount > 0) context.getString(R.string.wanted_title_new_format, title, newCount) else title,
                    isVirtual = true,
                ),
            )
        }
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
        if (currentCategoryId == WANTED_CATEGORY_ID) currentStreams = wantedRows()
        val last = lastPlayed
        _uiState.value =
            UiState.Success(
                categories = categories,
                selectedCategoryId = currentCategoryId,
                streams = currentStreams,
                streamsLoading = false,
                categoriesRefreshing = false,
                lastPlayedItemId = last?.second,
                lastPlayedSource = last?.first ?: 0L,
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
                val last = readLastPlayed()
                _uiState.value =
                    UiState.Success(
                        categories = categories,
                        selectedCategoryId = currentCategoryId,
                        streams = currentStreams,
                        streamsLoading = false,
                        categoriesRefreshing = false,
                        lastPlayedItemId = last?.second,
                        lastPlayedSource = last?.first ?: 0L,
                        categoriesPayloadSize = getCategoriesPayloadSize(),
                        streamsPayloadSize = getPayloadSize(currentCategoryId ?: ""),
                        selectedCategorySource = currentCategorySource,
                    )
            }
        }
    }

    /** Reloads [categoryId]: the selected category's source when it is the selected one. */
    fun refreshStreams(categoryId: String) {
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

/** A source of a list that couldn't connect: its message says so ([CategoryViewModel]). */
internal class SourceConnectException(
    cause: Throwable,
) : Exception(cause.message, cause)

/** No source of a list loaded: each one's name and failure. */
internal class SectionLoadException(
    val failures: List<Pair<String, Throwable>>,
) : Exception(failures.firstOrNull()?.second?.message, failures.firstOrNull()?.second)

/** One source's categories read for a merged list ([loadedCategories]). */
internal class SourceRead(
    val providerId: Long,
    val name: String,
    val categories: kotlin.Result<List<MediaCategory>>,
)

/**
 * The list a section shows (multi-source plan, Phase 6): the categories of every source that
 * loaded, merged ([mergeCategories]); a source that didn't is left out. A failure only when none
 * loaded, carrying each one's ([SectionLoadException]).
 */
internal fun loadedCategories(reads: List<SourceRead>): kotlin.Result<List<MediaCategory>> {
    val loaded = reads.filter { it.categories.isSuccess }
    return if (loaded.isEmpty() && reads.isNotEmpty()) {
        kotlin.Result.failure(SectionLoadException(reads.map { it.name to it.categories.exceptionOrNull()!! }))
    } else {
        kotlin.Result.success(mergeCategories(loaded.map { it.providerId to it.categories.getOrThrow() }))
    }
}
