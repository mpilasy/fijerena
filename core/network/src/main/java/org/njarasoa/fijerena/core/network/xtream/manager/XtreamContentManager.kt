package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.SharedPreferences
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.queue.RefreshPriority
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.resultOf
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_SERIES_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_STREAMS_TIMESTAMP_PREFIX
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_VOD_CATEGORIES_TIMESTAMP
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo
import org.njarasoa.fijerena.core.player.model.XtreamCategory
import org.njarasoa.fijerena.core.player.model.XtreamSeries
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.player.domain.ContentType

class XtreamContentManager(
    private val sessionManager: XtreamSessionManager,
    private val database: XtreamDatabase,
    private val sharedPreferences: SharedPreferences,
    private val providerSettings: ProviderSettings,
    private val metricsManager: XtreamMetricsManager,
    private val providerId: Long
) {
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
     */
    private val liveStreamRefreshThresholdMs: Long = 5 * 60 * 1000L // 5 minutes

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

            val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
            val categories = service.getCategories()

            // Insert into DB
            categoryDao.insertAll(categories.map {
                XtreamCategoryEntity(it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_LIVE)
            })
            sharedPreferences.edit().putLong(KEY_CATEGORIES_TIMESTAMP, System.currentTimeMillis()).apply()

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

            val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
            val categories = service.getVodCategories()

            categoryDao.insertAll(categories.map {
                XtreamCategoryEntity(it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_VOD)
            })
            // Fix: use VOD-specific timestamp key (was incorrectly using live TV key)
            sharedPreferences.edit().putLong(KEY_VOD_CATEGORIES_TIMESTAMP, System.currentTimeMillis()).apply()

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

            val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
            val categories = service.getSeriesCategories()

            categoryDao.insertAll(categories.map {
                XtreamCategoryEntity(it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_SERIES)
            })
            sharedPreferences.edit().putLong(KEY_SERIES_CATEGORIES_TIMESTAMP, System.currentTimeMillis()).apply()

            categories
        }
    }

    suspend fun getAllStreams(contentType: String): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        try {
            val entities = when (contentType) {
                ContentType.LIVE_TV -> streamDao.getAllStreams(providerId, XtreamStreamEntity.TYPE_LIVE).map { mapStreamEntityToModel(it) }
                ContentType.MOVIES -> streamDao.getAllStreams(providerId, XtreamStreamEntity.TYPE_VOD).map { mapStreamEntityToModel(it) }
                ContentType.TV_SHOWS -> seriesDao.getAllSeries(providerId).map { mapSeriesEntityToStream(it) }
                else -> streamDao.getAllStreams(providerId, XtreamStreamEntity.TYPE_LIVE).map { mapStreamEntityToModel(it) }
            }
            Result.Success(entities)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            if (forSearch) {
                return@suspendResultOf streamDao.searchStreams(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId).map { mapStreamEntityToModel(it) }
            }

            val dbEntities = streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId)

            if (dbEntities.isNotEmpty()) {
                val key = KEY_STREAMS_TIMESTAMP_PREFIX + "LIVE_ALL"
                if (!isCacheFresh(key, liveStreamRefreshThresholdMs)) {
                    syncStreams(XtreamStreamEntity.TYPE_LIVE)
                }
                return@suspendResultOf dbEntities.map { mapStreamEntityToModel(it) }
            }

            val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
            val streams = service.getStreams(categoryId)

            streamDao.insertAll(streams.map {
                 XtreamStreamEntity(
                     streamId = it.streamId,
                     providerId = providerId,
                     type = XtreamStreamEntity.TYPE_LIVE,
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

    suspend fun getVodStreams(categoryId: String, forSearch: Boolean = false): Result<List<XtreamStream>> = withContext(Dispatchers.IO) {
        suspendResultOf {
            if (forSearch) {
                return@suspendResultOf streamDao.searchStreams(providerId, XtreamStreamEntity.TYPE_VOD, categoryId).map { mapStreamEntityToModel(it) }
            }

            val dbEntities = streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_VOD, categoryId)

            if (dbEntities.isNotEmpty()) {
                val key = KEY_STREAMS_TIMESTAMP_PREFIX + "VOD_ALL"
                if (!isCacheFresh(key)) {
                    syncStreams(XtreamStreamEntity.TYPE_VOD)
                }
                return@suspendResultOf dbEntities.map { mapStreamEntityToModel(it) }
            }

            val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
            val streams = service.getVodStreams(categoryId)

            streamDao.insertAll(streams.map {
                 XtreamStreamEntity(
                     streamId = it.streamId,
                     providerId = providerId,
                     type = XtreamStreamEntity.TYPE_VOD,
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

            val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
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

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfo> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = sessionManager.apiService
                ?: throw Exception("Not authenticated. Please login first.")
            val startTime = System.currentTimeMillis()
            val seriesInfo = service.getSeriesInfo(seriesId)
            val fetchTime = System.currentTimeMillis() - startTime
            metricsManager.trackFetchTime("series_$seriesId", fetchTime)
            seriesInfo
        }
    }

    suspend fun getVodInfo(vodId: Int): Result<VodInfo> = withContext(Dispatchers.IO) {
        suspendResultOf {
            val service = sessionManager.apiService
                ?: throw Exception("Not authenticated. Please login first.")
            val startTime = System.currentTimeMillis()
            val vodInfo = service.getVodInfo(vodId)
            val fetchTime = System.currentTimeMillis() - startTime
            metricsManager.trackFetchTime("vod_$vodId", fetchTime)
            vodInfo
        }
    }

    fun buildStreamUrl(streamId: Int, contentType: String = "LIVE_TV", extension: String? = null): Result<String> = resultOf {
        val service = sessionManager.apiService
            ?: throw Exception("Not authenticated. Please login first.")
        when (contentType) {
            "LIVE_TV" -> service.buildStreamUrl(streamId)
            "MOVIES" -> service.buildVodStreamUrl(streamId, extension ?: "mp4")
            "TV_SHOWS" -> service.buildSeriesStreamUrl(streamId, extension ?: "mp4")
            else -> service.buildStreamUrl(streamId)
        }
    }

    fun buildEpisodeStreamUrl(episodeId: String, extension: String): Result<String> = resultOf {
        val service = sessionManager.apiService
            ?: throw Exception("Not authenticated. Please login first.")
        service.buildEpisodeStreamUrl(episodeId, extension)
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

    suspend fun syncCategories(type: String): Deferred<Unit> {
        val task = object : RefreshTask {
            override val id = "xtream_categories_${providerId}_$type"
            override val priority = if (type == XtreamCategoryEntity.TYPE_LIVE) RefreshPriority.MEDIUM else RefreshPriority.LOW

            override suspend fun execute() {
                 val service = sessionManager.apiService ?: return
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
                         base.copy(contentHash = base.computeContentHash())
                     }

                     val currentHashes = categoryDao.getCategoryHashes(providerId, type)

                     val toDeleteIds = currentHashes.keys - entities.mapTo(HashSet()) { it.categoryId }

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
                         sharedPreferences.edit().putLong(key, System.currentTimeMillis()).apply()
                     }

                 } catch (e: Exception) {
                     android.util.Log.e("XtreamContentManager", "Error syncing data", e)
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
                val service = sessionManager.apiService ?: return
                 try {
                     coroutineScope {
                         val batch = mutableListOf<XtreamStreamEntity>()
                         val BATCH_SIZE = 2000

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
                             val entity = base.copy(contentHash = base.computeContentHash())
                             seenIds.add(entity.streamId)

                             val oldHash = currentHashes[entity.streamId]
                             if (oldHash == null || oldHash != entity.contentHash) {
                                 batch.add(entity)
                             }

                             if (batch.size >= BATCH_SIZE) {
                                 val toInsert = ArrayList(batch)
                                 batch.clear()
                                 database.runInTransaction {
                                     streamDao.insertAll(toInsert)
                                 }
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
                         sharedPreferences.edit().putLong(key, System.currentTimeMillis()).apply()
                     }

                 } catch (e: Exception) {
                     android.util.Log.e("XtreamContentManager", "Error syncing data", e)
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
                val service = sessionManager.apiService ?: return
                 try {
                     coroutineScope {
                         val batch = mutableListOf<XtreamSeriesEntity>()
                         val BATCH_SIZE = 2000

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
                             val entity = base.copy(contentHash = base.computeContentHash())
                             seenIds.add(entity.seriesId)

                             val oldHash = currentHashes[entity.seriesId]
                             if (oldHash == null || oldHash != entity.contentHash) {
                                 batch.add(entity)
                             }

                             if (batch.size >= BATCH_SIZE) {
                                 val toInsert = ArrayList(batch)
                                 batch.clear()
                                 database.runInTransaction {
                                     seriesDao.insertAll(toInsert)
                                 }
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
                         sharedPreferences.edit().putLong(key, System.currentTimeMillis()).apply()
                     }

                 } catch (e: Exception) {
                     android.util.Log.e("XtreamContentManager", "Error syncing data", e)
                 }
            }
        }
        return RefreshQueue.submit(task)
    }

    /** Returns true if the given timestamp is recent enough to skip background refresh */
    private fun isCacheFresh(timestampKey: String, thresholdMs: Long = cacheExpiryMs): Boolean {
        val timestamp = sharedPreferences.getLong(timestampKey, 0L)
        return System.currentTimeMillis() - timestamp < thresholdMs
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
}
