package org.njarasoa.fijerena.core.player.domain

import org.njarasoa.fijerena.core.player.model.EpgResponse

interface MediaProvider {
    val providerId: Long
    val capabilities: ProviderCapabilities

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    fun isConnected(): Boolean

    suspend fun getCategories(contentType: String): Result<List<MediaCategory>>
    suspend fun getItems(categoryId: String, contentType: String): Result<List<MediaItem>>
    suspend fun getSeriesDetail(seriesId: String): Result<SeriesDetail>
    suspend fun getMovieDetail(movieId: String): Result<MovieDetail>
    suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String? = null,
        extension: String? = null
    ): Result<PlayableStream>

    suspend fun getEpg(streamId: String): Result<EpgResponse>? = null
    suspend fun getEpgBulk(streamIds: List<String>): Result<Map<String, EpgResponse>>? = null

    suspend fun onPlaybackProgress(itemId: String, positionMs: Long, durationMs: Long) {}
}
