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

    /**
     * Drops whatever this provider has cached for [itemId]'s detail, so the next read goes back to
     * the server. Refresh actions call it — without it a "refresh" re-serves the cached copy and
     * looks like it did nothing. No-op for providers that don't cache.
     */
    suspend fun invalidateCachedDetail(itemId: String) {}

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
        includeExcluded: Boolean = false,
    ): Result<List<MediaItem>>? = null

    /**
     * Number of items matching [query] that search skipped because their category is hidden
     * by the provider's category filters. 0 for providers with no exclusion concept.
     */
    suspend fun countExcludedSearchMatches(
        query: String,
        contentType: String,
    ): Int = 0

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

    suspend fun getPlaybackPosition(itemId: String): PlaybackStatus? = null

    suspend fun getPlaybackPositions(itemIds: List<String>): Result<Map<String, PlaybackStatus>>? = null

    /**
     * Episode count per series id, cheap and local — used as the denominator for a series row's
     * watch progress. Null when the provider can't answer without per-series network calls, in
     * which case series rows simply show no progress.
     */
    suspend fun getEpisodeCountsBySeries(): Map<String, Int>? = null

    suspend fun onPlaybackStarted(itemId: String) {}

    suspend fun onPlaybackStopped(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
    ) {}
}

data class PlaybackStatus(
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val itemName: String? = null,
    val categoryId: String? = null,
)
