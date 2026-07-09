package org.njarasoa.fijerena.core.network

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.njarasoa.fijerena.core.network.XtreamMapper.toDomain
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XtreamMediaProvider(
    override val providerId: Long,
    private val repository: XtreamRepository,
    private val tmdb: TmdbApiService = TmdbApiService(BuildConfig.TMDB_API_KEY),
) : MediaProvider {
    private val searchDataSizes = mutableMapOf<String, Long>()

    // Cache: tmdbSeriesId -> (season, episodeNumber) -> overview.
    // Keeps reopens cheap without hitting TMDB again this session.
    private val tmdbOverviewCache = mutableMapOf<Int, Map<Pair<Int, Int>, String>>()

    override val capabilities =
        ProviderCapabilities(
            supportedContentTypes = setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
            supportsEpg = true,
            supportsSearch = true,
            supportsAuthentication = true,
            supportsProgressSync = false,
        )

    override suspend fun connect(): kotlin.Result<Unit> =
        when (val result = repository.restoreSession()) {
            is Result.Success -> kotlin.Result.success(Unit)
            is Result.Error ->
                kotlin.Result.failure(
                    Exception("Failed to connect to Xtream provider: ${result.exception.message}", result.exception),
                )
        }

    override suspend fun disconnect() {
        repository.logout()
    }

    override fun isConnected(): Boolean = repository.isAuthenticated()

    override suspend fun getCategories(contentType: String): kotlin.Result<List<MediaCategory>> {
        val result =
            when (contentType) {
                ContentType.LIVE_TV -> repository.getCategories()
                ContentType.MOVIES -> repository.getVodCategories()
                ContentType.TV_SHOWS -> repository.getSeriesCategories()
                else -> repository.getCategories()
            }
        return when (result) {
            is Result.Success ->
                kotlin.Result.success(result.data.map { it.toDomain() })
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    private fun getMediaType(contentType: String): MediaType =
        when (contentType) {
            ContentType.LIVE_TV, "LIVE_TV" -> MediaType.LIVE_CHANNEL
            ContentType.MOVIES, "MOVIES" -> MediaType.MOVIE
            ContentType.TV_SHOWS, "TV_SHOWS" -> MediaType.SERIES
            else -> MediaType.LIVE_CHANNEL
        }

    override suspend fun getItems(
        categoryId: String,
        contentType: String,
    ): kotlin.Result<List<MediaItem>> {
        val mediaType = getMediaType(contentType)
        val result =
            when (contentType) {
                ContentType.LIVE_TV -> repository.getStreams(categoryId)
                ContentType.MOVIES -> repository.getVodStreams(categoryId)
                ContentType.TV_SHOWS -> repository.getSeries(categoryId)
                else -> repository.getStreams(categoryId)
            }
        return when (result) {
            is Result.Success ->
                kotlin.Result.success(result.data.map { it.toDomain(mediaType) })
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun getAllItems(contentType: String): kotlin.Result<List<MediaItem>> {
        val mediaType = getMediaType(contentType)
        // Use repository.getAllStreams which handles caching and fetching all streams
        val result = repository.getAllStreams(contentType)
        return when (result) {
            is Result.Success ->
                kotlin.Result.success(result.data.map { it.toDomain(mediaType) })
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun getSeriesDetail(seriesId: String): kotlin.Result<SeriesDetail> {
        val id =
            seriesId.toIntOrNull() ?: return kotlin.Result.failure(
                Exception("Invalid series ID: $seriesId"),
            )
        return when (val result = repository.getSeriesInfo(id)) {
            is Result.Success -> {
                val detail = result.data.toDomain(seriesId)
                val tmdbSeriesId = result.data.info?.tmdb.asString()?.toIntOrNull()
                val enriched =
                    if (tmdb.hasApiKey() && tmdbSeriesId != null) {
                        enrichWithTmdbOverviews(detail, tmdbSeriesId)
                    } else {
                        detail
                    }
                kotlin.Result.success(enriched)
            }
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    /**
     * Fetches per-episode overviews from TMDB (one call per season) and injects them
     * into each episode's metadata.plot. Xtream providers typically don't return
     * episode synopses, so this fills that gap.
     */
    private suspend fun enrichWithTmdbOverviews(
        detail: SeriesDetail,
        tmdbSeriesId: Int,
    ): SeriesDetail {
        // ⚡ Bolt: Avoid flatten().mapNotNull() to prevent intermediate list allocations
        val seasonNumbers = mutableSetOf<Int>()
        detail.episodes.values.forEach { episodesList ->
            episodesList.forEach { ep ->
                ep.seasonNumber?.let { seasonNumbers.add(it) }
            }
        }

        val overviews =
            tmdbOverviewCache[tmdbSeriesId] ?: fetchTmdbOverviews(
                tmdbSeriesId,
                seasonNumbers,
            ).also { tmdbOverviewCache[tmdbSeriesId] = it }

        if (overviews.isEmpty()) return detail

        val enrichedEpisodes =
            detail.episodes.mapValues { (_, list) ->
                list.map { ep ->
                    val season = ep.seasonNumber ?: return@map ep
                    val overview = overviews[season to ep.episodeNumber] ?: return@map ep
                    if (ep.metadata.plot.isNullOrBlank()) {
                        ep.copy(metadata = ep.metadata.copy(plot = overview))
                    } else {
                        ep
                    }
                }
            }
        return detail.copy(episodes = enrichedEpisodes)
    }

    private suspend fun fetchTmdbOverviews(
        tmdbSeriesId: Int,
        seasons: Set<Int>,
    ): Map<Pair<Int, Int>, String> =
        coroutineScope {
            val deferreds =
                seasons.map { season ->
                    async {
                        runCatching { tmdb.getSeason(tmdbSeriesId, season) }
                            .onFailure {
                                Log.w(
                                    "XtreamMediaProvider",
                                    "TMDB season $season fetch failed for series $tmdbSeriesId",
                                    it,
                                )
                            }.getOrNull()
                            ?.episodes
                            ?.mapNotNull { ep ->
                                val overview = ep.overview?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val s = ep.seasonNumber ?: season
                                (s to ep.episodeNumber) to overview
                            }.orEmpty()
                    }
                }

            // ⚡ Bolt: Use an explicit loop instead of flatMap { it.await() }.toMap()
            // to avoid allocating large intermediate lists and Map.Entry objects.
            val resultMap = HashMap<Pair<Int, Int>, String>()
            for (deferred in deferreds) {
                val items = deferred.await()
                for ((key, value) in items) {
                    resultMap[key] = value
                }
            }
            resultMap
        }

    override suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> {
        val id =
            movieId.toIntOrNull() ?: return kotlin.Result.failure(
                Exception("Invalid movie ID: $movieId"),
            )
        return when (val result = repository.getVodInfo(id)) {
            is Result.Success ->
                kotlin.Result.success(result.data.toDomain(movieId))
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String?,
        extension: String?,
    ): kotlin.Result<PlayableStream> {
        val isLive = contentType == ContentType.LIVE_TV

        if (episodeId != null && extension != null) {
            return when (val result = repository.buildEpisodeStreamUrl(episodeId, extension)) {
                is Result.Success ->
                    kotlin.Result.success(
                        PlayableStream(
                            uri = result.data,
                            isLive = false,
                            title = "",
                        ),
                    )
                is Result.Error ->
                    kotlin.Result.failure(result.exception)
            }
        }

        val streamId =
            itemId.toIntOrNull() ?: return kotlin.Result.failure(
                Exception("Invalid stream ID: $itemId"),
            )
        val streamName = repository.getStreamName(streamId, contentType) ?: ""
        return when (val result = repository.buildStreamUrl(streamId, contentType, extension)) {
            is Result.Success ->
                kotlin.Result.success(
                    PlayableStream(
                        uri = result.data,
                        isLive = isLive,
                        title = streamName,
                    ),
                )
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override fun getItemsIfCached(
        categoryId: String,
        contentType: String,
    ): List<MediaItem>? {
        val mediaType = getMediaType(contentType)
        val cached =
            when (contentType) {
                ContentType.LIVE_TV -> repository.getStreamsCached(categoryId)
                ContentType.MOVIES -> repository.getVodStreamsCached(categoryId)
                ContentType.TV_SHOWS -> repository.getSeriesCached(categoryId)
                else -> repository.getStreamsCached(categoryId)
            }
        return cached?.map { it.toDomain(mediaType) }
    }

    override suspend fun search(
        query: String,
        contentType: String,
    ): kotlin.Result<List<MediaItem>>? {
        val words = query.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() && !it.startsWith("-") }
        return if (words.isEmpty()) {
            null
        } else {
            // Sanitize input to prevent SQLite FTS syntax errors (like **) that trigger fallback hangs
            val ftsQuery = words.map { it.replace(Regex("[*\"'()\\^]"), "") }
                .filter { it.isNotBlank() }
                .joinToString(" ") { "${it}*" }
            
            if (ftsQuery.isBlank()) return null

            val mediaType = getMediaType(contentType)
            try {
                val results = repository.searchByFts(contentType, ftsQuery)
                kotlin.Result.success(results.map { it.toDomain(mediaType) })
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun getLastSearchDataSize(contentType: String): Long? = searchDataSizes[contentType]

    override suspend fun getEpg(streamId: String): kotlin.Result<EpgResponse>? {
        val id =
            streamId.toIntOrNull() ?: return kotlin.Result.failure(
                Exception("Invalid stream ID for EPG: $streamId"),
            )
        return when (val result = repository.getEpgForStream(id)) {
            is Result.Success ->
                kotlin.Result.success(result.data)
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun getEpgBulk(streamIds: List<String>): kotlin.Result<Map<String, EpgResponse>>? {
        val intIds = streamIds.mapNotNull { it.toIntOrNull() }
        if (intIds.isEmpty()) return kotlin.Result.success(emptyMap())
        return when (val result = repository.getEpgForStreams(intIds)) {
            is Result.Success ->
                kotlin.Result.success(result.data.mapKeys { it.key.toString() })
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun clearEpgCache() {
        repository.clearAllEpgCache()
    }

    /**
     * Triggers a full sync of categories and streams/series.
     * Used by background worker.
     */
    suspend fun syncAll() {
        val jobs =
            listOf(
                repository.syncCategories(XtreamCategoryEntity.TYPE_LIVE),
                repository.syncCategories(XtreamCategoryEntity.TYPE_VOD),
                repository.syncCategories(XtreamCategoryEntity.TYPE_SERIES),
                repository.syncStreams(XtreamStreamEntity.TYPE_LIVE),
                repository.syncStreams(XtreamStreamEntity.TYPE_VOD),
                repository.syncSeries(),
            )
        jobs.forEach { it.await() }
    }
}
