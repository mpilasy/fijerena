package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.EpgUtils
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.player.domain.MediaItem
import java.time.LocalDate
import java.time.ZoneId

class EpgViewModel(
    private val context: android.content.Context,
    private val categoryId: String
) : ViewModel() {

    private var repository: org.njarasoa.fijerena.core.network.MediaRepository? = null

    private suspend fun ensureRepo(): org.njarasoa.fijerena.core.network.MediaRepository {
        if (repository == null) {
            val container = org.njarasoa.fijerena.core.ui.di.AppContainer.getInstance(context)
            repository = container.getMediaRepository()
        }
        return repository!!
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val channelRows: List<EpgChannelRow>,
            val timeSlots: List<TimeSlot>,
            val currentTimeSlot: Int,
            val selectedDate: java.time.LocalDate,
            val epgLoadTime: String? = null,
            val epgMatchInfo: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class EpgSearchResult(
        val program: EpgProgram,
        val channel: org.njarasoa.fijerena.core.player.domain.MediaItem,
        val isCurrent: Boolean
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<EpgSearchResult>>(emptyList())
    val searchResults: StateFlow<List<EpgSearchResult>> = _searchResults.asStateFlow()

    private var currentDate = java.time.LocalDate.now()

    init {
        loadEpgData()
    }

    fun loadEpgData(date: java.time.LocalDate = currentDate) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            currentDate = date
            val startTime = System.currentTimeMillis()

            val repo = try {
                ensureRepo()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Initialization failed: ${e.message}")
                return@launch
            }

            // Check if provider supports EPG (allow if external XMLTV URL is configured)
            val capabilities = repo.getCapabilities()
            val hasExternalEpg = repo.hasIndexedEpgData()
            if (capabilities != null && !capabilities.supportsEpg && !hasExternalEpg) {
                _uiState.value = UiState.Error("EPG is not supported by this provider")
                return@launch
            }

            // Get items for category
            val itemsResult = repo.getItems(categoryId, "LIVE_TV")
            val items = itemsResult.getOrElse {
                _uiState.value = UiState.Error("Failed to load channels: ${it.message}")
                return@launch
            }.take(50)

            if (items.isEmpty()) {
                _uiState.value = UiState.Error("No channels found in this category")
                return@launch
            }

            // Get EPG for all items (uses XMLTV if configured, falls back to provider EPG)
            val epgResult = repo.getEpgBulkForItems(items)
            val epgData = epgResult.getOrElse {
                _uiState.value = UiState.Error("Failed to load EPG data: ${it.message}")
                return@launch
            }

            if (epgData.isEmpty()) {
                _uiState.value = UiState.Error("No EPG data available for these channels")
                return@launch
            }

            val channelRows = buildChannelRows(items, epgData, date)
            val timeSlots = generateTimeSlots(date)
            val currentSlot = calculateCurrentTimeSlot(timeSlots)
            val elapsed = System.currentTimeMillis() - startTime

            _uiState.value = UiState.Success(
                channelRows = channelRows,
                timeSlots = timeSlots,
                currentTimeSlot = currentSlot,
                selectedDate = date,
                epgLoadTime = "${elapsed}ms",
                epgMatchInfo = "${epgData.size}/${items.size} channels matched"
            )
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            val repo = ensureRepo()
            _isRefreshing.value = true
            repo.clearEpgCache()
            repo.clearXmltvCache()
            loadEpgData(currentDate)
            _isRefreshing.value = false
        }
    }

    fun selectPreviousDay() = loadEpgData(currentDate.minusDays(1))

    fun selectNextDay() = loadEpgData(currentDate.plusDays(1))

    fun jumpToNow() = loadEpgData(java.time.LocalDate.now())

    fun searchPrograms(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val state = _uiState.value
        if (state !is UiState.Success) return
        val lowerQuery = query.lowercase()
        _searchResults.value = state.channelRows.flatMap { row ->
            row.programs
                .filter { it.title.lowercase().contains(lowerQuery) }
                .map { program ->
                    EpgSearchResult(
                        program = program,
                        channel = row.channel,
                        isCurrent = org.njarasoa.fijerena.core.player.model.EpgUtils.isCurrentProgram(program)
                    )
                }
        }.sortedByDescending { it.isCurrent }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    private fun buildChannelRows(
        items: List<org.njarasoa.fijerena.core.player.domain.MediaItem>,
        epgData: Map<String, org.njarasoa.fijerena.core.player.model.EpgResponse>,
        date: java.time.LocalDate
    ): List<org.njarasoa.fijerena.core.player.model.EpgChannelRow> {
        val dayStart = date.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        val dayEnd = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()

        return items.map { item ->
            val programs = epgData[item.id]?.listings?.filter {
                it.startTime in dayStart..dayEnd || it.endTime in dayStart..dayEnd ||
                (it.startTime < dayStart && it.endTime > dayEnd)
            }?.sortedBy { it.startTime } ?: emptyList()

            org.njarasoa.fijerena.core.player.model.EpgChannelRow(item, programs)
        }
    }

    private fun generateTimeSlots(date: java.time.LocalDate): List<org.njarasoa.fijerena.core.player.model.TimeSlot> {
        val slots = mutableListOf<org.njarasoa.fijerena.core.player.model.TimeSlot>()
        val dayStart = date.atStartOfDay(java.time.ZoneId.systemDefault())

        for (i in 0 until 48) {
            val slotStart = dayStart.plusMinutes(i * 30L)
            val slotEnd = slotStart.plusMinutes(30)
            slots.add(
                org.njarasoa.fijerena.core.player.model.TimeSlot(
                    startTime = slotStart.toEpochSecond(),
                    endTime = slotEnd.toEpochSecond(),
                    slotIndex = i
                )
            )
        }
        return slots
    }

    private fun calculateCurrentTimeSlot(timeSlots: List<org.njarasoa.fijerena.core.player.model.TimeSlot>): Int {
        val now = System.currentTimeMillis() / 1000
        val index = timeSlots.indexOfFirst { now in it.startTime..it.endTime }
        return if (index >= 0) index else 0
    }
}

