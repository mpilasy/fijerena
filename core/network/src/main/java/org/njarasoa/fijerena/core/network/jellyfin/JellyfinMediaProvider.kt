package org.njarasoa.fijerena.core.network.jellyfin

import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.VideoTechInfo

class JellyfinMediaProvider(
    override val providerId: Long,
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    private val deviceId: String
) : MediaProvider {

    private val api = JellyfinApiService(serverUrl, deviceId)

    override val capabilities = ProviderCapabilities(
        supportedContentTypes = setOf("MOVIES", "TV_SHOWS"),
        supportsEpg = false,
        supportsSearch = true,
        supportsAuthentication = true,
        supportsProgressSync = true,
        supportsServerUserData = true
    )

    override suspend fun connect(): Result<Unit> {
        return api.authenticate(username, password).map { }
    }

    override suspend fun disconnect() {
        api.disconnect()
    }

    override fun isConnected(): Boolean = api.isAuthenticated()

    override suspend fun getCategories(contentType: String): Result<List<MediaCategory>> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return connectResult.map { emptyList() }
        }

        return api.getLibraries().map { libraries ->
            libraries.filter { library ->
                when (contentType) {
                    "MOVIES" -> library.collectionType == "movies"
                    "TV_SHOWS" -> library.collectionType == "tvshows"
                    else -> true
                }
            }.map { library ->
                MediaCategory(
                    id = library.id,
                    name = library.name
                )
            }
        }
    }

    override suspend fun getItems(categoryId: String, contentType: String): Result<List<MediaItem>> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return connectResult.map { emptyList() }
        }

        val includeTypes = when (contentType) {
            "MOVIES" -> "Movie"
            "TV_SHOWS" -> "Series"
            else -> null
        }

        return api.getItems(parentId = categoryId, includeItemTypes = includeTypes).map { items ->
            items.map { item -> item.toDomainItem(categoryId, contentType) }
        }
    }

    override suspend fun search(query: String, contentType: String): Result<List<MediaItem>> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return connectResult.map { emptyList() }
        }

        val includeTypes = when (contentType) {
            "MOVIES" -> "Movie"
            "TV_SHOWS" -> "Series"
            else -> null
        }

        return api.searchItems(query = query, includeItemTypes = includeTypes).map { items ->
            items.map { item -> item.toDomainItem(categoryId = "", contentType = contentType) }
        }
    }

    override suspend fun getSeriesDetail(seriesId: String): Result<SeriesDetail> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull()!!)
        }

        // Fetch the series item itself for its name and metadata
        val seriesResult = api.getItemById(seriesId)
        if (seriesResult.isFailure) return Result.failure(seriesResult.exceptionOrNull()!!)
        val seriesItem = seriesResult.getOrThrow()

        val seasonsResult = api.getSeasons(seriesId)
        if (seasonsResult.isFailure) return Result.failure(seasonsResult.exceptionOrNull()!!)

        val seasons = seasonsResult.getOrThrow()
        val episodesMap = mutableMapOf<String, List<EpisodeItem>>()

        for (season in seasons) {
            val episodesResult = api.getEpisodes(seriesId, season.id)
            if (episodesResult.isSuccess) {
                val seasonKey = (season.indexNumber ?: 0).toString()
                episodesMap[seasonKey] = episodesResult.getOrThrow().map { ep ->
                    EpisodeItem(
                        id = ep.id,
                        episodeNumber = ep.indexNumber ?: 0,
                        title = ep.name,
                        seasonNumber = ep.parentIndexNumber,
                        metadata = MediaMetadata(
                            plot = ep.overview,
                            duration = ep.runTimeTicks?.let { formatTicks(it) }
                        ),
                        thumbnailUrl = if (ep.imageTags.containsKey("Primary")) {
                            api.buildImageUrl(ep.id, "Primary")
                        } else null
                    )
                }
            }
        }

        return Result.success(
            SeriesDetail(
                id = seriesId,
                name = seriesItem.name,
                metadata = MediaMetadata(
                    plot = seriesItem.overview,
                    year = seriesItem.productionYear,
                    genre = seriesItem.genres.joinToString(", ").ifEmpty { null },
                    rating = seriesItem.officialRating
                ),
                coverUrl = if (seriesItem.imageTags.containsKey("Primary")) {
                    api.buildImageUrl(seriesId, "Primary")
                } else null,
                seasons = seasons.map { season ->
                    SeasonInfo(
                        seasonNumber = season.indexNumber ?: 0,
                        name = season.name,
                        coverUrl = if (season.imageTags.containsKey("Primary")) {
                            api.buildImageUrl(season.id, "Primary")
                        } else null
                    )
                },
                episodes = episodesMap
            )
        )
    }

    override suspend fun getMovieDetail(movieId: String): Result<MovieDetail> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull()!!)
        }

        return api.getItemById(movieId).map { item -> itemToMovieDetail(item) }
    }

    override suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String?,
        extension: String?
    ): Result<PlayableStream> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull()!!)
        }

        val streamItemId = episodeId ?: itemId

        // Determine container extension for proper media source detection
        // Jellyfin container can be comma-separated (e.g., "mov,mp4,m4a,3gp,3g2,mj2")
        val rawContainer = extension ?: run {
            val itemResult = api.getItemById(streamItemId)
            itemResult.getOrNull()?.let { item ->
                item.container ?: item.mediaSources.firstOrNull()?.container
            }
        }
        val container = rawContainer?.split(",")?.firstOrNull { ext ->
            ext.trim().lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m3u8", "mpd", "flv", "mpeg")
        }?.trim() ?: rawContainer?.split(",")?.firstOrNull()?.trim()

        val streamUrl = api.buildStreamUrl(streamItemId, container)
        val token = api.getAccessToken()

        return Result.success(
            PlayableStream(
                uri = streamUrl,
                headers = if (token != null) mapOf("X-Emby-Token" to token) else emptyMap(),
                isLive = false,
                title = ""
            )
        )
    }

    override suspend fun onPlaybackProgress(itemId: String, positionMs: Long, durationMs: Long) {
        if (!isConnected()) return
        val positionTicks = positionMs * 10_000
        val isPaused = false
        api.reportPlaybackProgress(itemId, positionTicks, isPaused)
    }

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean): Result<Unit> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))
        return if (isFavorite) api.addFavorite(itemId) else api.removeFavorite(itemId)
    }

    override suspend fun isFavorite(itemId: String): Boolean {
        if (!ensureConnected()) return false
        val item = api.getItemById(itemId).getOrNull() ?: return false
        return item.userData?.isFavorite == true
    }

    override suspend fun getFavoriteItems(contentType: String): Result<List<MediaItem>> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))
        val jellyfinType = contentTypeToJellyfinType(contentType)
        return api.getFavoriteItems(includeItemTypes = jellyfinType).map { items ->
            items.map { it.toDomainItem(categoryId = "", contentType = contentType) }
        }
    }

    override suspend fun getResumeItems(contentType: String): Result<List<MediaItem>> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))
        val jellyfinType = contentTypeToJellyfinType(contentType)
        return api.getResumableItems(includeItemTypes = jellyfinType).map { items ->
            items.map { it.toDomainItem(categoryId = "", contentType = contentType) }
        }
    }

    override suspend fun getRecentlyPlayed(contentType: String): Result<List<MediaItem>> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))
        val jellyfinType = contentTypeToJellyfinType(contentType)
        return api.getRecentlyPlayed(includeItemTypes = jellyfinType).map { items ->
            items.map { it.toDomainItem(categoryId = "", contentType = contentType) }
        }
    }

    override suspend fun getPlaybackPosition(itemId: String): Pair<Long, Long>? {
        if (!ensureConnected()) return null
        val item = api.getItemById(itemId).getOrNull() ?: return null
        val ticks = item.userData?.playbackPositionTicks ?: return null
        if (ticks <= 0) return null
        val posMs = ticks / 10_000
        val durMs = (item.runTimeTicks ?: 0L) / 10_000
        return Pair(posMs, durMs)
    }

    override suspend fun onPlaybackStarted(itemId: String) {
        if (!isConnected()) return
        api.reportPlaybackStart(itemId)
    }

    override suspend fun onPlaybackStopped(itemId: String, positionMs: Long, durationMs: Long) {
        if (!isConnected()) return
        api.reportPlaybackStopped(itemId, positionMs * 10_000)
    }

    private suspend fun ensureConnected(): Boolean {
        if (isConnected()) return true
        return connect().isSuccess
    }

    private fun contentTypeToJellyfinType(contentType: String): String? {
        return when (contentType) {
            "MOVIES" -> "Movie"
            "TV_SHOWS" -> "Series"
            else -> null
        }
    }

    private fun JellyfinItem.toDomainItem(categoryId: String, contentType: String): MediaItem {
        val mediaType = when (type) {
            "Movie" -> MediaType.MOVIE
            "Series" -> MediaType.SERIES
            "Episode" -> MediaType.EPISODE
            else -> if (contentType == "TV_SHOWS") MediaType.SERIES else MediaType.MOVIE
        }

        val provData = buildMap {
            userData?.let { ud ->
                val posMs = ud.playbackPositionTicks / 10_000
                val durMs = runTimeTicks?.let { it / 10_000 } ?: 0L
                put("playbackPosition", posMs.toString())
                put("duration", durMs.toString())
                put("isFavorite", ud.isFavorite.toString())
                put("isCompleted", ud.played.toString())
            }
        }

        return MediaItem(
            id = id,
            name = name,
            mediaType = mediaType,
            categoryId = categoryId,
            thumbnailUrl = if (imageTags.containsKey("Primary")) {
                api.buildImageUrl(id, "Primary")
            } else null,
            metadata = MediaMetadata(
                plot = overview,
                year = productionYear,
                genre = genres.joinToString(", ").ifEmpty { null },
                rating = officialRating,
                duration = runTimeTicks?.let { formatTicks(it) }
            ),
            providerData = provData
        )
    }

    private fun itemToMovieDetail(item: JellyfinItem): MovieDetail {
        val director = item.people.firstOrNull { it.type == "Director" }?.name
        val cast = item.people
            .filter { it.type == "Actor" }
            .joinToString(", ") { it.name }
            .ifEmpty { null }

        val mediaSource = item.mediaSources.firstOrNull()

        return MovieDetail(
            id = item.id,
            name = item.name,
            metadata = MediaMetadata(
                plot = item.overview,
                year = item.productionYear,
                genre = item.genres.joinToString(", ").ifEmpty { null },
                rating = item.officialRating,
                director = director,
                cast = cast,
                duration = item.runTimeTicks?.let { formatTicks(it) }
            ),
            coverUrl = if (item.imageTags.containsKey("Primary")) {
                api.buildImageUrl(item.id, "Primary")
            } else null,
            extension = item.container ?: mediaSource?.container,
            videoInfo = mediaSource?.let {
                VideoTechInfo(codecName = it.container)
            },
            audioInfo = null
        )
    }

    private fun formatTicks(ticks: Long): String {
        val totalSeconds = ticks / 10_000_000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
