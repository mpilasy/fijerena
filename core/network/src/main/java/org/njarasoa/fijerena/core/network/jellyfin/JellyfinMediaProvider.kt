package org.njarasoa.fijerena.core.network.jellyfin

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import org.njarasoa.fijerena.core.player.domain.AudioTechInfo
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.SubtitleTechInfo
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
    private val deviceId: String,
    savedToken: String? = null,
    savedUserId: String? = null,
    private val onSessionSaved: ((token: String, userId: String) -> Unit)? = null,
    private val onSessionCleared: (() -> Unit)? = null
) : MediaProvider {

    private val api = JellyfinApiService(serverUrl, deviceId).also {
        if (savedToken != null && savedUserId != null) {
            it.restoreSession(savedToken, savedUserId)
        }
    }

    // PlaySessionId per item, used for transcoding session reporting
    private val playSessionIds = mutableMapOf<String, String>()
    private val mediaSourceIds = mutableMapOf<String, String>()

    override val capabilities = ProviderCapabilities(
        supportedContentTypes = setOf("MOVIES", "TV_SHOWS"),
        supportsEpg = false,
        supportsSearch = true,
        supportsAuthentication = true,
        supportsProgressSync = true,
        supportsServerUserData = true
    )

    override suspend fun connect(): Result<Unit> {
        // Don't re-authenticate if already connected
        if (isConnected()) return Result.success(Unit)
        return api.authenticate(username, password).also { result ->
            result.onSuccess {
                val token = api.getAccessToken()
                val uid = api.getUserId()
                if (token != null && uid != null) onSessionSaved?.invoke(token, uid)
            }
        }.map { }
    }

    override suspend fun disconnect() {
        api.disconnect()
    }

    override fun isConnected(): Boolean = api.isAuthenticated()

    override suspend fun getCategories(contentType: String): Result<List<MediaCategory>> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))

        return withAutoReconnect {
            api.getLibraries().map { libraries ->
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
    }

    override suspend fun getItems(categoryId: String, contentType: String): Result<List<MediaItem>> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))

        val includeTypes = when (contentType) {
            "MOVIES" -> "Movie"
            "TV_SHOWS" -> "Series"
            else -> null
        }

        return withAutoReconnect {
            api.getItems(parentId = categoryId, includeItemTypes = includeTypes).map { items ->
                items.map { item -> item.toDomainItem(categoryId, contentType) }
            }
        }
    }

    override suspend fun search(query: String, contentType: String): Result<List<MediaItem>> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))

        val includeTypes = when (contentType) {
            "MOVIES" -> "Movie"
            "TV_SHOWS" -> "Series"
            else -> null
        }

        return withAutoReconnect {
            api.searchItems(query = query, includeItemTypes = includeTypes).map { items ->
                items.map { item -> item.toDomainItem(categoryId = "", contentType = contentType) }
            }
        }
    }

    override suspend fun getSeriesDetail(seriesId: String): Result<SeriesDetail> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))

        return withAutoReconnect { fetchSeriesDetail(seriesId) }
    }

    private suspend fun fetchSeriesDetail(seriesId: String): Result<SeriesDetail> {
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
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))

        return withAutoReconnect {
            api.getItemById(movieId).map { item -> itemToMovieDetail(item) }
        }
    }

    override suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String?,
        extension: String?
    ): Result<PlayableStream> {
        if (!ensureConnected()) return Result.failure(Exception("Not connected"))

        val streamItemId = episodeId ?: itemId
        val userId = api.getUserId() ?: return Result.failure(Exception("Not authenticated"))
        val token = api.getAccessToken()
        val headers = if (token != null) mapOf("X-Emby-Token" to token) else emptyMap()

        // Ask Jellyfin whether to direct-play or transcode based on our DeviceProfile
        val playbackInfo = api.getPlaybackInfo(streamItemId, userId)

        if (playbackInfo.isSuccess) {
            val info = playbackInfo.getOrThrow()
            val source = info.mediaSources.firstOrNull()

            // Store session IDs for accurate progress reporting
            info.playSessionId?.let { playSessionIds[streamItemId] = it }
            source?.id?.let { mediaSourceIds[streamItemId] = it }

            val streamUrl = when {
                source == null -> {
                    // No source in response — fall back to static direct play
                    api.buildStreamUrl(streamItemId)
                }
                source.supportsDirectPlay -> {
                    // Jellyfin confirms the device can decode this file natively
                    val container = source.container?.split(",")?.firstOrNull()?.trim()
                    api.buildStreamUrl(streamItemId, container, source.id)
                }
                source.transcodingUrl != null -> {
                    // Jellyfin will transcode to HLS; the URL includes all params + api_key
                    "$serverUrl${source.transcodingUrl}"
                }
                source.supportsDirectStream -> {
                    // Server streams the file without re-encoding (seeking handled by server)
                    val container = source.container?.split(",")?.firstOrNull()?.trim()
                    api.buildStreamUrl(streamItemId, container, source.id)
                }
                else -> api.buildStreamUrl(streamItemId)
            }

            return Result.success(
                PlayableStream(uri = streamUrl, headers = headers, isLive = false, title = "")
            )
        }

        // PlaybackInfo failed — fall back to legacy static direct play
        val rawContainer = extension ?: run {
            api.getItemById(streamItemId).getOrNull()
                ?.let { it.container ?: it.mediaSources.firstOrNull()?.container }
        }
        val container = rawContainer?.split(",")?.firstOrNull { ext ->
            ext.trim().lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m3u8", "mpd")
        }?.trim() ?: rawContainer?.split(",")?.firstOrNull()?.trim()

        return Result.success(
            PlayableStream(
                uri = api.buildStreamUrl(streamItemId, container),
                headers = headers,
                isLive = false,
                title = ""
            )
        )
    }

    override suspend fun onPlaybackProgress(itemId: String, positionMs: Long, durationMs: Long) {
        if (!isConnected()) return
        api.reportPlaybackProgress(
            itemId = itemId,
            positionTicks = positionMs * 10_000,
            isPaused = false,
            playSessionId = playSessionIds[itemId],
            mediaSourceId = mediaSourceIds[itemId]
        )
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
        api.reportPlaybackStart(
            itemId = itemId,
            playSessionId = playSessionIds[itemId],
            mediaSourceId = mediaSourceIds[itemId]
        )
    }

    override suspend fun onPlaybackStopped(itemId: String, positionMs: Long, durationMs: Long) {
        if (!isConnected()) return
        api.reportPlaybackStopped(
            itemId = itemId,
            positionTicks = positionMs * 10_000,
            playSessionId = playSessionIds[itemId],
            mediaSourceId = mediaSourceIds[itemId]
        )
        // Clean up session tracking after stop
        playSessionIds.remove(itemId)
        mediaSourceIds.remove(itemId)
    }

    private suspend fun ensureConnected(): Boolean {
        if (isConnected()) return true
        return connect().isSuccess
    }

    private suspend fun <T> withAutoReconnect(block: suspend () -> Result<T>): Result<T> {
        val result = block()
        if (result.isFailure) {
            val cause = result.exceptionOrNull()
            if (cause is ClientRequestException && cause.response.status == HttpStatusCode.Unauthorized) {
                // Token is invalid — clear persisted session and re-authenticate
                onSessionCleared?.invoke()
                api.disconnect()
                val reconnect = connect()
                if (reconnect.isSuccess) {
                    return block()
                }
                return Result.failure(Exception("Authentication failed"))
            }
        }
        return result
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
                rating = buildList {
                    communityRating?.let { add(String.format("%.1f", it)) }
                    officialRating?.let { add(it) }
                }.joinToString(" | ").ifEmpty { null },
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
        val videoStream = mediaSource?.mediaStreams?.firstOrNull { it.type == "Video" }
        val audioStreams = mediaSource?.mediaStreams?.filter { it.type == "Audio" } ?: emptyList()
        val subtitleStreams = mediaSource?.mediaStreams?.filter { it.type == "Subtitle" } ?: emptyList()

        // Prefer communityRating (numeric, e.g. "7.9") with officialRating (e.g. "PG-13") as supplement
        val ratingParts = mutableListOf<String>()
        item.communityRating?.let { ratingParts.add(String.format("%.1f", it)) }
        item.officialRating?.let { ratingParts.add(it) }
        val rating = ratingParts.joinToString(" | ").ifEmpty { null }

        return MovieDetail(
            id = item.id,
            name = item.name,
            metadata = MediaMetadata(
                plot = item.overview,
                year = item.productionYear,
                genre = item.genres.joinToString(", ").ifEmpty { null },
                rating = rating,
                director = director,
                cast = cast,
                duration = item.runTimeTicks?.let { formatTicks(it) }
            ),
            coverUrl = if (item.imageTags.containsKey("Primary")) {
                api.buildImageUrl(item.id, "Primary")
            } else null,
            extension = item.container ?: mediaSource?.container,
            videoInfo = videoStream?.let { vs ->
                VideoTechInfo(
                    width = vs.width,
                    height = vs.height,
                    codecName = vs.codec,
                    bitrate = vs.bitRate,
                    videoRange = vs.videoDoViTitle ?: vs.videoRange,
                    displayTitle = vs.displayTitle
                )
            },
            audioTracks = audioStreams.map { as_ ->
                AudioTechInfo(
                    codecName = as_.codec,
                    language = as_.language,
                    channels = as_.channels,
                    sampleRate = as_.sampleRate,
                    displayTitle = as_.displayTitle,
                    isDefault = as_.isDefault
                )
            },
            subtitleTracks = subtitleStreams.map { ss ->
                SubtitleTechInfo(
                    codecName = ss.codec,
                    language = ss.language,
                    displayTitle = ss.displayTitle,
                    isDefault = ss.isDefault
                )
            }
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
