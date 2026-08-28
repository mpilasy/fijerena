package org.njarasoa.fijerena.core.network.xtream.manager
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.asString
import org.njarasoa.fijerena.core.network.toJsonPrimitive
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.queue.RefreshPriority
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.resultOf
import org.njarasoa.fijerena.core.network.suspendResultOf
import org.njarasoa.fijerena.core.network.xtream.SyncDelta
import org.njarasoa.fijerena.core.player.api.XtreamResponse
import org.njarasoa.fijerena.core.network.xtream.db.*
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.model.*

/**
 * Data-hygiene guard, demoted from Phase 5 dedup (plans/watch-state-durable-storage-plan.md) to a
 * standalone one after an on-device check showed episode-`tmdbId`-based dedup couldn't work at all
 * — the real duplication lives across separate `xtream_series` rows (see
 * [org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao.getSiblingCompletedEpisodeIds]),
 * so dedup no longer reads this column. Kept anyway: panels frequently copy the *series* `tmdb_id`
 * into every episode's info block, and a `tmdbId` that repeats across more than one episode of the
 * same series is a series-level value, not episode identity, regardless of whether anything
 * currently consumes the column. [episodes] is expected to already be scoped to one series (as
 * [XtreamContentManager.getSeriesInfo]'s call site is), so no `seriesId` grouping is needed.
 */
internal fun guardSeriesLevelEpisodeTmdbIds(episodes: List<XtreamEpisodeEntity>): List<XtreamEpisodeEntity> {
    val tmdbIdCounts = episodes.mapNotNull { it.tmdbId }.groupingBy { it }.eachCount()
    return episodes.map { ep ->
        if (ep.tmdbId != null && (tmdbIdCounts[ep.tmdbId] ?: 0) > 1) ep.copy(tmdbId = null) else ep
    }
}

/**
 * Manages Xtream content (categories, streams, series) including database caching and sync logic.
 */
