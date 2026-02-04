package org.njarasoa.fijerena.core.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.TimeSlot
import org.njarasoa.fijerena.core.player.model.XtreamStream
import java.time.LocalDate
import java.time.ZoneId

class EpgViewModel(
    private val repository: XtreamRepository,
    private val categoryId: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val channelRows: List<EpgChannelRow>,
            val timeSlots: List<TimeSlot>,
            val currentTimeSlot: Int,
            val selectedDate: LocalDate
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentDate = LocalDate.now()

    init {
        loadEpgData()
    }

    fun loadEpgData(date: LocalDate = currentDate) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            currentDate = date

            // Get streams for category
            val streamsResult = repository.getStreams(categoryId)
            val streams = when (streamsResult) {
                is Result.Success -> streamsResult.data.take(50) // Limit to 50 channels for performance
                is Result.Error -> {
                    _uiState.value = UiState.Error("Failed to load channels: ${streamsResult.exception.message}")
                    return@launch
                }
            }

            if (streams.isEmpty()) {
                _uiState.value = UiState.Error("No channels found in this category")
                return@launch
            }

            // Get EPG for all streams
            val streamIds = streams.map { it.streamId }
            val epgResult = repository.getEpgForStreams(streamIds)
            val epgData = when (epgResult) {
                is Result.Success -> epgResult.data
                is Result.Error -> {
                    _uiState.value = UiState.Error("Failed to load EPG data: ${epgResult.exception.message}")
                    return@launch
                }
            }

            if (epgData.isEmpty()) {
                _uiState.value = UiState.Error("No EPG data available for these channels")
                return@launch
            }

            // Build channel rows with filtered programs for selected date
            val channelRows = buildChannelRows(streams, epgData, date)

            if (channelRows.isEmpty()) {
                _uiState.value = UiState.Error("No EPG data available for $date")
                return@launch
            }

            // Generate time slots (48 x 30-minute slots = 24 hours)
            val timeSlots = generateTimeSlots(date)

            // Calculate current time slot index
            val currentSlot = calculateCurrentTimeSlot(timeSlots)

            _uiState.value = UiState.Success(
                channelRows = channelRows,
                timeSlots = timeSlots,
                currentTimeSlot = currentSlot,
                selectedDate = date
            )
        }
    }

    fun selectPreviousDay() = loadEpgData(currentDate.minusDays(1))

    fun selectNextDay() = loadEpgData(currentDate.plusDays(1))

    fun jumpToNow() = loadEpgData(LocalDate.now())

    private fun buildChannelRows(
        streams: List<XtreamStream>,
        epgData: Map<Int, org.njarasoa.fijerena.core.player.model.EpgResponse>,
        date: LocalDate
    ): List<EpgChannelRow> {
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

        return streams.mapNotNull { stream ->
            val programs = epgData[stream.streamId]?.listings?.filter {
                // Include programs that start or end within the selected day
                it.startTime in dayStart..dayEnd || it.endTime in dayStart..dayEnd ||
                // Also include programs that span the entire day
                (it.startTime < dayStart && it.endTime > dayEnd)
            }?.sortedBy { it.startTime } ?: emptyList()

            if (programs.isNotEmpty()) {
                EpgChannelRow(stream, programs)
            } else null
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
