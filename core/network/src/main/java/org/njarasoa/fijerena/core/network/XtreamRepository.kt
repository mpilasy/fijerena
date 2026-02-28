package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamContentManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamEpgManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamMetricsManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamSessionManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamStatsManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamUserDataManager
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.player.domain.ContentType

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
    val contentType: String,      // ContentType.LIVE_TV, ContentType.MOVIES, or ContentType.TV_SHOWS
    val timestamp: Long = System.currentTimeMillis()  // For ordering
)

class XtreamRepository(
    private val accountManager: AccountManager,
    context: Context,
    private val providerId: Long = 0L,
    private val providerSettings: ProviderSettings = ProviderSettings.DEFAULT
) {
    private val cacheName = if (providerId > 0L) "xtream_cache_$providerId" else "xtream_cache"
    private val cache: SharedPreferences = context.getSharedPreferences(
        cacheName,
        Context.MODE_PRIVATE
    )
    private val appSettings = AppSettings(context)  // Keep for global settings (isDevMode)
    private val database = XtreamDatabase.getInstance(context)

    // Managers
    private val metricsManager = XtreamMetricsManager(appSettings)

    // StatsManager (needs to be initialized early for onClearCache callback if needed, but here onClearCache is just a lambda)
    private val statsManager = XtreamStatsManager(database, cache, metricsManager, providerId)

    private val sessionManager = XtreamSessionManager(accountManager) {
        statsManager.clearCache()
    }

    private val contentManager = XtreamContentManager(
        sessionManager, database, cache, providerSettings, metricsManager, providerId
    )

    private val userDataManager = XtreamUserDataManager(cache, providerSettings, providerId)

    private val epgManager = XtreamEpgManager(sessionManager, cache, providerSettings)


    // Delegate methods

    suspend fun login(url: String, username: String, password: String, rememberMe: Boolean): Result<XtreamAuthResponse> =
        sessionManager.login(url, username, password, rememberMe)

    suspend fun restoreSession(): Result<XtreamAuthResponse> = sessionManager.restoreSession()

    suspend fun updateProviderUrl(newUrl: String): Result<XtreamAuthResponse> = sessionManager.updateProviderUrl(newUrl)

    suspend fun reinitialize(url: String, username: String, password: String): Result<XtreamAuthResponse> =
        sessionManager.reinitialize(url, username, password)

    fun isAuthenticated(): Boolean = sessionManager.isAuthenticated()

    suspend fun logout(): Result<Unit> = sessionManager.logout()

    fun getCurrentUrl(): String? = sessionManager.getCurrentUrl()

    fun getCurrentUsername(): String? = sessionManager.getCurrentUsername()

    fun getCurrentPassword(): String? = sessionManager.getCurrentPassword()


    suspend fun getCategories(): Result<List<XtreamCategory>> = contentManager.getCategories()

    suspend fun getVodCategories(): Result<List<XtreamCategory>> = contentManager.getVodCategories()

    suspend fun getSeriesCategories(): Result<List<XtreamCategory>> = contentManager.getSeriesCategories()

    suspend fun syncCategories(type: String): Deferred<Unit> = contentManager.syncCategories(type)


    suspend fun getAllStreams(contentType: String): Result<List<XtreamStream>> =
         contentManager.getAllStreams(contentType)

    suspend fun getStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> =
        contentManager.getStreams(categoryId, forSearch)

    suspend fun getVodStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> =
        contentManager.getVodStreams(categoryId, forSearch)

    suspend fun getSeries(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> =
        contentManager.getSeries(categoryId, forSearch)

    suspend fun syncStreams(type: String): Deferred<Unit> = contentManager.syncStreams(type)

    suspend fun syncSeries(): Deferred<Unit> = contentManager.syncSeries()

    fun getStreamsCached(categoryId: String): List<XtreamStream>? = contentManager.getStreamsCached(categoryId)

    fun getVodStreamsCached(categoryId: String): List<XtreamStream>? = contentManager.getVodStreamsCached(categoryId)

    fun getSeriesCached(categoryId: String): List<XtreamStream>? = contentManager.getSeriesCached(categoryId)

    fun buildStreamUrl(streamId: Int, contentType: String = ContentType.LIVE_TV, extension: String? = null): Result<String> =
        contentManager.buildStreamUrl(streamId, contentType, extension)

    fun buildEpisodeStreamUrl(episodeId: String, extension: String): Result<String> =
        contentManager.buildEpisodeStreamUrl(episodeId, extension)

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo> = contentManager.getSeriesInfo(seriesId)

    suspend fun getVodInfo(vodId: Int): Result<VodInfo> = contentManager.getVodInfo(vodId)


    suspend fun getEpgForStream(streamId: Int): Result<EpgResponse> = epgManager.getEpgForStream(streamId)

    suspend fun getEpgForStreams(streamIds: List<Int>): Result<Map<Int, EpgResponse>> = epgManager.getEpgForStreams(streamIds)

    fun clearEpgCache(streamId: Int) = epgManager.clearEpgCache(streamId)

    fun clearAllEpgCache() = epgManager.clearAllEpgCache()


    fun saveLastPlayedStream(categoryId: String, streamId: Int, streamName: String, contentType: String) =
        userDataManager.saveLastPlayedStream(categoryId, streamId, streamName, contentType)

    fun getLastCategoryId(contentType: String): String? = userDataManager.getLastCategoryId(contentType)

    fun getLastStreamId(contentType: String): Int? = userDataManager.getLastStreamId(contentType)

    fun getLastContentType(): String? = userDataManager.getLastContentType()

    fun getWatchHistory(): List<WatchedStream> = userDataManager.getWatchHistory()

    fun clearWatchHistory() = userDataManager.clearWatchHistory()

    fun addFavorite(streamId: Int, streamName: String, categoryId: String, contentType: String): Boolean =
        userDataManager.addFavorite(streamId, streamName, categoryId, contentType)

    fun removeFavorite(streamId: Int, contentType: String): Boolean =
        userDataManager.removeFavorite(streamId, contentType)

    fun getFavorites(): List<FavoriteStream> = userDataManager.getFavorites()

    fun isFavorite(streamId: Int, contentType: String): Boolean = userDataManager.isFavorite(streamId, contentType)

    fun clearFavorites() = userDataManager.clearFavorites()

    fun savePlaybackPosition(
        streamId: Int, streamName: String, categoryId: String, contentType: String, position: Long, duration: Long
    ) = userDataManager.savePlaybackPosition(streamId, streamName, categoryId, contentType, position, duration)

    fun getPlaybackPosition(streamId: Int, contentType: String): WatchedStream? =
        userDataManager.getPlaybackPosition(streamId, contentType)

    fun getInProgressStreams(contentType: String): List<WatchedStream> = userDataManager.getInProgressStreams(contentType)

    fun clearPlaybackPosition(streamId: Int, contentType: String) = userDataManager.clearPlaybackPosition(streamId, contentType)


    suspend fun clearCache() = statsManager.clearCache()

    fun getCacheSize(): Long = statsManager.getCacheSize()

    suspend fun getCacheStats(): CacheStats = statsManager.getCacheStats()

    suspend fun clearCacheForContentType(contentType: String) = statsManager.clearCacheForContentType(contentType)

    suspend fun clearStreamsCache(categoryId: String) = statsManager.clearStreamsCache(categoryId)

    suspend fun clearCategoriesCache(contentType: String) = statsManager.clearCategoriesCache(contentType)


    // Metrics (Delegated partially via getFetchTime/Formatted/PayloadSize)

    fun getPayloadSize(key: String): String? = metricsManager.getPayloadSize(key)

    fun getFetchTime(key: String): Long? = metricsManager.getFetchTime(key)

    fun getFetchTimeFormatted(key: String): String? = metricsManager.getFetchTimeFormatted(key)

    fun getAppSettings(): AppSettings = appSettings

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
}
