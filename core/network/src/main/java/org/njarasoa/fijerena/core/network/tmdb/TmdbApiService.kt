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

    private val client: HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            defaultRequest { url("https://api.themoviedb.org/3/") }
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

    private fun io.ktor.client.request.HttpRequestBuilder.authenticate() {
        if (isV4Token) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        } else {
            parameter("api_key", apiKey)
        }
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()
}
