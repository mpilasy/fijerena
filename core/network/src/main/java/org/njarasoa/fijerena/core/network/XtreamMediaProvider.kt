package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.network.XtreamMapper.toDomain
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.model.EpgResponse

class XtreamMediaProvider(
    override val providerId: Long,
    private val repository: XtreamRepository
) : MediaProvider {

    private val searchDataSizes = mutableMapOf<String, Long>()

    override val capabilities = ProviderCapabilities(
        supportedContentTypes = setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
        supportsEpg = true,
        supportsSearch = true,
        supportsAuthentication = true,
        supportsProgressSync = false
    )

    override suspend fun connect(): kotlin.Result<Unit> {
        return when (repository.restoreSession()) {
            is Result.Success -> kotlin.Result.success(Unit)
            is Result.Error -> kotlin.Result.failure(
                Exception("Failed to connect to Xtream provider")
            )
        }
    }

    override suspend fun disconnect() {
        repository.logout()
    }

    override fun isConnected(): Boolean = repository.isAuthenticated()

    override suspend fun getCategories(contentType: String): kotlin.Result<List<MediaCategory>> {
        val result = when (contentType) {
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

    private fun getMediaType(contentType: String): MediaType {
        return when (contentType) {
            ContentType.LIVE_TV, "LIVE_TV" -> MediaType.LIVE_CHANNEL
            ContentType.MOVIES, "MOVIES" -> MediaType.MOVIE
            ContentType.TV_SHOWS, "TV_SHOWS" -> MediaType.SERIES
            else -> MediaType.LIVE_CHANNEL
        }
    }

    override suspend fun getItems(categoryId: String, contentType: String): kotlin.Result<List<MediaItem>> {
        val mediaType = getMediaType(contentType)
        val result = when (contentType) {
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
        val id = seriesId.toIntOrNull() ?: return kotlin.Result.failure(
            Exception("Invalid series ID: $seriesId")
        )
        return when (val result = repository.getSeriesInfo(id)) {
            is Result.Success ->
                kotlin.Result.success(result.data.toDomain(seriesId))
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> {
        val id = movieId.toIntOrNull() ?: return kotlin.Result.failure(
            Exception("Invalid movie ID: $movieId")
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
        extension: String?
    ): kotlin.Result<PlayableStream> {
        val isLive = contentType == ContentType.LIVE_TV

        if (episodeId != null && extension != null) {
            return when (val result = repository.buildEpisodeStreamUrl(episodeId, extension)) {
                is Result.Success ->
                    kotlin.Result.success(PlayableStream(
                        uri = result.data,
                        isLive = false,
                        title = ""
                    ))
                is Result.Error ->
                    kotlin.Result.failure(result.exception)
            }
        }

        val streamId = itemId.toIntOrNull() ?: return kotlin.Result.failure(
            Exception("Invalid stream ID: $itemId")
        )
        return when (val result = repository.buildStreamUrl(streamId, contentType, extension)) {
            is Result.Success ->
                kotlin.Result.success(PlayableStream(
                    uri = result.data,
                    isLive = isLive,
                    title = ""
                ))
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override fun getItemsIfCached(categoryId: String, contentType: String): List<MediaItem>? {
        val mediaType = getMediaType(contentType)
        val cached = when (contentType) {
            ContentType.LIVE_TV -> repository.getStreamsCached(categoryId)
            ContentType.MOVIES -> repository.getVodStreamsCached(categoryId)
            ContentType.TV_SHOWS -> repository.getSeriesCached(categoryId)
            else -> repository.getStreamsCached(categoryId)
        }
        return cached?.map { it.toDomain(mediaType) }
    }

    override suspend fun search(query: String, contentType: String): kotlin.Result<List<MediaItem>>? {
        // Xtream has no server-side search — return null to use client-side parallel iteration
        // (per-category API payloads are cached in SharedPreferences by XtreamRepository)
        return null
    }

    override fun getLastSearchDataSize(contentType: String): Long? = searchDataSizes[contentType]

    override suspend fun getEpg(streamId: String): kotlin.Result<EpgResponse>? {
        val id = streamId.toIntOrNull() ?: return kotlin.Result.failure(
            Exception("Invalid stream ID for EPG: $streamId")
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
        val jobs = listOf(
            repository.syncCategories(XtreamCategoryEntity.TYPE_LIVE),
            repository.syncCategories(XtreamCategoryEntity.TYPE_VOD),
            repository.syncCategories(XtreamCategoryEntity.TYPE_SERIES),
            repository.syncStreams(XtreamStreamEntity.TYPE_LIVE),
            repository.syncStreams(XtreamStreamEntity.TYPE_VOD),
            repository.syncSeries()
        )
        jobs.forEach { it.await() }
    }
}
