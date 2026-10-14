package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.ParsedQuery
import org.njarasoa.fijerena.core.network.SearchUtils
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import java.util.concurrent.ConcurrentHashMap

class SearchViewModel(
    private val context: android.content.Context,
    private val contentType: String,
) : ViewModel() {
    /**
     * A source this search covers. [name] is its source badge on the results, null when it is the
     * only source searched (no badge).
     */
    private class SearchSource(
        val providerId: Long,
        val name: String?,
    )

    @Volatile private var sources: List<SearchSource>? = null

    // Each searched source's repository, once built (a result's Favourite and Mark watched use its own).
    private val repositories = ConcurrentHashMap<Long, MediaRepository>()

    /**
     * The sources searched (docs/plans/20261012_multi-source-search-history-plan.md Phase 3): every
     * Xtream source of the profile in use, each in its own cached catalogue. A profile with no
     * Xtream source searches its other sources (Jellyfin, M3U…) instead, each its own way, as the
     * one in use was before Phase 6; one with no source searches nothing.
     */
    private suspend fun ensureSources(): List<SearchSource> =
        sources ?: run {
            val profileSources = AppContainer.getInstance(context).providerRepository.profileSourcesList()
            val searched = profileSources.filter { it.type == XTREAM_TYPE }.ifEmpty { profileSources }
            val named = searched.size > 1
            searched
                .map { SearchSource(it.id, it.name.takeIf { named }) }
                .also { sources = it }
        }

    private suspend fun repoFor(providerId: Long): MediaRepository =
        repositories[providerId] ?: AppContainer.getInstance(context).getMediaRepository(providerId).also { repositories[providerId] = it }

    sealed class UiState {
        data class Loading(
            val message: String? = null,
        ) : UiState()

        data class Success(
            val categoryResults: List<CategorySearchResult> = emptyList(),
            val allResults: List<SearchResult>,
            val filteredResults: List<SearchResult>,
            val query: String,
            /** Per content type, matches hidden because their category is excluded by the provider's category filters. */
            val excludedCountByType: Map<String, Int> = emptyMap(),
            val isSearching: Boolean = false,
            val searchProgress: String? = null,
            val searchDataSize: String? = null,
            val totalDuration: String? = null,
            val networkWallDuration: String? = null,
            val networkAccumDuration: String? = null,
            val networkCalls: Int = 0,
            val failedCalls: Int = 0,
            val firstError: String? = null,
            /** One raw "[dev] <source>: <error>" line per source left out because its search failed (developer mode shows them). */
            val sourceErrors: List<String> = emptyList(),
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    /**
     * One title on one source (Q1: the same title on two sources is two results). [sourceName] is
     * its source badge, null when the search covers a single source.
     */
    data class SearchResult(
        val itemId: String,
        val streamName: String,
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val thumbnailUrl: String? = null,
        val mediaType: org.njarasoa.fijerena.core.player.domain.MediaType? = null,
        val providerId: Long = 0L,
        val sourceName: String? = null,
    ) {
        /** A whole show (its id is the series id), not an episode. */
        val isShow: Boolean
            get() = contentType == ContentType.TV_SHOWS && mediaType != org.njarasoa.fijerena.core.player.domain.MediaType.EPISODE

        /** Its list key, unique across sources (the same id can be on two). */
        val listKey: String
            get() = "${providerId}_${contentType}_${categoryId}_$itemId"
    }

    /** A matching category on one source; [sourceName] as on [SearchResult]. */
    data class CategorySearchResult(
        val categoryId: String,
        val categoryName: String,
        val contentType: String,
        val providerId: Long = 0L,
        val sourceName: String? = null,
    ) {
        /** Its list key, unique across sources. */
        val listKey: String
            get() = "${providerId}_${contentType}_$categoryId"
    }

    private data class SearchableCategory(
        val category: org.njarasoa.fijerena.core.player.domain.MediaCategory,
        val contentType: String,
    )

    /** One source's part of a search. */
    private class SourceResults(
        val categories: List<CategorySearchResult>,
        val results: List<SearchResult>,
        val excludedCountByType: Map<String, Int>,
        val searchedOnServer: Boolean,
    )

    /** A source's session couldn't be restored ("session expired" when it is the only one searched). */
    private class SessionExpiredException(
        cause: Throwable?,
    ) : Exception(cause?.message, cause)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val appSettings =
        org.njarasoa.fijerena.core.network
            .AppSettings(context)

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // A source of the profile has live channels, so it has a guide to search: the Search screen
    // offers its Guide tab (search guide tab plan, O1).
    private val _hasGuide = MutableStateFlow(false)
    val hasGuide: StateFlow<Boolean> = _hasGuide.asStateFlow()

    private var searchJob: Job? = null

    // History entry added by the current as-you-type session; replaced on the next keystroke.
    private var typedHistoryEntry: String? = null

    companion object {
        private const val PARALLEL_BATCH_SIZE = 20
        private const val TARGET_RESULTS = 200
        private const val XTREAM_TYPE = "XTREAM"
        const val CONTENT_TYPE_ALL = "ALL"
    }

    // Pre-fetched categories per source (loaded in background on init)
    private val prefetchedCategories = ConcurrentHashMap<Long, List<SearchableCategory>>()

    init {
        _uiState.value =
            UiState.Success(
                allResults = emptyList(),
                filteredResults = emptyList(),
                query = "",
            )
        _searchHistory.value = appSettings.getSearchHistory()
        prefetchAll()
        followHasGuide()
        // The profile's sources changed (one turned on or off, added or deleted): the search covers
        // them as they now are, and the results on screen are searched again.
        viewModelScope.launchGuarded("SearchViewModel.followProfileSources") {
            AppContainer
                .getInstance(context)
                .providerRepository
                .profileSourceSet(appSettings.activeProfileId)
                .drop(1)
                .collect {
                    sources = null
                    prefetchedCategories.clear()
                    prefetchAll()
                    val query = (_uiState.value as? UiState.Success)?.query.orEmpty()
                    if (query.length >= 2) {
                        searchJob?.cancel()
                        searchJob = viewModelScope.launchGuarded("SearchViewModel.searchAgain", Dispatchers.IO) { doSearch(query) }
                    }
                }
        }
    }

    // Follows every write to the profile's sources, not just a change of which they are: a local
    // source's playlist added in Settings gives it live channels without changing the set.
    private fun followHasGuide() {
        viewModelScope.launchGuarded("SearchViewModel.hasGuide") {
            AppContainer
                .getInstance(context)
                .providerRepository
                .profileSources(appSettings.activeProfileId)
                .map { sources -> sources.any { MediaProviderFactory.hasLiveTv(it) } }
                .collect { _hasGuide.value = it }
        }
    }

    // Pre-fetch each source's category list + all missing/stale category items in background.
    private fun prefetchAll() {
        viewModelScope.launchGuarded("SearchViewModel.prefetch", Dispatchers.IO) {
            val searchSources =
                try {
                    ensureSources()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@launchGuarded
                }
            searchSources.forEach { source ->
                launch {
                    try {
                        prefetch(source)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("SearchViewModel", "Prefetch failed for source ${source.providerId}", e)
                    }
                }
            }
        }
    }

    private fun targetContentTypes(repo: MediaRepository): List<String> =
        if (contentType == CONTENT_TYPE_ALL) {
            repo.getCapabilities()?.supportedContentTypes?.toList() ?: emptyList()
        } else {
            listOf(contentType)
        }

    private suspend fun prefetch(source: SearchSource) {
        coroutineScope {
            val repo = repoFor(source.providerId)

            if (!repo.isConnected()) {
                repo.connect()
            }

            val capabilities = repo.getCapabilities()
            val allCategories = mutableListOf<SearchableCategory>()
            val semaphore = Semaphore(PARALLEL_BATCH_SIZE)

            targetContentTypes(repo).forEach { type ->
                val result = repo.getFilteredCategories(type)
                result.onSuccess { categories ->
                    val realCategories = categories.filter { !it.isVirtual }
                    realCategories.forEach { cat ->
                        allCategories.add(SearchableCategory(cat, type))
                    }

                    // Launch prefetch for this batch only if search is not supported natively via database/server
                    if (capabilities?.supportsSearch != true) {
                        realCategories.map { category ->
                            launch {
                                if (!repo.getItemsIfCached(category.id, type).isNullOrEmpty()) return@launch
                                semaphore.withPermit {
                                    try {
                                        repo.getItemsForSearch(category.id, type)
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        android.util.Log.e("SearchViewModel", "Failed to get items for search category ${category.id}", e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            prefetchedCategories[source.providerId] = allCategories
        }
    }

    /** [asYouType]: a debounced search while typing; keeps one history entry per typing session. */
    fun performSearch(
        query: String,
        asYouType: Boolean = false,
    ) {
        if (query.isBlank() || query.length < 2) return
        recordHistory(query, asYouType)
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch(Dispatchers.IO) {
                doSearch(query)
            }
    }

    private fun recordHistory(
        query: String,
        asYouType: Boolean,
    ) {
        val term = query.trim()
        val previous = typedHistoryEntry
        if (previous != null && !previous.equals(term, ignoreCase = true)) {
            appSettings.removeSearchHistory(previous)
        }
        typedHistoryEntry =
            when {
                !asYouType -> null
                previous.equals(term, ignoreCase = true) -> previous
                appSettings.getSearchHistory().any { it.equals(term, ignoreCase = true) } -> null
                else -> term
            }
        appSettings.addSearchHistory(term)
        _searchHistory.value = appSettings.getSearchHistory()
    }

    /** Keeps the typed term in the history (the user opened one of its results). */
    fun keepTypedSearch() {
        typedHistoryEntry = null
    }

    fun clearSearch() {
        typedHistoryEntry = null
        searchJob?.cancel()
        _uiState.value =
            UiState.Success(
                query = "",
                categoryResults = emptyList(),
                allResults = emptyList(),
                filteredResults = emptyList(),
                isSearching = false,
                searchProgress = null,
                searchDataSize = null,
                networkCalls = 0,
                failedCalls = 0,
                totalDuration = "0ms",
                networkWallDuration = "0ms",
                networkAccumDuration = "0ms",
                firstError = null,
            )
    }

    fun removeSearchHistoryEntry(query: String) {
        appSettings.removeSearchHistory(query)
        _searchHistory.value = appSettings.getSearchHistory()
    }

    fun clearSearchHistory() {
        appSettings.clearSearchHistory()
        _searchHistory.value = emptyList()
    }

    private fun formatSeconds(ms: Long): String = String.format(java.util.Locale.US, "%.1fs", ms / 1000.0)

    /**
     * Searches every source in parallel and merges their results, ranked together as before. A
     * source whose search fails is left out; when every one fails, the screen shows the error.
     */
    private suspend fun doSearch(query: String) {
        try {
            val startTime = System.currentTimeMillis()
            val searchSources = ensureSources()

            _uiState.value = UiState.Loading(context.getString(org.njarasoa.fijerena.core.ui.R.string.search_searching))

            val normalizedQuery = query.trim().lowercase()
            val parsedQuery = SearchUtils.parseQuery(normalizedQuery)
            val outcomes =
                coroutineScope {
                    searchSources
                        .map { source ->
                            async { source to suspendRunCatching { searchSource(source, query, normalizedQuery, parsedQuery) } }
                        }.awaitAll()
                }
            val searched = outcomes.mapNotNull { (_, outcome) -> outcome.getOrNull() }
            val failures = outcomes.mapNotNull { (source, outcome) -> outcome.exceptionOrNull()?.let { source to it } }
            failures.forEach { (source, e) -> android.util.Log.w("SearchViewModel", "Search failed on source ${source.providerId}", e) }

            _uiState.value =
                if (searched.isEmpty() && failures.isNotEmpty()) {
                    val e = failures.first().second
                    UiState.Error(
                        if (e is SessionExpiredException) {
                            context.getString(R.string.search_error_session_expired)
                        } else {
                            friendlyErrorMessage(e, context, appSettings.isDevMode)
                        },
                    )
                } else {
                    val finalResults = sortResults(searched.flatMap { it.results }, normalizedQuery, parsedQuery)
                    val elapsed = System.currentTimeMillis() - startTime
                    android.util.Log.i("SearchViewModel", "Search \"$query\": ${finalResults.size} results in ${elapsed}ms")
                    UiState.Success(
                        categoryResults = searched.flatMap { it.categories },
                        allResults = finalResults,
                        filteredResults = finalResults,
                        query = query,
                        excludedCountByType =
                            searched
                                .flatMap { it.excludedCountByType.entries }
                                .groupingBy { it.key }
                                .fold(0) { total, entry -> total + entry.value },
                        totalDuration = formatSeconds(elapsed),
                        networkCalls = searched.count { it.searchedOnServer },
                        sourceErrors =
                            failures.map { (source, e) ->
                                "[dev] ${source.name.orEmpty()}: ${e.message ?: e.javaClass.simpleName}"
                            },
                    )
                }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
        }
    }

    /** [source]'s results for [query]: its matching categories, its titles and its hidden counts. */
    private suspend fun searchSource(
        source: SearchSource,
        query: String,
        normalizedQuery: String,
        parsedQuery: ParsedQuery,
    ): SourceResults {
        val repo = repoFor(source.providerId)

        if (!repo.isConnected()) {
            val connectResult = repo.connect()
            if (connectResult.isFailure) throw SessionExpiredException(connectResult.exceptionOrNull())
        }

        val targetContentTypes = targetContentTypes(repo)

        // The background prefetch may not have populated the category list yet (a search fired
        // right after the screen opened). Fall back to loading categories now — they're
        // DB-cached and cheap — so category matching never silently sees an empty list.
        val realCategories =
            prefetchedCategories[source.providerId]?.takeIf { it.isNotEmpty() }
                ?: targetContentTypes.flatMap { type ->
                    repo
                        .getFilteredCategories(type)
                        .getOrNull()
                        ?.filter { !it.isVirtual }
                        ?.map { SearchableCategory(it, type) }
                        ?: emptyList()
                }
        // Build the id→name map from the UNFILTERED category list: a stream can belong to a
        // category hidden from browse by the provider's script/prefix filter (e.g. names with
        // non-Latin unicode like "FR| PRIME ᴿᴬᵂ"), and we still want its name shown on results
        // instead of a blank "Category:" line.
        val categoryNameById =
            targetContentTypes
                .flatMap { type -> repo.getCategories(type).getOrNull().orEmpty() }
                .associate { it.id to it.name }

        val matchingCategories =
            realCategories.mapNotNull {
                if (SearchUtils.matchesQuery(it.category.name, parsedQuery)) {
                    CategorySearchResult(it.category.id, it.category.name, it.contentType, source.providerId, source.name)
                } else {
                    null
                }
            }

        val excludedCountByType =
            targetContentTypes
                .associateWith { repo.countExcludedSearchMatches(query, it) }
                .filterValues { it > 0 }

        // Try server-side search first (e.g., Jellyfin)
        val serverResults = mutableListOf<SearchResult>()
        var serverSearchSuccess = false

        for (type in targetContentTypes) {
            val serverResult = repo.search(query, type)
            if (serverResult != null) {
                serverResult.fold(
                    onSuccess = { items ->
                        serverSearchSuccess = true

                        items.forEach { item ->
                            serverResults.add(
                                SearchResult(
                                    itemId = item.id,
                                    streamName = item.name,
                                    categoryId = item.categoryId,
                                    categoryName = categoryNameById[item.categoryId] ?: "",
                                    contentType = type,
                                    thumbnailUrl = item.thumbnailUrl,
                                    mediaType = item.mediaType,
                                    providerId = source.providerId,
                                    sourceName = source.name,
                                ),
                            )
                        }
                    },
                    onFailure = { },
                )
            }
        }

        val results =
            if (serverSearchSuccess) {
                serverResults
            } else {
                // Fall back to client-side search
                _uiState.value = UiState.Loading(context.getString(org.njarasoa.fijerena.core.ui.R.string.search_fallback_local))
                kotlinx.coroutines.delay(50) // Allow UI to render the new loading message

                val scanned = mutableListOf<SearchResult>()

                // Phase 1: Local cache scan
                for (sc in realCategories) {
                    currentCoroutineContext().job.ensureActive()
                    val cached = repo.getItemsIfCached(sc.category.id, sc.contentType)
                    if (!cached.isNullOrEmpty()) {
                        val categoryMatches = SearchUtils.matchesQuery(sc.category.name, parsedQuery)
                        cached.mapNotNullTo(scanned) { item ->
                            if (categoryMatches || SearchUtils.matchesQuery(item.name, parsedQuery)) {
                                SearchResult(
                                    item.id,
                                    item.name,
                                    sc.category.id,
                                    sc.category.name,
                                    sc.contentType,
                                    item.thumbnailUrl,
                                    item.mediaType,
                                    source.providerId,
                                    source.name,
                                )
                            } else {
                                null
                            }
                        }
                    }
                }
                sortResults(scanned, normalizedQuery, parsedQuery).take(TARGET_RESULTS)
            }
        return SourceResults(matchingCategories, results, excludedCountByType, serverSearchSuccess)
    }

    // O(N) bucketing rather than expensive string matching inside `compareBy`: the string
    // matching is evaluated exactly once per item, and the simple string sorting happens only
    // within the smaller buckets.
    private fun sortResults(
        results: List<SearchResult>,
        normalizedQuery: String,
        parsedQuery: ParsedQuery,
    ): List<SearchResult> {
        val exactMatches = ArrayList<SearchResult>()
        val startsWithMatches = ArrayList<SearchResult>()
        val queryMatches = ArrayList<SearchResult>()
        val others = ArrayList<SearchResult>()

        for (result in results) {
            val name = result.streamName
            if (name.equals(normalizedQuery, ignoreCase = true)) {
                exactMatches.add(result)
            } else if (name.startsWith(normalizedQuery, ignoreCase = true)) {
                startsWithMatches.add(result)
            } else if (!parsedQuery.isEmpty && SearchUtils.matchesQuery(name, parsedQuery)) {
                queryMatches.add(result)
            } else {
                others.add(result)
            }
        }

        val comparator = Comparator<SearchResult> { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.streamName, b.streamName) }
        exactMatches.sortWith(comparator)
        startsWithMatches.sortWith(comparator)
        queryMatches.sortWith(comparator)
        others.sortWith(comparator)

        val sorted = ArrayList<SearchResult>(results.size)
        sorted.addAll(exactMatches)
        sorted.addAll(startsWithMatches)
        sorted.addAll(queryMatches)
        sorted.addAll(others)
        return sorted
    }

    // Favourite and Mark watched act on the result's own source ([providerId], see SearchResult).
    fun isFavorite(
        providerId: Long,
        itemId: String,
        contentType: String,
    ): Boolean = repositories[providerId]?.isFavorite(itemId, contentType) ?: false

    fun isFavoriteCategory(
        providerId: Long,
        categoryId: String,
        contentType: String,
    ): Boolean = repositories[providerId]?.isFavoriteCategory(categoryId, contentType) ?: false

    fun toggleFavorite(
        providerId: Long,
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        isFavorite: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = repoFor(providerId)
            if (isFavorite) {
                repo.removeFavorite(itemId, contentType)
            } else {
                repo.addFavorite(itemId, itemName, categoryId, contentType)
            }
        }
    }

    fun toggleFavoriteCategory(
        providerId: Long,
        categoryId: String,
        categoryName: String,
        contentType: String,
        isFavorite: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = repoFor(providerId)
            if (isFavorite) {
                repo.removeFavoriteCategory(categoryId, contentType)
            } else {
                repo.addFavoriteCategory(categoryId, categoryName, contentType)
            }
        }
    }

    /**
     * Manual watched/unwatched mark (Phase 6, docs/plans/archive/20260828_watch-state-durable-storage-plan.md). Unlike
     * [isFavorite], this has no synchronous in-memory cache to read — `watch_state` reads are
     * suspend since Phase 3 — so building the long-press menu target for a search result means an
     * explicit fetch rather than an inline call. Null when the source keeps watched state on its
     * server (no such action).
     */
    suspend fun isWatchedSuspend(
        providerId: Long,
        itemId: String,
        contentType: String,
    ): Boolean? {
        val repo = repoFor(providerId)
        return if (repo.supportsMarkWatched) repo.getPlaybackPositionSuspend(itemId, contentType)?.isCompleted == true else null
    }

    fun toggleWatched(
        providerId: Long,
        itemId: String,
        contentType: String,
        isWatched: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repoFor(providerId).setWatched(itemId, contentType, !isWatched)
        }
    }

    /**
     * [result]'s long-press menu, on its own source: its favourite; a film's or an episode's "Mark
     * watched / unwatched" and a show's "Mark show watched / unwatched" (neither where the source
     * keeps watched state on its server). The TV and the phone open the same menu.
     */
    suspend fun menuTarget(result: SearchResult): FavoriteMenuTarget.Stream {
        val isWatchable = result.contentType == ContentType.MOVIES || (result.contentType == ContentType.TV_SHOWS && !result.isShow)
        val isWatched = if (isWatchable) isWatchedSuspend(result.providerId, result.itemId, result.contentType) else null
        val isShowWatched = if (result.isShow) showWatchedMarkState(result.providerId, result.itemId) else null
        return FavoriteMenuTarget.Stream(
            itemId = result.itemId,
            itemName = result.streamName,
            categoryId = result.categoryId,
            contentType = result.contentType,
            isFavorite = isFavorite(result.providerId, result.itemId, result.contentType),
            isWatched = isWatched,
            isShowWatched = isShowWatched,
        )
    }

    /**
     * For a show result's "Mark show watched / unwatched"
     * (docs/plans/archive/20261011_season-show-watched-plan.md Phase 3): null when the source keeps watched
     * state on its server (no such action), else whether every episode is watched (true) or not
     * (false, also when the show was never opened here or the read failed).
     */
    suspend fun showWatchedMarkState(
        providerId: Long,
        seriesId: String,
    ): Boolean? {
        val repo = repoFor(providerId)
        return if (repo.supportsMarkWatched) {
            suspendRunCatching { repo.episodesWatchState(seriesId, null) }.getOrNull() == true
        } else {
            null
        }
    }

    /** Marks every episode of [seriesId] watched or unwatched; a failure reaches [onError] in plain words. */
    fun setShowWatched(
        providerId: Long,
        seriesId: String,
        watched: Boolean,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launchGuarded("SearchViewModel.setShowWatched") {
            repoFor(providerId)
                .setEpisodesWatched(seriesId, null, watched)
                .onFailure { e ->
                    onError(
                        context.getString(R.string.watched_mark_many_failed) + "\n" +
                            friendlyErrorMessage(e, context, appSettings.isDevMode),
                    )
                }
        }
    }
}

/**
 * Groups search results by content type, with Live TV/Movies/TV Shows sorted first
 * (in that order) and any other types appended after.
 */
fun buildGroupedSearchResults(
    categoryResults: List<SearchViewModel.CategorySearchResult>,
    results: List<SearchViewModel.SearchResult>,
): List<Triple<String, List<SearchViewModel.CategorySearchResult>, List<SearchViewModel.SearchResult>>> {
    val catsByType = categoryResults.groupBy { it.contentType }
    val streamsByType = results.groupBy { it.contentType }
    val allTypes = (catsByType.keys + streamsByType.keys).distinct()
    val priorityTypes = listOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS)
    val sortedTypes = priorityTypes + (allTypes - priorityTypes.toSet())
    return sortedTypes.map { type -> Triple(type, catsByType[type].orEmpty(), streamsByType[type].orEmpty()) }
}

/** Adds [item] if absent, removes it if present. */
fun Set<String>.toggled(item: String): Set<String> = if (contains(item)) this - item else this + item
