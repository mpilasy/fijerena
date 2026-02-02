package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single EPG program entry.
 */
@Serializable
data class EpgProgram(
    @SerialName("id") val id: String,
    @SerialName("epg_id") val epgId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("lang") val language: String? = null,
    @SerialName("start") val start: String,  // Unix timestamp as string
    @SerialName("end") val end: String,
    @SerialName("description") val description: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("has_archive") val hasArchive: Int? = 0
) {
    val startTime: Long get() = start.toLongOrNull() ?: 0L
    val endTime: Long get() = end.toLongOrNull() ?: 0L
    val duration: Long get() = endTime - startTime
}

/**
 * Response from EPG API endpoints.
 */
@Serializable
data class EpgResponse(
    @SerialName("epg_listings") val listings: List<EpgProgram> = emptyList()
)

/**
 * Represents a row in the EPG grid (one channel with its programs).
 */
data class EpgChannelRow(
    val stream: XtreamStream,
    val programs: List<EpgProgram>
)

/**
 * Represents a 30-minute time slot in the EPG grid.
 */
data class TimeSlot(
    val startTime: Long,
    val endTime: Long,
    val slotIndex: Int
) {
    val durationMinutes: Int get() = ((endTime - startTime) / 60).toInt()
}
