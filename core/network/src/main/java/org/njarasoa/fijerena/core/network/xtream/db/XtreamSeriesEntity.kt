package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_series",
    primaryKeys = ["seriesId", "providerId"],
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["categoryId", "providerId"]),
        Index(value = ["providerId", "categoryId", "excluded"]),
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
    val excluded: Boolean = false,
    // TMDB-derived / full-detail cache fields — populated once a detail screen fetch completes.
    val contentRating: String? = null,
    val tmdbId: String? = null,
    val detailFetchedAt: Long? = null,
    val posterPath: String? = null,
    // Set every time `get_series_info` successfully persists this series' episode list — the
    // 24h freshness check that lets a detail-screen open skip the network round trip and rebuild
    // straight from `xtream_episodes` (see XtreamMediaProvider.EPISODE_LIST_CACHE_TTL_MS). A
    // separate stamp from detailFetchedAt above: that one guards the TMDB content-rating/plot
    // enrichment cache (7 days), a different freshness question from "did the episode list itself
    // change".
    val episodesFetchedAt: Long? = null,
) {
    companion object {
        fun computeHash(
            seriesId: Int,
            providerId: Long,
            num: Int?,
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
            result = 31 * result + (num ?: 0)
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
