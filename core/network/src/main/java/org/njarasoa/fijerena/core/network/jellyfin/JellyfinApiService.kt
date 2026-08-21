package org.njarasoa.fijerena.core.network.jellyfin

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class JellyfinApiService(
    private val serverUrl: String,
    private val deviceId: String,
) {
    private var accessToken: String? = null
    private var userId: String? = null
    private var serverId: String? = null

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    private val authHeader: String
        get() =
            buildString {
                append("MediaBrowser ")
                append("Client=\"Fijerena\", ")
                append("Device=\"Android\", ")
                append("DeviceId=\"$deviceId\", ")
                append("Version=\"1.0.0\"")
                accessToken?.let { append(", Token=\"$it\"") }
            }

    private val client =
        HttpClient(OkHttp) {
            expectSuccess = true
            engine {
                preconfigured = org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
                config {
                    // Additional configuration on top of shared client
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
        }.also { httpClient ->
            // Inject Authorization and X-Emby-Token on every request.
            httpClient.plugin(HttpSend).intercept { request ->
                val h = authHeader
                request.headers["Authorization"] = h
                accessToken?.let { request.headers["X-Emby-Token"] = it }
                execute(request)
            }
        }

    // DeviceProfile describing ExoPlayer + Jellyfin FFmpeg extension capabilities.
    // Jellyfin uses this to decide: direct play, direct stream, or transcode.
    private val deviceProfile: JsonObject by lazy { buildDeviceProfile() }

    fun getAccessToken(): String? = accessToken

    fun getUserId(): String? = userId

    /** Restore a previously persisted session without re-authenticating. */
    fun restoreSession(
        token: String,
        userId: String,
    ) {
        accessToken = token
        this.userId = userId
    }

    // ---- Auth ----

    suspend fun authenticate(
        username: String,
        password: String,
    ): Result<JellyfinAuthResponse> {
        return try {
            val response =
                client
                    .post("$serverUrl/Users/AuthenticateByName") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            buildJsonObject {
                                put("Username", username)
                                put("Password", password)
                                put("Pw", password)
                            },
                        )
                    }.body<JellyfinAuthResponse>()
            accessToken = response.accessToken
            userId = response.user.id
            serverId = response.serverId
            // Register client capabilities so Jellyfin knows what we can play
            postCapabilities()
            Result.success(response)
        } catch (e: io.ktor.client.plugins.ClientRequestException) {
            Log.e(TAG, "Auth client error: ${e.response.status}", e)
            val message =
                when (e.response.status.value) {
                    401 -> "Invalid username or password"
                    403 -> "Access denied. Account may be disabled."
                    else -> "Authentication failed (${e.response.status})"
                }
            Result.failure(Exception(message, e))
        } catch (e: io.ktor.client.plugins.ServerResponseException) {
            Log.e(TAG, "Auth server error: ${e.response.status}", e)
            if (e.response.status.value == 500 && password.isNotBlank()) {
                // AuthenticateByName endpoint is broken on this server — try the password
                // as a direct API key (user can generate one from Jellyfin Dashboard → API Keys)
                return authenticateWithApiKey(password)
            }
            val message =
                if (password.isBlank()) {
                    "Authentication failed: no password configured. Edit the provider to enter your password."
                } else {
                    "Server error (${e.response.status.value}). Check that the server URL is correct."
                }
            Result.failure(Exception(message, e))
        } catch (e: Exception) {
            Log.e(TAG, "Auth failed", e)
            Result.failure(e)
        }
    }

    private suspend fun authenticateWithApiKey(apiKey: String): Result<JellyfinAuthResponse> =
        try {
            // Temporarily set the API key as the token so the interceptor includes it
            accessToken = apiKey
            val user = client.get("$serverUrl/Users/Me").body<JellyfinUser>()
            userId = user.id
            postCapabilities()
            // Return a synthetic auth response so callers don't need to change
            Result.success(JellyfinAuthResponse(user = user, accessToken = apiKey))
        } catch (e: Exception) {
            accessToken = null
            userId = null
            Log.e(TAG, "API key auth failed")
            Result.failure(Exception("Authentication failed. Check credentials or provide a Dashboard API key as the password.", e))
        }

    fun isAuthenticated(): Boolean = accessToken != null && userId != null

    // ---- Capabilities ----

    /**
     * POST /Sessions/Capabilities/Full — tells Jellyfin this session is a capable
     * playback client. Called automatically after successful authentication.
     */
    suspend fun postCapabilities() {
        try {
            client.post("$serverUrl/Sessions/Capabilities/Full") {
                contentType(ContentType.Application.Json)
                setBody(JellyfinClientCapabilities())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post capabilities (non-fatal)", e)
        }
    }

    // ---- PlaybackInfo negotiation ----

    /**
     * POST /Items/{itemId}/PlaybackInfo with a DeviceProfile.
     *
     * Jellyfin responds with:
     * - [JellyfinPlaybackMediaSource.supportsDirectPlay] = true → stream file as-is
     * - [JellyfinPlaybackMediaSource.transcodingUrl] set → server will transcode to HLS
     * - [JellyfinPlaybackInfoResponse.playSessionId] → include in progress reports
     */
    suspend fun getPlaybackInfo(
        itemId: String,
        userId: String,
        startTimeTicks: Long = 0,
    ): Result<JellyfinPlaybackInfoResponse> =
        try {
            val request =
                JellyfinPlaybackInfoRequest(
                    userId = userId,
                    startTimeTicks = startTimeTicks,
                    deviceProfile = deviceProfile,
                )
            val response =
                client
                    .post("$serverUrl/Items/$itemId/PlaybackInfo") {
                        contentType(ContentType.Application.Json)
                        parameter("UserId", userId)
                        setBody(request)
                    }.body<JellyfinPlaybackInfoResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "PlaybackInfo request failed for $itemId", e)
            Result.failure(e)
        }

    // ---- Catalog browsing ----

    suspend fun getLibraries(): Result<List<JellyfinItem>> =
        try {
            val response =
                client
                    .get("$serverUrl/Users/$userId/Views")
                    .body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getItems(
        parentId: String? = null,
        includeItemTypes: String? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        ids: String? = null,
    ): Result<List<JellyfinItem>> =
        try {
            client
                .prepareGet("$serverUrl/Users/$userId/Items") {
                    parentId?.let { parameter("ParentId", it) }
                    includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                    ids?.let { parameter("Ids", it) }
                    parameter("SortBy", sortBy)
                    parameter("SortOrder", sortOrder)
                    parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData,ParentId")
                    parameter("Recursive", true)
                }.execute { response ->
                    response.bodyAsChannel().toInputStream().use { stream ->
                        val result = json.decodeFromStream<JellyfinItemsResponse>(stream)
                        Result.success(result.items)
                    }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Streaming version of getItems. Individual [JellyfinItem] objects are passed to [onItem].
     * Jellyfin wraps items in an object {"Items": [...], "TotalRecordCount": X}.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun getItemsStreaming(
        parentId: String? = null,
        includeItemTypes: String? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        onItem: (JellyfinItem) -> Unit,
    ) {
        client
            .prepareGet("$serverUrl/Users/$userId/Items") {
                parentId?.let { parameter("ParentId", it) }
                includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                parameter("SortBy", sortBy)
                parameter("SortOrder", sortOrder)
                parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData,ParentId")
                parameter("Recursive", true)
            }.execute { response ->
                response.bodyAsChannel().toInputStream().use { stream ->
                    // For Jellyfin, we'll decode the whole object but pass items individually
                    // to stay consistent with the repository pattern.
                    val result = json.decodeFromStream<JellyfinItemsResponse>(stream)
                    result.items.forEach { onItem(it) }
                }
            }
    }

    suspend fun getSeasons(seriesId: String): Result<List<JellyfinItem>> =
        try {
            val response =
                client
                    .get("$serverUrl/Shows/$seriesId/Seasons") {
                        parameter("UserId", userId)
                    }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getEpisodes(
        seriesId: String,
        seasonId: String,
    ): Result<List<JellyfinItem>> =
        try {
            val response =
                client
                    .get("$serverUrl/Shows/$seriesId/Episodes") {
                        parameter("SeasonId", seasonId)
                        parameter("UserId", userId)
                        parameter("Fields", "Overview,MediaSources,UserData")
                    }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getItemById(itemId: String): Result<JellyfinItem> =
        try {
            val response =
                client
                    .get("$serverUrl/Users/$userId/Items/$itemId") {
                        parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData,RemoteTrailers,ProviderIds")
                    }.body<JellyfinItem>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun searchItems(
        query: String,
        includeItemTypes: String? = null,
        limit: Int = 50,
    ): Result<List<JellyfinItem>> =
        try {
            client
                .prepareGet("$serverUrl/Users/$userId/Items") {
                    parameter("SearchTerm", query)
                    includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                    parameter("Limit", limit)
                    parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData")
                    parameter("Recursive", true)
                }.execute { response ->
                    response.bodyAsChannel().toInputStream().use { stream ->
                        val result = json.decodeFromStream<JellyfinItemsResponse>(stream)
                        Result.success(result.items)
                    }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }

    // ---- URL builders ----

    /**
     * Build a direct-play stream URL. Appends MediaSourceId when known.
     */
    fun buildStreamUrl(
        itemId: String,
        container: String? = null,
        mediaSourceId: String? = null,
    ): String {
        val ext = container?.let { ".$it" } ?: ""
        val sourceParam = mediaSourceId?.let { "&MediaSourceId=$it" } ?: ""
        return "$serverUrl/Videos/$itemId/stream$ext?Static=true&api_key=$accessToken$sourceParam"
    }

    fun buildImageUrl(
        itemId: String,
        imageType: String = "Primary",
        maxHeight: Int = 400,
    ): String = "$serverUrl/Items/$itemId/Images/$imageType?maxHeight=$maxHeight&api_key=$accessToken"

    // ---- Playback reporting ----

    suspend fun reportPlaybackProgress(
        itemId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
        playMethod: String? = null,
    ) {
        try {
            client.post("$serverUrl/Sessions/Playing/Progress") {
                contentType(ContentType.Application.Json)
                setBody(
                    JellyfinPlaybackProgress(
                        itemId = itemId,
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                        playSessionId = playSessionId,
                        mediaSourceId = mediaSourceId,
                        playMethod = playMethod,
                    ),
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("JellyfinApiService", "Failed to report playback progress", e)
        }
    }

    suspend fun reportPlaybackStart(
        itemId: String,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
        playMethod: String? = null,
    ) {
        try {
            client.post("$serverUrl/Sessions/Playing") {
                contentType(ContentType.Application.Json)
                setBody(
                    JellyfinPlaybackStart(
                        itemId = itemId,
                        playSessionId = playSessionId,
                        mediaSourceId = mediaSourceId,
                        playMethod = playMethod,
                    ),
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("JellyfinApiService", "Failed to report playback start", e)
        }
    }

    suspend fun reportPlaybackStopped(
        itemId: String,
        positionTicks: Long,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
        playMethod: String? = null,
    ) {
        try {
            client.post("$serverUrl/Sessions/Playing/Stopped") {
                contentType(ContentType.Application.Json)
                setBody(
                    JellyfinPlaybackStopped(
                        itemId = itemId,
                        positionTicks = positionTicks,
                        playSessionId = playSessionId,
                        mediaSourceId = mediaSourceId,
                        playMethod = playMethod,
                    ),
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("JellyfinApiService", "Failed to report playback stopped", e)
        }
    }

    suspend fun addFavorite(itemId: String): Result<Unit> =
        try {
            client.post("$serverUrl/Users/$userId/FavoriteItems/$itemId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun removeFavorite(itemId: String): Result<Unit> =
        try {
            client.delete("$serverUrl/Users/$userId/FavoriteItems/$itemId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getResumableItems(
        includeItemTypes: String? = null,
        limit: Int = 50,
    ): Result<List<JellyfinItem>> =
        try {
            val response =
                client
                    .get("$serverUrl/Users/$userId/Items") {
                        parameter("Filters", "IsResumable")
                        parameter("SortBy", "DatePlayed")
                        parameter("SortOrder", "Descending")
                        includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                        parameter("Limit", limit)
                        parameter("Fields", "Overview,MediaSources,UserData")
                        parameter("Recursive", true)
                    }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getFavoriteItems(
        includeItemTypes: String? = null,
        limit: Int = 50,
    ): Result<List<JellyfinItem>> =
        try {
            val response =
                client
                    .get("$serverUrl/Users/$userId/Items") {
                        parameter("Filters", "IsFavorite")
                        parameter("SortBy", "SortName")
                        parameter("SortOrder", "Ascending")
                        includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                        parameter("Limit", limit)
                        parameter("Fields", "Overview,MediaSources,UserData")
                        parameter("Recursive", true)
                    }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getRecentlyPlayed(
        includeItemTypes: String? = null,
        limit: Int = 50,
    ): Result<List<JellyfinItem>> =
        try {
            val response =
                client
                    .get("$serverUrl/Users/$userId/Items") {
                        parameter("IsPlayed", true)
                        parameter("SortBy", "DatePlayed")
                        parameter("SortOrder", "Descending")
                        includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                        parameter("Limit", limit)
                        parameter("Fields", "Overview,MediaSources,UserData")
                        parameter("Recursive", true)
                    }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }

    // ---- Quick Connect ----

    /**
     * Initiate a Quick Connect session.
     * The returned [JellyfinQuickConnectResult] contains a 6-digit [JellyfinQuickConnectResult.code]
     * to show the user and a [JellyfinQuickConnectResult.secret] to poll with.
     */
    suspend fun initiateQuickConnect(): Result<JellyfinQuickConnectResult> =
        try {
            val result =
                client
                    .post("$serverUrl/QuickConnect/Initiate") {
                        contentType(ContentType.Application.Json)
                    }.body<JellyfinQuickConnectResult>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Quick Connect initiate failed")
            Result.failure(e)
        }

    /**
     * Poll Quick Connect status. Returns [JellyfinQuickConnectResult.authenticated] = true
     * once the user approves the code in their Jellyfin web UI.
     */
    suspend fun pollQuickConnect(secret: String): Result<JellyfinQuickConnectResult> =
        try {
            val result =
                client
                    .get("$serverUrl/QuickConnect/Connect") {
                        parameter("secret", secret)
                    }.body<JellyfinQuickConnectResult>()
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Quick Connect poll failed")
            Result.failure(e)
        }

    /**
     * Exchange an approved Quick Connect secret for a full session token.
     * Call this after [pollQuickConnect] returns [JellyfinQuickConnectResult.authenticated] = true.
     */
    suspend fun authenticateWithQuickConnect(secret: String): Result<JellyfinAuthResponse> =
        try {
            val response =
                client
                    .post("$serverUrl/Users/AuthenticateWithQuickConnect") {
                        contentType(ContentType.Application.Json)
                        setBody(JellyfinQuickConnectAuthBody(secret = secret))
                    }.body<JellyfinAuthResponse>()
            accessToken = response.accessToken
            userId = response.user.id
            serverId = response.serverId
            postCapabilities()
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Quick Connect auth failed")
            Result.failure(e)
        }

    fun disconnect() {
        accessToken = null
        userId = null
        serverId = null
    }

    // ---- Device profile ----

    /**
     * Build a Jellyfin DeviceProfile for ExoPlayer on Android with the Jellyfin FFmpeg
     * extension (adds AC3, EAC3, DTS, TrueHD, MLP software decoding).
     *
     * Priority:
     *   1. Direct play  — server streams the original file, client decodes it
     *   2. Transcoding  — server encodes to HLS/H.264+AAC, client plays HLS
     *
     * Max bitrate is set to 140 Mbps to handle 4K HDR content.
     */
    private fun buildDeviceProfile(): JsonObject =
        buildJsonObject {
            put("MaxStreamingBitrate", 140_000_000)
            put("MaxStaticBitrate", 140_000_000)
            put("MusicStreamingTranscodingBitrate", 384_000)

            putJsonArray("DirectPlayProfiles") {
                // MP4 / M4V — most common VOD format
                addJsonObject {
                    put("Container", "mp4,m4v")
                    put("Type", "Video")
                    put("VideoCodec", "h264,hevc,vp9,av1,mpeg4")
                    put("AudioCodec", "aac,mp3,ac3,eac3,dts,truehd,mlp,flac,opus,vorbis,pcm")
                }
                // MKV — common for high-quality rips
                addJsonObject {
                    put("Container", "mkv")
                    put("Type", "Video")
                    put("VideoCodec", "h264,hevc,vp9,av1,mpeg4")
                    put("AudioCodec", "aac,mp3,ac3,eac3,dts,truehd,mlp,flac,opus,vorbis,pcm")
                }
                // WebM
                addJsonObject {
                    put("Container", "webm")
                    put("Type", "Video")
                    put("VideoCodec", "vp8,vp9,av1")
                    put("AudioCodec", "opus,vorbis")
                }
                // MPEG-TS (broadcast/recording)
                addJsonObject {
                    put("Container", "ts,mpegts,m2ts")
                    put("Type", "Video")
                    put("VideoCodec", "h264,hevc,mpeg2video")
                    put("AudioCodec", "aac,mp3,ac3,eac3")
                }
                // Audio-only
                addJsonObject {
                    put("Container", "mp3,flac,ogg,opus,wav,aac,m4a,wma")
                    put("Type", "Audio")
                }
            }

            putJsonArray("TranscodingProfiles") {
                // HLS/H.264+AAC — universally supported by ExoPlayer
                addJsonObject {
                    put("Container", "ts")
                    put("Type", "Video")
                    put("VideoCodec", "h264")
                    put("AudioCodec", "aac,mp3")
                    put("Protocol", "hls")
                    put("Context", "Streaming")
                    put("BreakOnNonKeyFrames", true)
                    put("MinSegments", 1)
                    put("SegmentLength", 0)
                    put("EstimateContentLength", false)
                }
                // Audio transcoding fallback
                addJsonObject {
                    put("Container", "mp3")
                    put("Type", "Audio")
                    put("AudioCodec", "mp3")
                    put("Protocol", "http")
                    put("Context", "Streaming")
                }
            }

            putJsonArray("ContainerProfiles") { }

            putJsonArray("CodecProfiles") {
                // H.264: up to High@L5.2
                addJsonObject {
                    put("Type", "Video")
                    put("Codec", "h264")
                    putJsonArray("Conditions") {
                        addJsonObject {
                            put("Condition", "EqualsAny")
                            put("Property", "VideoProfile")
                            put("Value", "high|main|baseline|constrained baseline|high 10|constrained high")
                            put("IsRequired", false)
                        }
                        addJsonObject {
                            put("Condition", "LessThanEqual")
                            put("Property", "VideoLevel")
                            put("Value", "52")
                            put("IsRequired", false)
                        }
                    }
                }
                // HEVC/H.265: main/main10 up to L6 (4K HDR)
                addJsonObject {
                    put("Type", "Video")
                    put("Codec", "hevc")
                    putJsonArray("Conditions") {
                        addJsonObject {
                            put("Condition", "EqualsAny")
                            put("Property", "VideoProfile")
                            put("Value", "main|main 10|main still picture")
                            put("IsRequired", false)
                        }
                        addJsonObject {
                            put("Condition", "LessThanEqual")
                            put("Property", "VideoLevel")
                            put("Value", "183")
                            put("IsRequired", false)
                        }
                    }
                }
            }

            putJsonArray("SubtitleProfiles") {
                addJsonObject {
                    put("Format", "srt")
                    put("Method", "External")
                }
                addJsonObject {
                    put("Format", "vtt")
                    put("Method", "External")
                }
                addJsonObject {
                    put("Format", "ass")
                    put("Method", "External")
                }
                addJsonObject {
                    put("Format", "ssa")
                    put("Method", "External")
                }
                addJsonObject {
                    put("Format", "sub")
                    put("Method", "External")
                }
                addJsonObject {
                    put("Format", "smi")
                    put("Method", "External")
                }
            }

            putJsonArray("ResponseProfiles") {
                addJsonObject {
                    put("Type", "Video")
                    put("Container", "m4v")
                    put("MimeType", "video/mp4")
                }
            }
        }

    companion object {
        private const val TAG = "JellyfinApi"
    }
}
