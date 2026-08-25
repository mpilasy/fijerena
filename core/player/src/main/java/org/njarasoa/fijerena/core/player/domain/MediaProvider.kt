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

    suspend fun getSeriesDetail(seriesId: SeriesId): Result<SeriesDetail>

    /**
     * The series as last stored locally, with no network call, or null when nothing usable is
     * cached. Lets a caller draw the screen while [getSeriesDetail] goes to the provider —
     * that call still runs, because only it can notice episodes added since.
     */
    suspend fun getCachedSeriesDetail(seriesId: SeriesId): SeriesDetail? = null

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
     * Titles related to [itemId] that this provider actually carries, for the two rows on a detail
     * screen.
     *
     * The related titles come from TMDB, which answers in TMDB titles rather than in the user's
     * catalogue, so an implementation has to match them back against what it holds and drop
     * whatever it cannot play — a row of titles the provider does not carry is a row of dead ends.
     * Either list is empty for every reason its row should simply not appear: no TMDB id, no API
     * key, a failed call, or too few surviving matches to be worth a row.
     */
    suspend fun getRelatedTitles(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): RelatedTitles = RelatedTitles()

    /**
     * TMDB's own title for [tmdbId], for showing next to a provider's stream name — Xtream stream
     * names are often raw release-file names rather than clean titles. Null for every reason it
     * should simply not show: no TMDB id, no API key, a failed call, or a provider (like Jellyfin)
     * whose own name is already clean.
     */
    suspend fun getTmdbTitle(
        tmdbId: String?,
        contentType: String,
    ): String? = null

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
