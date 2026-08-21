package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.trailerUrl

/**
 * Rebuilds a [SeriesDetail] from the rows already on disk, with no network call.
 *
 * Null when there are no stored episodes: a show with a synopsis and an empty episode list is
 * worse than a spinner, since the screen exists to pick an episode from.
 *
 * The seasons list is left empty on purpose. `sortedSeasons` derives seasons from the episode map
 * whenever the API didn't supply one, so a cached detail sorts and groups identically to a fetched
 * one without persisting a season table that only restates what the episodes already say.
 */
internal fun buildCachedSeriesDetail(
    series: XtreamSeriesEntity,
    episodes: List<XtreamEpisodeEntity>,
): SeriesDetail? {
    if (episodes.isEmpty()) return null

    return SeriesDetail(
        id = series.seriesId.toString(),
        name = series.name,
        metadata =
            MediaMetadata(
                plot = series.plot,
                cast = series.cast,
                director = series.director,
                genre = series.genre,
                releaseDate = series.releaseDate,
                rating = series.rating,
                contentRating = series.contentRating,
                duration = series.episodeRunTime,
                tmdbId = series.tmdbId,
                trailerUrl = trailerUrl(series.youtubeTrailer),
            ),
        coverUrl = series.cover,
        episodes =
            episodes
                .groupBy { (it.season ?: 0).toString() }
                .mapValues { (_, seasonEpisodes) -> seasonEpisodes.map { it.toEpisodeItem() } },
    )
}

private fun XtreamEpisodeEntity.toEpisodeItem(): EpisodeItem =
    EpisodeItem(
        id = id,
        episodeNumber = episodeNum,
        title = title,
        seasonNumber = season,
        extension = containerExtension,
        metadata =
            MediaMetadata(
                // Xtream's own synopsis when it sent one, else the TMDB overview backfilled later.
                plot = plot ?: overview,
                duration = duration,
                durationSecs = durationSecs,
                bitrate = bitrate,
                rating = rating,
                airDate = airDate,
                releaseDate = airDate,
                tmdbId = tmdbId,
            ),
        thumbnailUrl = movieImage,
    )
