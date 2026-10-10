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
     * Drops whatever this provider has cached for [itemId]'s detail, so the next read goes back to
     * the server. Refresh actions call it — without it a "refresh" re-serves the cached copy and
     * looks like it did nothing. No-op for providers that don't cache.
     */
    suspend fun invalidateCachedDetail(itemId: String) {}

    /**
     * Fetches the whole catalogue again, bypassing any local copy — for providers that load it in
     * one piece (a remote M3U playlist), where Refresh would otherwise re-serve the copy. No-op
     * for providers whose lists are fetched per call.
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
     * [ProviderCapabilities.supportsCatchup]. See docs/plans/20261010_catchup-plan.md.
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
     * TMDB's transparent-PNG wordmark art for [tmdbId], for the player OSD's title treatment.
     * Null for every reason it should simply not show: no TMDB id, no API key, a failed call, or
     * TMDB has no logo art for this title.
     */
    suspend fun getTmdbLogoUrl(
        tmdbId: String?,
        contentType: String,
    ): String? = null

    /**
     * TMDB's full-bleed backdrop art for [tmdbId], for the TV detail hero background. Null for
     * every reason it should simply not show: no TMDB id, no API key, a failed call, or TMDB has
     * no backdrop for this title — callers fall back to whatever backdrop the catalogue itself
     * carries, then to no image at all.
     */
    suspend fun getTmdbBackdropUrl(
        tmdbId: String?,
        contentType: String,
    ): String? = null

    /**
     * Other entries in this provider's local catalogue carrying the same TMDB id as [itemId] —
     * different rips/languages of the same movie or show, for the "other instances" picker next
     * to the stream name. Empty for every reason it should simply not show: no TMDB id, or
     * nothing else in the catalogue happens to carry that id yet.
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

    /**
     * Drops whatever in-memory (not disk/DB) caches this provider holds, in response to the
     * system signalling memory pressure ([android.content.ComponentCallbacks2.onTrimMemory]).
     * No-op for providers that don't cache in memory.
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
