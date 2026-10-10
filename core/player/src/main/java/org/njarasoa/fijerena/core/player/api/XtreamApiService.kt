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
 * Xtream IPTV API service for fetching categories and streams.
 * Uses OkHttp engine for better stability on Android TV hardware.
 *
 * @param baseUrl The Xtream API base URL (e.g., "http://example.com:8080")
 * @param streamOutputFormat The output format for live stream URLs: "m3u8" (HLS) or "ts" (MPEG-TS)
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

    // Held directly so close() can shut them down explicitly — Ktor's HttpClient.close() on the
    // OkHttp engine doesn't do this itself. Not a true leak (OkHttp's default dispatcher reclaims
    // idle threads after 60s and this pool evicts idle connections after 5 min on its own), but a
    // provider switch/reconnect leaves both lingering longer than necessary until then.
    private val okhttpDispatcher = okhttp3.Dispatcher()
    private val okhttpConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    private val client: HttpClient =
        HttpClient(OkHttp) {
            // Without this, a non-2xx response (401/403/429/502...) — often an HTML error page,
            // not JSON — goes straight into decodeFromString/decodeFromStream/decodeToSequence
            // below, surfacing as a confusing SerializationException ("Unexpected token '<'")
            // instead of a catchable, status-carrying exception (ClientRequestException /
            // ServerResponseException). Same as JellyfinApiService. Both are still ordinary
            // Exceptions, so generic `catch (e: Exception)` call sites keep working, and
            // friendlyErrorMessage()'s 401/403 string-matching can match them.
            expectSuccess = true
            install(ContentNegotiation) {
                json(json)
            }
            // An overall deadline per call, on top of OkHttp's per-read timeouts: a panel that
            // trickles a byte every few seconds never trips a read timeout and held Login, a
            // category load or a detail screen on a spinner indefinitely. The catalogue downloads
            // opt out ([noDeadline]): a big list on a slow panel legitimately takes minutes. See
            // docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-14.
            install(HttpTimeout) {
                requestTimeoutMillis = metadataTimeoutMs
            }

            // No ContentEncoding plugin here deliberately: advertising gzip/deflate via Ktor's
            // Accept-Encoding causes some Cloudflare-fronted panels to respond with zstd instead
            // (a codec this plugin doesn't decode), leaving the body empty downstream. The
            // underlying OkHttp engine already transparently handles gzip on its own as long as
            // we don't set Accept-Encoding ourselves.
            defaultRequest {
                url(normalizeBaseUrl(baseUrl))
            }

            engine {
                // ktor-client-okhttp 3.5.2's createOkHttpClient() only builds a fresh Dispatcher()
                // when `preconfigured == null`:
                //   val builder = (config.preconfigured ?: okHttpClientPrototype).newBuilder()
                //   if (config.preconfigured == null) { builder.dispatcher(Dispatcher()) }
                // With `preconfigured` set, the client would inherit NetworkModule.okHttpClient's
                // Dispatcher, and close() on any XtreamApiService would shut down the app-wide
                // shared executor, breaking Xtream, Jellyfin, TMDB, EPG downloads, and ExoPlayer
                // streaming permanently (the "executor rejected" reports of 2026-09-21). So set our
                // own Dispatcher explicitly here, same as the ConnectionPool below, so this client
                // owns both regardless of what a given Ktor version does by default.
                // `preconfigured` is kept only for DNS/timeout/redirect inheritance.
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
        // .body() (Ktor ContentNegotiation) fails to resolve a serializer for bare (non-List)
        // response types in this build — see getSeriesInfo/getVodInfo for the same workaround.
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun getCategories(): List<XtreamCategory> =
        client
            .get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_live_categories")
            }.body()

    suspend fun getVodCategories(): List<XtreamCategory> =
        client
            .get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_vod_categories")
            }.body()

    suspend fun getSeriesCategories(): List<XtreamCategory> =
        client
            .get("player_api.php") {
                parameter("username", username)
                parameter("password", password)
                parameter("action", "get_series_categories")
            }.body()

    /**
     * Fetches all live streams for a specific category.
     * Uses streaming response to handle potentially massive lists (50,000+ items).
     */
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
                    json.decodeFromStream<List<XtreamStream>>(stream).mapNotNull { it.withName() }
                }
            }

    /**
     * Streaming fetch for live streams. Items are passed to [onItem] as they are parsed.
     */
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
                    json.decodeToSequence<XtreamStream>(stream).forEach {
                        it.withName()?.let { item -> onItem(item) }
                    }
                }
            }
    }

    /**
     * Fetches all VOD streams (movies) for a specific category.
     * Uses streaming response to handle potentially massive lists.
     */
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
                    json.decodeFromStream<List<XtreamStream>>(stream).mapNotNull { it.withName() }
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
                    json.decodeToSequence<XtreamStream>(stream).forEach {
                        it.withName()?.let { item -> onItem(item) }
                    }
                }
            }
    }

    /**
     * Fetches all series (TV shows) for a specific category.
     * Uses streaming response to handle potentially massive lists.
     */
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
                    json.decodeFromStream<List<XtreamSeries>>(stream).mapNotNull { it.withName() }
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
                    json.decodeToSequence<XtreamSeries>(stream).forEach {
                        it.withName()?.let { item -> onItem(item) }
                    }
                }
            }
    }

    /**
     * Fetches detailed information about a specific series including seasons and episodes.
     *
     * @return the seasons and episodes, or which way the provider had nothing to give
     */
    suspend fun getSeriesInfo(seriesId: Int): XtreamResponse<SeriesInfo> =
        fetchItem(seriesId, "get_series_info", SeriesInfo::carriesNothing) {
            parameter("series_id", seriesId)
        }

    /**
     * Fetches detailed information about a specific VOD movie.
     *
     * @return the movie details, or which way the provider had nothing to give
     */
    suspend fun getVodInfo(vodId: Int): XtreamResponse<VodInfo> =
        fetchItem(vodId, "get_vod_info", VodInfo::carriesNothing) {
            parameter("vod_id", vodId)
        }

    /**
     * Runs one single-item lookup and classifies whatever comes back, including the ways it can
     * fail: a call that never completes is [XtreamResponse.Failed] rather than a thrown exception,
     * so a caller handles every outcome in one `when` instead of a `when` plus a `try`.
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
     * Builds a playable stream URL for a given stream ID.
     *
     * Format: http://url:port/live/username/password/streamId.[format]
     *
     * The output format is determined by [streamOutputFormat] (e.g., "m3u8" for HLS
     * or "ts" for MPEG-TS, as specified by the Xtream server's `output` parameter).
     */
    fun buildStreamUrl(streamId: Int): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/live/${encode(username)}/${encode(password)}/$streamId.$streamOutputFormat"
    }

    /**
     * Builds a playable VOD (movie) stream URL for a given stream ID.
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
     * Builds a playable Series (TV show) stream URL for a given stream ID.
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
     * Builds a playable episode stream URL for a specific episode.
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
     * Always HLS, whatever the live output. bears builds the archive playlist before answering
     * (~6 s per hour of window: 2 s for 15 min, 25 s for 4 h), but then seeks are segment requests.
     * `.ts` answers in a second, yet has no index: every seek is a binary search of range requests
     * (~20 s on bears), and a window still on air has no end to measure, so it never seeks at all.
     * Measured 2026-10-10, docs/plans/archive/20261010_catchup-plan.md → "Facts measured".
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

    /**
     * Fetches EPG data for a specific stream.
     * Endpoint: player_api.php?action=get_simple_data_table&stream_id=X
     */
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
     * Fallback: Short EPG (next X programs) for a specific stream.
     * Endpoint: player_api.php?action=get_short_epg&stream_id=X&limit=Y
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

    /**
     * Normalizes the base URL to ensure consistent formatting.
     * Removes trailing slashes and ensures http:// prefix.
     */
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
        // client.close() alone doesn't touch these — see okhttpDispatcher's kdoc.
        okhttpDispatcher.executorService.shutdown()
        okhttpConnectionPool.evictAll()
    }

    companion object {
        /** Overall deadline of a metadata call (login, categories, details, short EPG). */
        const val METADATA_REQUEST_TIMEOUT_MS = 60_000L
    }
}
