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
    val contentHash: Int = 0,
    val description: String? = null,

    // VOD metadata
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val youtubeTrailer: String? = null
) {
    companion object {
        const val TYPE_LIVE = "LIVE"
        const val TYPE_VOD = "VOD"

        fun computeHash(
            streamId: Int,
            providerId: Long,
            type: String,
            num: Int,
            name: String,
            streamType: String,
            streamIcon: String?,
            epgChannelId: String?,
            added: String?,
            categoryId: String,
            customSid: String?,
            tvArchive: Int,
            directSource: String?,
            tvArchiveDuration: Int,
            description: String? = null,
            cast: String? = null,
            director: String? = null,
            genre: String? = null,
            releaseDate: String? = null,
            rating: String? = null,
            duration: String? = null,
            youtubeTrailer: String? = null
        ): Int {
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
            result = 31 * result + (description?.hashCode() ?: 0)
            result = 31 * result + (cast?.hashCode() ?: 0)
            result = 31 * result + (director?.hashCode() ?: 0)
            result = 31 * result + (genre?.hashCode() ?: 0)
            result = 31 * result + (releaseDate?.hashCode() ?: 0)
            result = 31 * result + (rating?.hashCode() ?: 0)
            result = 31 * result + (duration?.hashCode() ?: 0)
            result = 31 * result + (youtubeTrailer?.hashCode() ?: 0)
            return result
        }
    }
}