class XtreamContentManager(
    private val sessionManager: XtreamSessionManager,
    private val database: XtreamDatabase,
    private val sharedPreferences: SharedPreferences,
    private val providerSettings: ProviderSettings,
    private val metricsManager: XtreamMetricsManager,
    private val providerId: Long,
) {
    private val categoryDao = database.categoryDao()
    private val streamDao = database.streamDao()
    private val seriesDao = database.seriesDao()
    private val episodeDao = database.episodeDao()

    // See MediaRepository's identical writeScope/commitAsync for the full rationale: commit()
    // on a background thread never leaves an entry in QueuedWork, unlike apply(). These freshness
    // timestamps are written on the same cold-entry path (category/stream load) as several
    // Media3-triggered startForegroundService() dispatches, so keeping this backlog at zero here
    // matters as much as in MediaRepository.
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private fun commitAsync(action: SharedPreferences.Editor.() -> Unit) {
        writeScope.launch { sharedPreferences.edit(commit = true, action = action) }
    }

    // Accumulates the row-level delta each sync task already computes for its own diff, so
    // syncAll() can report one combined total instead of throwing the counts away. Diagnostic
    // only — a sync triggered outside syncAll() (e.g. a lazy-load cache-miss refresh) adds into
    // the same counter, so a reading taken while such a sync overlaps a syncAll() run can include
    // a few rows that aren't actually syncAll()'s. Acceptable for "did anything change" reporting.
    private val pendingDelta = java.util.concurrent.atomic.AtomicReference(SyncDelta())

    private fun addDelta(delta: SyncDelta) {
        pendingDelta.updateAndGet { it + delta }
    }

    /** The combined delta from every sync task completed since the last call, then resets it. */
    fun consumeSyncDelta(): SyncDelta = pendingDelta.getAndSet(SyncDelta())

    companion object {
        private const val TAG = "XtreamContentManager"
        private const val KEY_CATEGORIES_TIMESTAMP = "categories_ts"
        private const val KEY_VOD_CATEGORIES_TIMESTAMP = "vod_categories_ts"
        private const val KEY_SERIES_CATEGORIES_TIMESTAMP = "series_categories_ts"
        private const val KEY_STREAMS_TIMESTAMP_PREFIX = "streams_ts_"
        // Matches the scheduled sync cadence in ProviderSyncManager, so a screen opened between
        // scheduled runs still tops the catalogue up rather than sitting on day-old data.
        private const val CACHE_EXPIRATION_MS = 4 * 3600 * 1000L // 4 hours
        // Episode synopses are written once and essentially never revised, so they get a much
        // longer life than the catalogue itself — long enough to make refetching rare, short
        // enough that a correction upstream still lands eventually.
        private const val OVERVIEW_CACHE_TTL_MS = 30L * 24 * 3600 * 1000 // 30 days

        // SQLite caps bound variables per statement (historically 999 on Android); chunk large
        // DELETE ... IN (...) batches well under that so deletes don't fail outright on
        // providers with very large catalogs.
        private const val SQLITE_DELETE_BATCH_SIZE = 900
    }

    suspend fun getCategories(): Result<List<XtreamCategory>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val dbEntities = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_LIVE)
                if (dbEntities.isNotEmpty()) {
                    if (!isCacheFresh(KEY_CATEGORIES_TIMESTAMP)) {
                        syncCategories(XtreamCategoryEntity.TYPE_LIVE)
                    }
                    return@suspendResultOf dbEntities.map { XtreamCategory(it.categoryId, it.categoryName, it.parentId) }
                }

                val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
                val categories = service.getCategories()
                categoryDao.insertAll(
                    categories.map {
                        XtreamCategoryEntity(
                            it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_LIVE,
                            excluded = !providerSettings.categoryFilters.shouldShowCategory(it.categoryName),
                        )
                    },
                )
                categories.filter { providerSettings.categoryFilters.shouldShowCategory(it.categoryName) }
            }
        }

    suspend fun getVodCategories(): Result<List<XtreamCategory>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val dbEntities = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_VOD)
                if (dbEntities.isNotEmpty()) {
                    if (!isCacheFresh(KEY_VOD_CATEGORIES_TIMESTAMP)) {
                        syncCategories(XtreamCategoryEntity.TYPE_VOD)
                    }
                    return@suspendResultOf dbEntities.map { XtreamCategory(it.categoryId, it.categoryName, it.parentId) }
                }

                val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
                val categories = service.getVodCategories()
                categoryDao.insertAll(
                    categories.map {
                        XtreamCategoryEntity(
                            it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_VOD,
                            excluded = !providerSettings.categoryFilters.shouldShowCategory(it.categoryName),
                        )
                    },
                )
                categories.filter { providerSettings.categoryFilters.shouldShowCategory(it.categoryName) }
            }
        }

    suspend fun getSeriesCategories(): Result<List<XtreamCategory>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val dbEntities = categoryDao.getCategories(providerId, XtreamCategoryEntity.TYPE_SERIES)
                if (dbEntities.isNotEmpty()) {
                    if (!isCacheFresh(KEY_SERIES_CATEGORIES_TIMESTAMP)) {
                        syncCategories(XtreamCategoryEntity.TYPE_SERIES)
                    }
                    return@suspendResultOf dbEntities.map { XtreamCategory(it.categoryId, it.categoryName, it.parentId) }
                }

                val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
                val categories = service.getSeriesCategories()
                categoryDao.insertAll(
                    categories.map {
                        XtreamCategoryEntity(
                            it.categoryId, providerId, it.categoryName, it.parentId, XtreamCategoryEntity.TYPE_SERIES,
                            excluded = !providerSettings.categoryFilters.shouldShowCategory(it.categoryName),
                        )
                    },
                )
                categories.filter { providerSettings.categoryFilters.shouldShowCategory(it.categoryName) }
            }
        }

    suspend fun getAllStreams(type: String): Result<List<XtreamStream>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                val dbEntities = streamDao.getAllStreams(providerId, type)
                if (dbEntities.isNotEmpty()) {
                    val key = KEY_STREAMS_TIMESTAMP_PREFIX + type + "_ALL"
                    if (!isCacheFresh(key)) {
                        syncStreams(type)
                    }
                    return@suspendResultOf dbEntities.map { mapStreamEntityToModel(it) }
                }

                val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
                val streamsRaw =
                    when (type) {
                        XtreamStreamEntity.TYPE_LIVE -> service.getStreams()
                        XtreamStreamEntity.TYPE_VOD -> service.getVodStreams()
                        else -> emptyList()
                    }

                val filters = providerSettings.categoryFilters
                val excludedCategoryIds: Set<String> =
                    if (filters.rules.isEmpty() && filters.allowedScripts.isEmpty()) {
                        emptySet()
                    } else {
                        val categories =
                            when (type) {
                                XtreamStreamEntity.TYPE_LIVE -> service.getCategories()
                                XtreamStreamEntity.TYPE_VOD -> service.getVodCategories()
                                else -> emptyList()
                            }
                        categories.filterNot { filters.shouldShowCategory(it.categoryName) }.map { it.categoryId }.toSet()
                    }

                streamDao.insertAll(
                    streamsRaw.map {
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
                            tvArchiveDuration = it.tvArchiveDuration,
                            description = it.description.asString(),
                            cast = it.cast,
                            director = it.director,
                            genre = it.genre,
                            releaseDate = it.releaseDate,
                            rating = it.rating.asString(),
                            duration = it.duration.asString(),
                            youtubeTrailer = it.youtubeTrailer,
                            excluded = it.categoryId in excludedCategoryIds,
                        )
                    },
                )

                streamsRaw.filterNot { it.categoryId in excludedCategoryIds }
            }
        }

    suspend fun getStreams(
        categoryId: String,
        forSearch: Boolean = false,
    ): Result<List<XtreamStream>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                if (forSearch) {
                    val ftsQuery = formatFtsQuery(categoryId)
                    return@suspendResultOf if (ftsQuery.isEmpty()) {
                        emptyList()
                    } else {
                        streamDao.searchByFts(providerId, XtreamStreamEntity.TYPE_LIVE, ftsQuery, false)
                            .map { mapStreamEntityToModel(it) }
                    }
                }

                val dbEntities = streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId)

                if (dbEntities.isNotEmpty()) {
                    val key = KEY_STREAMS_TIMESTAMP_PREFIX + "LIVE_ALL"
                    if (!isCacheFresh(key)) {
                        syncStreams(XtreamStreamEntity.TYPE_LIVE)
                    }
                    return@suspendResultOf dbEntities.map { mapStreamEntityToModel(it) }
                }

                val service = sessionManager.apiService ?: throw Exception("Not authenticated. Please login first.")
                val streams = service.getStreams(categoryId)
                val categoryExcluded =
                    categoryDao.getAllCategoriesIncludingExcluded(providerId, XtreamCategoryEntity.TYPE_LIVE)
                        .firstOrNull { it.categoryId == categoryId }?.excluded ?: false

                streamDao.insertAll(
                    streams.map {
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
                            tvArchiveDuration = it.tvArchiveDuration,
                            description = it.description.asString(),
                            cast = it.cast,
                            director = it.director,
                            genre = it.genre,
                            releaseDate = it.releaseDate,
                            rating = it.rating.asString(),
                            duration = it.duration.asString(),
                            youtubeTrailer = it.youtubeTrailer,
                            excluded = categoryExcluded,
                        )
                    },
                )

                streams
            }
        }

    suspend fun getVodStreams(
        categoryId: String,
        forSearch: Boolean = false,
    ): Result<List<XtreamStream>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                if (forSearch) {
                    val ftsQuery = formatFtsQuery(categoryId)
                    return@suspendResultOf if (ftsQuery.isEmpty()) {
                        emptyList()
                    } else {
                        streamDao.searchByFts(providerId, XtreamStreamEntity.TYPE_VOD, ftsQuery, false)
                            .map { mapStreamEntityToModel(it) }
                    }
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
                val categoryExcluded =
                    categoryDao.getAllCategoriesIncludingExcluded(providerId, XtreamCategoryEntity.TYPE_VOD)
                        .firstOrNull { it.categoryId == categoryId }?.excluded ?: false

                streamDao.insertAll(
                    streams.map {
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
                            tvArchiveDuration = it.tvArchiveDuration,
                            description = it.description.asString(),
                            cast = it.cast,
                            director = it.director,
                            genre = it.genre,
                            releaseDate = it.releaseDate,
                            rating = it.rating.asString(),
                            duration = it.duration.asString(),
                            youtubeTrailer = it.youtubeTrailer,
                            excluded = categoryExcluded,
                            tmdbId = it.tmdb.asString(),
                        )
                    },
                )

                streams
            }
        }

    suspend fun getSeries(
        categoryId: String,
        forSearch: Boolean = false,
    ): Result<List<XtreamStream>> =
        withContext(Dispatchers.IO) {
            suspendResultOf {
                if (forSearch) {
                    val ftsQuery = formatFtsQuery(categoryId)
                    return@suspendResultOf if (ftsQuery.isEmpty()) {
                        emptyList()
                    } else {
                        seriesDao.searchByFts(providerId, ftsQuery, false).map { mapSeriesEntityToStream(it) }
                    }
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
                val categoryExcluded =
                    categoryDao.getAllCategoriesIncludingExcluded(providerId, XtreamCategoryEntity.TYPE_SERIES)
                        .firstOrNull { it.categoryId == categoryId }?.excluded ?: false

                seriesDao.insertAll(
                    seriesList.map {
                        XtreamSeriesEntity(
                            seriesId = it.seriesId,
                            providerId = providerId,
                            num = it.num,
                            name = it.name,
                            cover = it.cover,
                            plot = it.plot.asString(),
                            cast = it.cast,
                            director = it.director,
                            genre = it.genre,
                            releaseDate = it.releaseDate,
                            lastModified = it.lastModified,
                            rating = it.rating.asString(),
                            rating5based = it.rating5based,
                            youtubeTrailer = it.youtubeTrailer,
                            episodeRunTime = it.episodeRunTime.asString(),
                            categoryId = it.categoryId,
                            excluded = categoryExcluded,
                            tmdbId = it.tmdb.asString(),
                        )
                    },
                )

                seriesList.map { series ->
                    XtreamStream(
                        num = series.num ?: 0,
                        name = series.name,
                        streamType = "series",
                        streamId = series.seriesId,
                        streamIcon = series.cover,
                        epgChannelId = null,
                        added = series.releaseDate,
                        categoryId = series.categoryId,
                        customSid = null,
                        tvArchive = 0,
                        directSource = null,
                        tvArchiveDuration = 0,
                    )
                }
            }
        }

    suspend fun syncCategories(type: String): Deferred<Unit> {
        val task =
            object : RefreshTask {
                override val id = "xtream_categories_${providerId}_$type"
                override val priority = RefreshPriority.HIGH

                override suspend fun execute() {
                    val service = sessionManager.apiService ?: return
                    try {
                        val categories =
                            when (type) {
                                XtreamCategoryEntity.TYPE_LIVE -> service.getCategories()
                                XtreamCategoryEntity.TYPE_VOD -> service.getVodCategories()
                                XtreamCategoryEntity.TYPE_SERIES -> service.getSeriesCategories()
                                else -> emptyList()
                            }

                        val entities =
                            categories.map {
                                XtreamCategoryEntity(
                                    categoryId = it.categoryId,
                                    providerId = providerId,
                                    categoryName = it.categoryName,
                                    parentId = it.parentId,
                                    type = type,
                                    contentHash =
                                        XtreamCategoryEntity.computeHash(
                                            it.categoryId,
                                            providerId,
                                            it.categoryName,
                                            it.parentId,
                                            type,
                                        ),
                                    excluded = !providerSettings.categoryFilters.shouldShowCategory(it.categoryName),
                                )
                            }

                        val currentHashes = categoryDao.getCategoryHashes(providerId, type)
                        val seenIds = entities.map { it.categoryId }.toSet()

                        if (seenIds.isEmpty() && currentHashes.isNotEmpty()) {
                            android.util.Log.w(
                                TAG,
                                "syncCategories($type): server returned 0 categories but ${currentHashes.size} exist locally — treating as a failed sync, not deleting",
                            )
                            return
                        }

                        val toDeleteIds = currentHashes.keys.filter { it !in seenIds }
                        var inserted = 0
                        var updated = 0
                        val toInsert =
                            entities.filter { newEntity ->
                                val oldHash = currentHashes[newEntity.categoryId]
                                val changed = oldHash == null || oldHash != newEntity.contentHash
                                if (changed) {
                                    if (oldHash == null) inserted++ else updated++
                                }
                                changed
                            }

                        if (toDeleteIds.isNotEmpty()) {
                            toDeleteIds.toList().chunked(SQLITE_DELETE_BATCH_SIZE).forEach { chunk ->
                                categoryDao.deleteByIds(providerId, type, chunk)
                            }
                        }
                        if (toInsert.isNotEmpty()) {
                            categoryDao.insertAll(toInsert)
                        }
                        addDelta(SyncDelta(inserted = inserted, updated = updated, deleted = toDeleteIds.size))

                        val key =
                            when (type) {
                                XtreamCategoryEntity.TYPE_LIVE -> KEY_CATEGORIES_TIMESTAMP
                                XtreamCategoryEntity.TYPE_VOD -> KEY_VOD_CATEGORIES_TIMESTAMP
                                XtreamCategoryEntity.TYPE_SERIES -> KEY_SERIES_CATEGORIES_TIMESTAMP
                                else -> ""
                            }
                        if (key.isNotEmpty()) {
                            commitAsync { putLong(key, System.currentTimeMillis()) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("XtreamContentManager", "Error syncing data", e)
                    }
                }
            }
        return RefreshQueue.submit(task)
    }

    suspend fun syncStreams(type: String): Deferred<Unit> {
        val task =
            object : RefreshTask {
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
                            var inserted = 0
                            var updated = 0

                            val filters = providerSettings.categoryFilters
                            val allowedCategoryIds: Set<String>? =
                                if (filters.rules.isEmpty() && filters.allowedScripts.isEmpty()) {
                                    null
                                } else {
                                    val categories =
                                        when (type) {
                                            XtreamStreamEntity.TYPE_LIVE -> service.getCategories()
                                            XtreamStreamEntity.TYPE_VOD -> service.getVodCategories()
                                            else -> emptyList()
                                        }
                                    categories.filter { filters.shouldShowCategory(it.categoryName) }.map { it.categoryId }.toSet()
                                }

                            val onStreamItem: suspend (XtreamStream) -> Unit = { it ->
                                val itemExcluded = allowedCategoryIds != null && it.categoryId !in allowedCategoryIds
                                val tmdbId = it.tmdb.asString()
                                val contentHash =
                                    XtreamStreamEntity.computeHash(
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
                                        description = it.description.asString(),
                                        cast = it.cast,
                                        director = it.director,
                                        genre = it.genre,
                                        releaseDate = it.releaseDate,
                                        rating = it.rating.asString(),
                                        duration = it.duration.asString(),
                                        youtubeTrailer = it.youtubeTrailer,
                                        tmdbId = tmdbId,
                                    )

                                seenIds.add(it.streamId)
                                val oldHash = currentHashes[it.streamId]
                                if (oldHash == null || oldHash != contentHash) {
                                    if (oldHash == null) inserted++ else updated++
                                    batch.add(
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
                                            tvArchiveDuration = it.tvArchiveDuration,
                                            description = it.description.asString(),
                                            cast = it.cast,
                                            director = it.director,
                                            genre = it.genre,
                                            releaseDate = it.releaseDate,
                                            rating = it.rating.asString(),
                                            duration = it.duration.asString(),
                                            youtubeTrailer = it.youtubeTrailer,
                                            contentHash = contentHash,
                                            excluded = itemExcluded,
                                            tmdbId = tmdbId,
                                        ),
                                    )
                                    if (batch.size >= BATCH_SIZE) {
                                        // ⚡ Bolt: Pass the mutable list directly without .toList() to avoid allocating a new list of 2000 elements
                                        streamDao.insertAll(batch)
                                        batch.clear()
                                    }
                                }
                            }

                            if (type == XtreamStreamEntity.TYPE_LIVE) {
                                service.getStreamsStreaming(null, onStreamItem)
                            } else {
                                service.getVodStreamsStreaming(null, onStreamItem)
                            }

                            if (seenIds.isEmpty() && currentHashes.isNotEmpty()) {
                                android.util.Log.w(
                                    TAG,
                                    "syncStreams($type): server returned 0 streams but ${currentHashes.size} exist locally — treating as a failed sync, not deleting",
                                )
                                return@coroutineScope
                            }

                            if (batch.isNotEmpty()) {
                                streamDao.insertAll(batch)
                            }

                            // Cleanup deleted
                            val allIds = streamDao.getStreamIds(providerId, type)
                            val toDelete = allIds.filter { it !in seenIds }
                            if (toDelete.isNotEmpty()) {
                                toDelete.chunked(SQLITE_DELETE_BATCH_SIZE).forEach { chunk ->
                                    streamDao.deleteByIds(providerId, type, chunk)
                                }
                            }
                            addDelta(SyncDelta(inserted = inserted, updated = updated, deleted = toDelete.size))

                            streamDao.rebuildFts()

                            commitAsync { putLong(KEY_STREAMS_TIMESTAMP_PREFIX + type, System.currentTimeMillis()) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("XtreamContentManager", "Error syncing streams", e)
                    }
                }
            }
        return RefreshQueue.submit(task)
    }

    suspend fun syncSeries(): Deferred<Unit> {
        val task =
            object : RefreshTask {
                override val id = "xtream_series_$providerId"
                override val priority = RefreshPriority.LOW

                override suspend fun execute() {
                    val service = sessionManager.apiService ?: return
                    try {
                        coroutineScope {
                            val batch = mutableListOf<XtreamSeriesEntity>()
                            val BATCH_SIZE = 1000
                            val currentHashes = seriesDao.getSeriesHashes(providerId)
                            val seenIds = mutableSetOf<Int>()
                            var inserted = 0
                            var updated = 0

                            val filters = providerSettings.categoryFilters
                            val allowedCategoryIds: Set<String>? =
                                if (filters.rules.isEmpty() && filters.allowedScripts.isEmpty()) {
                                    null
                                } else {
                                    service.getSeriesCategories()
                                        .filter { filters.shouldShowCategory(it.categoryName) }
                                        .map { it.categoryId }
                                        .toSet()
                                }

                            service.getSeriesStreaming(null) { it ->
                                val itemExcluded = allowedCategoryIds != null && it.categoryId !in allowedCategoryIds
                                val tmdbId = it.tmdb.asString()
                                val contentHash =
                                    XtreamSeriesEntity.computeHash(
                                        seriesId = it.seriesId,
                                        providerId = providerId,
                                        num = it.num,
                                        name = it.name,
                                        cover = it.cover,
                                        plot = it.plot.asString(),
                                        cast = it.cast,
                                        director = it.director,
                                        genre = it.genre,
                                        releaseDate = it.releaseDate,
                                        lastModified = it.lastModified,
                                        rating = it.rating.asString(),
                                        rating5based = it.rating5based,
                                        youtubeTrailer = it.youtubeTrailer,
                                        episodeRunTime = it.episodeRunTime.asString(),
                                        categoryId = it.categoryId,
                                        backdropPath = it.backdropPath?.joinToString(","),
                                        tmdbId = tmdbId,
                                    )
                                seenIds.add(it.seriesId)
                                val oldHash = currentHashes[it.seriesId]
                                if (oldHash == null || oldHash != contentHash) {
                                    if (oldHash == null) inserted++ else updated++
                                    batch.add(
                                        XtreamSeriesEntity(
                                            seriesId = it.seriesId,
                                            providerId = providerId,
                                            num = it.num,
                                            name = it.name,
                                            cover = it.cover,
                                            plot = it.plot.asString(),
                                            cast = it.cast,
                                            director = it.director,
                                            genre = it.genre,
                                            releaseDate = it.releaseDate,
                                            lastModified = it.lastModified,
                                            rating = it.rating.asString(),
                                            rating5based = it.rating5based,
                                            youtubeTrailer = it.youtubeTrailer,
                                            episodeRunTime = it.episodeRunTime.asString(),
                                            categoryId = it.categoryId,
                                            backdropPath = it.backdropPath?.joinToString(","),
                                            contentHash = contentHash,
                                            excluded = itemExcluded,
                                            tmdbId = tmdbId,
                                        ),
                                    )
                                    if (batch.size >= BATCH_SIZE) {
                                        // ⚡ Bolt: Pass the mutable list directly without .toList() to avoid allocating a new list of 1000 elements
                                        seriesDao.insertAll(batch)
                                        batch.clear()
                                    }
                                }
                            }

                            if (seenIds.isEmpty() && currentHashes.isNotEmpty()) {
                                android.util.Log.w(
                                    TAG,
                                    "syncSeries(): server returned 0 series but ${currentHashes.size} exist locally — treating as a failed sync, not deleting",
                                )
                                return@coroutineScope
                            }

                            if (batch.isNotEmpty()) {
                                seriesDao.insertAll(batch)
                            }

                            val allIds = seriesDao.getSeriesIds(providerId)
                            val toDelete = allIds.filter { it !in seenIds }
                            if (toDelete.isNotEmpty()) {
                                toDelete.chunked(SQLITE_DELETE_BATCH_SIZE).forEach { chunk ->
                                    seriesDao.deleteByIds(providerId, chunk)
                                }
                            }
                            addDelta(SyncDelta(inserted = inserted, updated = updated, deleted = toDelete.size))

                            seriesDao.rebuildFts()

                            commitAsync { putLong(KEY_STREAMS_TIMESTAMP_PREFIX + "SERIES", System.currentTimeMillis()) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("XtreamContentManager", "Error syncing series", e)
                    }
                }
            }
        return RefreshQueue.submit(task)
    }

    /**
     * Fetches a series' episode list, persisting what came back. The classification of the answer
     * is the API layer's ([XtreamResponse]); this only adds the side effects, and only for a
     * response that actually carried something.
     */
    suspend fun getSeriesInfo(seriesId: Int): XtreamResponse<SeriesInfo> =
        withContext(Dispatchers.IO) {
            try {
                val service =
                    sessionManager.apiService
                        ?: return@withContext XtreamResponse.Failed(Exception("Not authenticated. Please login first."))

                val startTime = System.currentTimeMillis()
                val response = service.getSeriesInfo(seriesId)
                val fetchTime = System.currentTimeMillis() - startTime
                metricsManager.trackFetchTime("series_$seriesId", fetchTime)

                val seriesInfo = (response as? XtreamResponse.Ok)?.value ?: return@withContext response

                // Update series metadata
                val info = seriesInfo.info
                if (info != null) {
                    seriesDao.getSeriesById(providerId, seriesId)?.let { existing ->
                        seriesDao.insertAll(
                            listOf(
                                existing.copy(
                                    plot = info.plot.asString(),
                                    cast = info.cast,
                                    director = info.director,
                                    genre = info.genre,
                                    releaseDate = info.releaseDate,
                                    rating = info.rating.asString(),
                                    rating5based = info.rating5based,
                                    youtubeTrailer = info.youtubeTrailer,
                                    episodeRunTime = info.episodeRunTime.asString(),
                                    backdropPath = info.backdropPath?.joinToString(","),
                                ),
                            ),
                        )
                    }
                }

                // Persist episodes for AI vectorization
                val episodesToInsert = mutableListOf<XtreamEpisodeEntity>()
                seriesInfo.episodes.forEach { (seasonNum, episodes) ->
                    val sNum = seasonNum.toIntOrNull()
                    episodes.forEach { ep ->
                        episodesToInsert.add(
                            XtreamEpisodeEntity(
                                id = ep.id,
                                seriesId = seriesId,
                                providerId = providerId,
                                season = sNum ?: ep.season,
                                episodeNum = ep.episodeNum,
                                title = ep.title,
                                containerExtension = ep.containerExtension,
                                overview = ep.info?.overview.asString(),
                                plot = ep.info?.plot.asString(),
                                airDate = ep.info?.airDate,
                                duration = ep.info?.duration.asString(),
                                durationSecs = ep.info?.durationSecs,
                                bitrate = ep.info?.bitrate,
                                rating = ep.info?.rating.asString(),
                                movieImage = ep.info?.movieImage ?: ep.info?.coverBig,
                                tmdbId = ep.info?.tmdbId,
                            ),
                        )
                    }
                }
                if (episodesToInsert.isNotEmpty()) {
                    episodeDao.insertAll(guardSeriesLevelEpisodeTmdbIds(episodesToInsert))
                }

                response
            } catch (e: Exception) {
                XtreamResponse.Failed(e)
            }
        }

    /** Fetches a movie's details, persisting what came back. See [getSeriesInfo]. */
    suspend fun getVodInfo(vodId: Int): XtreamResponse<VodInfo> =
        withContext(Dispatchers.IO) {
            try {
                val service =
                    sessionManager.apiService
                        ?: return@withContext XtreamResponse.Failed(Exception("Not authenticated. Please login first."))
                val startTime = System.currentTimeMillis()
                val response = service.getVodInfo(vodId)
                val fetchTime = System.currentTimeMillis() - startTime
                metricsManager.trackFetchTime("vod_$vodId", fetchTime)

                val vodInfo = (response as? XtreamResponse.Ok)?.value ?: return@withContext response

                // Update movie metadata for AI and UI
                val info = vodInfo.info
                if (info != null) {
                    streamDao.updateVodMetadata(
                        providerId = providerId,
                        streamId = vodId,
                        type = XtreamStreamEntity.TYPE_VOD,
                        description = info.plot.asString(),
                        cast = info.cast,
                        director = info.director,
                        genre = info.genre,
                        releaseDate = info.releaseDate,
                        rating = info.rating.asString(),
                        duration = info.duration.asString(),
                        youtubeTrailer = null, // MovieInfo doesn't seem to have trailer, but SeriesInfo does
                    )
                }

                response
            } catch (e: Exception) {
                XtreamResponse.Failed(e)
            }
        }

    /** Cached movie row, including any persisted TMDB content rating — no network call. */
    suspend fun getCachedMovieDetail(vodId: Int): XtreamStreamEntity? =
        withContext(Dispatchers.IO) { streamDao.getStreamById(providerId, vodId) }

    /** Persists the TMDB/full-detail fields once a movie detail fetch has completed. */
    suspend fun saveMovieDetailCache(
        vodId: Int,
        contentRating: String?,
        tmdbId: String?,
        containerExtension: String?,
        fetchedAt: Long,
    ) = withContext(Dispatchers.IO) {
        streamDao.updateDetailCache(providerId, vodId, XtreamStreamEntity.TYPE_VOD, contentRating, tmdbId, containerExtension, fetchedAt)
    }

    /**
     * Re-syncs the series catalogue and returns the id the provider currently uses for [name], or
     * null if it isn't there any more. Recovery path for a stored id the provider has stopped
     * recognising — ids change when a provider rebuilds its catalogue.
     */
    suspend fun resolveSeriesIdByName(name: String): Int? =
        withContext(Dispatchers.IO) {
            runCatching { syncSeries().await() }
            seriesDao
                .getAllSeries(providerId)
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.seriesId
        }

    /** Cached series row, used only to check for a still-fresh persisted TMDB content rating — episode list is always fetched live. */
    suspend fun getCachedSeriesEntity(seriesId: Int): XtreamSeriesEntity? =
        withContext(Dispatchers.IO) { seriesDao.getSeriesById(providerId, seriesId) }

    /** Persists the TMDB content rating once a series detail fetch has completed. */
    suspend fun saveSeriesDetailCache(
        seriesId: Int,
        contentRating: String?,
        tmdbId: String?,
        fetchedAt: Long,
    ) = withContext(Dispatchers.IO) {
        seriesDao.updateDetailCache(providerId, seriesId, contentRating, tmdbId, fetchedAt)
    }

    /**
     * Episode plots already stored for [seriesId], keyed by Xtream episode id. These are the TMDB
     * overviews [persistEpisodeOverviews] backfilled on an earlier visit; reading them back is what
     * stops the season fetches repeating on every cold start.
     */
    suspend fun getPersistedEpisodePlots(seriesId: Int): Map<String, String> =
        withContext(Dispatchers.IO) {
            val oldest = System.currentTimeMillis() - OVERVIEW_CACHE_TTL_MS
            episodeDao
                .getEpisodes(providerId, seriesId)
                .mapNotNull { entity ->
                    val plot = entity.plot?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    // Plots that predate the timestamp column, or that have aged out, are treated as
                    // absent so the next visit refetches them.
                    val fetchedAt = entity.plotFetchedAt ?: return@mapNotNull null
                    if (fetchedAt >= oldest) entity.id to plot else null
                }.toMap()
        }

    /**
     * The series as it was last stored — series row plus every episode row — with no network call.
     *
     * Both halves are already written on every [getSeriesInfo]: the series row picks up the plot,
     * cast and genre, and every episode is inserted. Nothing read this back as a whole, so opening
     * a show after a restart waited on a fresh get_series_info before it could draw anything, which
     * on a series the size of Law & Order is a several-hundred-episode response.
     *
     * See [buildCachedSeriesDetail] for what a stored row does and doesn't carry.
     */
    suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail? =
        withContext(Dispatchers.IO) {
            val series = seriesDao.getSeriesById(providerId, seriesId) ?: return@withContext null
            buildCachedSeriesDetail(series, episodeDao.getEpisodes(providerId, seriesId))
        }

    /** Backfills episode plots that TMDB filled in (Xtream itself rarely provides episode synopses) — never overwrites an existing plot. */
    suspend fun persistEpisodeOverviews(episodes: Map<String, List<EpisodeItem>>) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            episodes.values.flatten().forEach { ep ->
                val plot = ep.metadata.plot
                if (!plot.isNullOrBlank()) {
                    episodeDao.updateOverviewIfBlank(providerId, ep.id, plot, now)
                }
            }
        }

    fun buildStreamUrl(
        streamId: Int,
        contentType: String = "LIVE_TV",
        extension: String? = null,
    ): Result<String> =
        resultOf {
            val service =
                sessionManager.apiService
                    ?: throw Exception("Not authenticated. Please login first.")
            when (contentType) {
                "LIVE_TV" -> service.buildStreamUrl(streamId)
                "MOVIES" -> service.buildVodStreamUrl(streamId, extension ?: "mp4")
                "TV_SHOWS" -> service.buildSeriesStreamUrl(streamId, extension ?: "mp4")
                else -> throw Exception("Unknown content type: $contentType")
            }
        }

    fun buildEpisodeStreamUrl(
        episodeId: String,
        extension: String,
    ): Result<String> =
        resultOf {
            val service = sessionManager.apiService ?: throw Exception("Not authenticated")
            service.buildSeriesStreamUrl(episodeId.toInt(), extension)
        }

    suspend fun getStreamName(
        streamId: Int,
        contentType: String,
    ): String? =
        withContext(Dispatchers.IO) {
            when (contentType) {
                "LIVE_TV" -> streamDao.getStreamById(providerId, streamId)?.name
                "MOVIES" -> streamDao.getStreamById(providerId, streamId)?.name
                "TV_SHOWS" -> seriesDao.getSeriesById(providerId, streamId)?.name
                else -> null
            }
        }

    fun getStreamsCached(categoryId: String): List<XtreamStream>? {
        val dbEntities = streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_LIVE, categoryId)
        return if (dbEntities.isEmpty()) null else dbEntities.map { mapStreamEntityToModel(it) }
    }

    fun getVodStreamsCached(categoryId: String): List<XtreamStream>? {
        val dbEntities = streamDao.getStreamsByCategory(providerId, XtreamStreamEntity.TYPE_VOD, categoryId)
        return if (dbEntities.isEmpty()) null else dbEntities.map { mapStreamEntityToModel(it) }
    }

    fun getSeriesCached(categoryId: String): List<XtreamStream>? {
        val dbEntities = seriesDao.getSeriesByCategory(providerId, categoryId)
        return if (dbEntities.isEmpty()) null else dbEntities.map { mapSeriesEntityToStream(it) }
    }

    /** Other VOD entries carrying the same TMDB id, local catalogue only — see [XtreamStreamDao.getByTmdbId]. */
    suspend fun getAlternateVodStreams(
        tmdbId: String,
        excludeStreamId: Int,
    ): List<XtreamStream> =
        withContext(Dispatchers.IO) {
            streamDao.getByTmdbId(providerId, XtreamStreamEntity.TYPE_VOD, tmdbId, excludeStreamId)
                .map { mapStreamEntityToModel(it) }
        }

    /** As [getAlternateVodStreams], for series. */
    suspend fun getAlternateSeries(
        tmdbId: String,
        excludeSeriesId: Int,
    ): List<XtreamStream> =
        withContext(Dispatchers.IO) {
            seriesDao.getByTmdbId(providerId, tmdbId, excludeSeriesId).map { mapSeriesEntityToStream(it) }
        }

    private fun isCacheFresh(key: String): Boolean {
        val ts = sharedPreferences.getLong(key, 0L)
        return (System.currentTimeMillis() - ts) < CACHE_EXPIRATION_MS
    }

    private fun mapStreamEntityToModel(it: XtreamStreamEntity) =
        XtreamStream(
            num = it.num,
            name = it.name,
            streamType = it.streamType,
            streamId = it.streamId,
            streamIcon = it.streamIcon,
            epgChannelId = it.epgChannelId,
            added = it.added,
            categoryId = it.categoryId,
            customSid = it.customSid,
            tvArchive = it.tvArchive,
            directSource = it.directSource,
            tvArchiveDuration = it.tvArchiveDuration,
            description = it.description.toJsonPrimitive(),
            cast = it.cast,
            director = it.director,
            genre = it.genre,
            releaseDate = it.releaseDate,
            rating = it.rating.toJsonPrimitive(),
            duration = it.duration.toJsonPrimitive(),
            youtubeTrailer = it.youtubeTrailer,        )

    private fun mapSeriesEntityToStream(it: XtreamSeriesEntity) =
        XtreamStream(
            num = it.num ?: 0,
            name = it.name,
            streamType = "series",
            streamId = it.seriesId,
            streamIcon = it.cover,
            epgChannelId = null,
            added = it.releaseDate,
            categoryId = it.categoryId,
            customSid = null,
            tvArchive = 0,
            directSource = null,
            tvArchiveDuration = 0,
            description = it.plot.toJsonPrimitive(),
            cast = it.cast,
            director = it.director,
            genre = it.genre,
            releaseDate = it.releaseDate,
            rating = it.rating.toJsonPrimitive(),
            duration = it.episodeRunTime.toJsonPrimitive(),
            youtubeTrailer = it.youtubeTrailer,
        )

    suspend fun searchStreams(type: String, query: String, includeExcluded: Boolean = false): List<XtreamStream> =
        withContext(Dispatchers.IO) {
            streamDao.searchByFts(providerId, type, query, includeExcluded).map { mapStreamEntityToModel(it) }
        }

    suspend fun searchSeries(query: String, includeExcluded: Boolean = false): List<XtreamStream> =
        withContext(Dispatchers.IO) {
            seriesDao.searchByFts(providerId, query, includeExcluded).map { mapSeriesEntityToStream(it) }
        }

    /** How many matches for [query] are hidden because their category is excluded. */
    suspend fun countExcludedStreams(type: String, query: String): Int =
        withContext(Dispatchers.IO) {
            streamDao.countExcludedByFts(providerId, type, query)
        }

    suspend fun countExcludedSeries(query: String): Int =
        withContext(Dispatchers.IO) {
            seriesDao.countExcludedByFts(providerId, query)
        }

    suspend fun recomputeExclusions() = withContext(Dispatchers.IO) {
        XtreamCategoryExclusionSync.recompute(categoryDao, streamDao, seriesDao, providerId, providerSettings.categoryFilters)
    }

    /** Total category count for [type], including excluded ones — for "X of Y" style UI counts. */
    suspend fun getCategoryTotalCount(type: String): Int =
        withContext(Dispatchers.IO) {
            categoryDao.countCategories(providerId, type)
        }

    private fun formatFtsQuery(query: String): String {
        val words = query.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() && !it.startsWith("-") }
        // Sanitize input to prevent SQLite FTS syntax errors (like **) that trigger fallback hangs
        val ftsQuery = words.map { it.replace(Regex("[*\"'()\\^]"), "") }
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        return ftsQuery
    }

    /**
     * Episode count per series id, from the local cache only — one grouped query, no network.
     * Feeds the series-row watch bar's denominator; series never opened aren't in the cache and
     * are simply absent from the map.
     */
    suspend fun getEpisodeCountsBySeries(): Map<String, Int> =
        withContext(Dispatchers.IO) {
            episodeDao
                .countEpisodesBySeries(providerId)
                .mapKeys { (seriesId, _) -> seriesId.toString() }
        }
}
