package org.njarasoa.fijerena.core.network.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Minimal TMDB v3 client. Used to enrich Xtream series episodes with overviews,
 * since most Xtream providers only include a TMDB episode id and poster URL —
 * not the episode synopsis itself.
 */
class TmdbApiService(
    private val apiKey: String,
) {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    // TMDB v4 read-access tokens are JWTs (Bearer); v3 keys are 32-char hex (query param).
    private val isV4Token: Boolean = apiKey.startsWith("eyJ")

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
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
    ): TmdbSeasonResponse =
        client
            .get("tv/$tvId/season/$seasonNumber") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /** Per-country theatrical release dates and certifications (e.g. "PG-13") for a movie. */
    suspend fun getMovieReleaseDates(movieId: Int): TmdbReleaseDatesResponse =
        client.get("movie/$movieId/release_dates") { authenticate() }.body()

    /** Per-country content ratings (e.g. "TV-MA") for a TV series. */
    suspend fun getTvContentRatings(tvId: Int): TmdbContentRatingsResponse =
        client.get("tv/$tvId/content_ratings") { authenticate() }.body()

    /**
     * Algorithmic "if you liked this" list for a movie. TMDB also offers `/similar`, which always
     * returns 20 genre-adjacent rows of noticeably worse quality; `/recommendations` returning
     * few or zero rows for an obscure title is the better failure.
     */
    suspend fun getMovieRecommendations(movieId: Int): TmdbRecommendationsResponse =
        client
            .get("movie/$movieId/recommendations") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /** As [getMovieRecommendations], for a TV series. */
    suspend fun getTvRecommendations(tvId: Int): TmdbRecommendationsResponse =
        client
            .get("tv/$tvId/recommendations") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /**
     * Keyword and genre overlap for a movie. Always answers with a full page, and the further down
     * it goes the looser the connection — noticeably weaker than [getMovieRecommendations], which
     * is why the two are shown as separate rows rather than merged into one.
     */
    suspend fun getMovieSimilar(movieId: Int): TmdbRecommendationsResponse =
        client
            .get("movie/$movieId/similar") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /** As [getMovieSimilar], for a TV series. */
    suspend fun getTvSimilar(tvId: Int): TmdbRecommendationsResponse =
        client
            .get("tv/$tvId/similar") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /**
     * The movie's own TMDB record. Reuses [TmdbRecommendation]'s shape — `id`, `title`,
     * `release_date` and the rest line up with `/movie/{id}` too — for just the title.
     */
    suspend fun getMovieDetails(movieId: Int): TmdbRecommendation =
        client
            .get("movie/$movieId") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /** As [getMovieDetails], for a TV series. */
    suspend fun getTvDetails(tvId: Int): TmdbRecommendation =
        client
            .get("tv/$tvId") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    /** Backdrops/posters/logos for a movie. Only `logos` (transparent wordmark art) is used. */
    suspend fun getMovieImages(movieId: Int): TmdbImagesResponse =
        client.get("movie/$movieId/images") { authenticate() }.body()

    /** As [getMovieImages], for a TV series. */
    suspend fun getTvImages(tvId: Int): TmdbImagesResponse =
        client.get("tv/$tvId/images") { authenticate() }.body()

    /** The franchise's name and every movie in it, for the collection a movie's details named. */
    suspend fun getCollection(collectionId: Int): TmdbCollectionResponse =
        client
            .get("collection/$collectionId") {
                authenticate()
                parameter("language", "en-US")
            }.body()

    private fun io.ktor.client.request.HttpRequestBuilder.authenticate() {
        if (isV4Token) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        } else {
            parameter("api_key", apiKey)
        }
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    companion object {
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        const val POSTER_SIZE_W185 = "w185"
        const val POSTER_SIZE_W342 = "w342"
        const val POSTER_SIZE_ORIGINAL = "original"
        const val LOGO_SIZE_W500 = "w500"

        fun posterUrl(path: String?, size: String = POSTER_SIZE_W185): String? {
            val p = path?.trim()?.removePrefix("/") ?: return null
            if (p.isEmpty()) return null
            return "$IMAGE_BASE_URL$size/$p"
        }

        /**
         * Best logo for the OSD wordmark: prefer English, then any language-neutral mark (no
         * language tag at all — usually the studio's international art), then whatever else TMDB
         * has, each tier picking the widest/highest-voted first. Null when TMDB has none.
         */
        fun bestLogoUrl(logos: List<TmdbLogo>, language: String = "en"): String? {
            val ranked = compareByDescending<TmdbLogo> { it.voteAverage }.thenByDescending { it.width }
            val best =
                logos.filter { it.language == language }.maxWithOrNull(ranked)
                    ?: logos.filter { it.language.isNullOrBlank() }.maxWithOrNull(ranked)
                    ?: logos.maxWithOrNull(ranked)
            return posterUrl(best?.filePath, LOGO_SIZE_W500)
        }
    }
}
