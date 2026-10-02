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
        Index(value = ["providerId", "type", "categoryId"]),
        // Serves Phase 5 TMDB dedup lookups (see 20260828_watch-state-durable-storage-plan.md)
        Index(value = ["providerId", "tmdbId"]),
    ],
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
    val youtubeTrailer: String? = null,
    // Unused since schema v24: streams follow their category's flag (see XtreamStreamDao).
    // Dropping the column needs SQLite 3.35; minSdk 30 ships 3.28.
    val excluded: Boolean = false,
    // TMDB-derived / full-detail cache fields — populated once a detail screen fetch completes.
    val contentRating: String? = null,
    val tmdbId: String? = null,
    val containerExtension: String? = null,
    val detailFetchedAt: Long? = null,
    val posterPath: String? = null,
) {
    companion object {
        const val TYPE_LIVE = "LIVE"
        const val TYPE_VOD = "VOD"

        /**
         * Hash of the provider's catalogue fields, compared on each sync to skip unchanged rows.
         * `num` is left out: it is the stream's position in the provider's list, which shifts for
         * tens of thousands of rows whenever the provider adds one. Position changes go through
         * [XtreamStreamDao.updateNums] instead of rewriting the row.
         */
        fun computeHash(
            streamId: Int,
            providerId: Long,
            type: String,
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
            youtubeTrailer: String? = null,
            tmdbId: String? = null,
        ): Int {
            var result = streamId
            result = 31 * result + providerId.hashCode()
            result = 31 * result + type.hashCode()
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
            result = 31 * result + (tmdbId?.hashCode() ?: 0)
            return result
        }
    }
}

/** Stored state a catalogue sync compares against: the content hash, and the list position kept outside it. */
data class XtreamStreamSyncState(
    val streamId: Int,
    val num: Int,
    val contentHash: Int,
)

/** The columns a detail-screen fetch fills in (see [XtreamStreamDao.updateDetailCache]). */
data class XtreamStreamDetailCache(
    val streamId: Int,
    val contentRating: String?,
    val tmdbId: String?,
    val containerExtension: String?,
    val detailFetchedAt: Long?,
    val posterPath: String?,
)

/**
 * This catalogue row with [cache]'s detail-screen columns carried over, so a sync that rewrites a
 * changed movie doesn't throw away its TMDB details. The catalogue's own `tmdbId` wins when it has one.
 */
fun XtreamStreamEntity.withDetailCache(cache: XtreamStreamDetailCache?): XtreamStreamEntity =
    if (cache == null) {
        this
    } else {
        copy(
            contentRating = cache.contentRating,
            tmdbId = tmdbId ?: cache.tmdbId,
            containerExtension = cache.containerExtension,
            detailFetchedAt = cache.detailFetchedAt,
            posterPath = cache.posterPath,
        )
    }
