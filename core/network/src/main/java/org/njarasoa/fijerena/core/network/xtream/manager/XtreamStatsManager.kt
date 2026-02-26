package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_SERIES_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_STREAMS_TIMESTAMP_PREFIX
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_VOD_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.XtreamRepository.CacheStats
import org.njarasoa.fijerena.core.network.XtreamRepository.ContentTypeCacheStats

class XtreamStatsManager(
    private val database: XtreamDatabase,
    private val sharedPreferences: SharedPreferences,
    private val metricsManager: XtreamMetricsManager,
    private val providerId: Long
) {
    private val categoryDao = database.categoryDao()
    private val streamDao = database.streamDao()
    private val seriesDao = database.seriesDao()

    /**
     * Get total cache size (estimated from DB record count)
     * Returning 0 for now as exact byte size calculation from DB is expensive
     * and SharedPreferences size is negligible.
     */
    fun getCacheSize(): Long = 0L

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

    fun clearCache() {
        // Clear SharedPreferences timestamps and legacy keys
        sharedPreferences.edit().clear().apply()
        metricsManager.clearFetchTimes()

        // Clear DB
        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
        streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_LIVE)
        streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_VOD)
        seriesDao.deleteAll(providerId)
    }

    /**
     * Clear cache for specific content type (clears DB tables)
     */
    fun clearCacheForContentType(contentType: String) {
        val editor = sharedPreferences.edit()
        when (contentType) {
            "LIVE_TV" -> {
                editor.remove(KEY_CATEGORIES_TIMESTAMP)
                metricsManager.removeFetchTime("live_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
                streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_LIVE)
            }
            "MOVIES" -> {
                editor.remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                metricsManager.removeFetchTime("vod_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
                streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_VOD)
            }
            "TV_SHOWS" -> {
                editor.remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                metricsManager.removeFetchTime("series_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
                seriesDao.deleteAll(providerId)
            }
        }
        editor.apply()
    }

    /**
     * Clear streams cache for a specific category
     */
    fun clearStreamsCache(categoryId: String) {
        sharedPreferences.edit()
            .remove(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId)
            .apply()

        metricsManager.removeFetchTime("category_$categoryId")
        metricsManager.removeFetchTime("category_vod_$categoryId")
        metricsManager.removeFetchTime("category_series_$categoryId")

        // Since we don't know the type easily here without querying, and this method is legacy,
        // we'll try to delete from all stream types for this category
        streamDao.deleteByCategoryId(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId)
        streamDao.deleteByCategoryId(providerId, XtreamStreamEntity.TYPE_VOD, categoryId)
        seriesDao.deleteByCategoryId(providerId, categoryId)
    }

    /**
     * Clear categories cache for a specific content type
     */
    fun clearCategoriesCache(contentType: String) {
        val editor = sharedPreferences.edit()
        when (contentType) {
            "LIVE_TV" -> {
                editor.remove(KEY_CATEGORIES_TIMESTAMP)
                metricsManager.removeFetchTime("live_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
            }
            "MOVIES" -> {
                editor.remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                metricsManager.removeFetchTime("vod_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
            }
            "TV_SHOWS" -> {
                editor.remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                metricsManager.removeFetchTime("series_categories")
                categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
            }
        }
        editor.apply()
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
}
