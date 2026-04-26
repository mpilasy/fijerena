package org.njarasoa.fijerena.core.player.domain

import org.njarasoa.fijerena.core.player.model.EpgResponse

interface MediaProvider {
    val providerId: Long
    val capabilities: ProviderCapabilities

    suspend fun connect(): Result<Unit>

    suspend fun disconnect()

    fun isConnected(): Boolean

    suspend fun getCategories(contentType: String): Result<List<MediaCategory>>

    suspend fun getItems(
        categoryId: String,
        contentType: String,
    ): Result<List<MediaItem>>

    suspend fun getAllItems(contentType: String): Result<List<MediaItem>> = Result.failure(Exception("Not supported"))

    suspend fun getSeriesDetail(seriesId: String): Result<SeriesDetail>

    suspend fun getMovieDetail(movieId: String): Result<MovieDetail>

    suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String? = null,
        extension: String? = null,
    ): Result<PlayableStream>

    /** Returns cached items for a category or null if not cached. Never hits the network. */
    fun getItemsIfCached(
        categoryId: String,
        contentType: String,
    ): List<MediaItem>? = null

    suspend fun search(
        query: String,
        contentType: String,
    ): Result<List<MediaItem>>? = null

    /** Returns estimated byte size of the full dataset fetched for the last search (before filtering). */
    fun getLastSearchDataSize(contentType: String): Long? = null

    suspend fun getEpg(streamId: String): Result<EpgResponse>? = null

    suspend fun getEpgBulk(streamIds: List<String>): Result<Map<String, EpgResponse>>? = null

    suspend fun clearEpgCache() {}

    suspend fun onPlaybackProgress(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean = false,
    ) {}

    // Server-side user data methods (only Jellyfin overrides these)
    suspend fun setFavorite(
        itemId: String,
        isFavorite: Boolean,
    ): Result<Unit>? = null

    suspend fun isFavorite(itemId: String): Boolean? = null

    suspend fun getFavoriteItems(contentType: String): Result<List<MediaItem>>? = null

    suspend fun getResumeItems(contentType: String): Result<List<MediaItem>>? = null

    suspend fun getRecentlyPlayed(contentType: String): Result<List<MediaItem>>? = null

    suspend fun getPlaybackPosition(itemId: String): Pair<Long, Long>? = null // (positionMs, durationMs)

    suspend fun onPlaybackStarted(itemId: String) {}

    suspend fun onPlaybackStopped(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
    ) {}
}
