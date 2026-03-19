package org.njarasoa.fijerena.core.player.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Xtream IPTV API service for fetching categories and streams.
 * Uses OkHttp engine for better stability on Android TV hardware.
 *
 * @param baseUrl The Xtream API base URL (e.g., "http://example.com:8080")
 * @param username The Xtream account username
 * @param password The Xtream account password
 * @param streamOutputFormat The output format for live stream URLs: "m3u8" (HLS) or "ts" (MPEG-TS)
 */
class XtreamApiService(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val streamOutputFormat: String = "m3u8"
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

        install(ContentEncoding) {
            gzip()
            deflate()
        }

        defaultRequest {
            url(normalizeBaseUrl(baseUrl))
        }

        engine {
            preconfigured = org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
            config {
                // Additional configuration on top of shared client
                followRedirects(true)
                followSslRedirects(true)
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
     * Uses streaming response to handle potentially massive lists (50,000+ items).
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getStreams(categoryId: String? = null): List<XtreamStream> {
        return client.prepareGet("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_live_streams")
            if (categoryId != null) parameter("category_id", categoryId)
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { stream ->
                json.decodeFromStream<List<XtreamStream>>(stream)
            }
        }
    }

    /**
     * Streaming fetch for live streams. Items are passed to [onItem] as they are parsed.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getStreamsStreaming(categoryId: String? = null, onItem: suspend (XtreamStream) -> Unit) = coroutineScope {
        client.prepareGet("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_live_streams")
            if (categoryId != null) parameter("category_id", categoryId)
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { stream ->
                // TRUE streaming parse using decodeToSequence
                json.decodeToSequence<XtreamStream>(stream).forEach { 
                    onItem(it) 
                }
            }
        }
    }

    /**
     * Fetches all VOD streams (movies) for a specific category.
     * Uses streaming response to handle potentially massive lists.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getVodStreams(categoryId: String? = null): List<XtreamStream> {
        return client.prepareGet("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_vod_streams")
            if (categoryId != null) parameter("category_id", categoryId)
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { stream ->
                json.decodeFromStream<List<XtreamStream>>(stream)
            }
        }
    }

    /**
     * Streaming fetch for VOD streams.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getVodStreamsStreaming(categoryId: String? = null, onItem: suspend (XtreamStream) -> Unit) = coroutineScope {
        client.prepareGet("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_vod_streams")
            if (categoryId != null) parameter("category_id", categoryId)
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { stream ->
                // TRUE streaming parse
                json.decodeToSequence<XtreamStream>(stream).forEach { 
                    onItem(it) 
                }
            }
        }
    }

    /**
     * Fetches all series (TV shows) for a specific category.
     * Uses streaming response to handle potentially massive lists.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getSeries(categoryId: String? = null): List<XtreamSeries> {
        return client.prepareGet("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_series")
            if (categoryId != null) parameter("category_id", categoryId)
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { stream ->
                json.decodeFromStream<List<XtreamSeries>>(stream)
            }
        }
    }

    /**
     * Streaming fetch for series.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getSeriesStreaming(categoryId: String? = null, onItem: suspend (XtreamSeries) -> Unit) = coroutineScope {
        client.prepareGet("player_api.php") {
            parameter("username", username)
            parameter("password", password)
            parameter("action", "get_series")
            if (categoryId != null) parameter("category_id", categoryId)
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { stream ->
                // TRUE streaming parse
                json.decodeToSequence<XtreamSeries>(stream).forEach { 
                    onItem(it) 
                }
            }
        }
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
            return VodInfo(info = null, movieData = null)
        }

        return json.decodeFromString(responseText)
    }

    /**
     * Builds a playable stream URL for a given stream ID.
     *
     * Format: http://url:port/live/username/password/streamId.[format]
     *
     * The output format is determined by [streamOutputFormat] (e.g., "m3u8" for HLS
     * or "ts" for MPEG-TS, as specified by the Xtream server's `output` parameter).
     *
     * @param streamId The stream ID to build the URL for
     * @return The formatted stream URL
     */
    fun buildStreamUrl(streamId: Int): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/live/${encode(username)}/${encode(password)}/$streamId.$streamOutputFormat"
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
        return "$normalizedUrl/movie/${encode(username)}/${encode(password)}/$streamId.${encode(extension)}"
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
        return "$normalizedUrl/series/${encode(username)}/${encode(password)}/$streamId.${encode(extension)}"
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
        return "$normalizedUrl/series/${encode(username)}/${encode(password)}/${encode(episodeId)}.${encode(extension)}"
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
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
