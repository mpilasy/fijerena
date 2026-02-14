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
    private val repository: MediaRepository,
    private val categoryId: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val channelRows: List<EpgChannelRow>,
            val timeSlots: List<TimeSlot>,
            val currentTimeSlot: Int,
            val selectedDate: LocalDate,
            val epgLoadTime: String? = null,
            val epgMatchInfo: String? = null
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class EpgSearchResult(
        val program: EpgProgram,
        val channel: MediaItem,
        val isCurrent: Boolean
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<EpgSearchResult>>(emptyList())
    val searchResults: StateFlow<List<EpgSearchResult>> = _searchResults.asStateFlow()

    private var currentDate = LocalDate.now()

    init {
        loadEpgData()
    }

    fun loadEpgData(date: LocalDate = currentDate) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            currentDate = date
            val startTime = System.currentTimeMillis()

            // Check if provider supports EPG (allow if external XMLTV URL is configured)
            val capabilities = repository.getCapabilities()
            val hasExternalEpg = repository.hasIndexedEpgData()
            if (capabilities != null && !capabilities.supportsEpg && !hasExternalEpg) {
                _uiState.value = UiState.Error("EPG is not supported by this provider")
                return@launch
            }

            // Get items for category
            val itemsResult = repository.getItems(categoryId, "LIVE_TV")
            val items = itemsResult.getOrElse {
                _uiState.value = UiState.Error("Failed to load channels: ${it.message}")
                return@launch
            }.take(50)

            if (items.isEmpty()) {
                _uiState.value = UiState.Error("No channels found in this category")
                return@launch
            }

            // Get EPG for all items (uses XMLTV if configured, falls back to provider EPG)
            val epgResult = repository.getEpgBulkForItems(items)
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
            _isRefreshing.value = true
            repository.clearEpgCache()
            repository.clearXmltvCache()
            loadEpgData(currentDate)
            _isRefreshing.value = false
        }
    }

    fun selectPreviousDay() = loadEpgData(currentDate.minusDays(1))

    fun selectNextDay() = loadEpgData(currentDate.plusDays(1))

    fun jumpToNow() = loadEpgData(LocalDate.now())

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
                        isCurrent = EpgUtils.isCurrentProgram(program)
                    )
                }
        }.sortedByDescending { it.isCurrent }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    private fun buildChannelRows(
        items: List<MediaItem>,
        epgData: Map<String, EpgResponse>,
        date: LocalDate
    ): List<EpgChannelRow> {
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

        return items.map { item ->
            val programs = epgData[item.id]?.listings?.filter {
                it.startTime in dayStart..dayEnd || it.endTime in dayStart..dayEnd ||
                (it.startTime < dayStart && it.endTime > dayEnd)
            }?.sortedBy { it.startTime } ?: emptyList()

            EpgChannelRow(item, programs)
        }
    }

    private fun generateTimeSlots(date: LocalDate): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        val dayStart = date.atStartOfDay(ZoneId.systemDefault())

        for (i in 0 until 48) {
            val slotStart = dayStart.plusMinutes(i * 30L)
            val slotEnd = slotStart.plusMinutes(30)
            slots.add(
                TimeSlot(
                    startTime = slotStart.toEpochSecond(),
                    endTime = slotEnd.toEpochSecond(),
                    slotIndex = i
                )
            )
        }
        return slots
    }

    private fun calculateCurrentTimeSlot(timeSlots: List<TimeSlot>): Int {
        val now = System.currentTimeMillis() / 1000
        val index = timeSlots.indexOfFirst { now in it.startTime..it.endTime }
        return if (index >= 0) index else 0
    }
}
