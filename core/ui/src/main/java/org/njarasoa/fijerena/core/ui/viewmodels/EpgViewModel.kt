package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.ui.di.AppContainer
import java.time.LocalDate
import java.time.ZoneId

class EpgViewModel(
    private val context: Context,
    private val categoryId: String,
) : ViewModel() {
    sealed class UiState {
        data object Loading : UiState()

        data class Success(
            val channelRows: List<EpgChannelRow>,
            val timeSlots: List<TimeSlot>,
            val currentTimeSlot: Int,
            val selectedDate: LocalDate,
            val epgLoadTime: String? = null,
            val epgMatchInfo: String? = null,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class EpgSearchResult(
        val program: EpgProgram,
        val channel: MediaItem,
        val isCurrent: Boolean,
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<EpgSearchResult>>(emptyList())
    val searchResults: StateFlow<List<EpgSearchResult>> = _searchResults.asStateFlow()

    // Lazily initialized in init coroutine to avoid blocking the UI thread
    private lateinit var repository: MediaRepository

    private var currentDate = LocalDate.now()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository = AppContainer.getInstance(context).getMediaRepository()
            loadEpgDataInternal(currentDate)
        }
    }

    fun loadEpgData(date: LocalDate = currentDate) {
        viewModelScope.launch {
            if (!::repository.isInitialized) {
                repository = AppContainer.getInstance(context).getMediaRepository()
            }
            loadEpgDataInternal(date)
        }
    }

    private suspend fun loadEpgDataInternal(date: LocalDate) {
        _uiState.value = UiState.Loading
        currentDate = date
        val startTime = System.currentTimeMillis()

        // Check if provider supports EPG (allow if external XMLTV URL is configured)
        val capabilities = repository.getCapabilities()
        val hasExternalEpg = repository.hasIndexedEpgData()
        if (capabilities != null && !capabilities.supportsEpg && !hasExternalEpg) {
            _uiState.value = UiState.Error("EPG is not supported by this provider")
            return
        }

        // Get items for category
        val itemsResult = repository.getItems(categoryId, ContentType.LIVE_TV)
        val items =
            itemsResult
                .getOrElse {
                    _uiState.value = UiState.Error("Failed to load channels: ${it.message}")
                    return
                }.take(50)

        if (items.isEmpty()) {
            _uiState.value = UiState.Error("No channels found in this category")
            return
        }

        // Get EPG for all items (uses XMLTV if configured, falls back to provider EPG)
        val epgResult = repository.getEpgBulkForItems(items)
        val epgData =
            epgResult.getOrElse {
                _uiState.value = UiState.Error("Failed to load EPG data: ${it.message}")
                return
            }

        if (epgData.isEmpty()) {
            _uiState.value = UiState.Error("No EPG data available for these channels")
            return
        }

        // Pre-sort listings once so buildChannelRows can use binary search
        val sortedEpgData =
            epgData.mapValues { (_, response) ->
                EpgResponse(response.listings.sortedBy { it.startTime })
            }
        val channelRows = buildChannelRows(items, sortedEpgData, date)
        val timeSlots = generateTimeSlots(date)
        val currentSlot = calculateCurrentTimeSlot(timeSlots)
        val elapsed = System.currentTimeMillis() - startTime

        _uiState.value =
            UiState.Success(
                channelRows = channelRows,
                timeSlots = timeSlots,
                currentTimeSlot = currentSlot,
                selectedDate = date,
                epgLoadTime = "${elapsed}ms",
                epgMatchInfo = "${epgData.size}/${items.size} channels matched",
            )
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
            searchJob?.cancel()
            _searchResults.value = emptyList()
            return
        }
        // Debounce: cancel previous search, wait 200ms before scanning all programs
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(200)
            val state = _uiState.value
            if (state !is UiState.Success) return@launch
            val now = System.currentTimeMillis() / 1000

            val processors = Runtime.getRuntime().availableProcessors()
            val chunkSize = maxOf(1, state.channelRows.size / processors)

            val deferredResults = state.channelRows.chunked(chunkSize).map { chunk ->
                async(Dispatchers.Default) {
                    val current = mutableListOf<EpgSearchResult>()
                    val others = mutableListOf<EpgSearchResult>()
                    for (row in chunk) {
                        val channel = row.channel
                        for (program in row.programs) {
                            if (program.title.indexOf(query, ignoreCase = true) >= 0) {
                                val isCurrent = now in program.startTime..program.endTime
                                val result = EpgSearchResult(program, channel, isCurrent)
                                if (isCurrent) current.add(result) else others.add(result)
                            }
                        }
                    }
                    Pair(current, others)
                }
            }

            val finalCurrent = mutableListOf<EpgSearchResult>()
            val finalOthers = mutableListOf<EpgSearchResult>()
            deferredResults.awaitAll().forEach { (current, others) ->
                finalCurrent.addAll(current)
                finalOthers.addAll(others)
            }

            _searchResults.value = finalCurrent + finalOthers
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    private fun buildChannelRows(
        items: List<MediaItem>,
        epgData: Map<String, EpgResponse>,
        date: LocalDate,
    ): List<EpgChannelRow> {
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

        return items.map { item ->
            val listings = epgData[item.id]?.listings ?: emptyList()
            // Listings are pre-sorted by startTime — use binary search to find day range
            // Find first program that could overlap with the day (endTime > dayStart)
            var lo = 0
            var hi = listings.size
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (listings[mid].endTime <= dayStart) lo = mid + 1 else hi = mid
            }
            val start = lo
            // Find first program that starts after dayEnd (no overlap possible)
            hi = listings.size
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (listings[mid].startTime <= dayEnd) lo = mid + 1 else hi = mid
            }
            EpgChannelRow(item, listings.subList(start, lo))
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
                    slotIndex = i,
                ),
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
