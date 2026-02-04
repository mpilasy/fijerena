package org.njarasoa.fijerena.core.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class JellyfinApiService(
    private val serverUrl: String,
    private val deviceId: String
) {
    private var accessToken: String? = null
    private var userId: String? = null
    private var serverId: String? = null

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
    }

    private val authHeader: String
        get() = buildString {
            append("MediaBrowser ")
            append("Client=\"Fijerena\", ")
            append("Device=\"Android\", ")
            append("DeviceId=\"$deviceId\", ")
            append("Version=\"1.0.0\"")
            accessToken?.let { append(", Token=\"$it\"") }
        }

    fun getAccessToken(): String? = accessToken

    suspend fun authenticate(username: String, password: String): Result<JellyfinAuthResponse> {
        return try {
            val response = client.post("$serverUrl/Users/AuthenticateByName") {
                contentType(ContentType.Application.Json)
                header("X-Emby-Authorization", authHeader)
                setBody(JellyfinAuthBody(username = username, password = password))
            }.body<JellyfinAuthResponse>()
            accessToken = response.accessToken
            userId = response.user.id
            serverId = response.serverId
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isAuthenticated(): Boolean = accessToken != null && userId != null

    suspend fun getLibraries(): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Views") {
                header("X-Emby-Authorization", authHeader)
            }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getItems(
        parentId: String,
        includeItemTypes: String? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending"
    ): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Items") {
                header("X-Emby-Authorization", authHeader)
                parameter("ParentId", parentId)
                includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                parameter("SortBy", sortBy)
                parameter("SortOrder", sortOrder)
                parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData")
                parameter("Recursive", true)
            }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeasons(seriesId: String): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Shows/$seriesId/Seasons") {
                header("X-Emby-Authorization", authHeader)
                parameter("UserId", userId)
            }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Shows/$seriesId/Episodes") {
                header("X-Emby-Authorization", authHeader)
                parameter("SeasonId", seasonId)
                parameter("UserId", userId)
                parameter("Fields", "Overview,MediaSources,UserData")
            }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getItemById(itemId: String): Result<JellyfinItem> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Items/$itemId") {
                header("X-Emby-Authorization", authHeader)
                parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData")
            }.body<JellyfinItem>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchItems(
        query: String,
        includeItemTypes: String? = null,
        limit: Int = 50
    ): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Items") {
                header("X-Emby-Authorization", authHeader)
                parameter("SearchTerm", query)
                includeItemTypes?.let { parameter("IncludeItemTypes", it) }
                parameter("Limit", limit)
                parameter("Fields", "Overview,People,Genres,Studios,MediaSources,UserData")
                parameter("Recursive", true)
            }.body<JellyfinItemsResponse>()
            Result.success(response.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildStreamUrl(itemId: String, container: String? = null): String {
        val ext = container?.let { ".$it" } ?: ""
        return "$serverUrl/Videos/$itemId/stream${ext}?static=true&api_key=$accessToken"
    }

    fun buildImageUrl(itemId: String, imageType: String = "Primary", maxHeight: Int = 400): String {
        return "$serverUrl/Items/$itemId/Images/$imageType?maxHeight=$maxHeight&api_key=$accessToken"
    }

    suspend fun reportPlaybackProgress(itemId: String, positionTicks: Long, isPaused: Boolean) {
        try {
            client.post("$serverUrl/Sessions/Playing/Progress") {
                contentType(ContentType.Application.Json)
                header("X-Emby-Authorization", authHeader)
                setBody(JellyfinPlaybackProgress(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    isPaused = isPaused
                ))
            }
        } catch (_: Exception) {
            // Best-effort progress reporting
        }
    }

    suspend fun addFavorite(itemId: String): Result<Unit> {
        return try {
            client.post("$serverUrl/Users/$userId/FavoriteItems/$itemId") {
                header("X-Emby-Authorization", authHeader)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(itemId: String): Result<Unit> {
        return try {
            client.delete("$serverUrl/Users/$userId/FavoriteItems/$itemId") {
                header("X-Emby-Authorization", authHeader)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getResumableItems(
        includeItemTypes: String? = null,
        limit: Int = 50
    ): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Items") {
                header("X-Emby-Authorization", authHeader)
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
    }

    suspend fun getFavoriteItems(
        includeItemTypes: String? = null,
        limit: Int = 50
    ): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Items") {
                header("X-Emby-Authorization", authHeader)
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
    }

    suspend fun getRecentlyPlayed(
        includeItemTypes: String? = null,
        limit: Int = 50
    ): Result<List<JellyfinItem>> {
        return try {
            val response = client.get("$serverUrl/Users/$userId/Items") {
                header("X-Emby-Authorization", authHeader)
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
    }

    suspend fun reportPlaybackStart(itemId: String) {
        try {
            client.post("$serverUrl/Sessions/Playing") {
                contentType(ContentType.Application.Json)
                header("X-Emby-Authorization", authHeader)
                setBody(JellyfinPlaybackStart(itemId = itemId))
            }
        } catch (_: Exception) {
            // Best-effort reporting
        }
    }

    suspend fun reportPlaybackStopped(itemId: String, positionTicks: Long) {
        try {
            client.post("$serverUrl/Sessions/Playing/Stopped") {
                contentType(ContentType.Application.Json)
                header("X-Emby-Authorization", authHeader)
                setBody(JellyfinPlaybackStopped(
                    itemId = itemId,
                    positionTicks = positionTicks
                ))
            }
        } catch (_: Exception) {
            // Best-effort reporting
        }
    }

    fun disconnect() {
        accessToken = null
        userId = null
        serverId = null
    }
}
