package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.XmltvSearchService
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer

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

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Indexer state exposed for UI (progress banner, settings display). */
    val indexState: StateFlow<EpgIndexState> = EpgIndexer.getInstance(context).state

    private var searchJob: Job? = null
    private val searchService = XmltvSearchService(context)

    /** EPG file size in bytes, or null if no file. */
    val epgFileSizeBytes: Long?

    init {
        val managerFile = EpgFileManager.getInstance(context).getEpgFile()
        val physicalFile = java.io.File(context.cacheDir, "xmltv_global.xml")
        val file = managerFile
            ?: if (physicalFile.exists() && physicalFile.length() > 0) physicalFile else null
        epgFileSizeBytes = file?.length()
        if (file == null) {
            _uiState.value = UiState.NoEpgFile
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
            } catch (e: OutOfMemoryError) {
                System.gc()
                _uiState.value = UiState.Error("EPG file too large for search. Try a more specific query.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Search failed")
            }
        }
    }
}
