package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.network.asString

import org.njarasoa.fijerena.core.player.domain.AudioTechInfo
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.VideoTechInfo
import org.njarasoa.fijerena.core.player.model.Episode
import org.njarasoa.fijerena.core.player.model.Season
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

object XtreamMapper {
    fun XtreamCategory.toDomain(): MediaCategory =
        MediaCategory(
            id = categoryId,
            name = categoryName,
            parentId = if (parentId != 0) parentId.toString() else null,
        )

    fun XtreamStream.toDomain(mediaType: MediaType): MediaItem =
        MediaItem(
            id = streamId.toString(),
            name = name,
            mediaType = mediaType,
            categoryId = categoryId,
            thumbnailUrl = streamIcon,
            providerData =
                buildMap {
                    put("streamType", streamType)
                    epgChannelId?.let { put("epgChannelId", it) }
                    if (tvArchive != 0) put("tvArchive", tvArchive.toString())
                    directSource?.let { put("directSource", it) }
                },
            metadata =
                MediaMetadata(
                    plot = description.asString(),
                    cast = cast,
                    director = director,
                    genre = genre,
                    releaseDate = releaseDate,
                    rating = rating.asString(),
                    duration = duration.asString(),
                ),
        )

    fun XtreamSeries.toDomain(): MediaItem =
        MediaItem(
            id = seriesId.toString(),
            name = name,
            mediaType = MediaType.SERIES,
            categoryId = categoryId,
            thumbnailUrl = cover,
            metadata =
                MediaMetadata(
                    plot = plot.asString(),
                    cast = cast,
                    director = director,
                    genre = genre,
                    releaseDate = releaseDate,
                    rating = rating.asString(),
                ),
        )

    fun SeriesInfo.toDomain(seriesId: String): SeriesDetail =
        SeriesDetail(
            id = seriesId,
            name = info?.name ?: "",
            metadata =
                MediaMetadata(
                    plot = info?.plot.asString(),
                    cast = info?.cast,
                    director = info?.director,
                    genre = info?.genre,
                    releaseDate = info?.releaseDate,
                    rating = info?.rating.asString(),
                ),
            coverUrl = info?.cover,
            seasons = seasons.map { it.toDomain() },
            episodes =
                episodes.mapValues { (_, episodeList) ->
                    episodeList.map { it.toDomain() }
                },
        )

    fun Season.toDomain(): SeasonInfo =
        SeasonInfo(
            seasonNumber = seasonNumber,
            name = name,
            episodeCount = episodeCount,
            coverUrl = cover,
        )

    fun Episode.toDomain(): EpisodeItem =
        EpisodeItem(
            id = id,
            episodeNumber = episodeNum,
            title = title,
            seasonNumber = season,
            extension = containerExtension,
            metadata =
                MediaMetadata(
                    plot = info?.plot.asString() ?: info?.overview.asString(),
                    duration = info?.duration.asString(),
                    durationSecs = info?.durationSecs,
                    bitrate = info?.bitrate,
                    rating = info?.rating.asString(),
                    airDate = info?.airDate,
                    releaseDate = info?.airDate,
                    tmdbId = info?.tmdbId,
                ),
            thumbnailUrl = info?.movieImage ?: info?.coverBig,
        )

    fun VodInfo.toDomain(movieId: String): MovieDetail =
        MovieDetail(
            id = movieId,
            name = info?.name ?: "",
            metadata =
                MediaMetadata(
                    plot = info?.plot.asString(),
                    cast = info?.cast,
                    director = info?.director,
                    genre = info?.genre,
                    releaseDate = info?.releaseDate,
                    rating = info?.rating.asString(),
                    duration = info?.duration.asString(),
                    tmdbId = info?.tmdbId,
                ),
            coverUrl = info?.coverBig ?: info?.movieImage,
            extension = movieData?.containerExtension,
            videoInfo =
                info?.video?.let {
                    VideoTechInfo(
                        width = it.width,
                        height = it.height,
                        codecName = it.codecName,
                    )
                },
            audioTracks =
                listOfNotNull(
                    info?.audio?.let {
                        AudioTechInfo(
                            codecName = it.codecName,
                            language = it.language,
                        )
                    },
                ),
        )

    /** Builds a [MovieDetail] straight from the persisted cache row — no Xtream/TMDB call. */
    fun XtreamStreamEntity.toMovieDetail(movieId: String): MovieDetail =
        MovieDetail(
            id = movieId,
            name = name,
            metadata =
                MediaMetadata(
                    plot = description,
                    cast = cast,
                    director = director,
                    genre = genre,
                    releaseDate = releaseDate,
                    rating = rating,
                    duration = duration,
                    contentRating = contentRating,
                    tmdbId = tmdbId,
                ),
            coverUrl = streamIcon,
            extension = containerExtension,
        )

    fun WatchedStream.toDomain(mediaType: MediaType): MediaItem =
        MediaItem(
            id = streamId.toString(),
            name = streamName,
            mediaType = mediaType,
            categoryId = categoryId,
            providerData =
                buildMap {
                    put("playbackPosition", playbackPosition.toString())
                    put("duration", duration.toString())
                    put("isCompleted", isCompleted.toString())
                },
        )

    fun FavoriteStream.toDomain(mediaType: MediaType): MediaItem =
        MediaItem(
            id = streamId.toString(),
            name = streamName,
            mediaType = mediaType,
            categoryId = categoryId,
        )
}
