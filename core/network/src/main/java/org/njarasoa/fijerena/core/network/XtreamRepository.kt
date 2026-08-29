package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xtream.SyncDelta
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamContentManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamEpgManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamMetricsManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamSessionManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamStatsManager
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamStream

class XtreamRepository(
    private val accountManager: AccountManager,
    context: Context,
    private val providerId: Long = 0L,
    private val providerSettings: ProviderSettings = ProviderSettings.DEFAULT,
) {
    private val cacheName = if (providerId > 0L) "xtream_cache_$providerId" else "xtream_cache"
    private val cache: SharedPreferences =
        context.getSharedPreferences(
            cacheName,
            Context.MODE_PRIVATE,
        )
    private val appSettings = AppSettings(context) // Keep for global settings (isDevMode)
    private val database = XtreamDatabase.getInstance(context)

    // Managers
    private val metricsManager = XtreamMetricsManager(appSettings)

    // StatsManager (needs to be initialized early for onClearCache callback if needed, but here onClearCache is just a lambda)
    private val statsManager = XtreamStatsManager(database, cache, metricsManager, providerId)

    private val sessionManager =
        XtreamSessionManager(
            context,
            accountManager,
            { statsManager.clearCache() },
            providerSettings.streamOutputFormat,
            providerId,
        )

    private val contentManager =
        XtreamContentManager(
            sessionManager,
            database,
            cache,
            providerSettings,
            metricsManager,
            providerId,
        )


    private val epgManager = XtreamEpgManager(sessionManager, cache, providerSettings, database.epgCacheDao(), providerId)

    // Delegate methods

    suspend fun login(
        url: String,
        username: String,
        password: String,
        rememberMe: Boolean,
    ): Result<XtreamAuthResponse> = sessionManager.login(url, username, password, rememberMe)

    suspend fun restoreSession(): Result<XtreamAuthResponse> = sessionManager.restoreSession()

    suspend fun updateProviderUrl(newUrl: String): Result<XtreamAuthResponse> = sessionManager.updateProviderUrl(newUrl)

    suspend fun reinitialize(
        url: String,
        username: String,
        password: String,
    ): Result<XtreamAuthResponse> = sessionManager.reinitialize(url, username, password)

    fun isAuthenticated(): Boolean = sessionManager.isAuthenticated()

    suspend fun logout(): Result<Unit> = sessionManager.logout()

    fun getCurrentUrl(): String? = sessionManager.getCurrentUrl()

    fun getCurrentUsername(): String? = sessionManager.getCurrentUsername()

    fun getCurrentPassword(): String? = sessionManager.getCurrentPassword()

    suspend fun getCategories(): Result<List<XtreamCategory>> = contentManager.getCategories()

    suspend fun getVodCategories(): Result<List<XtreamCategory>> = contentManager.getVodCategories()

    suspend fun getSeriesCategories(): Result<List<XtreamCategory>> = contentManager.getSeriesCategories()

    suspend fun syncCategories(type: String): Deferred<Unit> = contentManager.syncCategories(type)

    suspend fun getAllStreams(contentType: String): Result<List<XtreamStream>> = contentManager.getAllStreams(contentType)

    suspend fun getStreams(
        categoryId: String,
        forSearch: Boolean = false,
    ): Result<List<XtreamStream>> = contentManager.getStreams(categoryId, forSearch)

    suspend fun getVodStreams(
        categoryId: String,
        forSearch: Boolean = false,
    ): Result<List<XtreamStream>> = contentManager.getVodStreams(categoryId, forSearch)

    suspend fun getSeries(
        categoryId: String,
        forSearch: Boolean = false,
    ): Result<List<XtreamStream>> = contentManager.getSeries(categoryId, forSearch)

    suspend fun syncStreams(type: String): Deferred<Unit> = contentManager.syncStreams(type)

    suspend fun getEpisodeCountsBySeries(): Map<String, Int> = contentManager.getEpisodeCountsBySeries()

    suspend fun syncSeries(): Deferred<Unit> = contentManager.syncSeries()

    suspend fun recomputeExclusions() = contentManager.recomputeExclusions()

    /** The combined insert/update/delete delta from every sync task run since the last call. */
    fun consumeSyncDelta(): SyncDelta = contentManager.consumeSyncDelta()

    suspend fun getCategoryTotalCount(contentType: String): Int =
        when (contentType) {
            ContentType.LIVE_TV -> contentManager.getCategoryTotalCount(XtreamCategoryEntity.TYPE_LIVE)
            ContentType.MOVIES -> contentManager.getCategoryTotalCount(XtreamCategoryEntity.TYPE_VOD)
            ContentType.TV_SHOWS -> contentManager.getCategoryTotalCount(XtreamCategoryEntity.TYPE_SERIES)
            else -> 0
        }

    fun getStreamsCached(categoryId: String): List<XtreamStream>? = contentManager.getStreamsCached(categoryId)

    fun getVodStreamsCached(categoryId: String): List<XtreamStream>? = contentManager.getVodStreamsCached(categoryId)

    fun getSeriesCached(categoryId: String): List<XtreamStream>? = contentManager.getSeriesCached(categoryId)

    suspend fun searchByFts(contentType: String, ftsQuery: String, includeExcluded: Boolean = false): List<XtreamStream> =
        when (contentType) {
            ContentType.LIVE_TV -> contentManager.searchStreams(XtreamStreamEntity.TYPE_LIVE, ftsQuery, includeExcluded)
            ContentType.MOVIES -> contentManager.searchStreams(XtreamStreamEntity.TYPE_VOD, ftsQuery, includeExcluded)
            ContentType.TV_SHOWS -> contentManager.searchSeries(ftsQuery, includeExcluded)
            else -> emptyList()
        }

    /** Other local catalogue entries sharing [tmdbId] — see [XtreamContentManager.getAlternateVodStreams]. */
    suspend fun getAlternateStreams(contentType: String, tmdbId: String, excludeId: Int): List<XtreamStream> =
        when (contentType) {
            ContentType.MOVIES -> contentManager.getAlternateVodStreams(tmdbId, excludeId)
            ContentType.TV_SHOWS -> contentManager.getAlternateSeries(tmdbId, excludeId)
            else -> emptyList()
        }

    suspend fun countExcludedByFts(contentType: String, ftsQuery: String): Int =
        when (contentType) {
            ContentType.LIVE_TV -> contentManager.countExcludedStreams(XtreamStreamEntity.TYPE_LIVE, ftsQuery)
            ContentType.MOVIES -> contentManager.countExcludedStreams(XtreamStreamEntity.TYPE_VOD, ftsQuery)
            ContentType.TV_SHOWS -> contentManager.countExcludedSeries(ftsQuery)
            else -> 0
        }

    fun buildStreamUrl(
        streamId: Int,
        contentType: String = ContentType.LIVE_TV,
        extension: String? = null,
    ): Result<String> = contentManager.buildStreamUrl(streamId, contentType, extension)

    fun buildEpisodeStreamUrl(
        episodeId: String,
        extension: String,
    ): Result<String> = contentManager.buildEpisodeStreamUrl(episodeId, extension)

    suspend fun getStreamName(
        streamId: Int,
        contentType: String,
    ): String? = contentManager.getStreamName(streamId, contentType)

    suspend fun getSeriesInfo(seriesId: Int): XtreamResponse<SeriesInfo> = contentManager.getSeriesInfo(seriesId)

    suspend fun getVodInfo(vodId: Int): XtreamResponse<VodInfo> = contentManager.getVodInfo(vodId)

    suspend fun getCachedMovieDetail(vodId: Int) = contentManager.getCachedMovieDetail(vodId)

    suspend fun saveMovieDetailCache(
        vodId: Int,
        contentRating: String?,
        tmdbId: String?,
        containerExtension: String?,
        fetchedAt: Long,
        posterPath: String? = null,
    ) = contentManager.saveMovieDetailCache(vodId, contentRating, tmdbId, containerExtension, fetchedAt, posterPath)

    suspend fun getCachedSeriesEntity(seriesId: Int) = contentManager.getCachedSeriesEntity(seriesId)

    suspend fun saveSeriesDetailCache(
        seriesId: Int,
        contentRating: String?,
        tmdbId: String?,
        fetchedAt: Long,
        posterPath: String? = null,
    ) = contentManager.saveSeriesDetailCache(seriesId, contentRating, tmdbId, fetchedAt, posterPath)

    suspend fun resolveSeriesIdByName(name: String): Int? = contentManager.resolveSeriesIdByName(name)

    suspend fun getPersistedEpisodePlots(seriesId: Int): Map<String, String> = contentManager.getPersistedEpisodePlots(seriesId)

    /** See [XtreamContentManager.getCachedSeriesDetail]. */
    suspend fun getCachedSeriesDetail(seriesId: Int) = contentManager.getCachedSeriesDetail(seriesId)

    suspend fun persistEpisodeOverviews(episodes: Map<String, List<EpisodeItem>>) = contentManager.persistEpisodeOverviews(episodes)

    suspend fun getEpgForStream(streamId: Int): Result<EpgResponse> = epgManager.getEpgForStream(streamId)

    suspend fun getEpgForStreams(streamIds: List<Int>): Result<Map<Int, EpgResponse>> = epgManager.getEpgForStreams(streamIds)

    fun clearEpgCache(streamId: Int) = epgManager.clearEpgCache(streamId)

    fun clearAllEpgCache() = epgManager.clearAllEpgCache()


    suspend fun clearCache() = statsManager.clearCache()

    fun getCacheSize(): Long = statsManager.getCacheSize()

    suspend fun getCacheStats(): CacheStats = statsManager.getCacheStats()

    suspend fun clearCacheForContentType(contentType: String) = statsManager.clearCacheForContentType(contentType)

    suspend fun clearStreamsCache(categoryId: String) = statsManager.clearStreamsCache(categoryId)

    suspend fun clearCategoriesCache(contentType: String) = statsManager.clearCategoriesCache(contentType)

    // Metrics (Delegated partially via getFetchTime/Formatted)

    fun getFetchTime(key: String): Long? = metricsManager.getFetchTime(key)

    fun getFetchTimeFormatted(key: String): String? = metricsManager.getFetchTimeFormatted(key)

    fun getAppSettings(): AppSettings = appSettings

    /**
     * Get cache statistics per content type (based on DB counts)
     */
    data class ContentTypeCacheStats(
        val size: Long, // kept for compatibility, always 0
        val categoryCount: Int,
        val itemsCount: Int, // Streams for Live/VOD, Series for TV Shows
        val episodesCount: Int = 0, // Only for TV Shows
    )

    data class CacheStats(
        val totalSize: Long,
        val liveTv: ContentTypeCacheStats,
        val movies: ContentTypeCacheStats,
        val tvShows: ContentTypeCacheStats,
        val epgCount: Int,
        val otherSize: Long,
    )
}
