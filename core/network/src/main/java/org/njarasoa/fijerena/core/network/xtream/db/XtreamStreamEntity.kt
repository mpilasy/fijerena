package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_streams",
    primaryKeys = ["streamId", "providerId", "type"],
    indices = [
        Index(value = ["providerId", "type"]),
        Index(value = ["categoryId", "providerId"]),
        // Composite index covering getStreamsByCategory query (providerId + type + categoryId)
        Index(value = ["providerId", "type", "categoryId"])
    ]
)
data class XtreamStreamEntity(
    val streamId: Int,
    val providerId: Long,
    val type: String, // LIVE, VOD

    val num: Int,
    val name: String,
    val streamType: String,
    val streamIcon: String? = null,
    val epgChannelId: String? = null,
    val added: String? = null,
    val categoryId: String,
    val customSid: String? = null,
    val tvArchive: Int = 0,
    val directSource: String? = null,
    val tvArchiveDuration: Int = 0,
    val contentHash: Int = 0
) {
    /** Hash of content fields only, excluding [contentHash] itself to avoid self-referential comparison. */
    fun computeContentHash(): Int {
        var result = streamId
        result = 31 * result + providerId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + num
        result = 31 * result + name.hashCode()
        result = 31 * result + streamType.hashCode()
        result = 31 * result + (streamIcon?.hashCode() ?: 0)
        result = 31 * result + (epgChannelId?.hashCode() ?: 0)
        result = 31 * result + (added?.hashCode() ?: 0)
        result = 31 * result + categoryId.hashCode()
        result = 31 * result + (customSid?.hashCode() ?: 0)
        result = 31 * result + tvArchive
        result = 31 * result + (directSource?.hashCode() ?: 0)
        result = 31 * result + tvArchiveDuration
        return result
    }

    companion object {
        const val TYPE_LIVE = "LIVE"
        const val TYPE_VOD = "VOD"
    }
}
