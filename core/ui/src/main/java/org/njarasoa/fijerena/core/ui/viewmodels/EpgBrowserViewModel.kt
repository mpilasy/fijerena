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
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.XmltvSearchService
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
            val programs: List<EpgBrowserProgram>,
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

                // Group programmes by normalized title
                val grouped = result.programmes
                    .groupBy { it.title.trim().lowercase() }
                    .map { (_, programmes) ->
                        val representative = programmes.first()
                        EpgBrowserProgram(
                            title = representative.title,
                            description = representative.description
                                ?: programmes.firstNotNullOfOrNull { it.description },
                            category = representative.category
                                ?: programmes.firstNotNullOfOrNull { it.category },
                            airings = programmes.map { prog ->
                                val channel = result.channels[prog.channelId]
                                EpgBrowserAiring(
                                    channelId = prog.channelId,
                                    channelName = channel?.displayName ?: prog.channelId,
                                    channelIconUrl = channel?.iconUrl,
                                    startEpoch = prog.startEpoch,
                                    endEpoch = prog.endEpoch
                                )
                            }.sortedBy { it.startEpoch }
                        )
                    }
                    .sortedByDescending { it.airings.size }

                val totalAirings = grouped.sumOf { it.airings.size }

                _uiState.value = UiState.Results(
                    query = query,
                    programs = grouped,
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
            val windowStart = now - 86400L
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
}
