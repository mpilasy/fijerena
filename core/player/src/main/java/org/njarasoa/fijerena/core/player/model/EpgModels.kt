package org.njarasoa.fijerena.core.player.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.njarasoa.fijerena.core.player.domain.MediaItem

@Serializable
data class EpgProgram(
    @SerialName("id") val id: String,
    @SerialName("epg_id") val epgId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("lang") val language: String? = null,
    @SerialName("start") val start: String,
    @SerialName("end") val end: String,
    @SerialName("description") val description: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("has_archive") val hasArchive: Int? = 0,
) {
    val startTime: Long get() = start.toLongOrNull() ?: 0L
    val endTime: Long get() = end.toLongOrNull() ?: 0L
    val duration: Long get() = endTime - startTime
}

/** Fraction of this program elapsed at [nowEpochSec], clamped to [0, 1]. */
fun EpgProgram.elapsedFraction(nowEpochSec: Long = System.currentTimeMillis() / 1000L): Float =
    if (duration > 0L) {
        ((nowEpochSec - startTime).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

@Serializable
data class EpgResponse(
    @SerialName("epg_listings") val listings: List<EpgProgram> = emptyList(),
)

/** Built once per EPG load and never mutated — see the note on [MediaItem]. */
@Immutable
data class EpgChannelRow(
    val channel: MediaItem,
    val programs: List<EpgProgram>,
)

data class TimeSlot(
    val startTime: Long,
    val endTime: Long,
    val slotIndex: Int,
) {
    val durationMinutes: Int get() = ((endTime - startTime) / 60).toInt()
}
