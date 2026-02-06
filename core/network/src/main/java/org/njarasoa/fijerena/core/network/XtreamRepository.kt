package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import java.util.concurrent.ConcurrentHashMap
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.network.provider.ProviderSettings

/**
 * Represents a watched stream in the history
 */
@Serializable
data class WatchedStream(
    val streamId: Int,
    val streamName: String,
    val categoryId: String,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val playbackPosition: Long = 0L,      // Current position in ms
    val duration: Long = 0L,               // Total duration in ms
    val isCompleted: Boolean = false       // True if watched > 95%
)

/**
 * Represents a favorite stream
 */
@Serializable
data class FavoriteStream(
    val streamId: Int,           // streamId for Live/Movies, seriesId for TV Shows
    val streamName: String,       // Display name
    val categoryId: String,       // Original category reference
    val contentType: String,      // "LIVE_TV", "MOVIES", or "TV_SHOWS"
    val timestamp: Long = System.currentTimeMillis()  // For ordering
)

class XtreamRepository(
    private val accountManager: AccountManager,
    context: Context,
    providerId: Long = 0L,
    private val providerSettings: ProviderSettings = ProviderSettings.DEFAULT
) {
    private var apiService: XtreamApiService? = null
    private val cacheName = if (providerId > 0L) "xtream_cache_$providerId" else "xtream_cache"
    private val cache: SharedPreferences = context.getSharedPreferences(
        cacheName,
        Context.MODE_PRIVATE
    )
    private val appSettings = AppSettings(context)  // Keep for global settings (isDevMode)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Whether caching is enabled for this provider */
    private val cachingEnabled: Boolean get() = providerSettings.cachingEnabled

    /** Cache expiry time in ms for this provider */
    private val cacheExpiryMs: Long get() = providerSettings.cacheExpiryMs

    // Payload size tracking for dev mode
    private val payloadSizes = ConcurrentHashMap<String, Long>()
    // Fetch time tracking (in milliseconds)
    private val fetchTimes = ConcurrentHashMap<String, Long>()

    companion object {
        // Cache expiry is now configurable via AppSettings (default: 24 hours)
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CATEGORIES_TIMESTAMP = "categories_timestamp"
        private const val KEY_VOD_CATEGORIES = "vod_categories"
        private const val KEY_VOD_CATEGORIES_TIMESTAMP = "vod_categories_timestamp"
        private const val KEY_SERIES_CATEGORIES = "series_categories"
        private const val KEY_SERIES_CATEGORIES_TIMESTAMP = "series_categories_timestamp"
        private const val KEY_STREAMS_PREFIX = "streams_"
        private const val KEY_STREAMS_TIMESTAMP_PREFIX = "streams_timestamp_"

        // Legacy keys (kept for backwards compatibility but not used)
        private const val KEY_LAST_CATEGORY_ID = "last_category_id"
        private const val KEY_LAST_STREAM_ID = "last_stream_id"
        private const val KEY_LAST_CONTENT_TYPE = "last_content_type"

        // Content-type specific last played tracking
        private const val KEY_LAST_LIVE_CATEGORY = "last_live_category"
        private const val KEY_LAST_LIVE_STREAM = "last_live_stream"
        private const val KEY_LAST_MOVIES_CATEGORY = "last_movies_category"
        private const val KEY_LAST_MOVIES_STREAM = "last_movies_stream"
        private const val KEY_LAST_TVSHOWS_CATEGORY = "last_tvshows_category"
        private const val KEY_LAST_TVSHOWS_STREAM = "last_tvshows_stream"

        // Watch history tracking
        private const val KEY_WATCH_HISTORY = "watch_history"

        // Favorites tracking
        private const val KEY_FAVORITES = "favorites"

        // EPG caching
        private const val KEY_EPG_PREFIX = "epg_"
        private const val KEY_EPG_TIMESTAMP_PREFIX = "epg_timestamp_"
        private const val EPG_CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 minutes

        private fun computeCacheSize(cache: SharedPreferences): Long {
            var totalSize = 0L
            cache.all.forEach { (_, value) ->
                when (value) {
                    is String -> totalSize += value.toByteArray(Charsets.UTF_8).size
                    is Long -> totalSize += 8
                    is Int -> totalSize += 4
                    is Boolean -> totalSize += 1
                }
            }
            return totalSize
        }

        fun computeCacheStats(cache: SharedPreferences): CacheStats {
            var liveTvSize = 0L
            var moviesSize = 0L
            var tvShowsSize = 0L
            var otherSize = 0L

            var liveTvCategoryCached = false
            var moviesCategoryCached = false
            var tvShowsCategoryCached = false

            var liveTvStreamsCount = 0
            var moviesStreamsCount = 0
            var tvShowsStreamsCount = 0
            var epgCount = 0

            cache.all.forEach { (key, value) ->
                val valueSize = when (value) {
                    is String -> value.toByteArray(Charsets.UTF_8).size.toLong()
                    is Long -> 8L
                    is Int -> 4L
                    is Boolean -> 1L
                    else -> 0L
                }

                when {
                    key == KEY_CATEGORIES || key == KEY_CATEGORIES_TIMESTAMP -> {
                        liveTvSize += valueSize
                        if (key == KEY_CATEGORIES) liveTvCategoryCached = true
                    }
                    key == KEY_VOD_CATEGORIES || key == KEY_VOD_CATEGORIES_TIMESTAMP -> {
                        moviesSize += valueSize
                        if (key == KEY_VOD_CATEGORIES) moviesCategoryCached = true
                    }
                    key == KEY_SERIES_CATEGORIES || key == KEY_SERIES_CATEGORIES_TIMESTAMP -> {
                        tvShowsSize += valueSize
                        if (key == KEY_SERIES_CATEGORIES) tvShowsCategoryCached = true
                    }
                    key.startsWith(KEY_STREAMS_PREFIX) && !key.contains("vod_") && !key.contains("series_") && !key.contains("search_") -> {
                        liveTvSize += valueSize
                        if (!key.contains("_timestamp_")) liveTvStreamsCount++
                    }
                    key.startsWith(KEY_STREAMS_PREFIX) && key.contains("vod_") && !key.contains("search_") -> {
                        moviesSize += valueSize
                        if (!key.contains("_timestamp_")) moviesStreamsCount++
                    }
                    key.startsWith(KEY_STREAMS_PREFIX) && key.contains("series_") && !key.contains("search_") -> {
                        tvShowsSize += valueSize
                        if (!key.contains("_timestamp_")) tvShowsStreamsCount++
                    }
                    key.startsWith(KEY_EPG_PREFIX) -> {
                        // EPG counted in other
                        otherSize += valueSize
                        if (!key.contains("_timestamp_")) epgCount++
                    }
                    else -> {
                        otherSize += valueSize
                    }
                }
            }

            return CacheStats(
                totalSize = computeCacheSize(cache),
                liveTv = ContentTypeCacheStats(liveTvSize, liveTvCategoryCached, liveTvStreamsCount),
                movies = ContentTypeCacheStats(moviesSize, moviesCategoryCached, moviesStreamsCount),
                tvShows = ContentTypeCacheStats(tvShowsSize, tvShowsCategoryCached, tvShowsStreamsCount),
                epgCount = epgCount,
                otherSize = otherSize
            )
        }

        fun clearCacheForContentTypeStatic(cache: SharedPreferences, contentType: String) {
            val editor = cache.edit()

            when (contentType) {
                "LIVE_TV" -> {
                    editor.remove(KEY_CATEGORIES)
                    editor.remove(KEY_CATEGORIES_TIMESTAMP)
                    cache.all.keys.filter { key ->
                        key.startsWith(KEY_STREAMS_PREFIX) &&
                        !key.contains("vod_") &&
                        !key.contains("series_") &&
                        !key.contains("search_")
                    }.forEach { key -> editor.remove(key) }
                }
                "MOVIES" -> {
                    editor.remove(KEY_VOD_CATEGORIES)
                    editor.remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                    cache.all.keys.filter { key ->
                        key.startsWith(KEY_STREAMS_PREFIX) &&
                        key.contains("vod_") &&
                        !key.contains("search_")
                    }.forEach { key -> editor.remove(key) }
                }
                "TV_SHOWS" -> {
                    editor.remove(KEY_SERIES_CATEGORIES)
                    editor.remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                    cache.all.keys.filter { key ->
                        key.startsWith(KEY_STREAMS_PREFIX) &&
                        key.contains("series_") &&
                        !key.contains("search_")
                    }.forEach { key -> editor.remove(key) }
                }
            }

            editor.apply()
        }
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

    /**
     * Updates the provider URL without changing username/password.
     * Re-authenticates with the new URL and clears cached data.
     */
    suspend fun updateProviderUrl(newUrl: String): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val credentials = accountManager.getCredentials()
                ?: throw Exception("No stored credentials found")

            val password = credentials.password
                ?: throw Exception("Password not stored. Please login again.")

            // Update URL in storage
            accountManager.updateUrl(newUrl)

            // Create new API service with updated URL
            val service = XtreamApiService(newUrl, credentials.username, password)
            val authResponse = service.authenticate()

            // Validate authentication response
            if (authResponse.userInfo?.auth != 1) {
                throw Exception("Authentication failed with new URL")
            }

            if (authResponse.userInfo?.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo?.status}")
            }

            // Save updated credentials with new URL
            accountManager.saveCredentials(
                newUrl,
                credentials.username,
                password,
                authResponse,
                rememberMe = true
            )

            // Clear all cached data since it's from the old provider
            clearCache()

            // Update the API service
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
                        val startTime = System.currentTimeMillis()
                        val fresh = service.getCategories()
                        val fetchTime = System.currentTimeMillis() - startTime
                        fetchTimes["live_categories"] = fetchTime
                        cacheCategories(fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedCategories
            }

            // No cache available, fetch from network
            val startTime = System.currentTimeMillis()
            val categories = service.getCategories()
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["live_categories"] = fetchTime
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
                        val startTime = System.currentTimeMillis()
                        val fresh = service.getVodCategories()
                        val fetchTime = System.currentTimeMillis() - startTime
                        fetchTimes["vod_categories"] = fetchTime
                        cacheVodCategories(fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedCategories
            }

            // No cache available, fetch from network
            val startTime = System.currentTimeMillis()
            val categories = service.getVodCategories()
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["vod_categories"] = fetchTime
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
                        val startTime = System.currentTimeMillis()
                        val fresh = service.getSeriesCategories()
                        val fetchTime = System.currentTimeMillis() - startTime
                        fetchTimes["series_categories"] = fetchTime
                        cacheSeriesCategories(fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedCategories
            }

            // No cache available, fetch from network
            val startTime = System.currentTimeMillis()
            val categories = service.getSeriesCategories()
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["series_categories"] = fetchTime
            cacheSeriesCategories(categories)
            categories
        }
    }

    private fun getCachedCategories(): List<XtreamCategory>? {
        if (!cachingEnabled) return null
        val timestamp = cache.getLong(KEY_CATEGORIES_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > cacheExpiryMs) {
            return null // Cache expired
        }

        val cached = cache.getString(KEY_CATEGORIES, null) ?: return null
        return try {
            val categories = json.decodeFromString<List<XtreamCategory>>(cached)
            // Track payload size when loading from cache (for dev mode)
            trackPayloadSize("live_categories", categories)
            categories
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheCategories(categories: List<XtreamCategory>) {
        trackPayloadSize("live_categories", categories)
        if (!cachingEnabled) return
        cache.edit()
            .putString(KEY_CATEGORIES, json.encodeToString(categories))
            .putLong(KEY_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedVodCategories(): List<XtreamCategory>? {
        if (!cachingEnabled) return null
        val timestamp = cache.getLong(KEY_VOD_CATEGORIES_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > cacheExpiryMs) {
            return null
        }
        val cached = cache.getString(KEY_VOD_CATEGORIES, null) ?: return null
        return try {
            val categories = json.decodeFromString<List<XtreamCategory>>(cached)
            trackPayloadSize("vod_categories", categories)
            categories
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheVodCategories(categories: List<XtreamCategory>) {
        trackPayloadSize("vod_categories", categories)
        if (!cachingEnabled) return
        cache.edit()
            .putString(KEY_VOD_CATEGORIES, json.encodeToString(categories))
            .putLong(KEY_VOD_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedSeriesCategories(): List<XtreamCategory>? {
        if (!cachingEnabled) return null
        val timestamp = cache.getLong(KEY_SERIES_CATEGORIES_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - timestamp > cacheExpiryMs) {
            return null
        }
        val cached = cache.getString(KEY_SERIES_CATEGORIES, null) ?: return null
        return try {
            val categories = json.decodeFromString<List<XtreamCategory>>(cached)
            trackPayloadSize("series_categories", categories)
            categories
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheSeriesCategories(categories: List<XtreamCategory>) {
        trackPayloadSize("series_categories", categories)
        if (!cachingEnabled) return
        cache.edit()
            .putString(KEY_SERIES_CATEGORIES, json.encodeToString(categories))
            .putLong(KEY_SERIES_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    suspend fun getStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            val cacheKey = if (forSearch) "search_$categoryId" else categoryId

            // Try to load from cache first
            val cachedStreams = getCachedStreams(cacheKey)
            if (cachedStreams != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val startTime = System.currentTimeMillis()
                        val fresh = service.getStreams(categoryId)
                        val fetchTime = System.currentTimeMillis() - startTime
                        fetchTimes["category_$cacheKey"] = fetchTime
                        cacheStreams(cacheKey, fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedStreams
            }

            // No cache available, fetch from network
            val startTime = System.currentTimeMillis()
            val streams = service.getStreams(categoryId)
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["category_$cacheKey"] = fetchTime
            cacheStreams(cacheKey, streams)
            streams
        }
    }

    suspend fun getVodStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            val cacheKey = if (forSearch) "search_vod_$categoryId" else "vod_$categoryId"

            // Try to load from cache first
            val cachedStreams = getCachedStreams(cacheKey)
            if (cachedStreams != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val startTime = System.currentTimeMillis()
                        val fresh = service.getVodStreams(categoryId)
                        val fetchTime = System.currentTimeMillis() - startTime
                        fetchTimes["category_$cacheKey"] = fetchTime
                        cacheStreams(cacheKey, fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedStreams
            }

            // No cache available, fetch from network
            val startTime = System.currentTimeMillis()
            val streams = service.getVodStreams(categoryId)
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["category_$cacheKey"] = fetchTime
            cacheStreams(cacheKey, streams)
            streams
        }
    }

    suspend fun getSeries(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")

            val cacheKey = if (forSearch) "search_series_$categoryId" else "series_$categoryId"

            // Try to load from cache first
            val cachedStreams = getCachedStreams(cacheKey)
            if (cachedStreams != null) {
                // Return cached data immediately, then refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val startTime = System.currentTimeMillis()
                        val seriesList = service.getSeries(categoryId)
                        val streams = convertSeriesToStreams(seriesList)
                        val fetchTime = System.currentTimeMillis() - startTime
                        fetchTimes["category_$cacheKey"] = fetchTime
                        cacheStreams(cacheKey, streams)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cachedStreams
            }

            // No cache available, fetch from network
            val startTime = System.currentTimeMillis()
            val seriesList = service.getSeries(categoryId)
            val streams = convertSeriesToStreams(seriesList)
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["category_$cacheKey"] = fetchTime
            cacheStreams(cacheKey, streams)
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
        if (!cachingEnabled) return null
        val timestamp = cache.getLong(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId, 0L)
        if (System.currentTimeMillis() - timestamp > cacheExpiryMs) {
            return null // Cache expired
        }

        val cached = cache.getString(KEY_STREAMS_PREFIX + categoryId, null) ?: return null
        return try {
            val streams = json.decodeFromString<List<XtreamStream>>(cached)
            trackPayloadSize("category_$categoryId", streams)
            streams
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheStreams(categoryId: String, streams: List<XtreamStream>) {
        trackPayloadSize("category_$categoryId", streams)
        if (!cachingEnabled) return
        try {
            // Only cache if stream list is reasonable size (< 500 items to avoid OOM)
            if (streams.size > 500) {
                println("XtreamRepository: Skipping cache for $categoryId - too many streams (${streams.size})")
                return
            }
            cache.edit()
                .putString(KEY_STREAMS_PREFIX + categoryId, json.encodeToString(streams))
                .putLong(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId, System.currentTimeMillis())
                .apply()
        } catch (e: OutOfMemoryError) {
            println("XtreamRepository: Failed to cache streams for $categoryId - OutOfMemoryError")
            // Clear the failed cache entry
            cache.edit().remove(KEY_STREAMS_PREFIX + categoryId).apply()
        } catch (e: Exception) {
            println("XtreamRepository: Failed to cache streams for $categoryId - ${e.message}")
        }
    }

    fun buildStreamUrl(streamId: Int, contentType: String = "LIVE_TV", extension: String? = null): Result<String> = resultOf {
        val service = apiService
            ?: throw Exception("Not authenticated. Please login first.")
        when (contentType) {
            "LIVE_TV" -> service.buildStreamUrl(streamId)
            "MOVIES" -> service.buildVodStreamUrl(streamId, extension ?: "mp4")
            "TV_SHOWS" -> service.buildSeriesStreamUrl(streamId, extension ?: "mp4")
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
            val startTime = System.currentTimeMillis()
            val seriesInfo = service.getSeriesInfo(seriesId)
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["series_$seriesId"] = fetchTime
            trackPayloadSize("series_$seriesId", seriesInfo)
            seriesInfo
        }
    }

    suspend fun getVodInfo(vodId: Int): Result<VodInfo> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService
                ?: throw Exception("Not authenticated. Please login first.")
            val startTime = System.currentTimeMillis()
            val vodInfo = service.getVodInfo(vodId)
            val fetchTime = System.currentTimeMillis() - startTime
            fetchTimes["vod_$vodId"] = fetchTime
            trackPayloadSize("vod_$vodId", vodInfo)
            vodInfo
        }
    }

    /**
     * Reinitialize the API service with new credentials (for provider switching).
     */
    suspend fun reinitialize(
        url: String,
        username: String,
        password: String
    ): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = XtreamApiService(url, username, password)
            val authResponse = service.authenticate()

            if (authResponse.userInfo?.auth != 1) {
                throw Exception("Authentication failed: Invalid credentials")
            }

            if (authResponse.userInfo?.status != "Active") {
                throw Exception("Account is not active: ${authResponse.userInfo?.status}")
            }

            apiService = service
            authResponse
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
        payloadSizes.clear()
        fetchTimes.clear()
    }

    /**
     * Get total cache size in bytes
     */
    fun getCacheSize(): Long = computeCacheSize(cache)

    /**
     * Get cache statistics per content type
     */
    data class ContentTypeCacheStats(
        val size: Long,
        val categoryCached: Boolean,
        val streamListsCount: Int
    )

    data class CacheStats(
        val totalSize: Long,
        val liveTv: ContentTypeCacheStats,
        val movies: ContentTypeCacheStats,
        val tvShows: ContentTypeCacheStats,
        val epgCount: Int,
        val otherSize: Long
    )

    fun getCacheStats(): CacheStats = computeCacheStats(cache)

    /**
     * Clear cache for specific content type
     */
    fun clearCacheForContentType(contentType: String) {
        clearCacheForContentTypeStatic(cache, contentType)
        // Clean up instance-specific tracking maps
        when (contentType) {
            "LIVE_TV" -> {
                payloadSizes.remove("live_categories")
                fetchTimes.remove("live_categories")
            }
            "MOVIES" -> {
                payloadSizes.remove("vod_categories")
                fetchTimes.remove("vod_categories")
            }
            "TV_SHOWS" -> {
                payloadSizes.remove("series_categories")
                fetchTimes.remove("series_categories")
            }
        }
    }

    fun getCurrentUrl(): String? {
        return accountManager.getCredentials()?.url
    }

    fun getCurrentUsername(): String? {
        return accountManager.getCredentials()?.username
    }

    fun getCurrentPassword(): String? {
        return accountManager.getCredentials()?.password
    }

    /**
     * Save last played stream with content-type specific tracking
     */
    fun saveLastPlayedStream(categoryId: String, streamId: Int, streamName: String, contentType: String) {
        val editor = cache.edit()

        // Save content-type specific last played
        when (contentType) {
            "LIVE_TV" -> {
                editor.putString(KEY_LAST_LIVE_CATEGORY, categoryId)
                editor.putInt(KEY_LAST_LIVE_STREAM, streamId)
            }
            "MOVIES" -> {
                editor.putString(KEY_LAST_MOVIES_CATEGORY, categoryId)
                editor.putInt(KEY_LAST_MOVIES_STREAM, streamId)
            }
            "TV_SHOWS" -> {
                editor.putString(KEY_LAST_TVSHOWS_CATEGORY, categoryId)
                editor.putInt(KEY_LAST_TVSHOWS_STREAM, streamId)
            }
        }

        // Save global last content type
        editor.putString(KEY_LAST_CONTENT_TYPE, contentType)

        editor.apply()

        // Add to watch history
        addToWatchHistory(streamId, streamName, categoryId, contentType)
    }

    /**
     * Get last played category for a specific content type
     */
    fun getLastCategoryId(contentType: String): String? {
        return when (contentType) {
            "LIVE_TV" -> cache.getString(KEY_LAST_LIVE_CATEGORY, null)
            "MOVIES" -> cache.getString(KEY_LAST_MOVIES_CATEGORY, null)
            "TV_SHOWS" -> cache.getString(KEY_LAST_TVSHOWS_CATEGORY, null)
            else -> null
        }
    }

    /**
     * Get last played stream for a specific content type
     */
    fun getLastStreamId(contentType: String): Int? {
        val streamId = when (contentType) {
            "LIVE_TV" -> cache.getInt(KEY_LAST_LIVE_STREAM, -1)
            "MOVIES" -> cache.getInt(KEY_LAST_MOVIES_STREAM, -1)
            "TV_SHOWS" -> cache.getInt(KEY_LAST_TVSHOWS_STREAM, -1)
            else -> -1
        }
        return if (streamId != -1) streamId else null
    }

    /**
     * Get the last content type that was played
     */
    fun getLastContentType(): String? {
        return cache.getString(KEY_LAST_CONTENT_TYPE, null)
    }

    /**
     * Add a stream to watch history (max 25 most recent)
     */
    private fun addToWatchHistory(
        streamId: Int,
        streamName: String,
        categoryId: String,
        contentType: String,
        playbackPosition: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false
    ) {
        val history = getWatchHistory().toMutableList()

        // Remove existing entry if present (to update timestamp/position)
        history.removeAll { it.streamId == streamId && it.contentType == contentType }

        // Add new entry at the beginning
        history.add(0, WatchedStream(
            streamId, streamName, categoryId, contentType,
            System.currentTimeMillis(),
            playbackPosition, duration, isCompleted
        ))

        // Keep only last N items based on settings
        val trimmedHistory = history.take(providerSettings.watchHistorySize)

        // Save to cache
        val historyJson = json.encodeToString(trimmedHistory)
        cache.edit().putString(KEY_WATCH_HISTORY, historyJson).apply()
    }

    /**
     * Get watch history (last 25 watched streams)
     */
    fun getWatchHistory(): List<WatchedStream> {
        val historyJson = cache.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<WatchedStream>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear watch history
     */
    fun clearWatchHistory() {
        cache.edit().remove(KEY_WATCH_HISTORY).apply()
    }

    /**
     * Add a stream to favorites
     */
    fun addFavorite(streamId: Int, streamName: String, categoryId: String, contentType: String): Boolean {
        val favorites = getFavorites().toMutableList()

        // Check for duplicate
        if (favorites.any { it.streamId == streamId && it.contentType == contentType }) {
            return false
        }

        // Add at beginning (newest first)
        favorites.add(0, FavoriteStream(streamId, streamName, categoryId, contentType))

        // Trim to max size
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)

        // Save
        cache.edit().putString(KEY_FAVORITES, json.encodeToString(trimmed)).apply()
        return true
    }

    /**
     * Remove a stream from favorites
     */
    fun removeFavorite(streamId: Int, contentType: String): Boolean {
        val favorites = getFavorites().toMutableList()
        val removed = favorites.removeAll { it.streamId == streamId && it.contentType == contentType }
        cache.edit().putString(KEY_FAVORITES, json.encodeToString(favorites)).apply()
        return removed
    }

    /**
     * Get all favorites
     */
    fun getFavorites(): List<FavoriteStream> {
        val json = cache.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            this.json.decodeFromString<List<FavoriteStream>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if a stream is favorited
     */
    fun isFavorite(streamId: Int, contentType: String): Boolean {
        return getFavorites().any { it.streamId == streamId && it.contentType == contentType }
    }

    /**
     * Clear all favorites
     */
    fun clearFavorites() {
        cache.edit().remove(KEY_FAVORITES).apply()
    }

    /**
     * Track payload size for dev mode
     */
    private inline fun <reified T> trackPayloadSize(key: String, data: T) {
        if (appSettings.isDevMode) {
            try {
                val jsonString = json.encodeToString(data)
                val sizeInBytes = jsonString.toByteArray(Charsets.UTF_8).size.toLong()
                payloadSizes[key] = sizeInBytes
                println("XtreamRepository: Tracked payload size for $key: ${formatBytes(sizeInBytes)}")
            } catch (e: Exception) {
                println("XtreamRepository: Failed to track payload size for $key: ${e.message}")
            }
        }
    }

    /**
     * Get payload size for a specific key in human-readable format
     */
    fun getPayloadSize(key: String): String? {
        if (!appSettings.isDevMode) return null
        val sizeInBytes = payloadSizes[key] ?: return null
        return formatBytes(sizeInBytes)
    }

    /**
     * Get fetch time for a specific key in milliseconds
     */
    fun getFetchTime(key: String): Long? {
        return fetchTimes[key]
    }

    /**
     * Get fetch time for a specific key in human-readable format
     */
    fun getFetchTimeFormatted(key: String): String? {
        if (!appSettings.isDevMode) return null
        val timeMs = fetchTimes[key] ?: return null
        return "${timeMs} ms"
    }

    /**
     * Clear categories cache for a specific content type
     */
    fun clearCategoriesCache(contentType: String) {
        when (contentType) {
            "LIVE_TV" -> {
                cache.edit()
                    .remove(KEY_CATEGORIES)
                    .remove(KEY_CATEGORIES_TIMESTAMP)
                    .apply()
                payloadSizes.remove("live_categories")
                fetchTimes.remove("live_categories")
            }
            "MOVIES" -> {
                cache.edit()
                    .remove(KEY_VOD_CATEGORIES)
                    .remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                    .apply()
                payloadSizes.remove("vod_categories")
                fetchTimes.remove("vod_categories")
            }
            "TV_SHOWS" -> {
                cache.edit()
                    .remove(KEY_SERIES_CATEGORIES)
                    .remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                    .apply()
                payloadSizes.remove("series_categories")
                fetchTimes.remove("series_categories")
            }
        }
    }

    /**
     * Clear streams cache for a specific category
     */
    fun clearStreamsCache(categoryId: String) {
        cache.edit()
            .remove(KEY_STREAMS_PREFIX + categoryId)
            .remove(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId)
            .apply()
        payloadSizes.remove("category_$categoryId")
        payloadSizes.remove("category_vod_$categoryId")
        payloadSizes.remove("category_series_$categoryId")
        fetchTimes.remove("category_$categoryId")
        fetchTimes.remove("category_vod_$categoryId")
        fetchTimes.remove("category_series_$categoryId")
    }

    /**
     * Format bytes to human-readable string (KB/MB/GB)
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Save playback position for a stream
     */
    fun savePlaybackPosition(
        streamId: Int,
        streamName: String,
        categoryId: String,
        contentType: String,
        position: Long,
        duration: Long
    ) {
        // Skip for Live TV
        if (contentType == "LIVE_TV") return

        // Calculate completion
        val progressPercent = if (duration > 0) {
            (position.toFloat() / duration.toFloat()) * 100f
        } else 0f

        val isCompleted = progressPercent > 95.0f

        addToWatchHistory(
            streamId, streamName, categoryId, contentType,
            position, duration, isCompleted
        )
    }

    /**
     * Get saved playback position for a stream
     */
    fun getPlaybackPosition(streamId: Int, contentType: String): WatchedStream? {
        return getWatchHistory()
            .firstOrNull { it.streamId == streamId && it.contentType == contentType }
    }

    /**
     * Get in-progress streams (for Continue Watching category)
     */
    fun getInProgressStreams(contentType: String): List<WatchedStream> {
        return getWatchHistory()
            .filter {
                it.contentType == contentType &&
                !it.isCompleted &&
                it.playbackPosition > 0 &&
                it.duration > 0
            }
            .filter {
                val progressPercent = (it.playbackPosition.toFloat() / it.duration.toFloat()) * 100f
                progressPercent in 2.0..95.0 // Only 2-95% watched
            }
    }

    /**
     * Clear playback position for a stream (when user manually restarts)
     */
    fun clearPlaybackPosition(streamId: Int, contentType: String) {
        val history = getWatchHistory().toMutableList()
        val index = history.indexOfFirst {
            it.streamId == streamId && it.contentType == contentType
        }

        if (index != -1) {
            val item = history[index]
            history[index] = item.copy(playbackPosition = 0L, isCompleted = false)
            cache.edit().putString(KEY_WATCH_HISTORY, json.encodeToString(history)).apply()
        }
    }

    /**
     * Get app settings instance
     */
    fun getAppSettings(): AppSettings = appSettings

    /**
     * Fetches EPG data for a specific stream with caching
     */
    suspend fun getEpgForStream(streamId: Int): Result<EpgResponse> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = apiService ?: throw Exception("Not authenticated")

            // Try cache first
            val cached = getCachedEpg(streamId)
            if (cached != null) {
                // Refresh in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val fresh = service.getEpgForStream(streamId)
                        cacheEpg(streamId, fresh)
                    } catch (e: Exception) {
                        // Ignore network errors when refreshing
                    }
                }
                return@suspendResultOf cached
            }

            val epg = service.getEpgForStream(streamId)
            cacheEpg(streamId, epg)
            epg
        }
    }

    /**
     * Fetches EPG data for multiple streams in parallel
     */
    suspend fun getEpgForStreams(streamIds: List<Int>): Result<Map<Int, EpgResponse>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val results = mutableMapOf<Int, EpgResponse>()
                streamIds.forEach { streamId ->
                    when (val result = getEpgForStream(streamId)) {
                        is Result.Success -> results[streamId] = result.data
                        is Result.Error -> {
                            // Continue on failure - EPG may not be available for all channels
                        }
                    }
                }
                results
            }
        }

    /**
     * Get cached EPG data for a stream
     */
    private fun getCachedEpg(streamId: Int): EpgResponse? {
        if (!cachingEnabled) return null
        val timestamp = cache.getLong(KEY_EPG_TIMESTAMP_PREFIX + streamId, 0L)
        if (System.currentTimeMillis() - timestamp > EPG_CACHE_EXPIRY_MS) {
            return null
        }
        val cached = cache.getString(KEY_EPG_PREFIX + streamId, null) ?: return null
        return try {
            json.decodeFromString<EpgResponse>(cached)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache EPG data for a stream
     */
    private fun cacheEpg(streamId: Int, epg: EpgResponse) {
        if (!cachingEnabled) return
        cache.edit()
            .putString(KEY_EPG_PREFIX + streamId, json.encodeToString(epg))
            .putLong(KEY_EPG_TIMESTAMP_PREFIX + streamId, System.currentTimeMillis())
            .apply()
    }

    /**
     * Clear EPG cache for a specific stream
     */
    fun clearEpgCache(streamId: Int) {
        cache.edit()
            .remove(KEY_EPG_PREFIX + streamId)
            .remove(KEY_EPG_TIMESTAMP_PREFIX + streamId)
            .apply()
    }

    /**
     * Clear all EPG cache
     */
    fun clearAllEpgCache() {
        val editor = cache.edit()
        cache.all.keys.filter { it.startsWith(KEY_EPG_PREFIX) || it.startsWith(KEY_EPG_TIMESTAMP_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}
