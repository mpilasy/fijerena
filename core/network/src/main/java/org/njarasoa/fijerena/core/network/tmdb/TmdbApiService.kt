package org.njarasoa.fijerena.core.network.tmdb

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.diagnostics.recordFailedCalls
import java.io.IOException
import java.util.Locale

/** What TMDB said to a key under test (Settings' Test button). */
enum class TmdbKeyCheck { VALID, REJECTED, UNREACHABLE }

/** No usable TMDB key: none entered, or TMDB refused it. Thrown before any request is made. */
class TmdbKeyMissingException : IOException("No usable TMDB key")

/**
 * Minimal TMDB v3 client. Used to enrich Xtream series episodes with overviews,
 * since most Xtream providers only include a TMDB episode id and poster URL —
 * not the episode synopsis itself.
 *
 * The key is the user's own (D4, the public release plan):
 * [activeKey] is read on every call, so entering, changing or removing it in Settings applies at
 * once. Without one every call fails before reaching the network and [hasApiKey] is false, so
 * callers skip TMDB altogether. A 401 hands the key to [onRejected] (`TmdbKeyStore.markRejected`),
 * after which [activeKey] returns null: a revoked key then behaves exactly like no key.
 *
 * Construct via [getInstance], not directly: the key is app-wide with no per-provider variation,
 * so there's nothing to gain from a separate instance per
 * [org.njarasoa.fijerena.core.network.XtreamMediaProvider] — only a separate `HttpClient` (and the
 * Ktor-internal `Dispatcher`/coroutine that comes with it, see
 * the xtream concurrency fixes plan, Finding 2) to leak if it's ever
 * discarded without being closed, which nothing here does.
 *
 * Every call goes through [limiter] (the cast tab plan, CT-D0). The single instance makes the limit
 * process-wide.
 */
