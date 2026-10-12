package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserDateGroup
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserMatchedStream
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgIndexBusyException
import org.njarasoa.fijerena.core.network.xmltv.EpgRefreshSchedule
import org.njarasoa.fijerena.core.network.xmltv.EpgSearchPath
import org.njarasoa.fijerena.core.network.xmltv.GuideAiringMatcher
import org.njarasoa.fijerena.core.network.xmltv.GuideChannels
import org.njarasoa.fijerena.core.network.xmltv.GuideScope
import org.njarasoa.fijerena.core.network.xmltv.XmltvSearchService
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.utils.UiText
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class EpgBrowserViewModel(
    private val context: Context,
    private val providerRepository: ProviderRepository,
    /**
     * Opened from a TV Guide (GD5): that guide's list — a category, or the virtual `recent` /
     * `favorites` — whose channels the screen offers as an "In <category> only" filter. Null: the
     * whole guide, no such filter.
     */
    private val categoryId: String? = null,
    /** The source of the TV Guide it was opened from; 0 when not opened from one. */
    private val contextProviderId: Long = 0L,
) : ViewModel() {
    /**
     * Internal key for grouping programs without allocating new lowercase strings.
     */
    private class ProgramGroupKey(
        title: String,
        description: String?,
    ) {
        private val trimmedTitle = title.trim()
        private val trimmedDescription = description?.trim() ?: ""
        private val hash: Int

        init {
            var h = 0
            for (i in trimmedTitle.indices) h = 31 * h + trimmedTitle[i].lowercaseChar().code

            // Incorporate a prime multiplier before description to reduce collisions
            h *= 31
            for (i in trimmedDescription.indices) h = 31 * h + trimmedDescription[i].lowercaseChar().code

            hash = h
        }

        override fun equals(other: Any?): Boolean {
            val isEqual =
                when {
                    this === other -> true
                    other !is ProgramGroupKey -> false
                    this.hash != other.hash -> false
                    !this.trimmedTitle.equals(other.trimmedTitle, ignoreCase = true) -> false
                    else -> this.trimmedDescription.equals(other.trimmedDescription, ignoreCase = true)
                }
            return isEqual
        }

        override fun hashCode(): Int = hash
    }

    sealed interface UiState {
        data object Idle : UiState

        data object NoEpgFile : UiState

        data object Searching : UiState

        /**
         * [query] couldn't run because the search index is being rebuilt. [refreshing] is true
         * while an EPG refresh is in progress, false when an interrupted rebuild is being redone.
         * The search reruns by itself once the index is ready.
         */
        data class IndexBusy(
            val query: String,
            val refreshing: Boolean,
        ) : UiState

        data class Results(
            val query: String,
            val dateGroups: List<EpgBrowserDateGroup>,
            val totalPrograms: Int,
            val totalAirings: Int,
            val truncated: Boolean,
            val searchTimeMs: Long,
            val searchedFromIndex: Boolean = false,
            val searchPath: EpgSearchPath = EpgSearchPath.NONE,
        ) : UiState

        data class Error(
            val message: String,
        ) : UiState
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val PREFETCH_DISTANCE = 25
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * The profile's sources that can have guide sources (they have live channels), id to name, in
     * its order: what the Guide sources button opens, picking the source first when there are
     * several (multi-source plan, O4).
     */
    private val _guideSourceOwners = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val guideSourceOwners: StateFlow<List<Pair<Long, String>>> = _guideSourceOwners.asStateFlow()

    /** Indexer state exposed for UI (progress banner, settings display). */
    val indexState: StateFlow<EpgIndexState> = EpgIndexer.getInstance(context).state

    private var searchJob: Job? = null
    private val searchService = XmltvSearchService(context)

    // Each airing is matched on its own source's channels and hidden live categories, re-read with
    // each search: an airing matched to a stream in a hidden category is dropped like an excluded
    // stream — it can't be opened, and a filter (a Kid profile's) must not be bypassed through the
    // guide. Kept out of EpgChannelMatcher, which is cached across profiles
    // (docs/plans/archive/20261007_guide-watch-wrong-channel-plan.md, fix 3).
    @Volatile private var airingMatcher: GuideAiringMatcher? = null

    // Cache AppSettings instance to avoid constructing a new object on every property read
    private val appSettings = AppSettings(context)
    val isDevMode: Boolean get() = appSettings.isDevMode

    private val _epgSearchHistory = MutableStateFlow<List<String>>(emptyList())
    val epgSearchHistory: StateFlow<List<String>> = _epgSearchHistory.asStateFlow()

    /**
     * The stream ids of [categoryId]'s channels, resolved the way the TV Guide resolves them
     * (virtual lists included), one entry per source; null without a [categoryId] or until they are loaded.
     */
    private val _contextChannels = MutableStateFlow<List<GuideChannels>?>(null)
    val contextChannels: StateFlow<List<GuideChannels>?> = _contextChannels.asStateFlow()

    private val _sourceLabels = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sourceLabels: StateFlow<Map<Long, String>> = _sourceLabels.asStateFlow()

    private val epgFileManager = EpgFileManager.getInstance(context)

    /**
     * The sources whose guides this screen searches: every source of the profile in use
     * ([GuideScope]). Everything below reports on them alone.
     */
    private val searchedProviderIdsFlow: Flow<Set<Long>> =
        providerRepository
            .profileSources(appSettings.activeProfileId)
            .map { list -> GuideScope.searchedProviders(list).mapTo(HashSet()) { it.id } }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /**
     * Another provider's refresh must not drive this screen's progress UI — one pipeline serves
     * every provider, so reading EpgFileManager.state directly showed its sources and totals here.
     */
    val epgProcessingState: StateFlow<EpgFileManager.MultiSourceState> =
        searchedProviderIdsFlow
            .flatMapLatest { providerIds ->
                if (providerIds.isEmpty()) {
                    flowOf(EpgFileManager.MultiSourceState.Idle)
                } else {
                    epgFileManager.stateFor(providerIds)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EpgFileManager.MultiSourceState.Idle)

    /**
     * The guide sources of the sources searched, not of every provider configured: a source that
     * isn't searched has no business reporting on this screen — reading them all is what let a
     * provider whose EPG had never run make a freshly-synced one read as "Never refreshed".
     */
    private val sourcesFlow: Flow<List<org.njarasoa.fijerena.core.network.provider.EpgSourceEntity>> =
        combine(searchedProviderIdsFlow, SettingsDatabase.getInstance(context).epgSourceDao().getAllSources()) { providerIds, sources ->
            sources.filter { it.providerId in providerIds }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /**
     * Oldest `lastIngestedAtMs` among enabled sources that have actually run — the data is only as
     * fresh as its stalest source, but a source that has never run is not a freshness figure at
     * all, it is a count. It shows up in [staleSourceCount] instead, so one never-run source no
     * longer hides how current everything else is.
     *
     * `null` when there are no enabled sources, `0L` when none of them has ever run.
     */
    val oldestEnabledIngestedAtMs: StateFlow<Long?> =
        sourcesFlow
            .map { list ->
                val enabled = list.filter { it.enabled }
                when {
                    enabled.isEmpty() -> null
                    else -> enabled.filter { it.lastIngestedAtMs > 0L }.minOfOrNull { it.lastIngestedAtMs } ?: 0L
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Enabled sources that ran, but longer ago than their own refresh interval allows. */
    val staleSourceCount: StateFlow<Int> =
        sourcesFlow
            .map { list ->
                val now = System.currentTimeMillis()
                val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
                list.count { it.enabled && it.lastIngestedAtMs > 0L && isOlderThanInterval(it, now, unsetHours) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun isOlderThanInterval(
        source: org.njarasoa.fijerena.core.network.provider.EpgSourceEntity,
        nowMs: Long,
        unsetHours: Int,
    ): Boolean = nowMs - source.lastIngestedAtMs > EpgRefreshSchedule.intervalMs(EpgRefreshSchedule.intervalHours(source, unsetHours))

    /** Whether any guide source is switched off — with none on, the header says the guide is off rather than missing. */
    val hasOffSources: StateFlow<Boolean> =
        sourcesFlow
            .map { list -> list.any { !it.enabled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Enabled sources that have never run at all — counted, not aged. */
    val neverRunSourceCount: StateFlow<Int> =
        sourcesFlow
            .map { list -> list.count { it.enabled && it.lastIngestedAtMs == 0L } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _toastMessage = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<UiText> = _toastMessage.asSharedFlow()

    fun refreshStale() {
        viewModelScope.launch {
            // A refresh covers the searched sources' own guide sources, and the task id it queues
            // under is scoped to those providers too.
            val providerIds =
                withContext(Dispatchers.IO) { GuideScope.searchedProviders(providerRepository.profileSourcesList()).map { it.id } }
            if (providerIds.isEmpty()) {
                _toastMessage.tryEmit(UiText.StringResource(R.string.epg_up_to_date))
                return@launch
            }
            val taskId = EpgFileManager.refreshStaleTaskId(providerIds)
            if (RefreshQueue.queuedTaskIds.value.contains(taskId)) {
                _toastMessage.tryEmit(UiText.StringResource(R.string.epg_refresh_in_queue))
                return@launch
            }
            val now = System.currentTimeMillis()
            val unsetHours = EpgRefreshSchedule.legacyIntervalHours(appSettings)
            val sourceDao = SettingsDatabase.getInstance(context).epgSourceDao()
            val stale =
                withContext(Dispatchers.IO) {
                    providerIds.flatMap { sourceDao.getEnabledSourcesForProvider(it) }
                }.filter { it.lastIngestedAtMs == 0L || isOlderThanInterval(it, now, unsetHours) }
            if (stale.isEmpty()) {
                _toastMessage.tryEmit(UiText.StringResource(R.string.epg_up_to_date))
                return@launch
            }
            _toastMessage.tryEmit(UiText.StringResource(R.string.epg_refreshing_stale, stale.size))
            epgFileManager.launchRefreshStale(providerIds)
        }
    }

    private val _pagedNowPlaying = MutableStateFlow<Flow<PagingData<EpgSearchResultRow>>>(emptyFlow())
    val pagedNowPlaying: StateFlow<Flow<PagingData<EpgSearchResultRow>>> = _pagedNowPlaying.asStateFlow()

    private val _pagedSearchResults = MutableStateFlow<Flow<PagingData<EpgSearchResultRow>>>(emptyFlow())
    val pagedSearchResults: StateFlow<Flow<PagingData<EpgSearchResultRow>>> = _pagedSearchResults.asStateFlow()

    init {
        val indexer = EpgIndexer.getInstance(context)
        when (indexer.state.value) {
            is EpgIndexState.Indexed -> {
                initPagedNowPlaying()
            }

            // Indexing/Optimizing/Failed still have a guide on disk — a refresh is no reason to
            // claim there isn't one.
            is EpgIndexState.NotIndexed -> {
                _uiState.value = UiState.NoEpgFile
            }

            else -> {}
        }
        viewModelScope.launchGuarded("EpgBrowserViewModel.indexState") {
            indexer.state.collect { state ->
                if (state !is EpgIndexState.Indexed) return@collect
                // A busy search, or a LIKE-fallback one that may be incomplete, gets the full
                // FTS answer once the index is ready.
                when (val current = _uiState.value) {
                    is UiState.IndexBusy -> {
                        performSearch(current.query)
                    }

                    is UiState.Results -> {
                        if (current.searchPath == EpgSearchPath.LIKE_FALLBACK) performSearch(current.query)
                    }

                    else -> {}
                }
            }
        }
        _epgSearchHistory.value = appSettings.getEpgSearchHistory()
        loadSourceLabels()
        viewModelScope.launchGuarded("EpgBrowserViewModel.channelMatcher") { ensureChannelMatcherCurrent(loadScope()) }
        loadGuideSourceOwners()
        categoryId?.let { id ->
            viewModelScope.launchGuarded("EpgBrowserViewModel.contextStreams") { loadContextStreamIds(id) }
        }
        // The profile's sources changed while this screen is open (turned on or off, here or on
        // another device): what was loaded once above follows, and the results on screen are rerun.
        viewModelScope.launchGuarded("EpgBrowserViewModel.sources") {
            searchedProviderIdsFlow.drop(1).collect {
                loadSourceLabels()
                loadGuideSourceOwners()
                ensureChannelMatcherCurrent(loadScope())
                categoryId?.let { id -> loadContextStreamIds(id) }
                (_uiState.value as? UiState.Results)?.let { performSearch(it.query) }
            }
        }
    }

    private suspend fun loadContextStreamIds(categoryId: String) {
        // "In <category> only" stays on the guide's own source; Recent and Favourites are every
        // source's, as the TV Guide lists them, each channel on its own source.
        val guideProviderId = contextProviderId.takeIf { it > 0L } ?: return
        val container = AppContainer.getInstance(context)
        val repository = container.getMediaRepository(guideProviderId)
        val lists = CategoryViewModel.guideLists(container, repository, categoryId)
        val items =
            (
                CategoryViewModel.virtualCategoryItems(lists, categoryId, ContentType.LIVE_TV)
                    ?: repository.getItems(categoryId, ContentType.LIVE_TV).getOrThrow()
            ).onSources(guideProviderId)
        _contextChannels.value =
            items.groupBy { it.providerId }.map { (source, sourceItems) ->
                val itemsById = sourceItems.associateBy { it.id }
                GuideChannels(
                    streamIds = sourceItems.mapTo(HashSet()) { it.id },
                    byEpgChannelId =
                        lists
                            .repoFor(source)
                            .matchGuideChannels(sourceItems)
                            .entries
                            .mapNotNull { (itemId, xmltvId) ->
                                val item = itemsById[itemId] ?: return@mapNotNull null
                                val streamId = item.id.toIntOrNull() ?: return@mapNotNull null
                                xmltvId.lowercase() to
                                    EpgBrowserMatchedStream(streamId, item.name, item.categoryId, providerId = source)
                            }.toMap(),
                    providerId = source,
                )
            }
    }

    private fun loadGuideSourceOwners() {
        viewModelScope.launchGuarded("EpgBrowserViewModel.guideSourceOwners") {
            _guideSourceOwners.value =
                withContext(Dispatchers.IO) {
                    providerRepository
                        .profileSourcesList()
                        .filter { MediaProviderFactory.hasLiveTv(it) }
                        .map { it.id to it.name }
                }
        }
    }

    /** The guide sources searched, of every source of the profile in use. */
    private suspend fun loadScope(): GuideScope = withContext(Dispatchers.IO) { GuideScope.load(context) }

    /**
     * Loads (or refreshes) [airingMatcher] for the sources of [scope]: one [EpgChannelMatcher] per
     * source (its global cache avoids redundant DB queries) and each source's hidden live
     * categories.
     */
    private suspend fun ensureChannelMatcherCurrent(scope: GuideScope) {
        withContext(Dispatchers.IO) {
            try {
                val xtreamDb = XtreamDatabase.getInstance(context)
                val hidden = HashMap<Long, Set<String>>()
                val matchers = HashMap<Long, EpgChannelMatcher>()
                for (provider in scope.providers) {
                    hidden[provider.id] =
                        xtreamDb
                            .categoryDao()
                            .getAllCategoriesIncludingExcluded(provider.id, XtreamCategoryEntity.TYPE_LIVE)
                            .filter { it.excluded }
                            .mapTo(HashSet()) { it.categoryId }
                    matchers[provider.id] =
                        EpgChannelMatcher.getOrCreate(provider.id) {
                            val t0 = System.currentTimeMillis()
                            val streams =
                                xtreamDb
                                    .streamDao()
                                    .getAllStreamsIncludingExcluded(provider.id, XtreamStreamEntity.TYPE_LIVE)
                            val t1 = System.currentTimeMillis()
                            android.util.Log.d("EpgBrowserViewModel", "Fetched ${streams.size} streams in ${t1 - t0}ms for new matcher")
                            streams
                        }
                }
                airingMatcher = GuideAiringMatcher(scope.providerIdBySourceId, matchers, hidden)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("EpgBrowserViewModel", "Failed to ensure channel matcher", e)
            }
        }
    }

    private fun loadSourceLabels() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val scope = GuideScope.load(context)
                    val sources = SettingsDatabase.getInstance(context).epgSourceDao().getAllSourcesOnce()
                    _sourceLabels.value = sources.filter { it.id in scope.providerIdBySourceId }.associate { it.id to it.label }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("EpgBrowserViewModel", "Failed to load EPG source labels", e)
                }
            }
        }
    }

    private fun initPagedNowPlaying() {
        val indexer = EpgIndexer.getInstance(context)
        viewModelScope.launchGuarded("EpgBrowserViewModel.pagedNowPlaying") {
            indexer.state.collect { state ->
                if (state is EpgIndexState.Indexed) {
                    loadNowPlaying()
                }
            }
        }
    }

    private fun loadNowPlaying() {
        viewModelScope.launchGuarded("EpgBrowserViewModel.loadNowPlaying") {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            // A channel of any searched source reads that source's guide sources.
            val sourceIds = loadScope().sourceIds

            val nowEpoch = System.currentTimeMillis() / 1000L
            _pagedNowPlaying.value =
                Pager(
                    config =
                        PagingConfig(
                            pageSize = PAGE_SIZE,
                            prefetchDistance = PREFETCH_DISTANCE,
                            enablePlaceholders = false,
                        ),
                ) {
                    dao.getPagedNowPlaying(nowEpoch, sourceIds)
                }.flow.cachedIn(viewModelScope)
        }
    }

    fun performSearch(query: String) {
        if (query.length < 2) return

        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                _uiState.value = UiState.Searching
                appSettings.addEpgSearchHistory(query)
                _epgSearchHistory.value = appSettings.getEpgSearchHistory()
                try {
                    val startTime = System.currentTimeMillis()
                    val scope = loadScope()
                    ensureChannelMatcherCurrent(scope)
                    val result = withContext(Dispatchers.IO) { searchService.search(query, scope.sourceIds) }
                    val elapsed = System.currentTimeMillis() - startTime

                    if (result == null) {
                        _uiState.value = UiState.NoEpgFile
                        return@launch
                    }

                    val sourceNames = scope.sourceNames
                    val allAirings =
                        result.programmes.map { prog ->
                            val channel = result.channels[prog.channelId]
                            val providerId = scope.providerIdBySourceId[prog.sourceId] ?: 0L
                            AiringWithProgramme(
                                title = prog.title,
                                description = prog.description,
                                category = prog.category,
                                airing =
                                    EpgBrowserAiring(
                                        channelId = prog.channelId,
                                        channelName = channel?.displayName ?: prog.channelId,
                                        channelIconUrl = channel?.iconUrl,
                                        startEpoch = prog.startEpoch,
                                        endEpoch = prog.endEpoch,
                                        sourceId = prog.sourceId,
                                        providerId = providerId,
                                        sourceName = sourceNames[providerId],
                                    ),
                            )
                        }

                    val dateGroups = applyChannelMatching(groupByDate(allAirings))
                    val totalAirings = allAirings.size
                    val totalPrograms = dateGroups.sumOf { it.programs.size }

                    _uiState.value =
                        UiState.Results(
                            query = query,
                            dateGroups = dateGroups,
                            totalPrograms = totalPrograms,
                            totalAirings = totalAirings,
                            truncated = result.truncated,
                            searchTimeMs = elapsed,
                            searchedFromIndex = result.searchedFromIndex,
                            searchPath = result.searchPath,
                        )

                    // Also set up paged search results for large datasets
                    initPagedSearch(query)
                } catch (e: OutOfMemoryError) {
                    System.gc()
                    _uiState.value = UiState.Error(context.getString(R.string.epg_error_file_too_large))
                } catch (e: EpgIndexBusyException) {
                    // Index is being rebuilt — not "no results": say so, and rerun the query when
                    // the index state reaches Indexed (see init).
                    _uiState.value = UiState.IndexBusy(query, isRefreshInProgress())
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
                }
            }
    }

    private fun isRefreshInProgress(): Boolean =
        when (epgFileManager.state.value) {
            is EpgFileManager.MultiSourceState.Pending,
            is EpgFileManager.MultiSourceState.Processing,
            is EpgFileManager.MultiSourceState.Finalizing,
            is EpgFileManager.MultiSourceState.Retrying,
            -> {
                true
            }

            else -> {
                EpgIndexer.getInstance(context).state.value.let {
                    it is EpgIndexState.Indexing || it is EpgIndexState.Optimizing
                }
            }
        }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = UiState.Idle
        _pagedSearchResults.value = emptyFlow()
    }

    fun removeEpgSearchHistoryEntry(query: String) {
        appSettings.removeEpgSearchHistory(query)
        _epgSearchHistory.value = appSettings.getEpgSearchHistory()
    }

    fun clearEpgSearchHistory() {
        appSettings.clearEpgSearchHistory()
        _epgSearchHistory.value = emptyList()
    }

    private fun initPagedSearch(query: String) {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value is EpgIndexState.Indexed) {
            val now = System.currentTimeMillis() / 1000L
            val windowStart = now
            val windowEnd = Long.MAX_VALUE // no upper limit, as in XmltvSearchService.search

            val sanitized =
                query
                    .replace("\"", "")
                    .replace("*", "")
                    .replace("(", "")
                    .replace(")", "")
                    .replace(":", "")
                    .trim()
            if (sanitized.isBlank()) return

            viewModelScope.launch {
                val ftsQuery = "\"$sanitized\"*"
                val db = EpgIndexDatabase.getInstance(context)
                val dao = db.epgIndexDao()

                val sourceIds = loadScope().sourceIds

                val matcher = airingMatcher
                _pagedSearchResults.value =
                    Pager(
                        config =
                            PagingConfig(
                                pageSize = PAGE_SIZE,
                                prefetchDistance = PREFETCH_DISTANCE,
                                enablePlaceholders = false,
                            ),
                    ) {
                        dao.searchByTitleFtsPaged(ftsQuery, sourceIds, windowStart, windowEnd)
                    }.flow
                        .map { pagingData ->
                            pagingData.filter { row ->
                                val matched = matcher?.match(row.sourceId, row.channelId, row.channelDisplayName)
                                matched == null || matcher?.isHidden(matched) != true
                            }
                        }.cachedIn(viewModelScope)
            }
        }
    }

    fun refreshNowPlaying() {
        initPagedNowPlaying()
    }

    private data class AiringWithProgramme(
        val title: String,
        val description: String?,
        val category: String?,
        val airing: EpgBrowserAiring,
    )

    private fun applyChannelMatching(dateGroups: List<EpgBrowserDateGroup>): List<EpgBrowserDateGroup> {
        val matcher = airingMatcher
        return if (matcher == null) {
            dateGroups
        } else {
            dateGroups
                .map { group ->
                    group.copy(
                        programs =
                            group.programs
                                .map { program ->
                                    // `program.airings` is already sorted by `startEpoch`. By accumulating
                                    // matches and non-matches separately and concatenating, we preserve
                                    // that order in O(N), with no re-sort.
                                    val matchedList = ArrayList<EpgBrowserAiring>()
                                    val unmatchedList = ArrayList<EpgBrowserAiring>()

                                    for (airing in program.airings) {
                                        val matched = matcher.match(airing.sourceId, airing.channelId, airing.channelName)
                                        when {
                                            // excluded channel, or one in a hidden category: drop from search results
                                            matched != null && matcher.isHidden(matched) -> Unit

                                            matched != null -> matchedList.add(airing.copy(matchedStream = matched))

                                            else -> unmatchedList.add(airing) // no corresponding stream at all — keep as before
                                        }
                                    }

                                    val sorted = ArrayList<EpgBrowserAiring>(matchedList.size + unmatchedList.size)
                                    sorted.addAll(matchedList)
                                    sorted.addAll(unmatchedList)

                                    program.copy(airings = sorted)
                                }.filter { it.airings.isNotEmpty() },
                    )
                }.filter { it.programs.isNotEmpty() }
        }
    }

    private fun groupByDate(airings: List<AiringWithProgramme>): List<EpgBrowserDateGroup> {
        val zoneId = java.time.ZoneId.systemDefault()
        val labelFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, Locale.getDefault())

        val now = java.time.Instant.now()
        val today = now.atZone(zoneId).toLocalDate()
        val tomorrow = today.plusDays(1)

        val byDay =
            airings.groupBy {
                java.time.Instant
                    .ofEpochSecond(it.airing.startEpoch)
                    .atZone(zoneId)
                    .toLocalDate()
            }

        return byDay.entries
            .sortedBy { it.key }
            .map { (localDate, dayAirings) ->
                val label =
                    when (localDate) {
                        today -> {
                            "Today"
                        }

                        tomorrow -> {
                            "Tomorrow"
                        }

                        else -> {
                            val zdt = localDate.atStartOfDay(zoneId)
                            labelFormat.format(java.util.Date.from(zdt.toInstant()))
                        }
                    }

                // Compute day start epoch (midnight local time) for sorting
                val dayStartEpoch = localDate.atStartOfDay(zoneId).toInstant().epochSecond

                val programs =
                    dayAirings
                        .groupBy {
                            ProgramGroupKey(it.title, it.description)
                        }.entries
                        .mapIndexed { progIndex, (key, group) ->
                            val rep = group.first()
                            val firstAiring = group.minBy { it.airing.startEpoch }.airing
                            val id = "${rep.title}::${rep.description}::${firstAiring.channelId}::${firstAiring.startEpoch}::$progIndex"
                            EpgBrowserProgram(
                                id = id,
                                title = rep.title,
                                description = rep.description ?: group.firstNotNullOfOrNull { it.description },
                                category = rep.category ?: group.firstNotNullOfOrNull { it.category },
                                airings = group.map { it.airing }.sortedBy { it.startEpoch },
                            )
                        }.sortedBy { it.airings.first().startEpoch }

                EpgBrowserDateGroup(
                    dateLabel = label,
                    dayStartEpoch = dayStartEpoch,
                    programs = programs,
                )
            }
    }
}

/** "N programs (M airings) — 1.2s [source]" summary line for the results header. */
fun EpgBrowserViewModel.UiState.Results.statsLine(): String {
    val timeStr = String.format(Locale.US, "%.1f", searchTimeMs / 1000.0)
    val truncatedSuffix = if (truncated) " (truncated)" else ""
    val sourceSuffix =
        when {
            !searchedFromIndex -> {
                " [XML scan]"
            }

            else -> {
                when (searchPath) {
                    EpgSearchPath.FTS_PHRASE -> " [FTS phrase]"
                    EpgSearchPath.FTS_AND -> " [FTS AND]"
                    EpgSearchPath.LIKE_FALLBACK -> " [LIKE fallback]"
                    EpgSearchPath.NONE -> " [indexed]"
                }
            }
        }
    return "$totalPrograms programs ($totalAirings airings) — ${timeStr}s$truncatedSuffix$sourceSuffix"
}

/** Why [EpgBrowserViewModel.UiState.IndexBusy.query] hasn't run yet. */
@androidx.compose.runtime.Composable
fun EpgBrowserViewModel.UiState.IndexBusy.message(): String =
    if (refreshing) {
        androidx.compose.ui.res
            .stringResource(R.string.epg_browser_busy_refreshing_format, query)
    } else {
        androidx.compose.ui.res
            .stringResource(R.string.epg_browser_busy_rebuilding_format, query)
    }

/**
 * Empty-state message when there are no (matched) results for the query — or none on the channels
 * of [contextName], when the results are limited to a TV Guide's list (GD5).
 */
@androidx.compose.runtime.Composable
fun EpgBrowserViewModel.UiState.Results.noResultsMessage(
    matchedOnly: Boolean,
    contextName: String? = null,
): String =
    if (contextName != null) {
        androidx.compose.ui.res
            .stringResource(R.string.epg_browser_no_results_in_category_format, query, contextName)
    } else if (matchedOnly) {
        androidx.compose.ui.res
            .stringResource(R.string.epg_browser_no_matched_results_format, query)
    } else {
        androidx.compose.ui.res
            .stringResource(R.string.epg_browser_no_results_format, query)
    }
