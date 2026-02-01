package org.njarasoa.fijerena.core.player.api

import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }

        defaultRequest {
            url(normalizeBaseUrl(baseUrl))
        }

        engine {
            config {
                // OkHttp-specific configuration for Android TV
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
     * Builds a playable stream URL for a given stream ID.
     *
     * Format: http://url:port/live/username/password/streamId.ts
     *
     * @param streamId The stream ID to build the URL for
     * @return The formatted stream URL
     */
    fun buildStreamUrl(streamId: Int): String {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        return "$normalizedUrl/live/$username/$password/$streamId.ts"
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
