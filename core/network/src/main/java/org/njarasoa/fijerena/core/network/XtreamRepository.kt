package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.queue.RefreshPriority

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
    private val providerId: Long = 0L,
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

    private val database = XtreamDatabase.getInstance(context)
    private val categoryDao = database.categoryDao()
    private val streamDao = database.streamDao()
    private val seriesDao = database.seriesDao()

    /** Whether caching is enabled for this provider */
    private val cachingEnabled: Boolean get() = providerSettings.cachingEnabled

    /** Cache expiry time in ms for this provider */
    private val cacheExpiryMs: Long get() = providerSettings.cacheExpiryMs

    /**
     * Skip background refresh if cache was written less than this many ms ago.
     * Live TV streams use a shorter threshold (5 min) since channel lists change more often.
     * Categories and VOD/series streams use the full cache expiry (24h default) — no background
     * refresh while the cache is valid.
     */
    private val liveStreamRefreshThresholdMs: Long = 5 * 60 * 1000L // 5 minutes

    // Payload size tracking for dev mode (now tracks DB operation sizes/counts if needed, or removed)
    // Fetch time tracking (in milliseconds)
    private val fetchTimes = ConcurrentHashMap<String, Long>()

    companion object {
        // Cache expiry is now configurable via AppSettings (default: 24 hours)
        private const val KEY_CATEGORIES_TIMESTAMP = "categories_timestamp"
        private const val KEY_VOD_CATEGORIES_TIMESTAMP = "vod_categories_timestamp"
        private const val KEY_SERIES_CATEGORIES_TIMESTAMP = "series_categories_timestamp"
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
        private const val EPG_CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
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
            val dbEntities = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_LIVE)

            if (dbEntities.isNotEmpty()) {
                if (!isCacheFresh(KEY_CATEGORIES_TIMESTAMP)) {
                    syncCategories(XtreamCategoryEntity.TYPE_LIVE)
                }
                return@suspendResultOf dbEntities.map {
                    XtreamCategory(it.categoryId, it.categoryName, it.parentId)
                }
            }

            val service = apiService ?: throw Exception("Not authenticated. Please login first.")
            val categories = service.getCategories()

            // Insert into DB
            categoryDao.insertAll(categories.map {
                XtreamCategoryEntity(it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_LIVE)
            })
            cache.edit().putLong(KEY_CATEGORIES_TIMESTAMP, System.currentTimeMillis()).apply()

            categories
        }
    }

    suspend fun getVodCategories(): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val dbEntities = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_VOD)

            if (dbEntities.isNotEmpty()) {
                if (!isCacheFresh(KEY_VOD_CATEGORIES_TIMESTAMP)) {
                    syncCategories(XtreamCategoryEntity.TYPE_VOD)
                }
                return@suspendResultOf dbEntities.map {
                    XtreamCategory(it.categoryId, it.categoryName, it.parentId)
                }
            }

            val service = apiService ?: throw Exception("Not authenticated. Please login first.")
            val categories = service.getVodCategories()

            categoryDao.insertAll(categories.map {
                XtreamCategoryEntity(it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_VOD)
            })
            cache.edit().putLong(KEY_CATEGORIES_TIMESTAMP, System.currentTimeMillis()).apply()

            categories
        }
    }

    suspend fun getSeriesCategories(): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val dbEntities = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_SERIES)

            if (dbEntities.isNotEmpty()) {
                if (!isCacheFresh(KEY_SERIES_CATEGORIES_TIMESTAMP)) {
                    syncCategories(XtreamCategoryEntity.TYPE_SERIES)
                }
                return@suspendResultOf dbEntities.map {
                    XtreamCategory(it.categoryId, it.categoryName, it.parentId)
                }
            }

            val service = apiService ?: throw Exception("Not authenticated. Please login first.")
            val categories = service.getSeriesCategories()

            categoryDao.insertAll(categories.map {
                XtreamCategoryEntity(it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_SERIES)
            })
            cache.edit().putLong(KEY_SERIES_CATEGORIES_TIMESTAMP, System.currentTimeMillis()).apply()

            categories
        }
    }

    /** Returns true if the given timestamp is recent enough to skip background refresh */
    private fun isCacheFresh(timestampKey: String, thresholdMs: Long = cacheExpiryMs): Boolean {
        val timestamp = cache.getLong(timestampKey, 0L)
        return System.currentTimeMillis() - timestamp < thresholdMs
    }



    suspend fun getStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> {
        return fetchStreams(
            type = XtreamStreamEntity.TYPE_LIVE,
            categoryId = categoryId,
            forSearch = forSearch,
            cacheKeySuffix = "LIVE_ALL",
            refreshThresholdMs = liveStreamRefreshThresholdMs,
            apiCall = { service, id -> service.getStreams(id) }
        )
    }

    suspend fun getVodStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> {
        return fetchStreams(
            type = XtreamStreamEntity.TYPE_VOD,
            categoryId = categoryId,
            forSearch = forSearch,
            cacheKeySuffix = "VOD_ALL",
            apiCall = { service, id -> service.getVodStreams(id) }
        )
    }

    private suspend fun fetchStreams(
        type: String,
        categoryId: String,
        forSearch: Boolean,
        cacheKeySuffix: String,
        refreshThresholdMs: Long = cacheExpiryMs,
        apiCall: suspend (XtreamApiService, String) -> List<XtreamStream>
    ): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            if (forSearch) {
                return@suspendResultOf streamDao.searchStreams(providerId, type, categoryId).map { mapStreamEntityToModel(it) }
            }

            val dbEntities = streamDao.getStreamsByCategory(providerId, type, categoryId)

            if (dbEntities.isNotEmpty()) {
                val key = KEY_STREAMS_TIMESTAMP_PREFIX + cacheKeySuffix
                if (!isCacheFresh(key, refreshThresholdMs)) {
                    syncStreams(type)
                }
                return@suspendResultOf dbEntities.map { mapStreamEntityToModel(it) }
            }

            val service = apiService ?: throw Exception("Not authenticated. Please login first.")
            val streams = apiCall(service, categoryId)

            streamDao.insertAll(streams.map {
                XtreamStreamEntity(
                    streamId = it.streamId,
                    providerId = providerId,
                    type = type,
                    num = it.num,
                    name = it.name,
                    streamType = it.streamType,
                    streamIcon = it.streamIcon,
                    epgChannelId = it.epgChannelId,
                    added = it.added,
                    categoryId = it.categoryId,
                    customSid = it.customSid,
                    tvArchive = it.tvArchive,
                    directSource = it.directSource,
                    tvArchiveDuration = it.tvArchiveDuration
                )
            })

            streams
        }
    }

    suspend fun getSeries(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            if (forSearch) {
                return@suspendResultOf seriesDao.searchSeries(providerId, categoryId).map { mapSeriesEntityToStream(it) }
            }

            val dbEntities = seriesDao.getSeriesByCategory(providerId, categoryId)

            if (dbEntities.isNotEmpty()) {
                val key = KEY_STREAMS_TIMESTAMP_PREFIX + "SERIES_ALL"
                if (!isCacheFresh(key)) {
                    syncSeries()
                }
                return@suspendResultOf dbEntities.map { mapSeriesEntityToStream(it) }
            }

            val service = apiService ?: throw Exception("Not authenticated. Please login first.")
            val seriesList = service.getSeries(categoryId)

            seriesDao.insertAll(seriesList.map {
                 XtreamSeriesEntity(
                     seriesId = it.seriesId,
                     providerId = providerId,
                     num = it.num,
                     name = it.name,
                     cover = it.cover,
                     plot = it.plot,
                     cast = it.cast,
                     director = it.director,
                     genre = it.genre,
                     releaseDate = it.releaseDate,
                     lastModified = it.lastModified,
                     rating = it.rating,
                     rating5based = it.rating5based,
                     youtubeTrailer = it.youtubeTrailer,
                     episodeRunTime = it.episodeRunTime,
                     categoryId = it.categoryId,
                     backdropPath = it.backdropPath?.joinToString(",")
                 )
            })

            seriesList.map { series ->
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
    }

    private fun mapStreamEntityToModel(entity: XtreamStreamEntity): XtreamStream {
        return XtreamStream(
            num = entity.num,
            name = entity.name,
            streamType = entity.streamType,
            streamId = entity.streamId,
            streamIcon = entity.streamIcon,
            epgChannelId = entity.epgChannelId,
            added = entity.added,
            categoryId = entity.categoryId,
            customSid = entity.customSid,
            tvArchive = entity.tvArchive,
            directSource = entity.directSource,
            tvArchiveDuration = entity.tvArchiveDuration
        )
    }

    private fun mapSeriesEntityToStream(entity: XtreamSeriesEntity): XtreamStream {
        return XtreamStream(
            num = entity.num ?: 0,
            name = entity.name,
            streamType = "series",
            streamId = entity.seriesId,
            streamIcon = entity.cover,
            epgChannelId = null,
            added = entity.lastModified,
            categoryId = entity.categoryId,
            customSid = null,
            tvArchive = 0,
            directSource = null,
            tvArchiveDuration = 0
        )
    }

    /** Returns streams for a category from the database. */
    fun getStreamsCached(categoryId: String): List<XtreamStream>? {
        return try {
            streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId)
                .map { mapStreamEntityToModel(it) }
        } catch (e: Exception) { null }
    }

    /** Returns VOD streams for a category from the database. */
    fun getVodStreamsCached(categoryId: String): List<XtreamStream>? {
        return try {
            streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_VOD, categoryId)
                .map { mapStreamEntityToModel(it) }
        } catch (e: Exception) { null }
    }

    /** Returns series for a category from the database. */
    fun getSeriesCached(categoryId: String): List<XtreamStream>? {
        return try {
            seriesDao.getSeriesByCategory(providerId, categoryId)
                .map { mapSeriesEntityToStream(it) }
        } catch (e: Exception) { null }
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
        // Clear SharedPreferences timestamps and legacy keys
        cache.edit().clear().apply()
        fetchTimes.clear()

        // Clear DB
        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
        streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_LIVE)
        streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_VOD)
        seriesDao.deleteAll(providerId)
    }

    /**
     * Get total cache size (estimated from DB record count)
     * Returning 0 for now as exact byte size calculation from DB is expensive
     * and SharedPreferences size is negligible.
     */
    fun getCacheSize(): Long = 0L

    /**
     * Get cache statistics per content type (based on DB counts)
     */
    data class ContentTypeCacheStats(
        val size: Long, // kept for compatibility, always 0
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

    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val liveCategories = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_LIVE)
        val vodCategories = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_VOD)
        val seriesCategories = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_SERIES)

        val liveStreamsCount = streamDao.getStreamIds(providerId, XtreamStreamEntity.TYPE_LIVE).size
        val vodStreamsCount = streamDao.getStreamIds(providerId, XtreamStreamEntity.TYPE_VOD).size
        val seriesCount = seriesDao.getSeriesIds(providerId).size

        CacheStats(
            totalSize = 0L,
            liveTv = ContentTypeCacheStats(0L, liveCategories.isNotEmpty(), liveStreamsCount),
            movies = ContentTypeCacheStats(0L, vodCategories.isNotEmpty(), vodStreamsCount),
            tvShows = ContentTypeCacheStats(0L, seriesCategories.isNotEmpty(), seriesCount),
            epgCount = 0, // EPG handled by EpgIndexDatabase
            otherSize = 0L
        )
    }

    /**
     * Clear cache for specific content type (clears DB tables)
     */
    fun clearCacheForContentType(contentType: String) {
        val editor = cache.edit()
        when (contentType) {
            "LIVE_TV" -> {
                editor.remove(KEY_CATEGORIES_TIMESTAMP)
                fetchTimes.remove("live_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
                streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_LIVE)
            }
            "MOVIES" -> {
                editor.remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                fetchTimes.remove("vod_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
                streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_VOD)
            }
            "TV_SHOWS" -> {
                editor.remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                fetchTimes.remove("series_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
                seriesDao.deleteAll(providerId)
            }
        }
        editor.apply()
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
     * Track payload size for dev mode (No-op after DB migration)
     */
    private inline fun <reified T> trackPayloadSize(key: String, data: T) {
        // No-op
    }

    /**
     * Get payload size for a specific key in human-readable format
     */
    fun getPayloadSize(key: String): String? {
        return null
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
                    .remove(KEY_CATEGORIES_TIMESTAMP)
                    .apply()
                fetchTimes.remove("live_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
            }
            "MOVIES" -> {
                cache.edit()
                    .remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                    .apply()
                fetchTimes.remove("vod_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
            }
            "TV_SHOWS" -> {
                cache.edit()
                    .remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                    .apply()
                fetchTimes.remove("series_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
            }
        }
    }

    /**
     * Clear streams cache for a specific category
     */
    fun clearStreamsCache(categoryId: String) {
        cache.edit()
            .remove(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId)
            .apply()

        fetchTimes.remove("category_$categoryId")
        fetchTimes.remove("category_vod_$categoryId")
        fetchTimes.remove("category_series_$categoryId")

        // Since we don't know the type easily here without querying, and this method is legacy,
        // we'll try to delete from all stream types for this category
        streamDao.deleteByCategoryId(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId)
        streamDao.deleteByCategoryId(providerId, XtreamStreamEntity.TYPE_VOD, categoryId)
        seriesDao.deleteByCategoryId(providerId, categoryId)
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
     * Fetches EPG data for multiple streams in parallel with batching to prevent OOM.
     */
    suspend fun getEpgForStreams(streamIds: List<Int>): Result<Map<Int, EpgResponse>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val results = mutableMapOf<Int, EpgResponse>()
                // Batch requests to prevent OOM from too many concurrent network tasks/buffers
                val batchSize = 5
                streamIds.chunked(batchSize).forEach { batch ->
                    batch.forEach { streamId ->
                        when (val result = getEpgForStream(streamId)) {
                            is Result.Success -> results[streamId] = result.data
                            is Result.Error -> {
                                // Continue on failure - EPG may not be available for all channels
                            }
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

    suspend fun syncCategories(type: String): Deferred<Unit> {
        val task = object : RefreshTask {
            override val id = "xtream_categories_${providerId}_$type"
            override val priority = if (type == XtreamCategoryEntity.TYPE_LIVE) RefreshPriority.MEDIUM else RefreshPriority.LOW

            override suspend fun execute() {
                 val service = apiService ?: return
                 try {
                     val apiCategories = when (type) {
                         XtreamCategoryEntity.TYPE_LIVE -> service.getCategories()
                         XtreamCategoryEntity.TYPE_VOD -> service.getVodCategories()
                         XtreamCategoryEntity.TYPE_SERIES -> service.getSeriesCategories()
                         else -> emptyList()
                     }

                     val entities = apiCategories.map {
                         val base = XtreamCategoryEntity(
                             categoryId = it.categoryId,
                             providerId = providerId,
                             categoryName = it.categoryName,
                             parentId = it.parentId,
                             type = type,
                             contentHash = 0
                         )
                         base.copy(contentHash = base.hashCode())
                     }

                     val currentHashes = categoryDao.getCategoryHashes(providerId, type)

                     val toDeleteIds = currentHashes.keys - entities.map { it.categoryId }.toSet()

                     val toInsert = entities.filter { newEntity ->
                         val oldHash = currentHashes[newEntity.categoryId]
                         oldHash == null || oldHash != newEntity.contentHash
                     }

                     if (toDeleteIds.isNotEmpty()) {
                         categoryDao.deleteByIds(providerId, type, toDeleteIds.toList())
                     }
                     if (toInsert.isNotEmpty()) {
                         categoryDao.insertAll(toInsert)
                     }

                     val key = when(type) {
                         XtreamCategoryEntity.TYPE_LIVE -> KEY_CATEGORIES_TIMESTAMP
                         XtreamCategoryEntity.TYPE_VOD -> KEY_VOD_CATEGORIES_TIMESTAMP
                         XtreamCategoryEntity.TYPE_SERIES -> KEY_SERIES_CATEGORIES_TIMESTAMP
                         else -> ""
                     }
                     if (key.isNotEmpty()) {
                         cache.edit().putLong(key, System.currentTimeMillis()).apply()
                     }

                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
            }
        }
        return RefreshQueue.submit(task)
    }

    suspend fun syncStreams(type: String): Deferred<Unit> {
        val task = object : RefreshTask {
            override val id = "xtream_streams_${providerId}_$type"
            override val priority = if (type == XtreamStreamEntity.TYPE_LIVE) RefreshPriority.HIGH else RefreshPriority.LOW
            override suspend fun execute() {
                val service = apiService ?: return
                 try {
                     coroutineScope {
                         val batch = mutableListOf<XtreamStreamEntity>()
                         val BATCH_SIZE = 500
                         
                         val currentHashes = streamDao.getStreamHashes(providerId, type)
                         val seenIds = mutableSetOf<Int>()

                         val onStreamItem: suspend (XtreamStream) -> Unit = { it ->
                             val base = XtreamStreamEntity(
                                 streamId = it.streamId,
                                 providerId = providerId,
                                 type = type,
                                 num = it.num,
                                 name = it.name,
                                 streamType = it.streamType,
                                 streamIcon = it.streamIcon,
                                 epgChannelId = it.epgChannelId,
                                 added = it.added,
                                 categoryId = it.categoryId,
                                 customSid = it.customSid,
                                 tvArchive = it.tvArchive,
                                 directSource = it.directSource,
                                 tvArchiveDuration = it.tvArchiveDuration,
                                 contentHash = 0
                             )
                             val entity = base.copy(contentHash = base.hashCode())
                             seenIds.add(entity.streamId)
                             
                             val oldHash = currentHashes[entity.streamId]
                             if (oldHash == null || oldHash != entity.contentHash) {
                                 batch.add(entity)
                             }
                             
                             if (batch.size >= BATCH_SIZE) {
                                 val toInsert = batch.toList()
                                 batch.clear()
                                 database.runInTransaction {
                                     streamDao.insertAll(toInsert)
                                 }
                                 delay(50) // Give GC time to breathe between massive batches
                             }
                         }

                         when (type) {
                             XtreamStreamEntity.TYPE_LIVE -> service.getStreamsStreaming(null, onStreamItem)
                             XtreamStreamEntity.TYPE_VOD -> service.getVodStreamsStreaming(null, onStreamItem)
                         }

                         // Flush remaining
                         if (batch.isNotEmpty()) {
                             streamDao.insertAll(batch)
                         }

                         // Delete removed streams
                         val toDeleteIds = currentHashes.keys - seenIds
                         if (toDeleteIds.isNotEmpty()) {
                             streamDao.deleteByIds(providerId, type, toDeleteIds.toList())
                         }

                         val key = KEY_STREAMS_TIMESTAMP_PREFIX + (if (type==XtreamStreamEntity.TYPE_LIVE) "LIVE_ALL" else "VOD_ALL")
                         cache.edit().putLong(key, System.currentTimeMillis()).apply()
                     }

                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
            }
        }
        return RefreshQueue.submit(task)
    }

    suspend fun syncSeries(): Deferred<Unit> {
        val task = object : RefreshTask {
            override val id = "xtream_series_${providerId}"
            override val priority = RefreshPriority.LOW
            override suspend fun execute() {
                val service = apiService ?: return
                 try {
                     coroutineScope {
                         val batch = mutableListOf<XtreamSeriesEntity>()
                         val BATCH_SIZE = 500
                         
                         val currentHashes = seriesDao.getSeriesHashes(providerId)
                         val seenIds = mutableSetOf<Int>()

                         val onSeriesItem: suspend (XtreamSeries) -> Unit = { it ->
                             val base = XtreamSeriesEntity(
                                 seriesId = it.seriesId,
                                 providerId = providerId,
                                 num = it.num,
                                 name = it.name,
                                 cover = it.cover,
                                 plot = it.plot,
                                 cast = it.cast,
                                 director = it.director,
                                 genre = it.genre,
                                 releaseDate = it.releaseDate,
                                 lastModified = it.lastModified,
                                 rating = it.rating,
                                 rating5based = it.rating5based,
                                 youtubeTrailer = it.youtubeTrailer,
                                 episodeRunTime = it.episodeRunTime,
                                 categoryId = it.categoryId,
                                 backdropPath = it.backdropPath?.joinToString(","),
                                 contentHash = 0
                             )
                             val entity = base.copy(contentHash = base.hashCode())
                             seenIds.add(entity.seriesId)
                             
                             val oldHash = currentHashes[entity.seriesId]
                             if (oldHash == null || oldHash != entity.contentHash) {
                                 batch.add(entity)
                             }
                             
                             if (batch.size >= BATCH_SIZE) {
                                 val toInsert = batch.toList()
                                 batch.clear()
                                 database.runInTransaction {
                                     seriesDao.insertAll(toInsert)
                                 }
                                 delay(50) // Give GC time to breathe
                             }
                         }

                         service.getSeriesStreaming(null, onSeriesItem)

                         // Flush remaining
                         if (batch.isNotEmpty()) {
                             seriesDao.insertAll(batch)
                         }

                         // Delete removed series
                         val toDeleteIds = currentHashes.keys - seenIds
                         if (toDeleteIds.isNotEmpty()) {
                             seriesDao.deleteByIds(providerId, toDeleteIds.toList())
                         }

                         val key = KEY_STREAMS_TIMESTAMP_PREFIX + "SERIES_ALL"
                         cache.edit().putLong(key, System.currentTimeMillis()).apply()
                     }

                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
            }
        }
        return RefreshQueue.submit(task)
    }
}
