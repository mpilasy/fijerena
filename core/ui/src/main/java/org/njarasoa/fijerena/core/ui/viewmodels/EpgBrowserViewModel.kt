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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserDateGroup
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher
import org.njarasoa.fijerena.core.network.xmltv.XmltvSearchService
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow

class EpgBrowserViewModel(
    private val context: Context
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object NoEpgFile : UiState
        data object Searching : UiState
        data class Indexing(
            val progressPercent: Int,
            val programmesIndexed: Int
        ) : UiState
        data class Results(
            val query: String,
            val dateGroups: List<EpgBrowserDateGroup>,
            val totalPrograms: Int,
            val totalAirings: Int,
            val truncated: Boolean,
            val searchTimeMs: Long,
            val searchedFromIndex: Boolean = false
        ) : UiState
        data class Error(val message: String) : UiState
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val PREFETCH_DISTANCE = 25
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Indexer state exposed for UI (progress banner, settings display). */
    val indexState: StateFlow<EpgIndexState> = EpgIndexer.getInstance(context).state

    private var searchJob: Job? = null
    private val searchService = XmltvSearchService(context)
    private var channelMatcher: EpgChannelMatcher? = null

    // Cache AppSettings instance to avoid constructing a new object on every property read
    private val appSettings = AppSettings(context)
    val isDevMode: Boolean get() = appSettings.isDevMode

    private val _sourceLabels = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sourceLabels: StateFlow<Map<Long, String>> = _sourceLabels.asStateFlow()

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
        loadSourceLabels()
        loadChannelMatcher()
    }

    private fun loadChannelMatcher() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val provider = ProviderDatabase.getInstance(context)
                        .providerDao().getActiveProvider() ?: return@withContext
                    val liveStreams = XtreamDatabase.getInstance(context)
                        .streamDao().getAllStreams(provider.id, XtreamStreamEntity.TYPE_LIVE)
                    if (liveStreams.isNotEmpty()) {
                        channelMatcher = EpgChannelMatcher(liveStreams)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EpgBrowserViewModel", "Failed to load live streams for channel matching", e)
                }
            }
        }
    }

    private fun loadSourceLabels() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val db = EpgIndexDatabase.getInstance(context)
                    val sources = db.epgSourceDao().getAllSourcesOnce()
                    _sourceLabels.value = sources.associate { it.id to it.label }
                } catch (e: Exception) {
                    android.util.Log.e("EpgBrowserViewModel", "Failed to load EPG source labels", e)
                }
            }
        }
    }

    private fun initPagedNowPlaying() {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value is EpgIndexState.Indexed) {
            val nowEpoch = System.currentTimeMillis() / 1000L
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()
            _pagedNowPlaying.value = Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    prefetchDistance = PREFETCH_DISTANCE,
                    enablePlaceholders = false
                )
            ) {
                dao.getPagedNowPlaying(nowEpoch)
            }.flow.cachedIn(viewModelScope)
        }
    }

    fun performSearch(query: String) {
        if (query.length < 2) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = UiState.Searching
            try {
                val startTime = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    searchService.search(query)
                }
                val elapsed = System.currentTimeMillis() - startTime

                if (result == null) {
                    _uiState.value = UiState.NoEpgFile
                    return@launch
                }

                // Convert all programmes to airings with programme info
                val allAirings = result.programmes.map { prog ->
                    val channel = result.channels[prog.channelId]
                    AiringWithProgramme(
                        title = prog.title,
                        description = prog.description,
                        category = prog.category,
                        airing = EpgBrowserAiring(
                            channelId = prog.channelId,
                            channelName = channel?.displayName ?: prog.channelId,
                            channelIconUrl = channel?.iconUrl,
                            startEpoch = prog.startEpoch,
                            endEpoch = prog.endEpoch,
                            sourceId = prog.sourceId
                        )
                    )
                }

                // Group by date, then by programme within each date
                val dateGroups = applyChannelMatching(groupByDate(allAirings))
                val totalAirings = allAirings.size
                val totalPrograms = dateGroups.sumOf { it.programs.size }

                _uiState.value = UiState.Results(
                    query = query,
                    dateGroups = dateGroups,
                    totalPrograms = totalPrograms,
                    totalAirings = totalAirings,
                    truncated = result.truncated,
                    searchTimeMs = elapsed,
                    searchedFromIndex = result.searchedFromIndex
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

    private fun initPagedSearch(query: String) {
        val indexer = EpgIndexer.getInstance(context)
        if (indexer.state.value is EpgIndexState.Indexed) {
            val now = System.currentTimeMillis() / 1000L
            val windowStart = now
            val windowEnd = now + 6 * 86400L

            val sanitized = query
                .replace("\"", "")
                .replace("*", "")
                .replace("(", "")
                .replace(")", "")
                .replace(":", "")
                .trim()
            if (sanitized.isBlank()) return

            val ftsQuery = "\"$sanitized\"*"
            val db = EpgIndexDatabase.getInstance(context)
            val dao = db.epgIndexDao()

            _pagedSearchResults.value = Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    prefetchDistance = PREFETCH_DISTANCE,
                    enablePlaceholders = false
                )
            ) {
                dao.searchByTitleFtsPaged(ftsQuery, windowStart, windowEnd)
            }.flow.cachedIn(viewModelScope)
        }
    }

    /**
     * Refresh the paged "Now Playing" data. Call when the user navigates
     * to the Now Playing view or when enough time has passed.
     */
    fun refreshNowPlaying() {
        initPagedNowPlaying()
    }

    private data class AiringWithProgramme(
        val title: String,
        val description: String?,
        val category: String?,
        val airing: EpgBrowserAiring
    )

    private fun applyChannelMatching(dateGroups: List<EpgBrowserDateGroup>): List<EpgBrowserDateGroup> {
        val matcher = channelMatcher ?: return dateGroups
        return dateGroups.map { group ->
            group.copy(
                programs = group.programs.map { program ->
                    val annotatedAirings = program.airings.map { airing ->
                        val matched = matcher.match(airing.channelId, airing.channelName)
                        if (matched != null) airing.copy(matchedStream = matched) else airing
                    }
                    // Sort matched-first, preserving startEpoch order within each group
                    val sorted = annotatedAirings.sortedWith(
                        compareByDescending<EpgBrowserAiring> { it.matchedStream != null }
                            .thenBy { it.startEpoch }
                    )
                    program.copy(airings = sorted)
                }
            )
        }
    }

    private fun groupByDate(airings: List<AiringWithProgramme>): List<EpgBrowserDateGroup> {
        val tz = TimeZone.getDefault()
        // Reuse a single formatter instance for display labels only
        val labelFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, Locale.getDefault()).apply { timeZone = tz }

        // Use epoch arithmetic for day-key grouping (avoids Date + SimpleDateFormat per airing)
        fun localDayOf(epochMillis: Long): Long = (epochMillis + tz.getOffset(epochMillis)) / 86400000L
        val nowMillis = System.currentTimeMillis()
        val todayDay = localDayOf(nowMillis)
        val tomorrowDay = todayDay + 1

        // Group airings by their local day number
        val byDay = airings.groupBy { localDayOf(it.airing.startEpoch * 1000L) }

        return byDay.entries
            .sortedBy { it.key }
            .map { (dayKey, dayAirings) ->
                // Compute date label
                val sampleDate = Date(dayAirings.first().airing.startEpoch * 1000L)
                val label = when (dayKey) {
                    todayDay -> "Today"
                    tomorrowDay -> "Tomorrow"
                    else -> labelFormat.format(sampleDate)
                }

                // Compute day start epoch for sorting
                val dayCal = Calendar.getInstance(tz).apply {
                    time = sampleDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Group by programme within this day
                val programs = dayAirings
                    .groupBy { it.title.trim().lowercase() to (it.description?.trim()?.lowercase() ?: "") }
                    .map { (_, group) ->
                        val rep = group.first()
                        EpgBrowserProgram(
                            title = rep.title,
                            description = rep.description ?: group.firstNotNullOfOrNull { it.description },
                            category = rep.category ?: group.firstNotNullOfOrNull { it.category },
                            airings = group.map { it.airing }.sortedBy { it.startEpoch }
                        )
                    }
                    .sortedBy { it.airings.first().startEpoch }

                EpgBrowserDateGroup(
                    dateLabel = label,
                    dayStartEpoch = dayCal.timeInMillis / 1000L,
                    programs = programs
                )
            }
    }
}
