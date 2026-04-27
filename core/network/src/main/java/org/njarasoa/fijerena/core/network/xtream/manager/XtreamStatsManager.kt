package org.njarasoa.fijerena.core.network.xtream.manager
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.XtreamRepository.CacheStats
import org.njarasoa.fijerena.core.network.XtreamRepository.ContentTypeCacheStats
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_SERIES_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_STREAMS_TIMESTAMP_PREFIX
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_VOD_CATEGORIES_TIMESTAMP

class XtreamStatsManager(
    private val database: XtreamDatabase,
    private val sharedPreferences: SharedPreferences,
    private val metricsManager: XtreamMetricsManager,
    private val providerId: Long,
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

    suspend fun getCacheStats(): CacheStats =
        withContext(Dispatchers.IO) {
            val liveCategoriesCount = categoryDao.countCategories(providerId, XtreamCategoryEntity.TYPE_LIVE)
            val vodCategoriesCount = categoryDao.countCategories(providerId, XtreamCategoryEntity.TYPE_VOD)
            val seriesCategoriesCount = categoryDao.countCategories(providerId, XtreamCategoryEntity.TYPE_SERIES)

            // Use COUNT(*) queries instead of loading full ID lists into memory
            val liveStreamsCount = streamDao.countStreams(providerId, XtreamStreamEntity.TYPE_LIVE)
            val vodStreamsCount = streamDao.countStreams(providerId, XtreamStreamEntity.TYPE_VOD)
            val seriesCount = seriesDao.countSeries(providerId)
            val episodesCount = database.episodeDao().countEpisodes(providerId)

            CacheStats(
                totalSize = 0L,
                liveTv = ContentTypeCacheStats(0L, liveCategoriesCount, liveStreamsCount),
                movies = ContentTypeCacheStats(0L, vodCategoriesCount, vodStreamsCount),
                tvShows = ContentTypeCacheStats(0L, seriesCategoriesCount, seriesCount, episodesCount),
                epgCount = 0, // EPG handled by EpgIndexDatabase
                otherSize = 0L,
            )
        }

    suspend fun clearCache() =
        withContext(Dispatchers.IO) {
            // Clear SharedPreferences timestamps and legacy keys
            sharedPreferences.edit { clear() }
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
    suspend fun clearCacheForContentType(contentType: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                when (contentType) {
                    "LIVE_TV" -> {
                        remove(KEY_CATEGORIES_TIMESTAMP)
                        metricsManager.removeFetchTime("live_categories")
                        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
                        streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_LIVE)
                    }
                    "MOVIES" -> {
                        remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                        metricsManager.removeFetchTime("vod_categories")
                        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
                        streamDao.deleteAll(providerId, XtreamStreamEntity.TYPE_VOD)
                    }
                    "TV_SHOWS" -> {
                        remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                        metricsManager.removeFetchTime("series_categories")
                        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
                        seriesDao.deleteAll(providerId)
                    }
                }
            }
        }

    /**
     * Clear streams cache for a specific category
     */
    suspend fun clearStreamsCache(categoryId: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                remove(KEY_STREAMS_TIMESTAMP_PREFIX + categoryId)
            }

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
    suspend fun clearCategoriesCache(contentType: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                when (contentType) {
                    "LIVE_TV" -> {
                        remove(KEY_CATEGORIES_TIMESTAMP)
                        metricsManager.removeFetchTime("live_categories")
                        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_LIVE)
                    }
                    "MOVIES" -> {
                        remove(KEY_VOD_CATEGORIES_TIMESTAMP)
                        metricsManager.removeFetchTime("vod_categories")
                        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_VOD)
                    }
                    "TV_SHOWS" -> {
                        remove(KEY_SERIES_CATEGORIES_TIMESTAMP)
                        metricsManager.removeFetchTime("series_categories")
                        categoryDao.deleteAll(providerId, XtreamCategoryEntity.TYPE_SERIES)
                    }
                }
            }
        }
}
