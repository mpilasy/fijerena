package org.njarasoa.fijerena.core.network
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xmltv.XmltvEpgService
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.PlaybackStatus
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
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
    val episodeId: String? = null,
    val episodeExtension: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
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
) {
    @Volatile
    private var provider: MediaProvider? = null

    private val cacheName = "media_cache_$providerId"
    private val cache: SharedPreferences =
        context.getSharedPreferences(
            cacheName,
            Context.MODE_PRIVATE,
        )
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

    // In-memory caches to avoid repeated JSON deserialization from SharedPreferences
    private var cachedWatchHistory: List<WatchedItem>? = null

    // O(1) lookup map for getPlaybackPosition — keyed by (itemId, contentType)
    private var watchHistoryLookup: Map<Pair<String, String>, WatchedItem>? = null
    private var cachedFavorites: List<FavoriteItem>? = null
    private var cachedFavoriteCategories: List<FavoriteCategoryItem>? = null

    // O(1) lookup sets for isFavorite/isFavoriteCategory — rebuilt when lists change
    private var favoriteIdSet: Set<Pair<String, String>>? = null
    private var favoriteCategoryIdSet: Set<Pair<String, String>>? = null
    private val watchHistoryLock = Any()
    private var watchHistoryDirty = false
    private val watchHistoryWriteThread = android.os.HandlerThread("WatchHistoryWriter").apply { start() }
    private val watchHistoryWriteHandler = android.os.Handler(watchHistoryWriteThread.looper)
    private val watchHistoryWriteRunnable = Runnable { flushWatchHistory() }

    companion object {
        private const val KEY_WATCH_HISTORY = "watch_history_v2"
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
        private const val KEY_XTREAM_EPG_INGESTED_AT = "xtream_epg_ingested_at"
        private const val XTREAM_EPG_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
    }

    private val usesServerUserData: Boolean
        get() = provider?.capabilities?.supportsServerUserData == true

    fun setProvider(mediaProvider: MediaProvider) {
        provider = mediaProvider
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
        if (filters.prefixes.isEmpty() && filters.allowedScripts.isEmpty()) return result

        return result.map { categories ->
            categories.filter { category ->
                filters.shouldShowCategory(category.name)
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

    suspend fun getSeriesDetail(seriesId: String): kotlin.Result<SeriesDetail> =
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
    ): kotlin.Result<List<MediaItem>>? = provider?.search(query, contentType)

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
     * Ingest Xtream API EPG into the EPG index if not done recently.
     * Fetches EPG for all live streams and stores in SQLite for search/now-playing.
     */
    suspend fun ingestXtreamEpgIfNeeded() {
        val prefs = context.getSharedPreferences("epg_ingestion_$providerId", Context.MODE_PRIVATE)
        val lastIngested = prefs.getLong(KEY_XTREAM_EPG_INGESTED_AT, 0L)
        if (System.currentTimeMillis() - lastIngested < XTREAM_EPG_TTL_MS) return

        try {
            val streams =
                withContext(Dispatchers.IO) {
                    XtreamDatabase
                        .getInstance(context)
                        .streamDao()
                        .getAllStreams(providerId, XtreamStreamEntity.TYPE_LIVE)
                }
            if (streams.isEmpty()) return

            val streamIds = streams.map { it.streamId.toString() }
            val epgResult = provider?.getEpgBulk(streamIds) ?: return
            val epgMap = epgResult.getOrNull() ?: return
            if (epgMap.isEmpty()) return

            val intEpgMap = mutableMapOf<Int, org.njarasoa.fijerena.core.player.model.EpgResponse>()
            for ((key, value) in epgMap) {
                val intKey = key.toIntOrNull() ?: continue
                intEpgMap[intKey] = value
            }
            if (intEpgMap.isEmpty()) return

            val streamInfo =
                streams.associate { stream ->
                    stream.streamId to
                        EpgIndexer.XtreamStreamInfo(
                            streamId = stream.streamId,
                            name = stream.name,
                            epgChannelId = stream.epgChannelId,
                            iconUrl = stream.streamIcon,
                        )
                }

            EpgIndexer.getInstance(context).ingestFromXtreamEpg(
                epgByStreamId = intEpgMap,
                streamInfo = streamInfo,
                providerId = providerId,
            )

            prefs.edit { putLong(KEY_XTREAM_EPG_INGESTED_AT, System.currentTimeMillis()) }
        } catch (_: Exception) {
            // Non-critical — silently fail
        }
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
        episodeId: String? = null,
        episodeExtension: String? = null,
        seriesId: String? = null,
        seriesName: String? = null,
    ) {
        cache.edit {
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
        cache.edit { putString(key, json.encodeToString(trimmed)) }
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
        episodeId: String? = null,
        episodeExtension: String? = null,
        seriesId: String? = null,
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
            watchHistoryLookup = null
            watchHistoryDirty = true
        }
        // Debounce disk write — coalesces rapid updates (e.g. playback progress) into one write
        watchHistoryWriteHandler.removeCallbacks(watchHistoryWriteRunnable)
        watchHistoryWriteHandler.postDelayed(watchHistoryWriteRunnable, 500L)
    }

    fun getWatchHistory(): List<WatchedItem> {
        synchronized(watchHistoryLock) {
            return getWatchHistoryLocked()
        }
    }

    private fun getWatchHistoryLocked(): List<WatchedItem> {
        // Return cached value if available
        cachedWatchHistory?.let { return it }

        val historyJson = cache.getString(KEY_WATCH_HISTORY, null)
        val history =
            if (historyJson == null) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<WatchedItem>>(historyJson)
                } catch (e: Exception) {
                    emptyList()
                }
            }

        // Populate cache
        cachedWatchHistory = history
        watchHistoryLookup = null
        return history
    }

    fun getWatchHistoryForContentType(contentType: String): List<MediaItem> {
        val mediaType = contentTypeToMediaType(contentType)
        return getWatchHistory()
            .asSequence()
            .filter { it.contentType == contentType }
            .map { watched ->
                MediaItem(
                    id = watched.itemId,
                    name = watched.itemName,
                    mediaType = mediaType,
                    categoryId = watched.categoryId,
                    providerData =
                        buildMap {
                            put("playbackPosition", watched.playbackPosition.toString())
                            put("duration", watched.duration.toString())
                            put("isCompleted", watched.isCompleted.toString())
                            watched.episodeId?.let { put("episodeId", it) }
                            watched.episodeExtension?.let { put("episodeExtension", it) }
                            watched.seriesId?.let { put("seriesId", it) }
                            watched.seriesName?.let { put("seriesName", it) }
                        },
                )
            }.toList()
    }

    fun flushWatchHistory() {
        synchronized(watchHistoryLock) {
            if (!watchHistoryDirty) return
            cachedWatchHistory?.let { history ->
                cache.edit { putString(KEY_WATCH_HISTORY, json.encodeToString(history)) }
            }
            watchHistoryDirty = false
        }
    }

    fun clearWatchHistory() {
        watchHistoryWriteHandler.removeCallbacks(watchHistoryWriteRunnable)
        synchronized(watchHistoryLock) {
            cachedWatchHistory = emptyList()
            watchHistoryLookup = null
            watchHistoryDirty = false
            cache.edit { remove(KEY_WATCH_HISTORY) }
        }
    }

    fun addFavorite(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
    ): Boolean {
        val favorites = getFavoriteItems().toMutableList()
        if (favorites.any { it.itemId == itemId && it.contentType == contentType }) {
            return false
        }
        favorites.add(0, FavoriteItem(itemId, itemName, categoryId, contentType))
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)
        cache.edit { putString(KEY_FAVORITES, json.encodeToString(trimmed)) }
        cachedFavorites = trimmed
        favoriteIdSet = null
        return true
    }

    fun removeFavorite(
        itemId: String,
        contentType: String,
    ): Boolean {
        val favorites = getFavoriteItems().toMutableList()
        val removed = favorites.removeAll { it.itemId == itemId && it.contentType == contentType }
        if (!removed) return false
        cache.edit { putString(KEY_FAVORITES, json.encodeToString(favorites)) }
        cachedFavorites = favorites
        favoriteIdSet = null
        return true
    }

    private fun getFavoriteItems(): List<FavoriteItem> {
        cachedFavorites?.let { return it }
        val favJson = cache.getString(KEY_FAVORITES, null) ?: return emptyList<FavoriteItem>().also { cachedFavorites = it }
        return try {
            json.decodeFromString<List<FavoriteItem>>(favJson).also { cachedFavorites = it }
        } catch (e: Exception) {
            emptyList<FavoriteItem>().also { cachedFavorites = it }
        }
    }

    fun getFavoritesForContentType(contentType: String): List<MediaItem> {
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
    ): Boolean {
        val set =
            favoriteIdSet ?: getFavoriteItems()
                .mapTo(HashSet()) { it.itemId to it.contentType }
                .also { favoriteIdSet = it }
        return (itemId to contentType) in set
    }

    fun clearFavorites() {
        cachedFavorites = emptyList()
        favoriteIdSet = null
        cache.edit { remove(KEY_FAVORITES) }
    }

    // --- Favorite Categories ---

    fun addFavoriteCategory(
        categoryId: String,
        categoryName: String,
        contentType: String,
    ): Boolean {
        val favorites = getFavoriteCategoryItems().toMutableList()
        if (favorites.any { it.categoryId == categoryId && it.contentType == contentType }) {
            return false
        }
        favorites.add(0, FavoriteCategoryItem(categoryId, categoryName, contentType))
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)
        cache.edit { putString(KEY_FAVORITE_CATEGORIES, json.encodeToString(trimmed)) }
        cachedFavoriteCategories = trimmed
        favoriteCategoryIdSet = null
        return true
    }

    fun removeFavoriteCategory(
        categoryId: String,
        contentType: String,
    ): Boolean {
        val favorites = getFavoriteCategoryItems().toMutableList()
        val removed = favorites.removeAll { it.categoryId == categoryId && it.contentType == contentType }
        if (!removed) return false
        cache.edit { putString(KEY_FAVORITE_CATEGORIES, json.encodeToString(favorites)) }
        cachedFavoriteCategories = favorites
        favoriteCategoryIdSet = null
        return true
    }

    fun getFavoriteCategoryItems(): List<FavoriteCategoryItem> {
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

    fun getFavoriteCategoriesForContentType(contentType: String): List<MediaCategory> =
        getFavoriteCategoryItems()
            .asSequence()
            .filter { it.contentType == contentType }
            .map { fav ->
                MediaCategory(
                    id = fav.categoryId,
                    name = fav.categoryName,
                    isVirtual = false,
                )
            }.toList()

    fun isFavoriteCategory(
        categoryId: String,
        contentType: String,
    ): Boolean {
        val set =
            favoriteCategoryIdSet ?: getFavoriteCategoryItems()
                .mapTo(HashSet()) { it.categoryId to it.contentType }
                .also { favoriteCategoryIdSet = it }
        return (categoryId to contentType) in set
    }

    fun clearFavoriteCategories() {
        cachedFavoriteCategories = emptyList()
        favoriteCategoryIdSet = null
        cache.edit { remove(KEY_FAVORITE_CATEGORIES) }
    }

    fun savePlaybackPosition(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        position: Long,
        duration: Long,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
    ) {
        if (contentType == ContentType.LIVE_TV) return
        if (usesServerUserData) return
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
        addToWatchHistory(
            itemId,
            itemName,
            categoryId,
            contentType,
            position,
            duration,
            isCompleted,
            episodeId = existing?.episodeId,
            episodeExtension = existing?.episodeExtension,
            seriesId = existing?.seriesId,
            seriesName = existing?.seriesName,
            audioTrackIndex = audioTrackIndex ?: existing?.audioTrackIndex,
            subtitleTrackIndex = subtitleTrackIndex ?: existing?.subtitleTrackIndex,
        )
    }

    fun getPlaybackPosition(
        itemId: String,
        contentType: String,
    ): WatchedItem? {
        synchronized(watchHistoryLock) {
            val map =
                watchHistoryLookup ?: getWatchHistoryLocked()
                    .associateBy { it.itemId to it.contentType }
                    .also { watchHistoryLookup = it }
            return map[itemId to contentType]
        }
    }

    fun getPlaybackPositions(itemIds: List<String>, contentType: String): Map<String, WatchedItem> {
        // Build a HashSet outside the synchronized block to minimize lock contention and time.
        // This is O(N) where N is the number of items in the category (can be large).
        val idSet = itemIds.toHashSet()

        synchronized(watchHistoryLock) {
            val history = getWatchHistoryLocked()

            // Optimization: Iterate over the watch history list (capped at 100 items) instead of
            // iterating over the potentially large itemIds list. This makes the complexity
            // inside the synchronized block O(HistorySize) instead of O(ItemIdsSize).
            val result = HashMap<String, WatchedItem>()
            for (i in history.indices) {
                val item = history[i]
                // Avoid Pair allocations by comparing fields directly and using the HashSet.
                if (item.contentType == contentType && item.itemId in idSet) {
                    result[item.itemId] = item
                }
            }
            return result
        }
    }

    fun getInProgressItems(contentType: String): List<MediaItem> {
        val mediaType = contentTypeToMediaType(contentType)
        return getWatchHistory()
            .asSequence()
            .filter { item ->
                item.contentType == contentType &&
                    !item.isCompleted &&
                    item.playbackPosition > 0 &&
                    item.duration > 0 &&
                    run {
                        val progress = (item.playbackPosition.toFloat() / item.duration.toFloat()) * 100f
                        progress in 2.0..95.0
                    }
            }.map { watched ->
                MediaItem(
                    id = watched.itemId,
                    name = watched.itemName,
                    mediaType = mediaType,
                    categoryId = watched.categoryId,
                    providerData =
                        buildMap {
                            put("playbackPosition", watched.playbackPosition.toString())
                            put("duration", watched.duration.toString())
                            put("isCompleted", watched.isCompleted.toString())
                        },
                )
            }.toList()
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

    suspend fun getInProgressItemsSuspend(contentType: String): List<MediaItem> {
        if (usesServerUserData) {
            return provider?.getResumeItems(contentType)?.getOrNull() ?: emptyList()
        }
        return rehydrateThumbnails(getInProgressItems(contentType), contentType)
    }

    suspend fun getWatchHistoryForContentTypeSuspend(contentType: String): List<MediaItem> {
        if (usesServerUserData) {
            return provider?.getRecentlyPlayed(contentType)?.getOrNull() ?: emptyList()
        }
        return rehydrateThumbnails(getWatchHistoryForContentType(contentType), contentType)
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
        if (usesServerUserData) {
            val status = provider?.getPlaybackPosition(itemId) ?: return null
            return WatchedItem(
                itemId = itemId,
                itemName = status.itemName ?: "",
                categoryId = status.categoryId ?: "",
                contentType = contentType,
                playbackPosition = status.positionMs,
                duration = status.durationMs,
                isCompleted = status.isCompleted,
            )
        }
        return getPlaybackPosition(itemId, contentType)
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
                watchHistoryLookup = null

                cache.edit { putString(KEY_WATCH_HISTORY, json.encodeToString(history)) }
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
        cache.edit { clear() }
        payloadSizes.clear()
        fetchTimes.clear()
        cachedFavorites = null
        cachedFavoriteCategories = null
        cachedRecentCategories.clear()
        synchronized(watchHistoryLock) {
            cachedWatchHistory = null
            watchHistoryLookup = null
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
}
