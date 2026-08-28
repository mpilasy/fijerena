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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xmltv.XmltvEpgService
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateDao
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateDao
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamEpisodeDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao
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
 * [MediaRepository.getRecentItemsFromWatchState], so anything that draws a progress bar is
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

/** A favourited stream as a `favorite_state` row. */
private fun FavoriteItem.toEntity(providerId: Long): FavoriteStateEntity =
    FavoriteStateEntity(
        providerId = providerId,
        itemId = itemId,
        contentType = contentType,
        kind = FavoriteKind.STREAM,
        name = itemName,
        parentCategoryId = categoryId,
        createdAt = timestamp,
    )

/** A favourited category as a `favorite_state` row: the category id *is* the item id. */
private fun FavoriteCategoryItem.toEntity(providerId: Long): FavoriteStateEntity =
    FavoriteStateEntity(
        providerId = providerId,
        itemId = categoryId,
        contentType = contentType,
        kind = FavoriteKind.CATEGORY,
        name = categoryName,
        parentCategoryId = null,
        createdAt = timestamp,
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
    // Injectable for the same reason as watchStateDao: setWatched/getSiblingCompleted* touch the
    // Xtream catalogue directly, and this module's unit tests have no Robolectric/Room to back a
    // real XtreamDatabase.getInstance(context) call under a plain mockk Context.
    private val streamDao: XtreamStreamDao = XtreamDatabase.getInstance(context).streamDao(),
    private val episodeDao: XtreamEpisodeDao = XtreamDatabase.getInstance(context).episodeDao(),
    private val favoriteStateDao: FavoriteStateDao = XtreamDatabase.getInstance(context).favoriteStateDao(),
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

    // Dedicated single-thread dispatcher for prefs writes below. commit() (not apply()) blocks
    // only this background thread until its own write finishes, so it never leaves anything
    // pending in the process-wide QueuedWork backlog. apply() returns immediately but registers
    // the write there; a later, unrelated Service dispatch (startService() -> ActivityThread.
    // handleServiceArgs) synchronously drains that whole backlog on the MAIN thread before
    // running — a well-known ANR trap when writes pile up faster than they drain. In-memory
    // caches are updated synchronously on the caller's thread before the write is dispatched,
    // so reads stay consistent regardless of when the background commit actually lands.
    //
    // watch_state is the one exception: savePlaybackPosition/saveLastPlayedItem have no
    // synchronous in-memory mirror of their own (Phase 4 removed the blob they used to update
    // synchronously alongside the table), so a read immediately following a write on another
    // thread can race the upsert. Neither writer is debounced, so the window is however long one
    // Room upsert takes, not the blob's old 500 ms — narrow, but real. Named as its own property
    // (rather than inlined into writeScope's constructor) so [awaitPendingWrites] can queue onto
    // the exact same serialized dispatcher without going through writeScope's Job.
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val writeScope = CoroutineScope(SupervisorJob() + writeDispatcher)

    private fun SharedPreferences.commitAsync(action: SharedPreferences.Editor.() -> Unit) {
        val prefs = this
        writeScope.launch { prefs.edit(commit = true, action = action) }
    }

    /**
     * Test-only synchronization point: suspends until every write already queued on
     * [writeScope] — specifically the fire-and-forget `watch_state` upserts from
     * [savePlaybackPosition]/[saveLastPlayedItem] — has finished. [writeDispatcher] runs at most
     * one task at a time, so an empty task queued here can only run once every earlier one has.
     */
    internal suspend fun awaitPendingWrites() {
        withContext(writeDispatcher) {}
    }

    companion object {
        private const val KEY_WATCH_HISTORY = "watch_history_v3"
        private const val KEY_WATCH_HISTORY_V2 = "watch_history_v2"
        private const val KEY_WATCH_STATE_MIGRATED = "watch_state_migrated_v1"
        private const val KEY_FAVORITES_MIGRATED = "favorites_migrated_v1"
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
        backfillAndPurgeWatchState()
        backfillAndPurgeFavorites()
    }

    /**
     * One-time per-provider copy of `watch_history_v3` into `watch_state`, followed by purging
     * the blob — Phase 2 and Phase 4 of docs/plans/watch-state-durable-storage-plan.md, combined into
     * one hook because Phase 4 depends on Phase 2 having actually run for *this* provider.
     *
     * Phases 2 and 4 are separate releases, so a provider nobody has opened since Phase 2 shipped
     * can reach a Phase 4 install still carrying an un-migrated blob — the flag gates on a
     * per-provider basis (never a single global flag) for exactly that reason. Guarded like
     * [org.njarasoa.fijerena.core.network.xtream.manager.XtreamEpgManager]'s
     * `purgeLegacyPrefsCache`, and hooked off [setProvider] rather than the constructor — a
     * provider-less repository (a unit test, or the brief window in [org.njarasoa.fijerena.core
     * .network.provider] wiring before a provider is attached) is not a real "use" yet.
     *
     * Backfill runs first when the flag is unset, purge always runs after: skipping straight to
     * purge on an unset flag would delete history that was never copied anywhere, silently and
     * unrecoverably, on a provider the user simply hasn't gotten around to opening. Backfill
     * itself is replay-safe — a crash between "rows written" and the flag commit just re-runs
     * this and re-upserts the same values, because every row goes through the same progress
     * upsert [savePlaybackPosition] uses rather than a raw insert.
     */
    private fun backfillAndPurgeWatchState() {
        val alreadyMigrated = cache.getBoolean(KEY_WATCH_STATE_MIGRATED, false)
        writeScope.launch {
            if (!alreadyMigrated) {
                // Reads the blob directly, not the public getWatchHistory() — that reads
                // watch_state since Phase 3, and would make this loop copy the table into itself.
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
                cache.edit(commit = true) { putBoolean(KEY_WATCH_STATE_MIGRATED, true) }
            }
            // The blob is guaranteed copied by this point — either just now, or on an earlier
            // release that already set the flag — so purging it here is always safe.
            cache.edit(commit = true) {
                remove(KEY_WATCH_HISTORY)
                remove(KEY_WATCH_HISTORY_V2)
            }
            synchronized(watchHistoryLock) {
                cachedWatchHistory = emptyList()
            }
        }
    }


    /**
     * One-time per-provider copy of the `favorites_v2` and `favorite_categories` blobs into
     * `favorite_state`, then purging both keys — see
     * `docs/plans/favorites-durable-storage-plan.md`.
     *
     * Per-provider rather than a single global flag, for the reason the watch-state plan sets out:
     * a provider nobody has opened since this shipped must not have its blob purged before it was
     * copied. Backfill and purge ship together here, so the "flag set, blob never copied" window
     * that plan worries about cannot open — the flag is still per-provider because the *hook* is
     * per-provider, and a second provider added later starts from its own unset flag.
     *
     * Runs before the snapshot is first read: [loadFavoriteSnapshotLocked] fills from the table, so
     * a snapshot taken before this finished would look empty. Reading the blobs here goes straight
     * to prefs rather than through the public getters, which now read the table and would copy it
     * into itself. Replay-safe: every row goes through the same upsert a normal favourite does.
     */
    private fun backfillAndPurgeFavorites() {
        if (cache.getBoolean(KEY_FAVORITES_MIGRATED, false)) {
            synchronized(favoriteLock) { loadFavoriteSnapshotLocked() }
            return
        }
        val blobFavorites = decodeBlob<FavoriteItem>(KEY_FAVORITES)
        val blobCategories = decodeBlob<FavoriteCategoryItem>(KEY_FAVORITE_CATEGORIES)
        for (item in blobFavorites) {
            favoriteStateDao.upsert(item.toEntity(providerId))
        }
        for (item in blobCategories) {
            favoriteStateDao.upsert(item.toEntity(providerId))
        }
        cache.edit(commit = true) {
            putBoolean(KEY_FAVORITES_MIGRATED, true)
            remove(KEY_FAVORITES)
            remove(KEY_FAVORITE_CATEGORIES)
        }
        synchronized(favoriteLock) {
            cachedFavorites = null
            cachedFavoriteCategories = null
            favoriteIdSet = null
            favoriteCategoryIdSet = null
            // Refill immediately rather than leaving it to whoever reads first: setProvider runs on
            // Dispatchers.IO, and the first reader is often on Main.
            loadFavoriteSnapshotLocked()
        }
    }

    /**
     * A legacy favourites blob, or an empty list if it is absent or unreadable. The
     * `catch { emptyList() }` that made a malformed blob indistinguishable from "no favourites"
     * only survives here, on the one-shot migration read, where there is genuinely nothing better
     * to do with a corrupt value that is about to be deleted.
     */
    private inline fun <reified T> decodeBlob(key: String): List<T> {
        val raw = cache.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<T>>(raw)
        } catch (_: Exception) {
            emptyList()
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

        // Owns recency and metadata only (Phase 4, docs/plans/watch-state-durable-storage-plan.md).
        // Must not name positionMs/durationMs/isCompleted, so a start write can never erase
        // progress a later progress write already stored. The blob write this used to fall back
        // to (`addToWatchHistory`) is gone — watch_history_v3 is retired.
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

    /**
     * Every watch-state row for this provider, across every content type, unbounded —
     * `watch_state`-backed since Phase 3 of docs/plans/watch-state-durable-storage-plan.md. No
     * production caller as of Phase 3 (the old sync `getRecentItems` was the one caller, and it
     * folded into [getRecentItemsFromWatchState], which reads the capped/collapsed queries
     * directly instead); kept for callers that want the whole history, such as a future export.
     */
    suspend fun getWatchHistory(): List<WatchedItem> = watchStateDao.getAll(providerId).map { it.toWatchedItem() }

    /**
     * The `watch_history_v3`/`v2` blob, decoded and cached. No writer reads this for an "existing
     * entry" any more — the table's own `COALESCE` upserts do that now — and nothing writes the
     * blob either (Phase 4). What's left: [backfillAndPurgeWatchState] copies whatever a
     * pre-Phase-4 install already wrote here into `watch_state` before purging it, and
     * [clearWatchHistory]/[clearPlaybackPosition] can still touch the same keys directly.
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

    /**
     * No-op since Phase 4 (docs/plans/watch-state-durable-storage-plan.md): there is no longer a
     * debounced blob write to force out early, and the table upserts were never debounced in the
     * first place — each dispatches to [writeScope] immediately. Kept, rather than deleted, so
     * its callers (a pause/stop hook expecting "make sure the last position landed") don't need
     * their own change; there is nothing left for it to flush.
     */
    fun flushWatchHistory() {
        // Intentionally empty.
    }

    /**
     * Wipes this provider's watch progress: the `watch_state` rows, the retired blob keys a
     * pre-Phase-4 install may still carry, and the in-memory views of both.
     *
     * Suspending because the rows are the real storage now — before Phase 4 this only removed the
     * blob keys, which after Phase 4 meant "Clear Progress" cleared nothing a user could see.
     * Published Recent lists are emptied too, or every surface showing one keeps rendering entries
     * whose rows are gone.
     */
    suspend fun clearWatchHistory() {
        watchStateDao.deleteAll(providerId)
        synchronized(watchHistoryLock) {
            cachedWatchHistory = emptyList()
            cache.commitAsync {
                remove(KEY_WATCH_HISTORY)
                remove(KEY_WATCH_HISTORY_V2)
            }
        }
        recentItemsFlows.values.forEach { it.value = emptyList() }
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
        val item = FavoriteItem(itemId, itemName, categoryId, contentType)
        // No take(favoritesMaxSize) here any more: the cap is what silently evicted the oldest
        // favourite once the list filled up. Rows are unbounded — see
        // docs/plans/favorites-durable-storage-plan.md.
        favorites.add(0, item)
        cachedFavorites = favorites
        favoriteIdSet = null
        writeScope.launch { favoriteStateDao.upsert(item.toEntity(providerId)) }
        return true
    }

    fun removeFavorite(
        itemId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val favorites = getFavoriteItems().toMutableList()
        val removed = favorites.removeAll { it.itemId == itemId && it.contentType == contentType }
        if (!removed) return false
        cachedFavorites = favorites
        favoriteIdSet = null
        writeScope.launch { favoriteStateDao.delete(providerId, itemId, contentType, FavoriteKind.STREAM) }
        return true
    }

    /**
     * Fills both favourite snapshots from `favorite_state` in one read.
     *
     * Blocking rather than suspending because Compose asks [isFavorite] synchronously while
     * composing a row, and `CategoryViewModel.rebuildVirtualCategories` asks
     * [getFavoriteCategoriesForContentType] from `Dispatchers.Main.immediate` — there is no
     * coroutine to suspend in at either call site.
     *
     * The `runBlocking(Dispatchers.IO)` is load-bearing, not decoration. Room asserts against
     * blocking queries on the main thread and throws `IllegalStateException`, which is exactly what
     * happened the first time this shipped without it: [setProvider] only invalidated the snapshot,
     * so the first read after a backfill ran cold on Main and killed the app on launch. Warming in
     * [setProvider] (below, on IO) makes that the rare path; this makes it survivable when it is
     * hit anyway. The stall it can cost is one indexed read of a small table — the blob it replaced
     * did a prefs read plus a JSON parse on the same thread.
     *
     * Caller must hold [favoriteLock].
     */
    private fun loadFavoriteSnapshotLocked() {
        if (cachedFavorites != null && cachedFavoriteCategories != null) return
        val rows = runBlocking(Dispatchers.IO) { favoriteStateDao.getAll(providerId) }
        cachedFavorites =
            rows
                .asSequence()
                .filter { it.kind == FavoriteKind.STREAM }
                .map { FavoriteItem(it.itemId, it.name, it.parentCategoryId ?: "", it.contentType, it.createdAt) }
                .toList()
        cachedFavoriteCategories =
            rows
                .asSequence()
                .filter { it.kind == FavoriteKind.CATEGORY }
                .map { FavoriteCategoryItem(it.itemId, it.name, it.contentType, it.createdAt) }
                .toList()
    }

    private fun getFavoriteItems(): List<FavoriteItem> = synchronized(favoriteLock) {
        loadFavoriteSnapshotLocked()
        return cachedFavorites.orEmpty()
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

    /** Streams only — both "Clear All Favorites" dialogs say "favorited streams". */
    fun clearFavorites() = synchronized(favoriteLock) {
        cachedFavorites = emptyList()
        favoriteIdSet = null
        writeScope.launch { favoriteStateDao.deleteAllOfKind(providerId, FavoriteKind.STREAM) }
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
        val item = FavoriteCategoryItem(categoryId, categoryName, contentType)
        favorites.add(0, item)
        cachedFavoriteCategories = favorites
        favoriteCategoryIdSet = null
        writeScope.launch { favoriteStateDao.upsert(item.toEntity(providerId)) }
        return true
    }

    fun removeFavoriteCategory(
        categoryId: String,
        contentType: String,
    ): Boolean = synchronized(favoriteLock) {
        val favorites = getFavoriteCategoryItems().toMutableList()
        val removed = favorites.removeAll { it.categoryId == categoryId && it.contentType == contentType }
        if (!removed) return false
        cachedFavoriteCategories = favorites
        favoriteCategoryIdSet = null
        writeScope.launch { favoriteStateDao.delete(providerId, categoryId, contentType, FavoriteKind.CATEGORY) }
        return true
    }

    fun getFavoriteCategoryItems(): List<FavoriteCategoryItem> = synchronized(favoriteLock) {
        loadFavoriteSnapshotLocked()
        return cachedFavoriteCategories.orEmpty()
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
        writeScope.launch { favoriteStateDao.deleteAllOfKind(providerId, FavoriteKind.CATEGORY) }
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
            // Owns position, duration, completion and lastPlayedAt (Phase 4,
            // docs/plans/watch-state-durable-storage-plan.md). No blob read-modify-write to preserve
            // metadata this call doesn't carry any more — the upsert's own `COALESCE` against
            // `watch_state`'s existing row does that for real, in SQL, instead of the app fetching
            // an "existing" entry first to pass back in.
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
                    seriesId = seriesId?.raw,
                    episodeId = episodeId?.raw,
                    seriesName = seriesName,
                    episodeExtension = episodeExtension,
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
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
     *
     * The numerator applies Phase 5 TMDB dedup, so a series watched through one language variant
     * reports progress on every variant. Without it this aggregate disagreed with the episode list
     * directly beneath it: [getSiblingCompletedEpisodeIds] spreads completion across sibling series
     * for the per-episode check, so the episodes showed checked while the row above them read 0%.
     */
    suspend fun getSeriesWatchProgress(): Map<String, Float> {
        val totals = provider?.getEpisodeCountsBySeries()
        val result = HashMap<String, Float>()
        if (totals != null && totals.isNotEmpty()) {
            val completedPerSeries = watchStateDao.getSeriesCompletedCounts(providerId, ContentType.TV_SHOWS)
            val deduped = episodeDao.getSiblingCompletedCountsBySeries(providerId)
            val completed = HashMap<String, Int>(completedPerSeries.size + deduped.size)
            for (row in completedPerSeries) {
                completed[row.seriesId] = row.completed
            }
            // Union, not replace. The dedup query reaches only series carrying a `tmdbId` and only
            // episodes still in the catalogue cache, while the direct count reaches any series with
            // a `watch_state` row. Taking the larger keeps both: a sibling-completed series gains
            // the spread, and a series the dedup query cannot see keeps the count it always had.
            // They overlap rather than sum — the same episode is counted by both — so max, not plus.
            for ((seriesId, count) in deduped) {
                val key = seriesId.toString()
                if (count > (completed[key] ?: 0)) {
                    completed[key] = count
                }
            }
            for ((seriesId, count) in completed) {
                val total = totals[seriesId]
                if (total != null && total > 0) {
                    result[seriesId] = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
            }
        }
        return result
    }

    /**
     * TMDB dedup (Phase 5, docs/plans/watch-state-durable-storage-plan.md): movie ids completed by a
     * different catalogue entry for the same title — a second language track, a 4K re-rip — under
     * its own id rather than this one. Xtream-only by construction: SMB, Local and Remote M3U have
     * no catalogue rows to join `watch_state` against, so the query simply returns nothing for
     * their `providerId` rather than needing a provider-type branch here. Movies only for now —
     * episode dedup needs its own call scoped to one series, from wherever an episode list reads
     * watched state, not this whole-content-type one.
     */
    suspend fun getSiblingCompletedMovieIds(): Set<String> =
        streamDao
            .getSiblingCompletedStreamIds(providerId, ContentType.MOVIES, XtreamStreamEntity.TYPE_VOD)
            .toSet()

    /**
     * TMDB dedup (Phase 5, docs/plans/watch-state-durable-storage-plan.md), episode form: episode ids
     * of [seriesId] completed by a sibling in a *different* series row that shares this series'
     * `tmdb_id` — a language/quality variant of the same show is a separate `xtream_series` row
     * with its own complete episode list, not a duplicate row within this one, so this is not the
     * same shape as [getSiblingCompletedMovieIds]. Call from wherever an episode list reads watched
     * state for that series. `seriesId` is a raw Xtream series id everywhere except a non-Xtream
     * provider, which has none — degrades to no dedup rather than a crash.
     */
    suspend fun getSiblingCompletedEpisodeIds(seriesId: String): Set<String> {
        val numericSeriesId = seriesId.toIntOrNull()
        val result =
            if (numericSeriesId != null) {
                episodeDao.getSiblingCompletedEpisodeIds(providerId, numericSeriesId).toSet()
            } else {
                emptySet()
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
     * unbounded (see docs/plans/watch-state-durable-storage-plan.md); `watchHistorySize` is now a
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
        val result =
            if (usesServerUserData) {
                provider?.isFavorite(itemId) ?: false
            } else {
                isFavorite(itemId, contentType)
            }
        return result
    }

    suspend fun addFavoriteSuspend(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
    ): Boolean {
        val result =
            if (usesServerUserData) {
                provider?.setFavorite(itemId, true)?.isSuccess ?: false
            } else {
                addFavorite(itemId, itemName, categoryId, contentType)
            }
        return result
    }

    suspend fun removeFavoriteSuspend(
        itemId: String,
        contentType: String,
    ): Boolean {
        val result =
            if (usesServerUserData) {
                provider?.setFavorite(itemId, false)?.isSuccess ?: false
            } else {
                removeFavorite(itemId, contentType)
            }
        return result
    }

    suspend fun getFavoritesForContentTypeSuspend(contentType: String): List<MediaItem> {
        val result =
            if (usesServerUserData) {
                provider?.getFavoriteItems(contentType)?.getOrNull() ?: emptyList()
            } else {
                rehydrateThumbnails(getFavoritesForContentType(contentType), contentType)
            }
        return result
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
        val ids = missing.mapNotNull { it.id.toIntOrNull() }
        val result =
            if (missing.isEmpty() || ids.isEmpty()) {
                items
            } else {
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
                if (icons.isEmpty()) {
                    items
                } else {
                    items.map { item ->
                        val url = if (item.thumbnailUrl.isNullOrBlank()) item.id.toIntOrNull()?.let { icons[it] } else null
                        if (url.isNullOrBlank()) item else item.copy(thumbnailUrl = url)
                    }
                }
            }
        return result
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

    /**
     * Track-setting fallback for an episode with no saved audio/subtitle choice of its own — the
     * series' most recently touched choice, if any. Callers only use this when the episode's own
     * row has neither index set; an episode that already has one sticks with it, no fallback.
     * Xtream-only by construction, like the sibling dedup queries: server-backed providers own
     * this state themselves (see [getPlaybackPositionSuspend]).
     */
    suspend fun getSeriesTrackPrefs(
        seriesId: SeriesId,
        contentType: String,
    ): Pair<Int?, Int?>? {
        if (usesServerUserData) return null
        val row = watchStateDao.getLatestSeriesTrackPrefs(providerId, seriesId.raw, contentType) ?: return null
        return row.audioTrackIndex to row.subtitleTrackIndex
    }

    suspend fun getPlaybackPositionsSuspend(
        itemIds: List<String>,
        contentType: String,
    ): Map<String, WatchedItem> {
        val result =
            if (usesServerUserData) {
                val positions = provider?.getPlaybackPositions(itemIds)?.getOrNull()
                if (positions != null) {
                    positions.mapValues { (id, status) ->
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
                } else {
                    emptyMap()
                }
            } else {
                getPlaybackPositions(itemIds, contentType)
            }
        return result
    }

    /**
     * Manual watched/unwatched mark (Phase 6, docs/plans/watch-state-durable-storage-plan.md), replacing
     * `clearPlaybackPosition` — which only ever cleared the blob and had no UI caller anywhere in
     * `tv/` or `mobile/`. Not routed through [savePlaybackPosition]: that method early-returns on
     * `position <= 0 && duration <= 0`, exactly what a manual mark looks like, so it needs its own
     * path rather than being mistaken for an empty session.
     *
     * `watched = true` writes one row; the sibling read query (Phase 5) spreads it across the
     * TMDB group on its own. `watched = false` has to clear the group itself here — a sibling's
     * completion would otherwise keep driving the check right back on, making the toggle look
     * like it did nothing. Xtream-only for the group clear, same as Phase 5: SMB, Local and Remote
     * M3U have no catalogue to find a group in, so only this one row changes for them.
     */
    suspend fun setWatched(
        itemId: String,
        contentType: String,
        watched: Boolean,
    ) {
        // Jellyfin owns this state server-side and has no MediaProvider capability to accept a
        // manual mark through this app — writing to the local watch_state table for it would be
        // dead data, since getPlaybackPositionSuspend's usesServerUserData branch never reads
        // local state back for these providers. No-op rather than write something nothing shows.
        if (!usesServerUserData) {
            val now = System.currentTimeMillis()
            if (watched) {
                // A never-played episode has no existing row to carry a seriesId forward from, and
                // this method's own signature has no seriesId parameter to accept one — resolve it
                // from the catalogue instead, or getSeriesCompletedCounts silently drops the row.
                val seriesId =
                    if (contentType == ContentType.TV_SHOWS) {
                        episodeDao.getSeriesIdForEpisode(providerId, itemId)?.toString()
                    } else {
                        null
                    }
                watchStateDao.markWatched(
                    providerId,
                    itemId,
                    contentType,
                    now,
                    seriesId = seriesId,
                    episodeId = if (seriesId != null) itemId else null,
                )
            } else {
                watchStateDao.markUnwatched(providerId, itemId, contentType, now)
                when (contentType) {
                    ContentType.MOVIES ->
                        streamDao.clearGroupCompletion(providerId, contentType, XtreamStreamEntity.TYPE_VOD, itemId, now)
                    ContentType.TV_SHOWS -> episodeDao.clearGroupCompletion(providerId, itemId, now)
                }
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
        writeScope.cancel()
    }
}
