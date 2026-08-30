package org.njarasoa.fijerena.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.XtreamMapper.toDomain
import org.njarasoa.fijerena.core.network.XtreamMapper.toMovieDetail
import org.njarasoa.fijerena.core.network.tmdb.TitleMatcher
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.SyncDelta
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.api.asThrowable
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
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

    // Full movie/series detail (plot, cast, genre, rating, contentRating, episodes, etc.) rarely
    // changes, but assembling it costs a live Xtream call plus TMDB enrichment — cache the fully
    // assembled result so reopening the same title doesn't repeat that work.
    private val movieDetailCache = TtlCache<String, MovieDetail>(DETAIL_CACHE_TTL_MS)
    private val seriesDetailCache = TtlCache<String, SeriesDetail>(DETAIL_CACHE_TTL_MS)


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
                // Callers run on Dispatchers.Main.immediate, and each toDomain allocates a
                // MediaItem, a providerData map and a MediaMetadata. On the largest category
                // (9,480 items) that measured 50ms of main-thread work at the moment of the tap.
                kotlin.Result.success(withContext(Dispatchers.Default) { result.data.map { it.toDomain(mediaType) } })
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
                kotlin.Result.success(withContext(Dispatchers.Default) { result.data.map { it.toDomain(mediaType) } })
            is Result.Error ->
                kotlin.Result.failure(result.exception)
        }
    }

    override suspend fun getEpisodeCountsBySeries(): Map<String, Int> = repository.getEpisodeCountsBySeries()

    /**
     * One line whenever a lookup ends as an error on screen. Without it a call that never
     * completed left no trace at all, so in a log capture "the user never opened anything" and
     * "the provider was unreachable" looked exactly the same.
     */
    private fun XtreamResponse<*>.logAsFailure(action: String, id: String) {
        when (this) {
            is XtreamResponse.Ok -> {}
            is XtreamResponse.Unavailable -> Log.w("XtreamMediaProvider", "$action $id: provider has nothing for it")
            is XtreamResponse.Malformed -> Log.w("XtreamMediaProvider", "$action $id: response could not be read", cause)
            is XtreamResponse.Failed -> Log.w("XtreamMediaProvider", "$action $id: call did not complete", cause)
        }
    }

    /**
     * Fetches the series info, applying the two recoveries worth trying when the provider answers
     * with nothing usable — [XtreamResponse] having already decided that it did.
     *
     * One retry, because these proxies blip: a second ask often returns the episode list the first
     * one omitted. Then a re-resolve by name, because a catalogue id the provider has stopped
     * recognising is the other reason for a permanent nothing — every later visit would show an
     * empty show forever otherwise.
     */
    private suspend fun resolveSeriesInfo(id: Int): XtreamResponse<SeriesInfo> {
        val first = repository.getSeriesInfo(id)
        if (first is XtreamResponse.Ok || first is XtreamResponse.Failed) return first

        val retry = repository.getSeriesInfo(id)
        if (retry is XtreamResponse.Ok) return retry
        Log.w("XtreamMediaProvider", "Series $id gave nothing usable twice")

        val name = repository.getCachedSeriesEntity(id)?.name
        val currentId = name?.let { repository.resolveSeriesIdByName(it) }
        return if (currentId == null || currentId == id) {
            retry
        } else {
            Log.i("XtreamMediaProvider", "Series $id is gone; provider now lists \"$name\" as $currentId")
            repository.getSeriesInfo(currentId)
        }
    }

    override suspend fun invalidateCachedDetail(itemId: String) {
        seriesDetailCache.remove(itemId)
        movieDetailCache.remove(itemId)
        // Also expire the persisted TMDB-derived row, otherwise a refresh still gets the stored
        // content rating (and, for a movie, the whole stored detail) for the rest of its 7 days.
        itemId.toIntOrNull()?.let { id ->
            repository.getCachedMovieDetail(id)?.let {
                repository.saveMovieDetailCache(id, it.contentRating, it.tmdbId, it.containerExtension, 0L)
            }
            repository.getCachedSeriesEntity(id)?.let {
                repository.saveSeriesDetailCache(id, it.contentRating, it.tmdbId, 0L)
            }
        }
    }

    override suspend fun getCachedSeriesDetail(seriesId: SeriesId): SeriesDetail? {
        seriesDetailCache.get(seriesId.raw)?.let { return it }
        val id = seriesId.raw.toIntOrNull() ?: return null
        return repository.getCachedSeriesDetail(id)
    }

    override suspend fun getSeriesDetail(seriesId: SeriesId): kotlin.Result<SeriesDetail> {
        // Xtream numbers its catalogue; unwrap once here and keep the rest raw.
        val rawSeriesId = seriesId.raw
        seriesDetailCache.get(rawSeriesId)?.let { return kotlin.Result.success(it) }
        val id =
            rawSeriesId.toIntOrNull() ?: return kotlin.Result.failure(
                Exception("Invalid series ID: $rawSeriesId"),
            )
        // Always hit Xtream for the episode list — ongoing shows add episodes, and skipping this
        // call would mean not noticing new ones until the persisted cache expires. Only the TMDB
        // content-rating round trip below is skipped when we already have a fresh persisted value.
        return when (val result = resolveSeriesInfo(id)) {
            is XtreamResponse.Ok -> {
                // Xtream re-sends the episode list on every visit, and it rarely carries synopses —
                // so fill in the ones TMDB gave us last time before deciding whether to ask TMDB
                // again. Without this the season fetches below repeat on every cold start, since
                // their in-memory cache dies with the process.
                val detail = result.value.toDomain(rawSeriesId).withPlots(repository.getPersistedEpisodePlots(id))
                val tmdbSeriesId = result.value.info?.tmdb.asString()?.toIntOrNull()
                var enriched = detail
                if (tmdb.hasApiKey() && tmdbSeriesId != null) {
                    if (detail.hasEpisodeWithoutPlot()) {
                        enriched = enrichWithTmdbOverviews(enriched, tmdbSeriesId)
                    }

                    val cachedSeries = repository.getCachedSeriesEntity(id)
                    val cachedRating = cachedSeries?.contentRating
                    val cachedRatingFresh =
                        cachedRating != null &&
                            cachedSeries.detailFetchedAt != null &&
                            System.currentTimeMillis() - cachedSeries.detailFetchedAt < DETAIL_CACHE_TTL_MS
                    val certification = if (cachedRatingFresh) cachedRating else fetchTvCertification(tmdbSeriesId)
                    if (certification != null) {
                        enriched = enriched.copy(metadata = enriched.metadata.copy(contentRating = certification))
                    }
                    if (!cachedRatingFresh) {
                        val tmdbDetails = runCatching { tmdb.getTvDetails(tmdbSeriesId) }.getOrNull()
                        val tmdbPosterPath = tmdbDetails?.posterPath
                        repository.saveSeriesDetailCache(id, certification, tmdbSeriesId.toString(), System.currentTimeMillis(), tmdbPosterPath)
                    }
                }
                repository.persistEpisodeOverviews(enriched.episodes)
                // A series with no episodes is never a legitimate answer — it means the provider
                // returned nothing useful this time. Caching it would serve the empty screen for
                // the cache's whole lifetime, which no amount of clearing the provider's stored
                // catalogue would fix, since this cache lives in memory.
                if (enriched.episodes.values.any { it.isNotEmpty() }) {
                    seriesDetailCache.put(rawSeriesId, enriched)
                }
                kotlin.Result.success(enriched)
            }
            else -> {
                result.logAsFailure("get_series_info", rawSeriesId)
                kotlin.Result.failure(result.asThrowable())
            }
        }
    }

    private suspend fun fetchTvCertification(tmdbSeriesId: Int): String? =
        runCatching { tmdb.getTvContentRatings(tmdbSeriesId) }
            .onFailure { Log.w("XtreamMediaProvider", "TMDB content rating fetch failed for series $tmdbSeriesId", it) }
            .getOrNull()
            ?.let { extractCertification(it.results.map { r -> r.country to r.rating }) }

    private suspend fun fetchMovieCertification(tmdbMovieId: Int): String? =
        runCatching { tmdb.getMovieReleaseDates(tmdbMovieId) }
            .onFailure { Log.w("XtreamMediaProvider", "TMDB release dates fetch failed for movie $tmdbMovieId", it) }
            .getOrNull()
            ?.let { response ->
                extractCertification(
                    response.results.map { it.country to it.releaseDates.firstNotNullOfOrNull { d -> d.certification?.takeIf { c -> c.isNotBlank() } } },
                )
            }

    /** Prefers the US entry (TMDB's most reliably populated region), else the first non-blank one. */
    private fun extractCertification(byCountry: List<Pair<String, String?>>): String? =
        byCountry.firstOrNull { it.first == "US" }?.second?.takeIf { it.isNotBlank() }
            ?: byCountry.firstNotNullOfOrNull { it.second?.takeIf { c -> c.isNotBlank() } }

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

    /** This detail with [plots] (keyed by episode id) filling in any episode that has none. */
    private fun SeriesDetail.withPlots(plots: Map<String, String>): SeriesDetail =
        if (plots.isEmpty()) {
            this
        } else {
            copy(
                episodes =
                    episodes.mapValues { (_, list) ->
                        list.map { ep ->
                            val stored = plots[ep.id]
                            if (ep.metadata.plot.isNullOrBlank() && !stored.isNullOrBlank()) {
                                ep.copy(metadata = ep.metadata.copy(plot = stored))
                            } else {
                                ep
                            }
                        }
                    },
            )
        }

    /** Whether any episode still lacks a synopsis — the only reason to spend TMDB season calls. */
    private fun SeriesDetail.hasEpisodeWithoutPlot(): Boolean = episodes.values.any { list -> list.any { it.metadata.plot.isNullOrBlank() } }

    private suspend fun fetchTmdbOverviews(
        tmdbSeriesId: Int,
        seasons: Set<Int>,
    ): Map<Pair<Int, Int>, String> =
        coroutineScope {
            // A long-running show would otherwise fan out one request per season at once, which is
            // the shape most likely to get throttled at the other end.
            val inFlight = Semaphore(MAX_CONCURRENT_TMDB_REQUESTS)
            seasons
                .map { season ->
                    async {
                        runCatching { inFlight.withPermit { tmdb.getSeason(tmdbSeriesId, season) } }
                            .onFailure { Log.w("XtreamMediaProvider", "TMDB season $season fetch failed for series $tmdbSeriesId", it) }
                            .getOrNull()
                            ?.episodes
                            ?.mapNotNull { ep ->
                                val overview = ep.overview?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val s = ep.seasonNumber ?: season
                                (s to ep.episodeNumber) to overview
                            }.orEmpty()
                    }
                }.flatMap { it.await() }
                .toMap()
        }

    override suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> {
        movieDetailCache.get(movieId)?.let { return kotlin.Result.success(it) }
        val id =
            movieId.toIntOrNull() ?: return kotlin.Result.failure(
                Exception("Invalid movie ID: $movieId"),
            )

        // A movie's own metadata never changes after release (unlike a series' episode list), so a
        // fresh persisted cache row is served with no Xtream or TMDB call at all, surviving restarts.
        repository.getCachedMovieDetail(id)?.let { cached ->
            if (cached.detailFetchedAt != null && System.currentTimeMillis() - cached.detailFetchedAt < DETAIL_CACHE_TTL_MS) {
                val detail = cached.toMovieDetail(movieId)
                movieDetailCache.put(movieId, detail)
                return kotlin.Result.success(detail)
            }
        }

        return when (val result = repository.getVodInfo(id)) {
            is XtreamResponse.Ok -> {
                val detail = result.value.toDomain(movieId)
                val tmdbMovieId = detail.metadata.tmdbId?.toIntOrNull()
                var enriched = detail
                var tmdbPosterPath: String? = null
                if (tmdb.hasApiKey() && tmdbMovieId != null) {
                    val certification = fetchMovieCertification(tmdbMovieId)
                    if (certification != null) {
                        enriched = enriched.copy(metadata = enriched.metadata.copy(contentRating = certification))
                    }
                    val tmdbDetails = runCatching { tmdb.getMovieDetails(tmdbMovieId) }.getOrNull()
                    tmdbPosterPath = tmdbDetails?.posterPath
                }
                movieDetailCache.put(movieId, enriched)
                repository.saveMovieDetailCache(
                    vodId = id,
                    contentRating = enriched.metadata.contentRating,
                    tmdbId = enriched.metadata.tmdbId,
                    containerExtension = enriched.extension,
                    fetchedAt = System.currentTimeMillis(),
                    posterPath = tmdbPosterPath,
                )
                kotlin.Result.success(enriched)
            }
            // The provider answered and had nothing to say about this movie. Its own catalogue
            // row is still local, and a movie needs no more than that to play — the stream URL is
            // built from the id, with the extension defaulting to mp4. Failing here would refuse
            // to open a film whose only problem is missing metadata, which is what this did
            // between 4220ce69 and now.
            is XtreamResponse.Unavailable, is XtreamResponse.Malformed -> {
                result.logAsFailure("get_vod_info", movieId)
                val cached =
                    repository.getCachedMovieDetail(id)
                        ?: return kotlin.Result.failure(result.asThrowable())
                // Deliberately not cached: this is the degraded answer, and the next visit should
                // ask the provider again rather than replay it.
                kotlin.Result.success(cached.toMovieDetail(movieId))
            }
            // The call never completed, so nothing else will either — including playback.
            is XtreamResponse.Failed -> {
                result.logAsFailure("get_vod_info", movieId)
                kotlin.Result.failure(result.asThrowable())
            }
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

    /** Builds the FTS MATCH expression for [query], or null when nothing searchable remains. */
    private fun buildFtsQuery(query: String): String? {
        val words = query.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() && !it.startsWith("-") }
        if (words.isEmpty()) return null
        // Sanitize input to prevent SQLite FTS syntax errors (like **) that trigger fallback hangs
        val ftsQuery = words.map { it.replace(Regex("[*\"'()\\^]"), "") }
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it}*" }
        return ftsQuery.ifBlank { null }
    }

    override suspend fun search(
        query: String,
        contentType: String,
        includeExcluded: Boolean,
    ): kotlin.Result<List<MediaItem>>? {
        val ftsQuery = buildFtsQuery(query) ?: return null
        val mediaType = getMediaType(contentType)
        return try {
            val results = repository.searchByFts(contentType, ftsQuery, includeExcluded)
            kotlin.Result.success(results.map { it.toDomain(mediaType) })
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getRelatedTitles(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): RelatedTitles {
        if (!tmdb.hasApiKey()) return RelatedTitles()
        val id = tmdbId?.toIntOrNull() ?: return RelatedTitles()
        if (contentType != ContentType.MOVIES && contentType != ContentType.TV_SHOWS) return RelatedTitles()

        // Three independent endpoints, so ask for all of them at once rather than paying the
        // round trips one after the other. Collections are a movie-only TMDB concept.
        val (recommended, similar, collectionFetch) =
            coroutineScope {
                val recommendedCall = async { fetchRelated(id, contentType, similar = false) }
                val similarCall = async { fetchRelated(id, contentType, similar = true) }
                val collectionCall = async { if (contentType == ContentType.MOVIES) fetchCollection(id) else CollectionFetch() }
                Triple(recommendedCall.await(), similarCall.await(), collectionCall.await())
            }

        val mediaType = getMediaType(contentType)
        val taken = mutableSetOf<String>()
        // Runs first, so a title belonging to the same collection as this one is always credited
        // there even when TMDB also lists it under recommendations/similar — and the collection
        // itself, being explicit rather than algorithmic, gets a row even with just one match.
        val collectionMatches = matchToCatalogue(collectionFetch.parts, itemId, contentType, mediaType, taken, minCount = 1)
        return RelatedTitles(
            collection = collectionMatches,
            collectionName = collectionFetch.name.takeIf { collectionMatches.isNotEmpty() },
            recommended = matchToCatalogue(recommended, itemId, contentType, mediaType, taken),
            // Runs last and shares [taken], so a title TMDB returns from more than one endpoint
            // appears only under the strongest heading instead of filling multiple rows.
            similar = matchToCatalogue(similar, itemId, contentType, mediaType, taken),
        )
    }

    override suspend fun getTmdbTitle(
        tmdbId: String?,
        contentType: String,
    ): String? {
        if (!tmdb.hasApiKey()) return null
        val id = tmdbId?.toIntOrNull() ?: return null
        if (contentType != ContentType.MOVIES && contentType != ContentType.TV_SHOWS) return null

        return try {
            val details = if (contentType == ContentType.MOVIES) tmdb.getMovieDetails(id) else tmdb.getTvDetails(id)
            details.originalDisplayTitle
        } catch (e: Exception) {
            Log.w("XtreamMediaProvider", "TMDB title for $contentType $id: ${e.message}")
            null
        }
    }

    override suspend fun getAlternateStreams(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): List<MediaItem> {
        if (tmdbId.isNullOrBlank()) return emptyList()
        if (contentType != ContentType.MOVIES && contentType != ContentType.TV_SHOWS) return emptyList()
        val excludeId = itemId.toIntOrNull() ?: return emptyList()

        return try {
            repository.getAlternateStreams(contentType, tmdbId, excludeId).map { it.toDomain(getMediaType(contentType)) }
        } catch (e: Exception) {
            Log.w("XtreamMediaProvider", "Alternate streams for $contentType tmdb $tmdbId: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchRelated(
        tmdbId: Int,
        contentType: String,
        similar: Boolean,
    ): List<TmdbRecommendation> =
        try {
            when {
                contentType == ContentType.MOVIES && similar -> tmdb.getMovieSimilar(tmdbId)
                contentType == ContentType.MOVIES -> tmdb.getMovieRecommendations(tmdbId)
                similar -> tmdb.getTvSimilar(tmdbId)
                else -> tmdb.getTvRecommendations(tmdbId)
            }.results
        } catch (e: Exception) {
            // A title filed under the wrong type 404s here. The rows are a bonus, never an error,
            // and one endpoint failing must not cost the other its row.
            Log.w("XtreamMediaProvider", "TMDB ${if (similar) "similar" else "recommendations"} for $contentType $tmdbId: ${e.message}")
            emptyList()
        }

    private data class CollectionFetch(
        val name: String? = null,
        val parts: List<TmdbRecommendation> = emptyList(),
    )

    /** The other movies in [movieId]'s TMDB collection, if it belongs to one. */
    private suspend fun fetchCollection(movieId: Int): CollectionFetch =
        try {
            val collectionId = tmdb.getMovieDetails(movieId).belongsToCollection?.id ?: return CollectionFetch()
            val collection = tmdb.getCollection(collectionId)
            CollectionFetch(name = collection.name, parts = collection.parts)
        } catch (e: Exception) {
            Log.w("XtreamMediaProvider", "TMDB collection for movie $movieId: ${e.message}")
            CollectionFetch()
        }

    /**
     * Keeps the TMDB titles this provider can actually play, in the order TMDB ranked them.
     * [taken] carries normalized titles already claimed — by an earlier row, or by an earlier
     * entry in this one — so nothing is listed twice.
     */
    private suspend fun matchToCatalogue(
        results: List<TmdbRecommendation>,
        itemId: String,
        contentType: String,
        mediaType: MediaType,
        taken: MutableSet<String>,
        minCount: Int = MIN_RELATED_TITLES,
    ): List<MediaItem> {
        val matches = mutableListOf<MediaItem>()
        for (result in results) {
            val title = result.displayTitle ?: continue
            val key = TitleMatcher.normalize(title).text
            // Providers list the same film in several categories and qualities; one row each.
            if (key.isBlank() || key in taken) continue

            val ftsQuery = buildFtsQuery(title) ?: continue
            val candidates =
                try {
                    repository.searchByFts(contentType, ftsQuery, includeExcluded = false)
                } catch (e: Exception) {
                    continue
                }
            val hit =
                candidates.firstOrNull { candidate ->
                    candidate.streamId.toString() != itemId &&
                        TitleMatcher.matches(
                            catalogueTitle = candidate.name,
                            catalogueYear = candidate.releaseDate?.take(4)?.toIntOrNull(),
                            tmdbTitle = title,
                            tmdbYear = result.year,
                        )
                } ?: continue
            taken += key
            // The card shows this name and the screen it opens is titled with it, so the provider's
            // "NF - " / "4K-AMZ - " tags come off here rather than at either call site.
            matches += hit.toDomain(mediaType).let { it.copy(name = TitleMatcher.stripProviderPrefix(it.name)) }
        }
        return if (matches.size < minCount) emptyList() else matches
    }

    override suspend fun countExcludedSearchMatches(
        query: String,
        contentType: String,
    ): Int {
        val ftsQuery = buildFtsQuery(query) ?: return 0
        return try {
            repository.countExcludedByFts(contentType, ftsQuery)
        } catch (e: Exception) {
            0
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
     * Used by background worker. Returns how many rows actually changed, summed across all six
     * sync tasks — all-zero means the provider's catalog didn't change since the last sync.
     */
    suspend fun syncAll(): SyncDelta {
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
        repository.recomputeExclusions()
        return repository.consumeSyncDelta()
    }

    /** Total category count for [contentType], including any excluded by category filters — for "X of Y" UI counts. */
    suspend fun getCategoryTotalCount(contentType: String): Int = repository.getCategoryTotalCount(contentType)

    companion object {
        // Detail data (plot/cast/genre/rating/contentRating) rarely changes for a given title —
        // long TTL avoids re-hitting Xtream + TMDB every time a detail screen is reopened.
        private const val DETAIL_CACHE_TTL_MS = 7 * 24 * 3600 * 1000L // 7 days
        private const val MAX_CONCURRENT_TMDB_REQUESTS = 10

        // Below this a row reads as an accident rather than a suggestion, so it is not shown.
        private const val MIN_RELATED_TITLES = 3
    }
}
