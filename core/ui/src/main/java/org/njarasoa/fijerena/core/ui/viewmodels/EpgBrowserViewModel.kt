package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserDateGroup
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgSearchPath
import org.njarasoa.fijerena.core.network.xmltv.XmltvSearchService
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.util.Date
import java.util.Locale
import org.njarasoa.fijerena.core.ui.utils.UiText
import org.njarasoa.fijerena.core.ui.R

class EpgBrowserViewModel(
    private val context: Context,
    private val providerRepository: ProviderRepository,
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
            if (this === other) return true
            if (other !is ProgramGroupKey) return false
            if (this.hash != other.hash) return false

            if (!this.trimmedTitle.equals(other.trimmedTitle, ignoreCase = true)) return false
            return this.trimmedDescription.equals(other.trimmedDescription, ignoreCase = true)
        }

        override fun hashCode(): Int = hash
    }

    enum class SearchMode {
        PROGRAMME,
        CHANNEL,
    }

    sealed interface UiState {
        data object Idle : UiState

        data object NoEpgFile : UiState

        data object Searching : UiState

        data class Indexing(
            val progressPercent: Int,
            val programmesIndexed: Int,
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

    private val _searchMode = MutableStateFlow(SearchMode.PROGRAMME)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _activeProviderName = MutableStateFlow<String?>(null)
    val activeProviderName: StateFlow<String?> = _activeProviderName.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        if (_searchMode.value != mode) {
            _searchMode.value = mode
            _uiState.value = UiState.Idle
            _pagedSearchResults.value = emptyFlow()
        }
    }

    /** Indexer state exposed for UI (progress banner, settings display). */
    val indexState: StateFlow<EpgIndexState> = EpgIndexer.getInstance(context).state

    private var searchJob: Job? = null
    private val searchService = XmltvSearchService(context)
    private var channelMatcher: EpgChannelMatcher? = null

    @Volatile private var lastMatcherProviderId: Long? = null

    // Cache AppSettings instance to avoid constructing a new object on every property read
    private val appSettings = AppSettings(context)
    val isDevMode: Boolean get() = appSettings.isDevMode

    private val _epgSearchHistory = MutableStateFlow<List<String>>(emptyList())
    val epgSearchHistory: StateFlow<List<String>> = _epgSearchHistory.asStateFlow()

    private val _epgSettings = MutableStateFlow(
        EpgManagementViewModel.EpgSettings(
            autoRefreshEnabled = appSettings.epgAutoRefreshEnabled,
            epgRefreshTime = appSettings.epgRefreshTime,
            epgRefreshInterval = appSettings.epgRefreshInterval,
        )
    )
    val epgSettings: StateFlow<EpgManagementViewModel.EpgSettings> = _epgSettings.asStateFlow()

    private val _sourceLabels = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sourceLabels: StateFlow<Map<Long, String>> = _sourceLabels.asStateFlow()

    private val epgFileManager = EpgFileManager.getInstance(context)

    val epgProcessingState: StateFlow<EpgFileManager.MultiSourceState> = epgFileManager.state

    private val sourcesFlow: Flow<List<org.njarasoa.fijerena.core.network.provider.EpgSourceEntity>> =
        SettingsDatabase.getInstance(context).epgSourceDao().getAllSources()

    /** Oldest `lastIngestedAtMs` across enabled sources — the data is only as fresh as its stalest source.
     *  `null` when there are no enabled sources. `0L` when at least one enabled source has never been ingested. */
    val oldestEnabledIngestedAtMs: StateFlow<Long?> =
        sourcesFlow
            .map { list ->
                val enabled = list.filter { it.enabled }
                if (enabled.isEmpty()) null else enabled.minOf { it.lastIngestedAtMs }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val staleSourceCount: StateFlow<Int> =
        sourcesFlow
            .combine(epgSettings) { list, settings ->
                val interval = settings.epgRefreshInterval
                val staleThreshold = if (interval <= 0) 24L * 3600 * 1000 else interval.toLong() * 3600 * 1000
                val threshold = System.currentTimeMillis() - staleThreshold
                list.count { it.enabled && (it.lastIngestedAtMs == 0L || it.lastIngestedAtMs < threshold) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _toastMessage = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<UiText> = _toastMessage.asSharedFlow()

    fun refreshStale() {
        val taskId = "epg_refresh_stale"
        if (RefreshQueue.queuedTaskIds.value.contains(taskId)) {
            _toastMessage.tryEmit(UiText.StringResource(R.string.epg_refresh_in_queue))
            return
        }
        viewModelScope.launch {
            val interval = epgSettings.value.epgRefreshInterval
            val staleThreshold = if (interval <= 0) 24L * 3600 * 1000 else interval.toLong() * 3600 * 1000
            val thresholdMs = System.currentTimeMillis() - staleThreshold
            val stale =
                withContext(Dispatchers.IO) {
                    SettingsDatabase.getInstance(context).epgSourceDao().getStaleSources(thresholdMs)
                }
            if (stale.isEmpty()) {
                _toastMessage.tryEmit(UiText.StringResource(R.string.epg_up_to_date))
                return@launch
            }
            _toastMessage.tryEmit(UiText.StringResource(R.string.epg_refreshing_stale, stale.size))
            epgFileManager.launchRefreshStale()
        }
    }

    // --------------- Paging flows ---------------

    private val _pagedNowPlaying = MutableStateFlow<Flow<PagingData<EpgSearchResultRow>>>(emptyFlow())
    val pagedNowPlaying: StateFlow<Flow<PagingData<EpgSearchResultRow>>> = _pagedNowPlaying.asStateFlow()

    private val _pagedSearchResults = MutableStateFlow<Flow<PagingData<EpgSearchResultRow>>>(emptyFlow())
    val pagedSearchResults: StateFlow<Flow<PagingData<EpgSearchResultRow>>> = _pagedSearchResults.asStateFlow()

    init {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value is EpgIndexState.Indexed) {
            // Set up paged "Now Playing" flow when index is available
            initPagedNowPlaying()
        } else {
            _uiState.value = UiState.NoEpgFile
        }
        _epgSearchHistory.value = appSettings.getEpgSearchHistory()
        loadSourceLabels()
        viewModelScope.launch { ensureChannelMatcherCurrent() }
        loadActiveProviderName()
    }

    private fun loadActiveProviderName() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val provider =
                        SettingsDatabase
                            .getInstance(context)
                            .providerDao()
                            .getActiveProvider()
                    _activeProviderName.value = provider?.name
                } catch (e: Exception) {
                    android.util.Log.e("EpgBrowserViewModel", "Failed to load active provider name", e)
                }
            }
        }
    }

    /**
     * Loads (or refreshes) [channelMatcher] for the currently active provider.
     * If the active provider hasn't changed since the last load, this is a no-op.
     * Must be called from a coroutine; runs its DB work on [Dispatchers.IO].
     */
    private suspend fun ensureChannelMatcherCurrent() {
        withContext(Dispatchers.IO) {
            try {
                val provider =
                    SettingsDatabase
                        .getInstance(context)
                        .providerDao()
                        .getActiveProvider() ?: return@withContext
                if (provider.id == lastMatcherProviderId) return@withContext
                val t0 = System.currentTimeMillis()
                val liveStreams =
                    XtreamDatabase
                        .getInstance(context)
                        .streamDao()
                        .getAllStreams(provider.id, XtreamStreamEntity.TYPE_LIVE)
                val t1 = System.currentTimeMillis()
                channelMatcher = if (liveStreams.isNotEmpty()) EpgChannelMatcher(liveStreams) else null
                val t2 = System.currentTimeMillis()
                android.util.Log.d("EpgBrowserViewModel", "ensureChannelMatcher: getAllStreams=${t1 - t0}ms count=${liveStreams.size} matcherInit=${t2 - t1}ms total=${t2 - t0}ms")
                lastMatcherProviderId = provider.id
            } catch (e: Exception) {
                android.util.Log.e("EpgBrowserViewModel", "Failed to load live streams for channel matching", e)
            }
        }
    }

    private fun loadSourceLabels() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val settingsDb = SettingsDatabase.getInstance(context)
                    val sources = settingsDb.epgSourceDao().getAllSourcesOnce()
                    _sourceLabels.value = sources.associate { it.id to it.label }
                } catch (e: Exception) {
                    android.util.Log.e("EpgBrowserViewModel", "Failed to load EPG source labels", e)
                }
            }
        }
    }

    private fun initPagedNowPlaying() {
        val indexer = EpgIndexer.getInstance(context)
        viewModelScope.launch {
            indexer.state.collect { state ->
                if (state is EpgIndexState.Indexed) {
                    loadNowPlaying()
                }
            }
        }
    }

    private fun loadNowPlaying() {
        viewModelScope.launch {
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            val activeProviderId = providerRepository.getActiveProvider()?.id ?: -1L
            val settingsDb = SettingsDatabase.getInstance(context)
            val sourceDao = settingsDb.epgSourceDao()
            val validSources = sourceDao.getEnabledSourcesForSearch(if (activeProviderId != -1L) activeProviderId else null)
            val sourceIds = validSources.map { it.id }

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
                    ensureChannelMatcherCurrent()
                    val mode = _searchMode.value

                    val result =
                        withContext(Dispatchers.IO) {
                            when (mode) {
                                SearchMode.PROGRAMME -> searchService.search(query)
                                SearchMode.CHANNEL -> searchService.searchByChannel(query)
                            }
                        }
                    val elapsed = System.currentTimeMillis() - startTime

                    if (result == null) {
                        _uiState.value = UiState.NoEpgFile
                        return@launch
                    }

                    // Convert all programmes to airings with programme info
                    val allAirings =
                        result.programmes.map { prog ->
                            val channel = result.channels[prog.channelId]
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
                                    ),
                            )
                        }

                    // Group by date, then by programme within each date
                    val dateGroups =
                        applyChannelMatching(
                            if (mode == SearchMode.PROGRAMME) {
                                groupByDate(allAirings)
                            } else {
                                groupByChannel(allAirings)
                            },
                        )
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
                    _uiState.value = UiState.Error("EPG file too large for search. Try a more specific query.")
                } catch (e: Exception) {
                    _uiState.value = UiState.Error(e.message ?: "Search failed")
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
            val windowEnd = now + 6 * 86400L

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

                val activeProviderId = providerRepository.getActiveProvider()?.id ?: -1L
                val settingsDb = SettingsDatabase.getInstance(context)
                val sourceDao = settingsDb.epgSourceDao()
                val validSources = sourceDao.getEnabledSourcesForSearch(if (activeProviderId != -1L) activeProviderId else null)
                val sourceIds = validSources.map { it.id }

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
                    }.flow.cachedIn(viewModelScope)
            }
        }
    }

    fun refreshNowPlaying() {
        refreshSettings()
        initPagedNowPlaying()
    }

    private fun refreshSettings() {
        _epgSettings.value = EpgManagementViewModel.EpgSettings(
            autoRefreshEnabled = appSettings.epgAutoRefreshEnabled,
            epgRefreshTime = appSettings.epgRefreshTime,
            epgRefreshInterval = appSettings.epgRefreshInterval,
        )
    }

    private data class AiringWithProgramme(
        val title: String,
        val description: String?,
        val category: String?,
        val airing: EpgBrowserAiring,
    )

    private fun applyChannelMatching(dateGroups: List<EpgBrowserDateGroup>): List<EpgBrowserDateGroup> {
        val matcher = channelMatcher ?: return dateGroups
        return dateGroups.map { group ->
            group.copy(
                programs =
                    group.programs.map { program ->
                        // ⚡ Bolt: Performance Optimization
                        // Replaced O(N log N) `sortedWith` with an O(N) stable bucketing approach.
                        // `program.airings` is already sorted by `startEpoch`. By accumulating
                        // matches and non-matches separately and concatenating, we preserve
                        // the initial chronological ordering natively without redundant allocations.
                        val matchedList = ArrayList<EpgBrowserAiring>()
                        val unmatchedList = ArrayList<EpgBrowserAiring>()

                        for (airing in program.airings) {
                            val matched = matcher.match(airing.channelId, airing.channelName)
                            if (matched != null) {
                                matchedList.add(airing.copy(matchedStream = matched))
                            } else {
                                unmatchedList.add(airing)
                            }
                        }

                        val sorted = ArrayList<EpgBrowserAiring>(matchedList.size + unmatchedList.size)
                        sorted.addAll(matchedList)
                        sorted.addAll(unmatchedList)

                        program.copy(airings = sorted)
                    },
            )
        }
    }

    private fun groupByDate(airings: List<AiringWithProgramme>): List<EpgBrowserDateGroup> {
        val zoneId = java.time.ZoneId.systemDefault()
        val labelFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, Locale.getDefault())

        val now = java.time.Instant.now()
        val today = now.atZone(zoneId).toLocalDate()
        val tomorrow = today.plusDays(1)

        // Group airings by their local date
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
                // Compute date label
                val label =
                    when (localDate) {
                        today -> "Today"
                        tomorrow -> "Tomorrow"
                        else -> {
                            val zdt = localDate.atStartOfDay(zoneId)
                            labelFormat.format(java.util.Date.from(zdt.toInstant()))
                        }
                    }

                // Compute day start epoch (midnight local time) for sorting
                val dayStartEpoch = localDate.atStartOfDay(zoneId).toInstant().epochSecond

                // Group by programme within this day
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

    private fun groupByChannel(airings: List<AiringWithProgramme>): List<EpgBrowserDateGroup> {
        // For "What's on", we group by channel name and reuse EpgBrowserDateGroup
        // with the channel name as the label.
        // ⚡ Bolt: Group by channelId directly to avoid allocating temporary Pair objects for every airing
        val byChannel = airings.groupBy { it.airing.channelId }

        return byChannel.entries
            // Use String.CASE_INSENSITIVE_ORDER to avoid allocating new String objects during sorting
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value.first().airing.channelName })
            .mapIndexed { index, (_, channelAirings) ->
                val channelName = channelAirings.first().airing.channelName
                val programs =
                    channelAirings
                        .mapIndexed { progIndex, airingWithProg ->
                            EpgBrowserProgram(
                                id = "$channelName::${airingWithProg.title}::${airingWithProg.airing.startEpoch}::$progIndex",
                                title = airingWithProg.title,
                                description = airingWithProg.description,
                                category = airingWithProg.category,
                                airings = listOf(airingWithProg.airing),
                            )
                        }.sortedBy { it.airings.first().startEpoch }

                val channelId = channelAirings.first().airing.channelId
                EpgBrowserDateGroup(
                    dateLabel = channelName,
                    // Use a unique ID derived from channelId hash or index to avoid collisions
                    dayStartEpoch = (channelId.hashCode().toLong() and 0xFFFFFFFFL) or (index.toLong() shl 32),
                    programs = programs,
                )
            }
    }
}
