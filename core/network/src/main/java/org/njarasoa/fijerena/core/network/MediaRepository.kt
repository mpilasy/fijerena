package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.cancel
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xmltv.XmltvEpgService
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.PlaybackStatus
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WatchedItem(
    val itemId: String,
    val itemName: String,
    val categoryId: String,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val playbackPosition: Long = 0L,
    val duration: Long = 0L,
    val isCompleted: Boolean = false,
    val episodeId: EpisodeId? = null,
    val episodeExtension: String? = null,
    val seriesId: SeriesId? = null,
    val seriesName: String? = null,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
)

/**
 * Fraction watched (0..1) when this item is far enough in to be worth resuming but
 * not close enough to the end to count as done, else null. Same band as
 * [MediaRepository.getRecentItems], so anything that draws a progress bar is
 * exactly what the detail screen offers a Resume button for.
 */
fun WatchedItem.resumeProgress(): Float? {
    if (isCompleted || playbackPosition <= 0L || duration <= 0L) return null
    val fraction = playbackPosition.toFloat() / duration.toFloat()
    return if (fraction * 100f in 2.0..95.0) fraction else null
}

/**
 * `watch_state` row as a [WatchedItem], for callers that still speak the blob-era shape.
 * `timestamp` becomes `lastPlayedAt` — the value [EpisodeSelectionScreen]'s "most recently played"
 * lookup and the Recent row both actually mean — falling back to `updatedAt` for a Phase 6 row
 * whose completion was set without ever playing anything.
 */
private fun WatchStateEntity.toWatchedItem(): WatchedItem =
    WatchedItem(
        itemId = itemId,
        itemName = itemName,
        categoryId = categoryId,
        contentType = contentType,
        timestamp = lastPlayedAt ?: updatedAt,
        playbackPosition = positionMs,
        duration = durationMs,
        isCompleted = isCompleted,
        episodeId = episodeId?.let { EpisodeId(it) },
        episodeExtension = episodeExtension,
        seriesId = seriesId?.let { SeriesId(it) },
        seriesName = seriesName,
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
    )

