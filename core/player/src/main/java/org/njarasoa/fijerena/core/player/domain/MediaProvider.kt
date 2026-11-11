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
     * The series as last stored locally, with no network call; null when nothing usable is cached.
     * A caller draws the screen with it while [getSeriesDetail] still runs, as only that call sees
     * new episodes.
     */
    suspend fun getCachedSeriesDetail(seriesId: SeriesId): SeriesDetail? = null

    /**
     * The TMDB id the catalogue on the device gives [itemId] (a film or show), with no network
     * call, or null when it has none — for finding other copies when the item's page can't load.
     */
    suspend fun getCatalogueTmdbId(
        itemId: String,
        contentType: String,
    ): String? = null

    /**
     * A synopsis for [episode] fetched on demand, or null when there is none or the provider has no
     * source for it. Called when playback starts on an episode that has no plot yet, so the OSD can
     * describe the episode rather than the show.
     */
    suspend fun fetchEpisodePlot(
        seriesId: SeriesId,
        episode: EpisodeItem,
    ): String? = null

    suspend fun getMovieDetail(movieId: String): Result<MovieDetail>

    /**
     * Drops this provider's cached detail of [itemId], so the next read goes to the server. Refresh
     * actions call it, else a refresh serves the cache again. No-op for providers that do not
     * cache.
     */
    suspend fun invalidateCachedDetail(itemId: String) {}

    /**
     * Fetches the whole catalogue again, past any local copy, for providers that load it in one
     * piece (a remote M3U playlist). No-op for providers that fetch lists per call.
     */
    suspend fun refreshCatalog(): Result<Unit> = Result.success(Unit)

    suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String? = null,
        extension: String? = null,
    ): Result<PlayableStream>

    /**
     * The live channel [itemId]'s archive from [startEpochSec] for [durationSec] (catch-up), as a
     * stream that seeks and pauses like a film. Fails for a provider without
     * [ProviderCapabilities.supportsCatchup]. See the catchup plan.
     */
    suspend fun resolveCatchupStream(
        itemId: String,
        startEpochSec: Long,
        durationSec: Long,
    ): Result<PlayableStream> = Result.failure(UnsupportedOperationException("Catch-up is not supported by this source"))

    /**
     * How many days back each live channel among [itemIds] keeps an archive, for the channels that
     * have one; a channel missing from the map has none. Local, no network call.
     */
    suspend fun getCatchupDays(itemIds: Collection<String>): Map<String, Int> = emptyMap()

    /**
     * The ids among [itemIds] of [contentType] that this source no longer lists (a Recent row whose
     * channel or title left it). Empty when the source cannot tell. Local, no network call.
     */
    suspend fun missingItems(
        itemIds: Collection<String>,
        contentType: String,
    ): Set<String> = emptySet()

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
     * Titles related to [itemId] that this provider carries, for the two rows on a detail screen.
     *
     * TMDB answers in TMDB titles, so an implementation matches them back against its catalogue and
     * drops what it cannot play. Either list is empty when its row must not show: no TMDB id, no
     * API key, a failed call, or too few matches for a row.
     */
    suspend fun getRelatedTitles(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): RelatedTitles = RelatedTitles()

    /**
     * TMDB's own title for [tmdbId], shown next to a provider's stream name (Xtream names are often
     * raw release-file names). Null when there is no TMDB id, no API key, a failed call, or a
     * provider (like Jellyfin) whose own name is clean.
     */
    suspend fun getTmdbTitle(
        tmdbId: String?,
        contentType: String,
    ): String? = null

    /**
     * TMDB's transparent-PNG wordmark for [tmdbId], for the player OSD's title. Null when there is
     * no TMDB id, no API key, a failed call, or no logo on TMDB.
     */
    suspend fun getTmdbLogoUrl(
        tmdbId: String?,
        contentType: String,
    ): String? = null

    /**
     * TMDB's full-bleed backdrop for [tmdbId], for the TV detail hero. Null when there is no TMDB
     * id, no API key, a failed call, or no backdrop on TMDB; callers then use the catalogue's
     * backdrop, then no image.
     */
    suspend fun getTmdbBackdropUrl(
        tmdbId: String?,
        contentType: String,
    ): String? = null

    /**
     * TMDB's YouTube trailer for [tmdbId], for a title whose source sends none. Null for no TMDB
     * id, no API key, a failed call, or no trailer on TMDB.
     */
    suspend fun getTmdbTrailerUrl(
        tmdbId: String?,
        contentType: String,
    ): String? = null

    /**
     * Other entries in this provider's catalogue with the same TMDB id as [itemId] (other rips or
     * languages), for the "other instances" picker next to the stream name. Empty with no TMDB id
     * or no other match. A blank [itemId] leaves none out (the copies on a source other than the
     * item's).
     */
    suspend fun getAlternateStreams(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): List<MediaItem> = emptyList()

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

    // Server-side user data (only Jellyfin overrides these).
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
     * Episode count per series id, cheap and local: the denominator of a series row's watch
     * progress. Null when the provider cannot answer without per-series network calls; series rows
     * then show no progress.
     */
    suspend fun getEpisodeCountsBySeries(): Map<String, Int>? = null

    suspend fun onPlaybackStarted(itemId: String) {}

    suspend fun onPlaybackStopped(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
    ) {}

    /**
     * Drops this provider's in-memory (not disk or DB) caches under memory pressure
     * ([android.content.ComponentCallbacks2.onTrimMemory]). No-op for providers without memory
     * caches.
     */
    fun trimMemory() {}
}

data class PlaybackStatus(
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val itemName: String? = null,
    val categoryId: String? = null,
)
