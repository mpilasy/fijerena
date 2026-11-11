package org.njarasoa.fijerena.core.player.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.ConnectionPool
import org.njarasoa.fijerena.core.player.diagnostics.recordFailedCalls
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamEpgAnswer
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.player.model.toEpgResponse
import org.njarasoa.fijerena.core.player.model.withName
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Xtream IPTV API service for categories and streams, on the OkHttp engine (more stable on Android
 * TV hardware).
 *
 * @param baseUrl The Xtream API base URL (for example "http://example.com:8080")
 *
 * @param streamOutputFormat The output format of live stream URLs: "m3u8" (HLS) or "ts" (MPEG-TS)
 *
 * @param metadataTimeoutMs Overall deadline of a metadata call; the catalogue downloads have none
 */
class XtreamApiService(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val streamOutputFormat: String = "m3u8",
    private val metadataTimeoutMs: Long = METADATA_REQUEST_TIMEOUT_MS,
) {
    private val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    // Held so close() can shut them down: Ktor's HttpClient.close() on the OkHttp engine does not.
    // Not a true leak (both free idle resources on their own), but a provider switch keeps them
    // longer than needed.
    private val okhttpDispatcher = okhttp3.Dispatcher()
    private val okhttpConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    private val client: HttpClient =
        HttpClient(OkHttp) {
            // Without this, a non-2xx response (often an HTML error page) goes into the JSON
            // decoders and shows as a confusing SerializationException ("Unexpected token '<'")
            // instead of a status-carrying ClientRequestException / ServerResponseException. Same
            // as JellyfinApiService. Both are ordinary Exceptions, so `catch (e: Exception)` sites
            // keep working and friendlyErrorMessage()'s 401/403 matching finds them.
            expectSuccess = true
            install(ContentNegotiation) {
                json(json)
            }
            // An overall deadline per call, on top of OkHttp's per-read timeouts: a panel that
            // trickles a byte every few seconds never trips a read timeout and held Login, a
            // category load or a detail screen on a spinner. The catalogue downloads opt out
            // ([noDeadline]): a big list on a slow panel can take minutes. See the next level rock
            // solid resilience plan, R-14.
            install(HttpTimeout) {
                requestTimeoutMillis = metadataTimeoutMs
            }
            recordFailedCalls()

            // No ContentEncoding plugin on purpose: Ktor's Accept-Encoding makes some
            // Cloudflare-fronted panels answer with zstd, which it does not decode, so the body
            // arrives empty. OkHttp handles gzip itself while we do not set Accept-Encoding.
            defaultRequest {
                url(normalizeBaseUrl(baseUrl))
            }

            engine {
                // ktor-client-okhttp 3.5.2's createOkHttpClient() only builds a fresh Dispatcher()
                // when `preconfigured == null`. With `preconfigured` set, the client inherits
                // NetworkModule.okHttpClient's Dispatcher, and close() on any XtreamApiService
                // shuts down the app-wide executor (Xtream, Jellyfin, TMDB, EPG downloads and
                // ExoPlayer all fail with "executor rejected"). So set our own Dispatcher here, as
                // the ConnectionPool below, whatever a Ktor version does by default.
                // `preconfigured` is kept only for DNS, timeout and redirect settings.
                preconfigured = org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
                config {
                    followRedirects(true)
                    followSslRedirects(true)
                    dispatcher(okhttpDispatcher)
                    connectionPool(okhttpConnectionPool)
                }
            }
        }

    suspend fun authenticate(): XtreamAuthResponse {
        val response =
            client.get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
            }
        // .body() (Ktor ContentNegotiation) cannot resolve a serializer for bare (non-List)
        // response types in this build; getSeriesInfo / getVodInfo use the same workaround.
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getCategories(): List<XtreamCategory> =
        client
            .get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_live_categories")
            }.body<List<XtreamCategory?>>()
            .filterNotNull()
            .filter { it.categoryId.isNotBlank() }

    suspend fun getVodCategories(): List<XtreamCategory> =
        client
            .get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_vod_categories")
            }.body<List<XtreamCategory?>>()
            .filterNotNull()
            .filter { it.categoryId.isNotBlank() }

    suspend fun getSeriesCategories(): List<XtreamCategory> =
        client
            .get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_series_categories")
            }.body<List<XtreamCategory?>>()
            .filterNotNull()
            .filter { it.categoryId.isNotBlank() }

    /** All live streams of a category, read as a stream: lists can have 50,000+ items. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getStreams(categoryId: String? = null): List<XtreamStream> =
        client
            .prepareGet("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_live_streams")
                if (categoryId != null) parameter("category_id", categoryId)
                noDeadline()
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    json.decodeFromStream<List<XtreamStream?>>(stream).mapNotNull { it?.withName() }
                }
            }

    /** Live streams read as a stream: items go to [onItem] as they are parsed. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getStreamsStreaming(
        categoryId: String? = null,
        onItem: suspend (XtreamStream) -> Unit,
    ) = coroutineScope {
        client
            .prepareGet("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_live_streams")
                if (categoryId != null) parameter("category_id", categoryId)
                noDeadline()
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    json.decodeToSequence<XtreamStream?>(stream).forEach {
                        it?.withName()?.let { item -> onItem(item) }
                    }
                }
            }
    }

    /** All VOD streams (films) of a category, read as a stream. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getVodStreams(categoryId: String? = null): List<XtreamStream> =
        client
            .prepareGet("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_vod_streams")
                if (categoryId != null) parameter("category_id", categoryId)
                noDeadline()
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    json.decodeFromStream<List<XtreamStream?>>(stream).mapNotNull { it?.withName() }
                }
            }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getVodStreamsStreaming(
        categoryId: String? = null,
        onItem: suspend (XtreamStream) -> Unit,
    ) = coroutineScope {
        client
            .prepareGet("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_vod_streams")
                if (categoryId != null) parameter("category_id", categoryId)
                noDeadline()
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    json.decodeToSequence<XtreamStream?>(stream).forEach {
                        it?.withName()?.let { item -> onItem(item) }
                    }
                }
            }
    }

    /** All series (TV shows) of a category, read as a stream. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getSeries(categoryId: String? = null): List<XtreamSeries> =
        client
            .prepareGet("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_series")
                if (categoryId != null) parameter("category_id", categoryId)
                noDeadline()
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    json.decodeFromStream<List<XtreamSeries?>>(stream).mapNotNull { it?.withName() }
                }
            }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getSeriesStreaming(
        categoryId: String? = null,
        onItem: suspend (XtreamSeries) -> Unit,
    ) = coroutineScope {
        client
            .prepareGet("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_series")
                if (categoryId != null) parameter("category_id", categoryId)
                noDeadline()
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    json.decodeToSequence<XtreamSeries?>(stream).forEach {
                        it?.withName()?.let { item -> onItem(item) }
                    }
                }
            }
    }

    /**
     * A series' details with its seasons and episodes.
     *
     * @return the seasons and episodes, or the way the provider had nothing to give
     */
    suspend fun getSeriesInfo(seriesId: Int): XtreamResponse<SeriesInfo> =
        fetchItem(seriesId, "get_series_info", SeriesInfo::carriesNothing) {
            parameter("series_id", seriesId)
        }

    /**
     * A VOD film's details.
     *
     * @return the film's details, or the way the provider had nothing to give
     */
    suspend fun getVodInfo(vodId: Int): XtreamResponse<VodInfo> =
        fetchItem(vodId, "get_vod_info", VodInfo::carriesNothing) {
            parameter("vod_id", vodId)
        }

    /**
     * Runs one single-item lookup and classifies what comes back, failures too: a call that never
     * completes is [XtreamResponse.Failed], not an exception, so a caller handles every outcome in
     * one `when`.
     */
    private suspend inline fun <reified T> fetchItem(
        itemId: Int,
        action: String,
        noinline carriesNothing: (T) -> Boolean,
        crossinline params: HttpRequestBuilder.() -> Unit,
    ): XtreamResponse<T> {
        val raw =
            try {
                client
                    .get("player_api.php") {
                        parameter("username", username)
                        parameter("password", password)
                        parameter("action", action)
                        params()
                    }.bodyAsText()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return XtreamResponse.Failed(e)
            }

        return json.parseItemResponse(raw, itemId, action, carriesNothing)
    }

    /**
     * A playable live stream URL.
     *
     * Format: http://url:port/live/username/password/streamId.[format]
     *
     * [streamOutputFormat] sets the format ("m3u8" for HLS or "ts" for MPEG-TS, from the Xtream
     * server's `output` parameter).
     */
    fun buildStreamUrl(streamId: Int): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/live/${encode(username)}/${encode(password)}/$streamId.$streamOutputFormat"
    }

    /**
     * A playable VOD (film) stream URL.
     *
     * Format: http://url:port/movie/username/password/streamId.ext
     */
    fun buildVodStreamUrl(
        streamId: Int,
        extension: String = "mp4",
    ): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/movie/${encode(username)}/${encode(password)}/$streamId.${encode(extension)}"
    }

    /**
     * A playable series stream URL.
     *
     * Format: http://url:port/series/username/password/streamId.ext
     */
    fun buildSeriesStreamUrl(
        streamId: Int,
        extension: String = "mp4",
    ): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/series/${encode(username)}/${encode(password)}/$streamId.${encode(extension)}"
    }

    /**
     * A playable episode stream URL.
     *
     * Format: http://url:port/series/username/password/episodeId.ext
     */
    fun buildEpisodeStreamUrl(
        episodeId: String,
        extension: String,
    ): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/series/${encode(username)}/${encode(password)}/${encode(episodeId)}.${encode(extension)}"
    }

    /**
     * Builds a catch-up URL: [durationMin] minutes of live stream [streamId]'s archive from
     * [start], the panel's wall-clock time as `YYYY-MM-DD:HH-MM` ([XtreamPanelClock.timeshiftStart]).
     *
     * Always HLS, whatever the live output. panel-a builds the archive playlist before answering
     * (~6 s per hour of window: 2 s for 15 min, 25 s for 4 h), but then seeks are segment requests.
     * `.ts` answers in a second, yet has no index: every seek is a binary search of range requests
     * (~20 s on panel-a), and a window still on air has no end to measure, so it never seeks at
     * all. Measured in the catchup plan, "Facts measured".
     *
     * Format: http://url:port/timeshift/username/password/durationMin/start/streamId.m3u8
     */
    fun buildTimeshiftUrl(
        streamId: Int,
        start: String,
        durationMin: Long,
    ): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/timeshift/${encode(username)}/${encode(password)}/$durationMin/$start/$streamId.m3u8"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** A catalogue download: only OkHttp's per-read timeouts apply, not the overall deadline. */
    private fun HttpRequestBuilder.noDeadline() = timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }

    /** EPG data of one stream. Endpoint: player_api.php?action=get_simple_data_table&stream_id=X */
    suspend fun getEpgForStream(streamId: Int): EpgResponse {
        val response =
            client.get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_simple_data_table")
                parameter("stream_id", streamId)
            }
        return json.decodeFromString<XtreamEpgAnswer>(response.bodyAsText()).toEpgResponse()
    }

    /**
     * Fallback: the short EPG (next X programmes) of one stream. Endpoint:
     * player_api.php?action=get_short_epg&stream_id=X&limit=Y
     */
    suspend fun getShortEpg(
        streamId: Int,
        limit: Int = 10,
    ): EpgResponse {
        val response =
            client.get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_short_epg")
                parameter("stream_id", streamId)
                parameter("limit", limit)
            }
        return json.decodeFromString<XtreamEpgAnswer>(response.bodyAsText()).toEpgResponse()
    }

    /** Normalises the base URL: no trailing slash, and an http:// prefix. */
    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim()

        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }

        return normalized
    }

    fun close() {
        client.close()
        // client.close() alone does not shut these down (see okhttpDispatcher).
        okhttpDispatcher.executorService.shutdown()
        okhttpConnectionPool.evictAll()
    }

    companion object {
        /** Overall deadline of a metadata call (login, categories, details, short EPG). */
        const val METADATA_REQUEST_TIMEOUT_MS = 60_000L
    }
}