@Serializable
data class FavoriteItem(
    val itemId: String,
    val itemName: String,
    val categoryId: String,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class FavoriteCategoryItem(
    val categoryId: String,
    val categoryName: String,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class RecentCategory(
    val categoryId: String,
    val categoryName: String,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class MediaRepository(
    private val context: Context,
    private val providerId: Long,
    private val providerSettings: ProviderSettings = ProviderSettings.DEFAULT,
    private val watchStateDao: WatchStateDao = XtreamDatabase.getInstance(context).watchStateDao(),
) : Closeable {
    @Volatile
    private var provider: MediaProvider? = null

    private val cacheName = "media_cache_$providerId"
    private val cache: SharedPreferences by lazy {
        context.getSharedPreferences(
            cacheName,
            Context.MODE_PRIVATE,
        )
    }
    private val appSettings = AppSettings(context) // Keep for global settings (isDevMode)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val xmltvEpgService: XmltvEpgService by lazy {
        XmltvEpgService(context, providerId)
    }

    private val payloadSizes = ConcurrentHashMap<String, Long>()
    private val fetchTimes = ConcurrentHashMap<String, Long>()

    // Keyed by content type; see recentItems().
    private val recentItemsFlows = ConcurrentHashMap<String, MutableStateFlow<List<MediaItem>?>>()

    // In-memory caches to avoid repeated JSON deserialization from SharedPreferences
    private var cachedWatchHistory: List<WatchedItem>? = null

    private var cachedFavorites: List<FavoriteItem>? = null
    private var cachedFavoriteCategories: List<FavoriteCategoryItem>? = null

    // O(1) lookup sets for isFavorite/isFavoriteCategory — rebuilt when lists change
    private var favoriteIdSet: Set<Pair<String, String>>? = null
    private var favoriteCategoryIdSet: Set<Pair<String, String>>? = null
    private val favoriteLock = Any()
    private val watchHistoryLock = Any()
    private var watchHistoryDirty = false
    private val watchHistoryWriteThread = android.os.HandlerThread("WatchHistoryWriter").apply { start() }
    private val watchHistoryWriteHandler = android.os.Handler(watchHistoryWriteThread.looper)
    private val watchHistoryWriteRunnable = Runnable { flushWatchHistory() }

    // Dedicated single-thread dispatcher for prefs writes below. commit() (not apply()) blocks
    // only this background thread until its own write finishes, so it never leaves anything
    // pending in the process-wide QueuedWork backlog. apply() returns immediately but registers
    // the write there; a later, unrelated Service dispatch (startService() -> ActivityThread.
    // handleServiceArgs) synchronously drains that whole backlog on the MAIN thread before
    // running — a well-known ANR trap when writes pile up faster than they drain. In-memory
    // caches are updated synchronously on the caller's thread before the write is dispatched,
    // so reads stay consistent regardless of when the background commit actually lands.
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private fun SharedPreferences.commitAsync(action: SharedPreferences.Editor.() -> Unit) {
        val prefs = this
        writeScope.launch { prefs.edit(commit = true, action = action) }
    }

    companion object {
        private const val KEY_WATCH_HISTORY = "watch_history_v3"
        private const val KEY_WATCH_HISTORY_V2 = "watch_history_v2"
        private const val KEY_WATCH_STATE_MIGRATED = "watch_state_migrated_v1"
        private const val KEY_FAVORITES = "favorites_v2"
        private const val KEY_FAVORITE_CATEGORIES = "favorite_categories"
        private const val KEY_RECENT_CATEGORIES = "recent_categories"
        private const val MAX_RECENT_CATEGORIES = 20

        private const val KEY_LAST_LIVE_CATEGORY = "last_live_category"
        private const val KEY_LAST_LIVE_ITEM = "last_live_item"
        private const val KEY_LAST_MOVIES_CATEGORY = "last_movies_category"
        private const val KEY_LAST_MOVIES_ITEM = "last_movies_item"
        private const val KEY_LAST_TVSHOWS_CATEGORY = "last_tvshows_category"
        private const val KEY_LAST_TVSHOWS_ITEM = "last_tvshows_item"
        private const val KEY_LAST_CONTENT_TYPE = "last_content_type"
    }

    private val usesServerUserData: Boolean
        get() = provider?.capabilities?.supportsServerUserData == true

    fun setProvider(mediaProvider: MediaProvider) {
        provider = mediaProvider
        backfillWatchState()
    }

    /**
     * One-time per-provider copy of `watch_history_v3` into `watch_state`, so Phase 3 reads have
     * something to read once the blob is retired. Guarded exactly like
     * [org.njarasoa.fijerena.core.network.xtream.manager.XtreamEpgManager]'s
     * `purgeLegacyPrefsCache`. Hooked off [setProvider] rather than the constructor — a
     * provider-less repository (a unit test, or the brief window in [org.njarasoa.fijerena.core
     * .network.provider] wiring before a provider is attached) is not a real "use" yet.
     *
     * Replay-safe: a crash between "rows written" and the flag commit just re-runs this and
     * re-upserts the same values, because every row goes through the same progress upsert
     * [savePlaybackPosition] uses rather than a raw insert — see Migration in
     * plans/watch-state-durable-storage-plan.md.
     */
    private fun backfillWatchState() {
        val alreadyMigrated = cache.getBoolean(KEY_WATCH_STATE_MIGRATED, false)
        if (!alreadyMigrated) {
            writeScope.launch {
                // Reads the blob directly, not the public getWatchHistory() — that flips to
                // reading watch_state in Phase 3, and would make this loop copy the table into
                // itself.
                val blobHistory = synchronized(watchHistoryLock) { getWatchHistoryLocked() }
                for (item in blobHistory) {
                    watchStateDao.upsertProgress(
                        providerId = providerId,
                        itemId = item.itemId,
                        contentType = item.contentType,
                        itemName = item.itemName,
                        categoryId = item.categoryId,
                        positionMs = item.playbackPosition,
                        durationMs = item.duration,
                        isCompleted = item.isCompleted,
                        now = item.timestamp,
                        seriesId = item.seriesId?.raw,
                        episodeId = item.episodeId?.raw,
                        seriesName = item.seriesName,
                        episodeExtension = item.episodeExtension,
                        audioTrackIndex = item.audioTrackIndex,
                        subtitleTrackIndex = item.subtitleTrackIndex,
                    )
                }
                cache.commitAsync { putBoolean(KEY_WATCH_STATE_MIGRATED, true) }
            }
        }
    }

    fun getProvider(): MediaProvider? = provider

    fun getCapabilities(): ProviderCapabilities? = provider?.capabilities

    fun getProviderSettings(): ProviderSettings = providerSettings

    fun getCategoryFilters(): CategoryFilters = providerSettings.categoryFilters

    fun isAutoResumeEnabled(): Boolean = providerSettings.autoResumeEnabled

    fun isCachingEnabled(): Boolean = providerSettings.cachingEnabled

    fun getProviderId(): Long = providerId

    // --- Provider-delegated operations ---

    suspend fun connect(): kotlin.Result<Unit> = provider?.connect() ?: kotlin.Result.failure(Exception("No provider set"))

    suspend fun disconnect() {
        provider?.disconnect()
    }

    fun isConnected(): Boolean = provider?.isConnected() == true

    suspend fun getCategories(contentType: String): kotlin.Result<List<MediaCategory>> =
        provider?.getCategories(contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))

    /**
     * Get categories filtered by provider's category filters.
     * If no filters are set, returns all categories.
     */
    suspend fun getFilteredCategories(contentType: String): kotlin.Result<List<MediaCategory>> {
        val result = getCategories(contentType)
        if (result.isFailure) return result

        val filters = providerSettings.categoryFilters
        if (filters.rules.isEmpty() && filters.allowedScripts.isEmpty()) return result

        // ScriptDetector.detectScript allocates a map and walks Character.UnicodeBlock per
        // category. With ~870 categories that is tens of milliseconds, and callers run on
        // Dispatchers.Main.immediate, so it landed on the UI thread at screen entry.
        return withContext(Dispatchers.Default) {
            result.map { categories ->
                categories.filter { category ->
                    filters.shouldShowCategory(category.name)
                }
            }
        }
    }

    suspend fun getItems(
        categoryId: String,
        contentType: String,
    ): kotlin.Result<List<MediaItem>> =
        provider?.getItems(categoryId, contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))

    fun getItemsIfCached(
        categoryId: String,
        contentType: String,
    ): List<MediaItem>? = provider?.getItemsIfCached(categoryId, contentType)

    suspend fun getItemsForSearch(
        categoryId: String,
        contentType: String,
    ): kotlin.Result<List<MediaItem>> =
        provider?.getItems(categoryId, contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))

    suspend fun getAllItems(contentType: String): kotlin.Result<List<MediaItem>> =
        provider?.getAllItems(contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))

    /** Forces the next detail read for [itemId] to go back to the provider — see MediaProvider. */
    suspend fun invalidateCachedDetail(itemId: String) {
        provider?.invalidateCachedDetail(itemId)
    }

    /** See [MediaProvider.getCachedSeriesDetail]. */
    suspend fun getCachedSeriesDetail(seriesId: SeriesId): SeriesDetail? = provider?.getCachedSeriesDetail(seriesId)

    suspend fun getSeriesDetail(seriesId: SeriesId): kotlin.Result<SeriesDetail> =
        provider?.getSeriesDetail(seriesId)
            ?: kotlin.Result.failure(Exception("No provider set"))

    suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> =
        provider?.getMovieDetail(movieId)
            ?: kotlin.Result.failure(Exception("No provider set"))

    suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String? = null,
        extension: String? = null,
    ): kotlin.Result<PlayableStream> =
        provider?.resolvePlayableStream(itemId, contentType, episodeId, extension)
            ?: kotlin.Result.failure(Exception("No provider set"))

    suspend fun search(
        query: String,
        contentType: String,
        includeExcluded: Boolean = false,
    ): kotlin.Result<List<MediaItem>>? = provider?.search(query, contentType, includeExcluded)

    /** See [MediaProvider.getRelatedTitles]. Either list is empty when its row should not appear. */
    suspend fun getRelatedTitles(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): RelatedTitles = provider?.getRelatedTitles(itemId, tmdbId, contentType) ?: RelatedTitles()

    /** See [MediaProvider.getTmdbTitle]. */
    suspend fun getTmdbTitle(
        tmdbId: String?,
        contentType: String,
    ): String? = provider?.getTmdbTitle(tmdbId, contentType)

    /** See [MediaProvider.getAlternateStreams]. */
    suspend fun getAlternateStreams(
        itemId: String,
        tmdbId: String?,
        contentType: String,
    ): List<MediaItem> = provider?.getAlternateStreams(itemId, tmdbId, contentType) ?: emptyList()

    /** Matches for [query] that search skipped because their category is hidden by category filters. */
    suspend fun countExcludedSearchMatches(
        query: String,
        contentType: String,
    ): Int = provider?.countExcludedSearchMatches(query, contentType) ?: 0

    fun getLastSearchDataSize(contentType: String): Long? = provider?.getLastSearchDataSize(contentType)

    suspend fun getEpg(streamId: String): kotlin.Result<EpgResponse>? = provider?.getEpg(streamId)

    suspend fun getEpgBulk(streamIds: List<String>): kotlin.Result<Map<String, EpgResponse>>? = provider?.getEpgBulk(streamIds)

    suspend fun clearEpgCache() {
        provider?.clearEpgCache()
    }

    suspend fun getEpgBulkForItems(items: List<MediaItem>): kotlin.Result<Map<String, EpgResponse>> {
        // Try XMLTV EPG from SQLite index
        try {
            val xmltvResult = xmltvEpgService.getEpgForChannels(items)
            // Check that at least one REQUESTED item has EPG data, not just cached leftovers
            if (items.any { xmltvResult.containsKey(it.id) }) {
                return kotlin.Result.success(xmltvResult)
            }
        } catch (_: Exception) {
            // Fall through to provider EPG
        }
        // Fallback to provider's native EPG
        val streamIds = items.map { it.id }
        return provider?.getEpgBulk(streamIds)
            ?: kotlin.Result.success(emptyMap())
    }

    fun clearXmltvCache() {
        xmltvEpgService.clearCache()
    }

    /**
     * Lightweight now-playing query from the EPG index.
     * Returns map of itemId → currently-airing EpgProgram.
     */
    suspend fun getNowPlayingFromIndex(items: List<MediaItem>): Map<String, EpgProgram> =
        try {
            xmltvEpgService.getNowPlayingForItems(items)
        } catch (_: Exception) {
            emptyMap()
        }

    /**
     * Check whether the SQLite EPG index has data available for search/display.
     */
    fun hasIndexedEpgData(): Boolean {
        val state = org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
            .getInstance(context)
            .state.value
        return state !is org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState.NotIndexed
    }

    // --- Progress sync hook ---

    suspend fun onPlaybackStarted(itemId: String) {
        provider?.onPlaybackStarted(itemId)
    }

    suspend fun onPlaybackProgress(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean = false,
    ) {
        provider?.onPlaybackProgress(itemId, positionMs, durationMs, isPaused)
    }

    suspend fun onPlaybackStopped(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        provider?.onPlaybackStopped(itemId, positionMs, durationMs)
    }

    // --- Local-only operations (favorites, watch history, playback progress) ---

    fun saveLastPlayedItem(
        categoryId: String,
        itemId: String,
        itemName: String,
        contentType: String,
        episodeId: EpisodeId? = null,
        episodeExtension: String? = null,
        seriesId: SeriesId? = null,
        seriesName: String? = null,
    ) {
        cache.commitAsync {
            when (contentType) {
                ContentType.LIVE_TV -> {
                    putString(KEY_LAST_LIVE_CATEGORY, categoryId)
                    putString(KEY_LAST_LIVE_ITEM, itemId)
                }
                ContentType.MOVIES -> {
                    putString(KEY_LAST_MOVIES_CATEGORY, categoryId)
                    putString(KEY_LAST_MOVIES_ITEM, itemId)
                }
                ContentType.TV_SHOWS -> {
                    putString(KEY_LAST_TVSHOWS_CATEGORY, categoryId)
                    putString(KEY_LAST_TVSHOWS_ITEM, itemId)
                }
            }
            putString(KEY_LAST_CONTENT_TYPE, contentType)
        }

        // ALWAYS save to local watch history as a robust fallback
        addToWatchHistory(
            itemId,
            itemName,
            categoryId,
            contentType,
            episodeId = episodeId,
            episodeExtension = episodeExtension,
            seriesId = seriesId,
            seriesName = seriesName,
        )

        // Dual write (Phase 2, plans/watch-state-durable-storage-plan.md): owns recency and
        // metadata only. Must not name positionMs/durationMs/isCompleted, so a start write can
        // never erase progress a later progress write already stored.
        val recencyNow = System.currentTimeMillis()
        writeScope.launch {
            watchStateDao.upsertRecency(
                providerId = providerId,
                itemId = itemId,
                contentType = contentType,
                itemName = itemName,
                categoryId = categoryId,
                now = recencyNow,
                seriesId = seriesId?.raw,
                episodeId = episodeId?.raw,
                seriesName = seriesName,
                episodeExtension = episodeExtension,
                audioTrackIndex = null,
                subtitleTrackIndex = null,
            )
        }
    }

    fun getLastCategoryId(contentType: String): String? =
        when (contentType) {
            ContentType.LIVE_TV -> cache.getString(KEY_LAST_LIVE_CATEGORY, null)
            ContentType.MOVIES -> cache.getString(KEY_LAST_MOVIES_CATEGORY, null)
            ContentType.TV_SHOWS -> cache.getString(KEY_LAST_TVSHOWS_CATEGORY, null)
            else -> null
        }

    fun getLastItemId(contentType: String): String? =
        when (contentType) {
            ContentType.LIVE_TV -> cache.getString(KEY_LAST_LIVE_ITEM, null)
            ContentType.MOVIES -> cache.getString(KEY_LAST_MOVIES_ITEM, null)
            ContentType.TV_SHOWS -> cache.getString(KEY_LAST_TVSHOWS_ITEM, null)
            else -> null
        }

    fun getLastContentType(): String? = cache.getString(KEY_LAST_CONTENT_TYPE, null)

    // --- Recent Categories ---

    // In-memory cache for recent categories — avoids JSON deserialization from SharedPreferences on every call
    private var cachedRecentCategories: MutableMap<String, List<RecentCategory>> = mutableMapOf()

    fun addToCategoryHistory(
        categoryId: String,
        categoryName: String,
        contentType: String,
    ) {
        val key = KEY_RECENT_CATEGORIES + "_" + contentType
        val existing = getRecentCategoryList(key)
        // Remove existing entry for same category, add new one at front
        val updated = existing.filter { it.categoryId != categoryId }.toMutableList()
        updated.add(0, RecentCategory(categoryId, categoryName, contentType, System.currentTimeMillis()))
        // Keep max 20 entries
        val trimmed = updated.take(MAX_RECENT_CATEGORIES)
        cache.commitAsync { putString(key, json.encodeToString(trimmed)) }
        cachedRecentCategories[contentType] = trimmed
    }

    fun getRecentlyViewedCategories(contentType: String): List<RecentCategory> {
        cachedRecentCategories[contentType]?.let { return it }
        val key = KEY_RECENT_CATEGORIES + "_" + contentType
        return getRecentCategoryList(key).also { cachedRecentCategories[contentType] = it }
    }

    private fun getRecentCategoryList(key: String): List<RecentCategory> {
        val raw = cache.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addToWatchHistory(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        playbackPosition: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false,
        episodeId: EpisodeId? = null,
        episodeExtension: String? = null,
        seriesId: SeriesId? = null,
        seriesName: String? = null,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
    ) {
        synchronized(watchHistoryLock) {
            val history = getWatchHistoryLocked().toMutableList()
            history.removeAll { it.itemId == itemId && it.contentType == contentType }
            history.add(
                0,
                WatchedItem(
                    itemId,
                    itemName,
                    categoryId,
                    contentType,
                    System.currentTimeMillis(),
                    playbackPosition,
                    duration,
                    isCompleted,
                    episodeId = episodeId,
                    episodeExtension = episodeExtension,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                ),
            )
            val trimmed = history.take(providerSettings.watchHistorySize)

            // Update in-memory cache immediately (reads always see latest data)
            cachedWatchHistory = trimmed
            watchHistoryDirty = true
        }
        // Debounce disk write — coalesces rapid updates (e.g. playback progress) into one write
        watchHistoryWriteHandler.removeCallbacks(watchHistoryWriteRunnable)
        watchHistoryWriteHandler.postDelayed(watchHistoryWriteRunnable, 500L)
    }

    /**
     * Every watch-state row for this provider, across every content type, unbounded —
     * `watch_state`-backed since Phase 3 of plans/watch-state-durable-storage-plan.md. No
     * production caller as of Phase 3 (the old sync `getRecentItems` was the one caller, and it
     * folded into [getRecentItemsFromWatchState], which reads the capped/collapsed queries
     * directly instead); kept for callers that want the whole history, such as a future export.
     */
    suspend fun getWatchHistory(): List<WatchedItem> = watchStateDao.getAll(providerId).map { it.toWatchedItem() }

    /**
     * The `watch_history_v3`/`v2` blob, decoded and cached. Still what every writer reads its
     * "existing entry" from and what [backfillWatchState] copies out of — Phase 4 retires this.
     * `internal` rather than `private` only so tests can assert against the blob directly, now
     * that [getWatchHistory] no longer does; callers outside a `synchronized(watchHistoryLock)`
     * block (as every production caller already is) get an unsynchronized read.
     */
    internal fun getWatchHistoryLocked(): List<WatchedItem> {
        val currentCache = cachedWatchHistory
        val history = if (currentCache != null) {
            currentCache
        } else {
            val v3Json = cache.getString(KEY_WATCH_HISTORY, null)
            val loaded = if (v3Json != null) {
                try {
                    json.decodeFromString<List<WatchedItem>>(v3Json)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                val v2Json = cache.getString(KEY_WATCH_HISTORY_V2, null)
                if (v2Json != null) {
                    try {
                        val v2Items = json.decodeFromString<List<WatchedItem>>(v2Json)
                        val normalized = v2Items.map { item ->
                            if (item.contentType == ContentType.TV_SHOWS && item.episodeId == null) {
                                item.copy(episodeId = EpisodeId(item.itemId))
                            } else {
                                item
                            }
                        }
                        cache.commitAsync { putString(KEY_WATCH_HISTORY, json.encodeToString(normalized)) }
                        normalized
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
            cachedWatchHistory = loaded
            loaded
        }
        return history
    }

    fun flushWatchHistory() {
        synchronized(watchHistoryLock) {
            if (!watchHistoryDirty) return
            cachedWatchHistory?.let { history ->
                cache.commitAsync { putString(KEY_WATCH_HISTORY, json.encodeToString(history)) }
            }
            watchHistoryDirty = false
        }
    }

    fun clearWatchHistory() {
        watchHistoryWriteHandler.removeCallbacks(watchHistoryWriteRunnable)
        synchronized(watchHistoryLock) {
            cachedWatchHistory = emptyList()
            watchHistoryDirty = false
            cache.commitAsync {
                remove(KEY_WATCH_HISTORY)
                remove(KEY_WATCH_HISTORY_V2)
            }
        }
    }

    fun addFavorite(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val favorites = getFavoriteItems().toMutableList()
        if (favorites.any { it.itemId == itemId && it.contentType == contentType }) {
            return false
        }
        favorites.add(0, FavoriteItem(itemId, itemName, categoryId, contentType))
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)
        cache.commitAsync { putString(KEY_FAVORITES, json.encodeToString(trimmed)) }
        cachedFavorites = trimmed
        favoriteIdSet = null
        return true
    }

    fun removeFavorite(
        itemId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val favorites = getFavoriteItems().toMutableList()
        val removed = favorites.removeAll { it.itemId == itemId && it.contentType == contentType }
        if (!removed) return false
        cache.commitAsync { putString(KEY_FAVORITES, json.encodeToString(favorites)) }
        cachedFavorites = favorites
        favoriteIdSet = null
        return true
    }

    private fun getFavoriteItems(): List<FavoriteItem> = synchronized(favoriteLock) {
        cachedFavorites?.let { return it }
        val favJson = cache.getString(KEY_FAVORITES, null) ?: return emptyList<FavoriteItem>().also { cachedFavorites = it }
        return try {
            json.decodeFromString<List<FavoriteItem>>(favJson).also { cachedFavorites = it }
        } catch (e: Exception) {
            emptyList<FavoriteItem>().also { cachedFavorites = it }
        }
    }

    fun getFavoritesForContentType(contentType: String): List<MediaItem> = synchronized(favoriteLock) {
        val mediaType = contentTypeToMediaType(contentType)
        return getFavoriteItems()
            .asSequence()
            .filter { it.contentType == contentType }
            .map { fav ->
                MediaItem(
                    id = fav.itemId,
                    name = fav.itemName,
                    mediaType = mediaType,
                    categoryId = fav.categoryId,
                )
            }.toList()
    }

    fun isFavorite(
        itemId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val set =
            favoriteIdSet ?: getFavoriteItems()
                .mapTo(HashSet()) { it.itemId to it.contentType }
                .also { favoriteIdSet = it }
        return (itemId to contentType) in set
    }

    fun clearFavorites() = synchronized(favoriteLock) {
        cachedFavorites = emptyList()
        favoriteIdSet = null
        cache.commitAsync { remove(KEY_FAVORITES) }
    }

    // --- Favorite Categories ---

    fun addFavoriteCategory(
        categoryId: String,
        categoryName: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val favorites = getFavoriteCategoryItems().toMutableList()
        if (favorites.any { it.categoryId == categoryId && it.contentType == contentType }) {
            return false
        }
        favorites.add(0, FavoriteCategoryItem(categoryId, categoryName, contentType))
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)
        cache.commitAsync { putString(KEY_FAVORITE_CATEGORIES, json.encodeToString(trimmed)) }
        cachedFavoriteCategories = trimmed
        favoriteCategoryIdSet = null
        return true
    }

    fun removeFavoriteCategory(
        categoryId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val favorites = getFavoriteCategoryItems().toMutableList()
        val removed = favorites.removeAll { it.categoryId == categoryId && it.contentType == contentType }
        if (!removed) return false
        cache.commitAsync { putString(KEY_FAVORITE_CATEGORIES, json.encodeToString(favorites)) }
        cachedFavoriteCategories = favorites
        favoriteCategoryIdSet = null
        return true
    }

    fun getFavoriteCategoryItems(): List<FavoriteCategoryItem> = synchronized(favoriteLock) {
        cachedFavoriteCategories?.let { return it }
        val raw =
            cache.getString(KEY_FAVORITE_CATEGORIES, null)
                ?: return emptyList<FavoriteCategoryItem>().also { cachedFavoriteCategories = it }
        return try {
            json.decodeFromString<List<FavoriteCategoryItem>>(raw).also { cachedFavoriteCategories = it }
        } catch (_: Exception) {
            emptyList<FavoriteCategoryItem>().also { cachedFavoriteCategories = it }
        }
    }

    fun getFavoriteCategoriesForContentType(contentType: String): List<MediaCategory> = synchronized(favoriteLock) {
        return@synchronized getFavoriteCategoryItems()
            .asSequence()
            .filter { it.contentType == contentType }
            .map { fav ->
                MediaCategory(
                    id = fav.categoryId,
                    name = fav.categoryName,
                    isVirtual = false,
                )
            }.toList()
    }

    fun isFavoriteCategory(
        categoryId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val set =
            favoriteCategoryIdSet ?: getFavoriteCategoryItems()
                .mapTo(HashSet()) { it.categoryId to it.contentType }
                .also { favoriteCategoryIdSet = it }
        return (categoryId to contentType) in set
    }

    fun clearFavoriteCategories() = synchronized(favoriteLock) {
        cachedFavoriteCategories = emptyList()
        favoriteCategoryIdSet = null
        cache.commitAsync { remove(KEY_FAVORITE_CATEGORIES) }
    }

    /**
     * Records a resume point, and with it the identity of what was played.
     *
     * The episode/series fields matter because this is the write that *creates* the row for a
     * short session: [saveLastPlayedItem] only runs once a couple of percent has been watched, so
     * sampling an episode and leaving used to mint a history row carrying a position and nothing
     * that says which episode of which show it was. Recent then had only the id to go on and read
     * an episode as a series. Callers that know the metadata must pass it; what they omit falls
     * back to whatever the existing row already had.
     */
    fun savePlaybackPosition(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        position: Long,
        duration: Long,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        episodeId: EpisodeId? = null,
        episodeExtension: String? = null,
        seriesId: SeriesId? = null,
        seriesName: String? = null,
    ) {
        // Live TV never gets a resume point, server-backed providers own this state themselves,
        // and an empty session (left while idle or still buffering) carries no information —
        // writing it would overwrite a real resume point, and any completed mark, with zeroes.
        val shouldRecord =
            contentType != ContentType.LIVE_TV && !usesServerUserData && !(position <= 0L && duration <= 0L)
        if (shouldRecord) {
            val progressPercent =
                if (duration > 0) {
                    (position.toFloat() / duration.toFloat()) * 100f
                } else {
                    0f
                }
            val isCompleted = progressPercent > 95.0f
            // Preserve metadata from existing entry
            val existing =
                synchronized(watchHistoryLock) {
                    getWatchHistoryLocked().firstOrNull { it.itemId == itemId && it.contentType == contentType }
                }
            val resolvedEpisodeId = episodeId ?: existing?.episodeId
            val resolvedEpisodeExtension = episodeExtension ?: existing?.episodeExtension
            val resolvedSeriesId = seriesId ?: existing?.seriesId
            val resolvedSeriesName = seriesName ?: existing?.seriesName
            val resolvedAudioTrackIndex = audioTrackIndex ?: existing?.audioTrackIndex
            val resolvedSubtitleTrackIndex = subtitleTrackIndex ?: existing?.subtitleTrackIndex
            addToWatchHistory(
                itemId,
                itemName,
                categoryId,
                contentType,
                position,
                duration,
                isCompleted,
                episodeId = resolvedEpisodeId,
                episodeExtension = resolvedEpisodeExtension,
                seriesId = resolvedSeriesId,
                seriesName = resolvedSeriesName,
                audioTrackIndex = resolvedAudioTrackIndex,
                subtitleTrackIndex = resolvedSubtitleTrackIndex,
            )

            // Dual write (Phase 2, plans/watch-state-durable-storage-plan.md): owns position,
            // duration, completion and lastPlayedAt. COALESCE in the upsert keeps metadata this
            // call doesn't carry, same as the read-modify-write above achieves for the blob.
            val progressNow = System.currentTimeMillis()
            writeScope.launch {
                watchStateDao.upsertProgress(
                    providerId = providerId,
                    itemId = itemId,
                    contentType = contentType,
                    itemName = itemName,
                    categoryId = categoryId,
                    positionMs = position,
                    durationMs = duration,
                    isCompleted = isCompleted,
                    now = progressNow,
                    seriesId = resolvedSeriesId?.raw,
                    episodeId = resolvedEpisodeId?.raw,
                    seriesName = resolvedSeriesName,
                    episodeExtension = resolvedEpisodeExtension,
                    audioTrackIndex = resolvedAudioTrackIndex,
                    subtitleTrackIndex = resolvedSubtitleTrackIndex,
                )
            }
        }
    }

    /**
     * Bulk lookup for a category page: every stored position/completion for [contentType],
     * filtered down to [itemIds]. Fetches the whole content type rather than an `IN (…)` query —
     * deliberate, see "Fetching the whole content type…" in the plan — so this is one indexed
     * query plus an in-memory filter instead of a bind-variable list sized to the category.
     */
    suspend fun getPlaybackPositions(
        itemIds: List<String>,
        contentType: String,
    ): Map<String, WatchedItem> {
        val idSet = itemIds.toHashSet()
        val rows = watchStateDao.getByContentType(providerId, contentType)
        val result = HashMap<String, WatchedItem>()
        for (row in rows) {
            if (row.itemId in idSet) {
                result[row.itemId] = row.toWatchedItem()
            }
        }
        return result
    }

    /**
     * Per-series watch rollup for the TV Shows list: fraction of a series' episodes that are
     * completed, keyed by series id.
     *
     * Series rows can't use [getPlaybackPositions] directly — watch history is keyed by episode
     * id, with the series id only carried alongside — so this is a real aggregate over
     * `watch_state`, divided by the provider's cached episode count. Series the provider can't
     * count locally are absent, and show no progress rather than a wrong one.
     */
    suspend fun getSeriesWatchProgress(): Map<String, Float> {
        val totals = provider?.getEpisodeCountsBySeries()
        val result = HashMap<String, Float>()
        if (totals != null && totals.isNotEmpty()) {
            val completedPerSeries = watchStateDao.getSeriesCompletedCounts(providerId, ContentType.TV_SHOWS)
            for (row in completedPerSeries) {
                val total = totals[row.seriesId]
                if (total != null && total > 0) {
                    result[row.seriesId] = (row.completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
            }
        }
        return result
    }

    /**
     * Shared per-content-type Recent list. One list feeding every surface that shows it — the
     * browse row, the Live TV preview panel and the player's channel flyout — so they cannot
     * disagree after a write. null means "not loaded yet", which is what callers render a
     * spinner for; an empty list means loaded and genuinely empty.
     */
    fun recentItems(contentType: String): StateFlow<List<MediaItem>?> = recentItemsFlow(contentType).asStateFlow()

    /** Re-reads the Recent list and publishes it to every [recentItems] collector. */
    suspend fun refreshRecentItems(contentType: String): List<MediaItem> {
        val items = getRecentItemsSuspend(contentType)
        recentItemsFlow(contentType).value = items
        return items
    }

    private fun recentItemsFlow(contentType: String): MutableStateFlow<List<MediaItem>?> =
        recentItemsFlows.getOrPut(contentType) { MutableStateFlow(null) }

    /**
     * The single "Recent" list: everything watched for [contentType], resumable items first and
     * the rest of the history after, each half newest-first. Storage in `watch_state` is
     * unbounded (see plans/watch-state-durable-storage-plan.md); `watchHistorySize` is now a
     * display cap taken by the query itself, not a retention policy — [getPlaybackPositions] and
     * [getSeriesWatchProgress] are unaffected by it, since a watched check or a resume bar is an
     * attribute of a stream, not a history entry.
     *
     * LIVE_TV degenerates to plain recency — [savePlaybackPosition] never records a position for
     * live streams, so no live entry can fall in the resumable band.
     *
     * The cap is per content type, not shared like the old blob's was: an in-progress movie can
     * no longer be pushed out of its own Recent row by heavy channel surfing on Live TV.
     */
    internal suspend fun getRecentItemsFromWatchState(contentType: String): List<MediaItem> {
        val mediaType = contentTypeToMediaType(contentType)
        val limit = providerSettings.watchHistorySize
        // TV Shows: one card per series rather than one per episode, collapsed in SQL before the
        // LIMIT — collapsing after would yield fewer cards than asked for. See "Series collapse
        // before the limit, not after" in the plan.
        val rows =
            if (contentType == ContentType.TV_SHOWS) {
                watchStateDao.getRecentSeriesCollapsed(providerId, contentType, limit)
            } else {
                watchStateDao.getRecent(providerId, contentType, limit)
            }
        val entries = rows.map { it.toWatchedItem() }
        val (inProgress, rest) = entries.partition { it.resumeProgress() != null }
        return (inProgress + rest).map { it.toRecentMediaItem(mediaType) }
    }

    /**
     * A history entry as a Recent row card. A TV Shows entry that knows its series becomes a card
     * for the show, carrying the episode to resume; one that does not stays a card for the episode
     * itself, which plays. Both say so in [MediaItem.target] rather than leaving the nav hosts to
     * infer it — inferring it is what once sent an episode id to the series screen.
     */
    private fun WatchedItem.toRecentMediaItem(mediaType: MediaType): MediaItem {
        val seriesCardId = if (contentType == ContentType.TV_SHOWS) seriesId else null
        return MediaItem(
            id = seriesCardId?.raw ?: itemId,
            name = if (seriesCardId != null) seriesName ?: itemName else itemName,
            mediaType = mediaType,
            categoryId = categoryId,
            target = recentTarget(seriesCardId),
        )
    }

    /**
     * A TV Shows history entry is always written from the player, whose stream id is the
     * episode's — so [WatchedItem.itemId] names the episode even on entries too old to carry an
     * explicit `episodeId`, and an entry with no series id is still safe to play.
     */
    private fun WatchedItem.recentTarget(seriesCardId: SeriesId?): BrowseTarget =
        when {
            seriesCardId != null ->
                BrowseTarget.Series(seriesId = seriesCardId, resumeEpisodeId = episodeId ?: EpisodeId(itemId))
            contentType == ContentType.TV_SHOWS ->
                BrowseTarget.Episode(
                    episodeId = episodeId ?: EpisodeId(itemId),
                    seriesId = seriesId,
                    seriesName = seriesName,
                    extension = episodeExtension,
                )
            contentType == ContentType.MOVIES -> BrowseTarget.Movie(itemId)
            else -> BrowseTarget.Channel(itemId)
        }

    // --- Server-aware suspend methods (branch on supportsServerUserData) ---

    suspend fun isFavoriteSuspend(
        itemId: String,
        contentType: String,
    ): Boolean {
        if (usesServerUserData) {
            return provider?.isFavorite(itemId) ?: false
        }
        return isFavorite(itemId, contentType)
    }

    suspend fun addFavoriteSuspend(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
    ): Boolean {
        if (usesServerUserData) {
            return provider?.setFavorite(itemId, true)?.isSuccess ?: false
        }
        return addFavorite(itemId, itemName, categoryId, contentType)
    }

    suspend fun removeFavoriteSuspend(
        itemId: String,
        contentType: String,
    ): Boolean {
        if (usesServerUserData) {
            return provider?.setFavorite(itemId, false)?.isSuccess ?: false
        }
        return removeFavorite(itemId, contentType)
    }

    suspend fun getFavoritesForContentTypeSuspend(contentType: String): List<MediaItem> {
        if (usesServerUserData) {
            return provider?.getFavoriteItems(contentType)?.getOrNull() ?: emptyList()
        }
        return rehydrateThumbnails(getFavoritesForContentType(contentType), contentType)
    }

    /**
     * Server-backed providers own the resume/recency semantics themselves, so the two endpoints
     * are merged rather than re-filtered: resume items first, then recently-played minus
     * anything already shown. Providers implementing only one endpoint degrade to just that one.
     */
    suspend fun getRecentItemsSuspend(contentType: String): List<MediaItem> {
        val items =
            if (usesServerUserData) {
                val mediaProvider = provider
                if (mediaProvider == null) {
                    emptyList()
                } else {
                    coroutineScope {
                        val resumeItems = async { mediaProvider.getResumeItems(contentType)?.getOrNull().orEmpty() }
                        val recentlyPlayed = async { mediaProvider.getRecentlyPlayed(contentType)?.getOrNull().orEmpty() }
                        val resume = resumeItems.await()
                        val shown = resume.mapTo(HashSet()) { it.id }
                        resume + recentlyPlayed.await().filter { it.id !in shown }
                    }
                }
            } else {
                rehydrateThumbnails(getRecentItemsFromWatchState(contentType), contentType)
            }
        return items
    }

    /**
     * Locally-stored favorites / watch-history keep only id+name+category, not the poster URL.
     * Look the image back up from the synced Xtream DB (the same source the browse grid uses:
     * `streamIcon` for movies/live, `cover` for series) so these tiles show art instead of the
     * letter fallback. Items not yet in the cache are left with a null thumbnail, as before.
     */
    private suspend fun rehydrateThumbnails(
        items: List<MediaItem>,
        contentType: String,
    ): List<MediaItem> {
        val missing = items.filter { it.thumbnailUrl.isNullOrBlank() }
        if (missing.isEmpty()) return items
        val ids = missing.mapNotNull { it.id.toIntOrNull() }
        if (ids.isEmpty()) return items

        val icons: Map<Int, String?> =
            withContext(Dispatchers.IO) {
                val db = XtreamDatabase.getInstance(context)
                when (contentType) {
                    ContentType.MOVIES -> db.streamDao().getIconsByIds(providerId, XtreamStreamEntity.TYPE_VOD, ids)
                    ContentType.LIVE_TV -> db.streamDao().getIconsByIds(providerId, XtreamStreamEntity.TYPE_LIVE, ids)
                    ContentType.TV_SHOWS -> db.seriesDao().getCoversByIds(providerId, ids)
                    else -> emptyMap()
                }
            }
        if (icons.isEmpty()) return items

        return items.map { item ->
            if (!item.thumbnailUrl.isNullOrBlank()) return@map item
            val url = item.id.toIntOrNull()?.let { icons[it] }
            if (url.isNullOrBlank()) item else item.copy(thumbnailUrl = url)
        }
    }

    suspend fun getPlaybackPositionSuspend(
        itemId: String,
        contentType: String,
    ): WatchedItem? {
        val result =
            if (usesServerUserData) {
                provider?.getPlaybackPosition(itemId)?.let { status ->
                    WatchedItem(
                        itemId = itemId,
                        itemName = status.itemName ?: "",
                        categoryId = status.categoryId ?: "",
                        contentType = contentType,
                        playbackPosition = status.positionMs,
                        duration = status.durationMs,
                        isCompleted = status.isCompleted,
                    )
                }
            } else {
                watchStateDao.getItem(providerId, itemId, contentType)?.toWatchedItem()
            }
        return result
    }

    suspend fun getPlaybackPositionsSuspend(
        itemIds: List<String>,
        contentType: String,
    ): Map<String, WatchedItem> {
        if (usesServerUserData) {
            val positions = provider?.getPlaybackPositions(itemIds)?.getOrNull() ?: return emptyMap()
            return positions.mapValues { (id, status) ->
                WatchedItem(
                    itemId = id,
                    itemName = status.itemName ?: "",
                    categoryId = status.categoryId ?: "",
                    contentType = contentType,
                    playbackPosition = status.positionMs,
                    duration = status.durationMs,
                    isCompleted = status.isCompleted,
                )
            }
        }
        return getPlaybackPositions(itemIds, contentType)
    }

    fun clearPlaybackPosition(
        itemId: String,
        contentType: String,
    ) {
        synchronized(watchHistoryLock) {
            val history = getWatchHistoryLocked().toMutableList()
            val index =
                history.indexOfFirst {
                    it.itemId == itemId && it.contentType == contentType
                }
            if (index != -1) {
                val item = history[index]
                history[index] = item.copy(playbackPosition = 0L, isCompleted = false)

                // Update cache
                cachedWatchHistory = history

                cache.commitAsync { putString(KEY_WATCH_HISTORY, json.encodeToString(history)) }
            }
        }
    }

    fun getAppSettings(): AppSettings = appSettings

    // --- Payload/fetch time tracking ---

    fun getPayloadSize(key: String): String? {
        if (!appSettings.isDevMode) return null
        val sizeInBytes = payloadSizes[key] ?: return null
        return formatBytes(sizeInBytes)
    }

    fun getFetchTimeFormatted(key: String): String? {
        if (!appSettings.isDevMode) return null
        val timeMs = fetchTimes[key] ?: return null
        return "$timeMs ms"
    }

    // --- Cache management ---

    fun clearCache() {
        cache.commitAsync { clear() }
        payloadSizes.clear()
        fetchTimes.clear()
        cachedFavorites = null
        cachedFavoriteCategories = null
        cachedRecentCategories.clear()
        synchronized(watchHistoryLock) {
            cachedWatchHistory = null
        }
    }

    fun getCacheSize(): Long {
        var totalSize = 0L
        cache.all.forEach { (_, value) ->
            when (value) {
                // Estimate UTF-8 size without allocating byte arrays (worst-case 3 bytes per char)
                is String -> totalSize += value.length.toLong() * 3
                is Long -> totalSize += 8
                is Int -> totalSize += 4
                is Boolean -> totalSize += 1
            }
        }
        return totalSize
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }

    private fun contentTypeToMediaType(contentType: String): MediaType =
        when (contentType) {
            ContentType.LIVE_TV -> MediaType.LIVE_CHANNEL
            ContentType.MOVIES -> MediaType.MOVIE
            ContentType.TV_SHOWS -> MediaType.SERIES
            else -> MediaType.LIVE_CHANNEL
        }

    override fun close() {
        watchHistoryWriteHandler.removeCallbacks(watchHistoryWriteRunnable)
        flushWatchHistory()
        watchHistoryWriteThread.quitSafely()
        writeScope.cancel()
    }
}
