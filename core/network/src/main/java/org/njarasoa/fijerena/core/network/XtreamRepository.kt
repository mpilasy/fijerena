package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream

class XtreamRepository(
    private val accountManager: AccountManager,
    context: Context
) {
    private var apiService: XtreamApiService? = null
    private val cache: SharedPreferences = context.getSharedPreferences(
        "xtream_cache",
        Context.MODE_PRIVATE
    )
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CATEGORIES_TIMESTAMP = "categories_timestamp"
        private const val KEY_VOD_CATEGORIES = "vod_categories"
        private const val KEY_VOD_CATEGORIES_TIMESTAMP = "vod_categories_timestamp"
        private const val KEY_SERIES_CATEGORIES = "series_categories"
        private const val KEY_SERIES_CATEGORIES_TIMESTAMP = "series_categories_timestamp"
        private const val KEY_STREAMS_PREFIX = "streams_"
        private const val KEY_STREAMS_TIMESTAMP_PREFIX = "streams_timestamp_"
        private const val KEY_LAST_CATEGORY_ID = "last_category_id"
        private const val KEY_LAST_STREAM_ID = "last_stream_id"
        private const val KEY_LAST_CONTENT_TYPE = "last_content_type"
    }

    suspend fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean
    ): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = XtreamApiService(url, username, password)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo?.auth != 1) {
                throw Exception("Authentication failed: Invalid credentials")
            }

            if (authResponse.userInfo?.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo?.status}")
            }

            // Save credentials
            accountManager.saveCredentials(url, username, password, authResponse, rememberMe)

            // Store the API service for future use
            apiService = service

            authResponse
        }
    }

    suspend fun restoreSession(): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val credentials = accountManager.getCredentials()
                ?: throw Exception("No stored credentials found")

            val password = credentials.password
                ?: throw Exception("Password not stored. Please login again.")

            val service = XtreamApiService(credentials.url, credentials.username, password)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo?.auth != 1) {
                accountManager.clearCredentials()
                throw Exception("Stored credentials are invalid")
            }

            if (authResponse.userInfo?.status != "Active") {
                accountManager.clearCredentials()
                throw Exception("Account is not active: ${authResponse.userInfo?.status}")
            }

            // Update stored auth response
            accountManager.saveCredentials(
                credentials.url,
                credentials.username,
                password,
                authResponse,
                rememberMe = true
            )

            // Store the API service for future use
            apiService = service

            authResponse
        }
    }

    suspend fun getCategories(): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            // Try to load from cache first
            val cachedCategories = getCachedCategories()
            if (cachedCategories != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = service.getCategories()
                        cacheCategories(fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedCategories
            }

            // No cache available, fetch from network
            val categories = service.getCategories()
            cacheCategories(categories)
            categories
        }
    }

    suspend fun getVodCategories(): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            // Try to load from cache first
            val cachedCategories = getCachedVodCategories()
            if (cachedCategories != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = service.getVodCategories()
                        cacheVodCategories(fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedCategories
            }

            // No cache available, fetch from network
            val categories = service.getVodCategories()
            cacheVodCategories(categories)
            categories
        }
    }

    suspend fun getSeriesCategories(): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            // Try to load from cache first
            val cachedCategories = getCachedSeriesCategories()
            if (cachedCategories != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = service.getSeriesCategories()
                        cacheSeriesCategories(fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedCategories
            }

            // No cache available, fetch from network
            val categories = service.getSeriesCategories()
            cacheSeriesCategories(categories)
            categories
        }
    }

    private fun getCachedCategories(): List<XtreamCategory>? {
        val timestamp = cache.getLong(KEY_CATEGORIES_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS) {
            return null // Cache expired
        }

        val cached = cache.getString(KEY_CATEGORIES, null) ?: return null
        return try {
            json.decodeFromString<List<XtreamCategory>>(cached)
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheCategories(categories: List<XtreamCategory>) {
        cache.edit()
            .putString(KEY_CATEGORIES, json.encodeToString(categories))
            .putLong(KEY_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedVodCategories(): List<XtreamCategory>? {
        val timestamp = cache.getLong(KEY_VOD_CATEGORIES_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS) {
            return null
        }
        val cached = cache.getString(KEY_VOD_CATEGORIES, null) ?: return null
        return try {
            json.decodeFromString<List<XtreamCategory>>(cached)
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheVodCategories(categories: List<XtreamCategory>) {
        cache.edit()
            .putString(KEY_VOD_CATEGORIES, json.encodeToString(categories))
            .putLong(KEY_VOD_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedSeriesCategories(): List<XtreamCategory>? {
        val timestamp = cache.getLong(KEY_SERIES_CATEGORIES_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS) {
            return null
        }
        val cached = cache.getString(KEY_SERIES_CATEGORIES, null) ?: return null
        return try {
            json.decodeFromString<List<XtreamCategory>>(cached)
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheSeriesCategories(categories: List<XtreamCategory>) {
        cache.edit()
            .putString(KEY_SERIES_CATEGORIES, json.encodeToString(categories))
            .putLong(KEY_SERIES_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    suspend fun getStreams(categoryId: String): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            // Try to load from cache first
            val cachedStreams = getCachedStreams(categoryId)
            if (cachedStreams != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = service.getStreams(categoryId)
                        cacheStreams(categoryId, fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedStreams
            }

            // No cache available, fetch from network
            val streams = service.getStreams(categoryId)
            cacheStreams(categoryId, streams)
            streams
        }
    }

    suspend fun getVodStreams(categoryId: String): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            // Try to load from cache first
            val cachedStreams = getCachedStreams("vod_$categoryId")
            if (cachedStreams != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = service.getVodStreams(categoryId)
                        cacheStreams("vod_$categoryId", fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedStreams
            }

            // No cache available, fetch from network
            val streams = service.getVodStreams(categoryId)
            cacheStreams("vod_$categoryId", streams)
            streams
        }
    }

    suspend fun getSeries(categoryId: String): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            // Try to load from cache first
            val cachedStreams = getCachedStreams("series_$categoryId")
            if (cachedStreams != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val seriesList = service.getSeries(categoryId)
                        val streams = convertSeriesToStreams(seriesList)
                        cacheStreams("series_$categoryId", streams)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedStreams
            }

            // No cache available, fetch from network
            val seriesList = service.getSeries(categoryId)
            val streams = convertSeriesToStreams(seriesList)
            cacheStreams("series_$categoryId", streams)
            streams
        }
    }

    /**
     * Converts XtreamSeries list to XtreamStream list for UI compatibility
     */
    private fun convertSeriesToStreams(seriesList: List<XtreamSeries>): List<XtreamStream> {
        return seriesList.map { series ->
            XtreamStream(
                num = series.num ?: 0,
                name = series.name,
                streamType = "series",
                streamId = series.seriesId,
                streamIcon = series.cover,
                epgChannelId = null,
                added = series.lastModified,
                categoryId = series.categoryId,
                customSid = null,
                tvArchive = 0,
                directSource = null,
                tvArchiveDuration = 0
            )
        }
    }

    private fun getCachedStreams(categoryId: String): List<XtreamStream>? {
        val timestamp = cache.getLong(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId, 0L)
        if (System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS) {
            return null // Cache expired
        }

        val cached = cache.getString(KEY_STREAMS_PREFIX + categoryId, null) ?: return null
        return try {
            json.decodeFromString<List<XtreamStream>>(cached)
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheStreams(categoryId: String, streams: List<XtreamStream>) {
        cache.edit()
            .putString(KEY_STREAMS_PREFIX + categoryId, json.encodeToString(streams))
            .putLong(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId, System.currentTimeMillis())
            .apply()
    }

    fun buildStreamUrl(streamId: Int, contentType: String = "LIVE_TV"): Result<String> = resultOf {
        val service = apiService
            ?: throw Exception("Not authenticated. Please login first.")
        when (contentType) {
            "LIVE_TV" -> service.buildStreamUrl(streamId)
            "MOVIES" -> service.buildVodStreamUrl(streamId)
            "TV_SHOWS" -> service.buildSeriesStreamUrl(streamId)
            else -> service.buildStreamUrl(streamId)
        }
    }

    fun buildEpisodeStreamUrl(episodeId: String, extension: String): Result<String> = resultOf {
        val service = apiService
            ?: throw Exception("Not authenticated. Please login first.")
        service.buildEpisodeStreamUrl(episodeId, extension)
    }

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")
            service.getSeriesInfo(seriesId)
        }
    }

    suspend fun getVodInfo(vodId: Int): Result<VodInfo> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")
            service.getVodInfo(vodId)
        }
    }

    fun isAuthenticated(): Boolean {
        return apiService != null && accountManager.hasStoredCredentials()
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        resultOf {
            accountManager.clearCredentials()
            apiService = null
            clearCache()
        }
    }

    fun clearCache() {
        cache.edit().clear().apply()
    }

    fun getCurrentUrl(): String? {
        return accountManager.getCredentials()?.url
    }

    fun getCurrentUsername(): String? {
        return accountManager.getCredentials()?.username
    }

    fun saveLastCategory(categoryId: String) {
        cache.edit()
            .putString(KEY_LAST_CATEGORY_ID, categoryId)
            .apply()
    }

    fun saveLastPlayedStream(categoryId: String, streamId: Int) {
        cache.edit()
            .putString(KEY_LAST_CATEGORY_ID, categoryId)
            .putInt(KEY_LAST_STREAM_ID, streamId)
            .apply()
    }

    fun getLastCategoryId(): String? {
        return cache.getString(KEY_LAST_CATEGORY_ID, null)
    }

    fun getLastStreamId(): Int? {
        val streamId = cache.getInt(KEY_LAST_STREAM_ID, -1)
        return if (streamId != -1) streamId else null
    }

    fun saveLastContentType(contentType: String) {
        cache.edit()
            .putString(KEY_LAST_CONTENT_TYPE, contentType)
            .apply()
    }

    fun getLastContentType(): String? {
        return cache.getString(KEY_LAST_CONTENT_TYPE, null)
    }
}
