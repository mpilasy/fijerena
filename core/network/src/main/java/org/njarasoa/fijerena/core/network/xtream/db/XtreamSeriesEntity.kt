package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_series",
    primaryKeys = ["seriesId", "providerId"],
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["categoryId", "providerId"]),
        // Backs XtreamSeriesDao.getByTmdbId() and the sibling-series lookups in
        // XtreamEpisodeDao (getSiblingCompletedEpisodeIds/getSiblingCompletedCountsBySeries/
        // clearGroupCompletion) — none of the existing indices cover tmdbId at all.
        Index(value = ["providerId", "tmdbId"]),
    ],
)
data class XtreamSeriesEntity(
    val seriesId: Int,
    val providerId: Long,
    val num: Int? = null,
    val name: String,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val lastModified: String? = null,
    val rating: String? = null,
    val rating5based: Double? = null,
    val youtubeTrailer: String? = null,
    val episodeRunTime: String? = null,
    val categoryId: String,
    val backdropPath: String? = null, // Comma separated URLs
    val contentHash: Int = 0,
    // Unused since schema v24: series follow their category's flag (see XtreamSeriesDao).
    val excluded: Boolean = false,
    // TMDB-derived / full-detail cache fields — populated once a detail screen fetch completes.
    val contentRating: String? = null,
    val tmdbId: String? = null,
    val detailFetchedAt: Long? = null,
    val posterPath: String? = null,
    // Set every time `get_series_info` successfully persists this series' episode list — the
    // freshness check that lets a detail-screen open skip the network round trip and rebuild
    // straight from `xtream_episodes` (see XtreamMediaProvider.EPISODE_LIST_CACHE_TTL_MS). A
    // separate stamp from detailFetchedAt above: that one guards the TMDB content-rating/plot
    // enrichment cache (7 days), a different freshness question from "did the episode list itself
    // change". A catalogue sync clears it when the series' catalogue entry changes (see withDetailCache).
    val episodesFetchedAt: Long? = null,
) {
    companion object {
        /**
         * Hash of the provider's catalogue fields, compared on each sync to skip unchanged rows.
         * `num` is left out: it is the series' position in the provider's list, which shifts for
         * tens of thousands of rows whenever the provider adds one, and series are listed by name.
         */
        fun computeHash(
            seriesId: Int,
            providerId: Long,
            name: String,
            cover: String?,
            plot: String?,
            cast: String?,
            director: String?,
            genre: String?,
            releaseDate: String?,
            lastModified: String?,
            rating: String?,
            rating5based: Double?,
            youtubeTrailer: String?,
            episodeRunTime: String?,
            categoryId: String,
            backdropPath: String?,
            tmdbId: String?,
        ): Int {
            var result = seriesId
            result = 31 * result + providerId.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + (cover?.hashCode() ?: 0)
            result = 31 * result + (plot?.hashCode() ?: 0)
            result = 31 * result + (cast?.hashCode() ?: 0)
            result = 31 * result + (director?.hashCode() ?: 0)
            result = 31 * result + (genre?.hashCode() ?: 0)
            result = 31 * result + (releaseDate?.hashCode() ?: 0)
            result = 31 * result + (lastModified?.hashCode() ?: 0)
            result = 31 * result + (rating?.hashCode() ?: 0)
            result = 31 * result + (rating5based?.hashCode() ?: 0)
            result = 31 * result + (youtubeTrailer?.hashCode() ?: 0)
            result = 31 * result + (episodeRunTime?.hashCode() ?: 0)
            result = 31 * result + categoryId.hashCode()
            result = 31 * result + (backdropPath?.hashCode() ?: 0)
            result = 31 * result + (tmdbId?.hashCode() ?: 0)
            return result
        }
    }
}

/** The columns a detail-screen fetch fills in (see [XtreamSeriesDao.updateDetailCache]). */
data class XtreamSeriesDetailCache(
    val seriesId: Int,
    val contentRating: String?,
    val tmdbId: String?,
    val detailFetchedAt: Long?,
    val posterPath: String?,
)

/**
 * This catalogue row with [cache]'s detail-screen columns carried over, so a sync that rewrites a
 * changed series doesn't throw away its TMDB details. The catalogue's own `tmdbId` wins when it has one.
 *
 * `episodesFetchedAt` is deliberately not carried over: the provider changed this series (usually
 * new episodes), so the stored episode list must be fetched again on the next open.
 */
fun XtreamSeriesEntity.withDetailCache(cache: XtreamSeriesDetailCache?): XtreamSeriesEntity =
    if (cache == null) {
        this
    } else {
        copy(
            contentRating = cache.contentRating,
            tmdbId = tmdbId ?: cache.tmdbId,
            detailFetchedAt = cache.detailFetchedAt,
            posterPath = cache.posterPath,
        )
    }