class TmdbApiService(
    private val activeKey: () -> String?,
    private val onRejected: (String) -> Unit = {},
    private val appLanguage: () -> String = { Locale.getDefault().language },
    private val limiter: TmdbRateLimiter = TmdbRateLimiter(),
) {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            // `preconfigured` does NOT guarantee an isolated Dispatcher.
            // ktor-client-okhttp 3.5.2 (the version actually resolved here) only builds a fresh
            // Dispatcher() when `preconfigured == null` — when it's set, the built client shares
            // NetworkModule.okHttpClient's real Dispatcher object. This client has no close()
            // today, so it isn't actively triggering it, but see
            // core/player/api/XtreamApiService.kt's engine block for the incident this exact
            // pattern caused there once its close() calls landed. Own Dispatcher + ConnectionPool
            // set explicitly below so this stays safe if a close() is ever added.
            engine {
                preconfigured = org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
                config {
                    dispatcher(okhttp3.Dispatcher())
                    connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
                }
            }
            install(ContentNegotiation) { json(json) }
            // An overall deadline per call, not only per read: see
            // the next level rock solid resilience plan → R-14.
            install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            recordFailedCalls()
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            defaultRequest { url("https://api.themoviedb.org/3/") }
        }
    }

    /**
     * Fetch a full season from TMDB. One call returns overviews for every episode
     * in that season, so we batch at the season level.
     */
    suspend fun getSeason(
        tvId: Int,
        seasonNumber: Int,
    ): TmdbSeasonResponse = fetch("tv/$tvId/season/$seasonNumber", Language.APP).body()

    /** Per-country theatrical release dates and certifications (e.g. "PG-13") for a movie. */
    suspend fun getMovieReleaseDates(movieId: Int): TmdbReleaseDatesResponse = fetch("movie/$movieId/release_dates").body()

    /** Per-country content ratings (e.g. "TV-MA") for a TV series. */
    suspend fun getTvContentRatings(tvId: Int): TmdbContentRatingsResponse = fetch("tv/$tvId/content_ratings").body()

    /**
     * Algorithmic "if you liked this" list for a movie. TMDB also offers `/similar`, which always
     * returns 20 genre-adjacent rows of noticeably worse quality; `/recommendations` returning
     * few or zero rows for an obscure title is the better failure.
     *
     * In English whatever the app language, like the other related-title lists and [getCollection]:
     * their titles are matched against the provider's catalogue, whose language has nothing to do
     * with the app's.
     */
    suspend fun getMovieRecommendations(movieId: Int): TmdbRecommendationsResponse =
        fetch("movie/$movieId/recommendations", Language.ENGLISH).body()

    /** As [getMovieRecommendations], for a TV series. */
    suspend fun getTvRecommendations(tvId: Int): TmdbRecommendationsResponse = fetch("tv/$tvId/recommendations", Language.ENGLISH).body()

    /**
     * Keyword and genre overlap for a movie. Always answers with a full page, and the further down
     * it goes the looser the connection — noticeably weaker than [getMovieRecommendations], which
     * is why the two are shown as separate rows rather than merged into one.
     */
    suspend fun getMovieSimilar(movieId: Int): TmdbRecommendationsResponse = fetch("movie/$movieId/similar", Language.ENGLISH).body()

    /** As [getMovieSimilar], for a TV series. */
    suspend fun getTvSimilar(tvId: Int): TmdbRecommendationsResponse = fetch("tv/$tvId/similar", Language.ENGLISH).body()

    /**
     * The movie's own TMDB record. Reuses [TmdbRecommendation]'s shape — `id`, `title`,
     * `release_date` and the rest line up with `/movie/{id}` too. In the app language, since its
     * plot fills a missing one; the original title and the collection id don't depend on it.
     */
    suspend fun getMovieDetails(movieId: Int): TmdbRecommendation = fetch("movie/$movieId", Language.APP).body()

    /** As [getMovieDetails], for a TV series. */
    suspend fun getTvDetails(tvId: Int): TmdbRecommendation = fetch("tv/$tvId", Language.APP).body()

    /**
     * As [getMovieDetails] or [getTvDetails], with the cast and crew and the videos in the same call
     * (a TMDB page). Videos in the app language, English or none, since most trailers are English.
     * A film's cast and crew come in `credits`; a show's in `aggregate_credits`, which covers all its
     * seasons (CT-D3). [TmdbRecommendation.anyCredits] gives either.
     */
    suspend fun getDetailsWithCredits(
        movie: Boolean,
        id: Int,
    ): TmdbRecommendation =
        fetch(
            if (movie) "movie/$id" else "tv/$id",
            Language.APP,
            mapOf(
                "append_to_response" to if (movie) "credits,videos" else "aggregate_credits,videos",
                "include_video_language" to "${appLanguage()},en,null",
            ),
        ).body()

    /** The title's trailers, teasers and clips: in the app language, English or none, as [getDetailsWithCredits]. */
    suspend fun getVideos(
        movie: Boolean,
        id: Int,
    ): TmdbVideos =
        fetch(
            if (movie) "movie/$id/videos" else "tv/$id/videos",
            Language.APP,
            mapOf("include_video_language" to "${appLanguage()},en,null"),
        ).body()

    /** Backdrops/posters/logos for a movie. `logos` (wordmark art) and `backdrops` (hero
     * background) are used; posters come from the Xtream catalogue instead. */
    suspend fun getMovieImages(movieId: Int): TmdbImagesResponse = fetch("movie/$movieId/images").body()

    /** As [getMovieImages], for a TV series. */
    suspend fun getTvImages(tvId: Int): TmdbImagesResponse = fetch("tv/$tvId/images").body()

    /** The franchise's name and every movie in it, for the collection a movie's details named. */
    suspend fun getCollection(collectionId: Int): TmdbCollectionResponse = fetch("collection/$collectionId", Language.ENGLISH).body()

    /**
     * Settings' Test button: whether TMDB accepts [candidate], which need not be saved yet. Never
     * marks a key rejected — only the saved key's own calls do that.
     */
    suspend fun checkKey(candidate: String): TmdbKeyCheck {
        val key = candidate.trim()
        val result =
            if (key.isBlank()) {
                TmdbKeyCheck.REJECTED
            } else {
                try {
                    keyCheckResult(limitedGet("authentication") { authenticate(key) }.status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "TMDB key check failed: ${e.javaClass.simpleName}")
                    TmdbKeyCheck.UNREACHABLE
                }
            }
        return result
    }

    fun hasApiKey(): Boolean = activeKey() != null

    /** Page [page] (from 1) of [chart], titles in English as the catalogue's mostly are. */
    suspend fun getChartPage(
        chart: TmdbChart,
        page: Int,
    ): TmdbRecommendationsResponse = fetch(chart.path, Language.ENGLISH, chart.parameters + ("page" to page.toString())).body()

    /**
     * Films and shows whose title matches [query], ranked by TMDB (`search/multi`, in the app
     * language, no adult titles). People come back too: callers keep `mediaType` `movie` and `tv`.
     * The query is text the user typed — see `PRIVACY.md` (wanted titles plan, Phase 3).
     */
    suspend fun searchMulti(query: String): TmdbRecommendationsResponse =
        fetch("search/multi", Language.APP, mapOf("query" to query, "include_adult" to "false")).body()

    /**
     * A person and all the films and shows of the person (`combined_credits`). The titles are in
     * English, as the catalogue matching needs. The name and the biography are in the app language:
     * when it is not English, a second call without the credits gets them; a blank biography there
     * keeps the English one.
     */
    suspend fun getPerson(personId: Int): TmdbPerson {
        val english: TmdbPerson =
            fetch("person/$personId", Language.ENGLISH, mapOf("append_to_response" to "combined_credits")).body()
        if (tmdbLanguage(appLanguage()) == ENGLISH) return english
        val local: TmdbPerson =
            try {
                fetch("person/$personId", Language.APP).body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The English text is better than no page.
                Log.w(TAG, "TMDB person $personId in the app language: ${e.message}")
                return english
            }
        return english.copy(
            name = local.name?.takeIf { it.isNotBlank() } ?: english.name,
            biography = local.biography?.takeIf { it.isNotBlank() } ?: english.biography,
        )
    }

    private enum class Language { NONE, ENGLISH, APP }

    /**
     * One call with the active key. No key: throws before any request. Any non-2xx answer throws
     * too (callers already treat a failure as "no TMDB data"); a 401 first reports the key as
     * rejected, which turns TMDB off until another key is entered. A 429 waits and tries once more
     * ([TmdbRateLimiter]); a second 429 throws.
     */
    private suspend fun fetch(
        path: String,
        language: Language = Language.NONE,
        parameters: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val key = activeKey() ?: throw TmdbKeyMissingException()
        val response =
            limitedGet(path) {
                authenticate(key)
                parameters.forEach { (name, value) -> parameter(name, value) }
                when (language) {
                    Language.NONE -> Unit
                    Language.ENGLISH -> parameter("language", ENGLISH)
                    Language.APP -> parameter("language", tmdbLanguage(appLanguage()))
                }
            }
        if (response.status == HttpStatusCode.Unauthorized) {
            onRejected(key)
            throw TmdbKeyMissingException()
        }
        if (!response.status.isSuccess()) throw IOException("TMDB $path: HTTP ${response.status.value}")
        return response
    }

    /** A GET to [path] in the [limiter], tried once more after a 429. */
    private suspend fun limitedGet(
        path: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        limiter.execute(
            retryAfter = { response ->
                if (response.status == HttpStatusCode.TooManyRequests) {
                    TmdbRateLimiter.RetryAfter(response.headers[HttpHeaders.RetryAfter])
                } else {
                    null
                }
            },
        ) { client.get(path, block) }

    private fun io.ktor.client.request.HttpRequestBuilder.authenticate(key: String) {
        if (isV4Token(key)) {
            header(HttpHeaders.Authorization, "Bearer $key")
        } else {
            parameter("api_key", key)
        }
    }

    companion object {
        /** TMDB is a fast CDN-fronted API answering small JSON: a call slower than this is stuck. */
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val TAG = "TmdbApiService"
        private const val ENGLISH = "en-US"

        @Volatile
        private var instance: TmdbApiService? = null

        /** The single, process-wide [TmdbApiService] — see the class kdoc for why. */
        fun getInstance(context: Context): TmdbApiService =
            instance ?: synchronized(this) {
                instance ?: TmdbKeyStore(context).let { TmdbApiService(it::activeKey, it::markRejected) }.also { instance = it }
            }

        /** TMDB v4 read access tokens are JWTs (Bearer); v3 keys are 32-char hex (query param). */
        fun isV4Token(key: String): Boolean = key.startsWith("eyJ")

        /**
         * TMDB's `language` for the app language [appLanguage] (`en`, `fr`, `mg`). TMDB has almost no
         * Malagasy text, and for a language it lacks it sends empty overviews rather than English
         * ones, so anything but French asks for English.
         */
        fun tmdbLanguage(appLanguage: String): String = if (appLanguage == "fr") "fr-FR" else ENGLISH

        /**
         * The YouTube id of the title's trailer, or null: a trailer before a teaser, then an
         * official one, then one in [language] (`en`, `fr`…) before any other.
         */
        fun bestTrailerKey(
            videos: List<TmdbVideo>,
            language: String,
        ): String? =
            videos
                .filter { it.site == YOUTUBE && !it.key.isNullOrBlank() && (it.type == TRAILER || it.type == TEASER) }
                .sortedWith(
                    compareBy<TmdbVideo> { it.type != TRAILER }
                        .thenBy { !it.official }
                        .thenBy { it.language != language },
                ).firstOrNull()
                ?.key

        private const val YOUTUBE = "YouTube"
        private const val TRAILER = "Trailer"
        private const val TEASER = "Teaser"

        /** The Test button's verdict on TMDB's answer to `/authentication`. */
        fun keyCheckResult(status: HttpStatusCode): TmdbKeyCheck =
            when {
                status.isSuccess() -> TmdbKeyCheck.VALID
                status == HttpStatusCode.Unauthorized -> TmdbKeyCheck.REJECTED
                else -> TmdbKeyCheck.UNREACHABLE
            }

        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        const val POSTER_SIZE_W185 = "w185"
        const val POSTER_SIZE_W342 = "w342"
        const val POSTER_SIZE_ORIGINAL = "original"
        const val LOGO_SIZE_W500 = "w500"
        const val BACKDROP_SIZE_W1280 = "w1280"
        const val PROFILE_SIZE_W185 = "w185"

        // The large photo of a person's page; TMDB has no width step between w185 and h632.
        const val PROFILE_SIZE_H632 = "h632"

        fun posterUrl(
            path: String?,
            size: String = POSTER_SIZE_W185,
        ): String? {
            val p = path?.trim()?.removePrefix("/") ?: return null
            if (p.isEmpty()) return null
            return "$IMAGE_BASE_URL$size/$p"
        }

        /** A person's photo (`profile_path`), or null when TMDB has none. */
        fun profileUrl(
            path: String?,
            size: String = PROFILE_SIZE_W185,
        ): String? = posterUrl(path, size)

        /**
         * Best logo for the OSD wordmark: prefer English, then any language-neutral mark (no
         * language tag at all — usually the studio's international art), then whatever else TMDB
         * has, each tier picking the widest/highest-voted first. Null when TMDB has none.
         */
        fun bestLogoUrl(
            logos: List<TmdbImage>,
            language: String = "en",
        ): String? {
            // Ascending comparator, `maxWithOrNull` below: compareByDescending here would have
            // maxWithOrNull pick the *lowest*-voted/narrowest logo, since maxWithOrNull returns
            // the comparator's greatest element and a descending comparator inverts what "greatest"
            // means relative to the actual field values.
            val ranked = compareBy<TmdbImage> { it.voteAverage }.thenBy { it.width }
            val best =
                logos.filter { it.language == language }.maxWithOrNull(ranked)
                    ?: logos.filter { it.language.isNullOrBlank() }.maxWithOrNull(ranked)
                    ?: logos.maxWithOrNull(ranked)
            return posterUrl(best?.filePath, LOGO_SIZE_W500)
        }

        /**
         * Best backdrop for the TV detail hero: prefer language-neutral art (almost all
         * backdrops are — a plain scene carries no text), highest-voted/widest first, falling
         * back to whatever else TMDB has. Null when TMDB has none.
         */
        fun bestBackdropUrl(backdrops: List<TmdbImage>): String? {
            // See the comment on the equivalent line in bestLogoUrl: ascending comparator on
            // purpose, not compareByDescending.
            val ranked = compareBy<TmdbImage> { it.voteAverage }.thenBy { it.width }
            val best =
                backdrops.filter { it.language.isNullOrBlank() }.maxWithOrNull(ranked)
                    ?: backdrops.maxWithOrNull(ranked)
            return posterUrl(best?.filePath, BACKDROP_SIZE_W1280)
        }
    }
}
