package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.network.XtreamMapper.toDomain
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

    override val capabilities = ProviderCapabilities(
        supportedContentTypes = setOf("LIVE_TV", "MOVIES", "TV_SHOWS"),
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
            "LIVE_TV" -> repository.getCategories()
            "MOVIES" -> repository.getVodCategories()
            "TV_SHOWS" -> repository.getSeriesCategories()
            else -> repository.getCategories()
        }
        return when (result) {
            is Result.Success ->
                kotlin.Result.success(result.data.map { it.toDomain() })
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun getItems(categoryId: String, contentType: String): kotlin.Result<List<MediaItem>> {
        val mediaType = when (contentType) {
            "LIVE_TV" -> MediaType.LIVE_CHANNEL
            "MOVIES" -> MediaType.MOVIE
            "TV_SHOWS" -> MediaType.SERIES
            else -> MediaType.LIVE_CHANNEL
        }
        val result = when (contentType) {
            "LIVE_TV" -> repository.getStreams(categoryId)
            "MOVIES" -> repository.getVodStreams(categoryId)
            "TV_SHOWS" -> repository.getSeries(categoryId)
            else -> repository.getStreams(categoryId)
        }
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
        val isLive = contentType == "LIVE_TV"

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
}
