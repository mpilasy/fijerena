package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_episodes",
    primaryKeys = ["id", "providerId"],
    indices = [
        Index(value = ["seriesId", "providerId"]),
        Index(value = ["providerId"]),
        // Backs the sibling-episode joins in XtreamEpisodeDao (matching by (season, episodeNum)
        // across sibling series sharing a tmdbId) — those filter/join on this triple with no
        // covering index today.
        Index(value = ["providerId", "season", "episodeNum"]),
    ],
)
data class XtreamEpisodeEntity(
    val id: String, // Xtream episode ID (string)
    val seriesId: Int,
    val providerId: Long,
    val season: Int? = null,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String,
    // Info from EpisodeInfo
    val overview: String? = null,
    val plot: String? = null,
    val airDate: String? = null,
    val duration: String? = null,
    val durationSecs: Int? = null,
    val bitrate: Int? = null,
    val rating: String? = null,
    val movieImage: String? = null,
    val tmdbId: String? = null,
    /** When [plot] was backfilled from TMDB — synopses go stale far slower than an episode list. */
    val plotFetchedAt: Long? = null,
    val contentHash: Int = 0,
)
