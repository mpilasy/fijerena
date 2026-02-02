package org.njarasoa.fijerena.core.player.api

import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Xtream IPTV API service for fetching categories and streams.
 * Uses OkHttp engine for better stability on Android TV hardware.
 *
 * @param baseUrl The Xtream API base URL (e.g., "http://example.com:8080")
 * @param username The Xtream account username
 * @param password The Xtream account password
 */
class XtreamApiService(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }

        defaultRequest {
            url(normalizeBaseUrl(baseUrl))
        }

        engine {
            config {
                // OkHttp-specific configuration for Android TV
                followRedirects(true)
                followSslRedirects(true)
                // Increase timeouts for large VOD/Series category responses
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Authenticates with the Xtream API and retrieves user/server information.
     *
     * @return Authentication response containing user and server info
     * @throws Exception if authentication fails or the request fails
     */
    suspend fun authenticate(): XtreamAuthResponse {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
        }.body()
    }

    /**
     * Fetches all live TV categories from the Xtream API.
     *
     * @return List of categories
     * @throws Exception if the request fails
     */
    suspend fun getCategories(): List<XtreamCategory> {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_live_categories")
        }.body()
    }

    /**
     * Fetches all VOD (movie) categories from the Xtream API.
     *
     * @return List of VOD categories
     * @throws Exception if the request fails
     */
    suspend fun getVodCategories(): List<XtreamCategory> {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_vod_categories")
        }.body()
    }

    /**
     * Fetches all Series (TV show) categories from the Xtream API.
     *
     * @return List of series categories
     * @throws Exception if the request fails
     */
    suspend fun getSeriesCategories(): List<XtreamCategory> {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_series_categories")
        }.body()
    }

    /**
     * Fetches all live streams for a specific category.
     *
     * @param categoryId The category ID to fetch streams for
     * @return List of streams in the category
     * @throws Exception if the request fails
     */
    suspend fun getStreams(categoryId: String): List<XtreamStream> {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_live_streams")
            parameter("category_id", categoryId)
        }.body()
    }

    /**
     * Fetches all VOD streams (movies) for a specific category.
     *
     * @param categoryId The category ID to fetch VOD streams for
     * @return List of VOD streams in the category
     * @throws Exception if the request fails
     */
    suspend fun getVodStreams(categoryId: String): List<XtreamStream> {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_vod_streams")
            parameter("category_id", categoryId)
        }.body()
    }

    /**
     * Fetches all series (TV shows) for a specific category.
     *
     * @param categoryId The category ID to fetch series for
     * @return List of series in the category
     * @throws Exception if the request fails
     */
    suspend fun getSeries(categoryId: String): List<XtreamSeries> {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_series")
            parameter("category_id", categoryId)
        }.body()
    }

    /**
     * Fetches detailed information about a specific series including seasons and episodes.
     *
     * @param seriesId The series ID to fetch info for
     * @return Series info with seasons and episodes
     * @throws Exception if the request fails
     */
    suspend fun getSeriesInfo(seriesId: Int): SeriesInfo {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_series_info")
            parameter("series_id", seriesId)
        }.body()
    }

    /**
     * Fetches detailed information about a specific VOD movie.
     *
     * @param vodId The VOD movie ID to fetch info for
     * @return VOD info with movie details
     * @throws Exception if the request fails
     */
    suspend fun getVodInfo(vodId: Int): VodInfo {
        val response = client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_vod_info")
            parameter("vod_id", vodId)
        }

        val responseText = response.bodyAsText()

        // Some providers return an empty array [] instead of an object when VOD info is unavailable
        if (responseText.trim().startsWith("[")) {
            println("XtreamApiService: VOD info returned array (likely empty), returning empty VodInfo")
            return VodInfo(info = null, movieData = null)
        }

        return json.decodeFromString(responseText)
    }

    /**
     * Builds a playable stream URL for a given stream ID.
     *
     * Format: http://url:port/live/username/password/streamId.m3u8
     *
     * @param streamId The stream ID to build the URL for
     * @return The formatted stream URL in HLS format
     */
    fun buildStreamUrl(streamId: Int): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/live/$username/$password/$streamId.m3u8"
    }

    /**
     * Builds a playable VOD (movie) stream URL for a given stream ID.
     *
     * Format: http://url:port/movie/username/password/streamId.ext
     *
     * @param streamId The VOD stream ID to build the URL for
     * @param extension The file extension (e.g., "mp4", "mkv")
     * @return The formatted VOD stream URL
     */
    fun buildVodStreamUrl(streamId: Int, extension: String = "mp4"): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/movie/$username/$password/$streamId.$extension"
    }

    /**
     * Builds a playable Series (TV show) stream URL for a given stream ID.
     *
     * Format: http://url:port/series/username/password/streamId.ext
     *
     * @param streamId The series stream ID to build the URL for
     * @param extension The file extension (e.g., "mp4", "mkv")
     * @return The formatted series stream URL
     */
    fun buildSeriesStreamUrl(streamId: Int, extension: String = "mp4"): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/series/$username/$password/$streamId.$extension"
    }

    /**
     * Builds a playable episode stream URL for a specific episode.
     *
     * Format: http://url:port/series/username/password/episodeId.ext
     *
     * @param episodeId The episode ID to build the URL for
     * @param extension The file extension (e.g., "mp4", "mkv")
     * @return The formatted episode stream URL
     */
    fun buildEpisodeStreamUrl(episodeId: String, extension: String): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/series/$username/$password/$episodeId.$extension"
    }

    /**
     * Fetches EPG data for a specific stream.
     * Endpoint: player_api.php?action=get_simple_data_table&stream_id=X
     *
     * @param streamId The stream ID to fetch EPG data for
     * @return EPG response containing program listings
     * @throws Exception if the request fails
     */
    suspend fun getEpgForStream(streamId: Int): EpgResponse {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_simple_data_table")
            parameter("stream_id", streamId)
        }.body()
    }

    /**
     * Fallback: Short EPG (next X programs) for a specific stream.
     * Endpoint: player_api.php?action=get_short_epg&stream_id=X&limit=Y
     *
     * @param streamId The stream ID to fetch short EPG for
     * @param limit Maximum number of programs to fetch (default: 10)
     * @return EPG response containing limited program listings
     * @throws Exception if the request fails
     */
    suspend fun getShortEpg(streamId: Int, limit: Int = 10): EpgResponse {
        return client.get("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_short_epg")
            parameter("stream_id", streamId)
            parameter("limit", limit)
        }.body()
    }

    /**
     * Normalizes the base URL to ensure consistent formatting.
     * Removes trailing slashes and ensures http:// prefix.
     */
    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim()

        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        // Ensure http:// or https:// prefix
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }

        return normalized
    }

    /**
     * Closes the HTTP client and releases resources.
     * Call this when the service is no longer needed.
     */
    fun close() {
        client.close()
    }
}
