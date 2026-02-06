package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
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
    val isCompleted: Boolean = false
)

@Serializable
data class FavoriteItem(
    val itemId: String,
    val itemName: String,
    val categoryId: String,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MediaRepository(
    private val context: Context,
    private val providerId: Long,
    private val providerSettings: ProviderSettings = ProviderSettings.DEFAULT
) {
    private var provider: MediaProvider? = null

    private val cacheName = "media_cache_$providerId"
    private val cache: SharedPreferences = context.getSharedPreferences(
        cacheName,
        Context.MODE_PRIVATE
    )
    private val appSettings = AppSettings(context)  // Keep for global settings (isDevMode)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val payloadSizes = ConcurrentHashMap<String, Long>()
    private val fetchTimes = ConcurrentHashMap<String, Long>()

    companion object {
        private const val KEY_WATCH_HISTORY = "watch_history_v2"
        private const val KEY_FAVORITES = "favorites_v2"

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
    }

    fun getProvider(): MediaProvider? = provider

    fun getCapabilities(): ProviderCapabilities? = provider?.capabilities

    fun getProviderSettings(): ProviderSettings = providerSettings

    fun getCategoryFilters(): CategoryFilters = providerSettings.categoryFilters

    fun isAutoResumeEnabled(): Boolean = providerSettings.autoResumeEnabled

    fun isCachingEnabled(): Boolean = providerSettings.cachingEnabled

    // --- Provider-delegated operations ---

    suspend fun connect(): kotlin.Result<Unit> {
        return provider?.connect() ?: kotlin.Result.failure(Exception("No provider set"))
    }

    suspend fun disconnect() {
        provider?.disconnect()
    }

    fun isConnected(): Boolean = provider?.isConnected() == true

    suspend fun getCategories(contentType: String): kotlin.Result<List<MediaCategory>> {
        return provider?.getCategories(contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))
    }

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

    suspend fun getItems(categoryId: String, contentType: String): kotlin.Result<List<MediaItem>> {
        return provider?.getItems(categoryId, contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))
    }

    suspend fun getItemsForSearch(categoryId: String, contentType: String): kotlin.Result<List<MediaItem>> {
        return provider?.getItems(categoryId, contentType)
            ?: kotlin.Result.failure(Exception("No provider set"))
    }

    suspend fun getSeriesDetail(seriesId: String): kotlin.Result<SeriesDetail> {
        return provider?.getSeriesDetail(seriesId)
            ?: kotlin.Result.failure(Exception("No provider set"))
    }

    suspend fun getMovieDetail(movieId: String): kotlin.Result<MovieDetail> {
        return provider?.getMovieDetail(movieId)
            ?: kotlin.Result.failure(Exception("No provider set"))
    }

    suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String? = null,
        extension: String? = null
    ): kotlin.Result<PlayableStream> {
        return provider?.resolvePlayableStream(itemId, contentType, episodeId, extension)
            ?: kotlin.Result.failure(Exception("No provider set"))
    }

    suspend fun search(query: String, contentType: String): kotlin.Result<List<MediaItem>>? {
        return provider?.search(query, contentType)
    }

    suspend fun getEpg(streamId: String): kotlin.Result<EpgResponse>? {
        return provider?.getEpg(streamId)
    }

    suspend fun getEpgBulk(streamIds: List<String>): kotlin.Result<Map<String, EpgResponse>>? {
        return provider?.getEpgBulk(streamIds)
    }

    // --- Progress sync hook ---

    suspend fun onPlaybackProgress(itemId: String, positionMs: Long, durationMs: Long) {
        provider?.onPlaybackProgress(itemId, positionMs, durationMs)
    }

    // --- Local-only operations (favorites, watch history, playback progress) ---

    fun saveLastPlayedItem(categoryId: String, itemId: String, itemName: String, contentType: String) {
        val editor = cache.edit()
        when (contentType) {
            "LIVE_TV" -> {
                editor.putString(KEY_LAST_LIVE_CATEGORY, categoryId)
                editor.putString(KEY_LAST_LIVE_ITEM, itemId)
            }
            "MOVIES" -> {
                editor.putString(KEY_LAST_MOVIES_CATEGORY, categoryId)
                editor.putString(KEY_LAST_MOVIES_ITEM, itemId)
            }
            "TV_SHOWS" -> {
                editor.putString(KEY_LAST_TVSHOWS_CATEGORY, categoryId)
                editor.putString(KEY_LAST_TVSHOWS_ITEM, itemId)
            }
        }
        editor.putString(KEY_LAST_CONTENT_TYPE, contentType)
        editor.apply()
        if (!usesServerUserData) {
            addToWatchHistory(itemId, itemName, categoryId, contentType)
        }
    }

    fun getLastCategoryId(contentType: String): String? {
        return when (contentType) {
            "LIVE_TV" -> cache.getString(KEY_LAST_LIVE_CATEGORY, null)
            "MOVIES" -> cache.getString(KEY_LAST_MOVIES_CATEGORY, null)
            "TV_SHOWS" -> cache.getString(KEY_LAST_TVSHOWS_CATEGORY, null)
            else -> null
        }
    }

    fun getLastItemId(contentType: String): String? {
        return when (contentType) {
            "LIVE_TV" -> cache.getString(KEY_LAST_LIVE_ITEM, null)
            "MOVIES" -> cache.getString(KEY_LAST_MOVIES_ITEM, null)
            "TV_SHOWS" -> cache.getString(KEY_LAST_TVSHOWS_ITEM, null)
            else -> null
        }
    }

    fun getLastContentType(): String? = cache.getString(KEY_LAST_CONTENT_TYPE, null)

    private fun addToWatchHistory(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        playbackPosition: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false
    ) {
        val history = getWatchHistory().toMutableList()
        history.removeAll { it.itemId == itemId && it.contentType == contentType }
        history.add(0, WatchedItem(
            itemId, itemName, categoryId, contentType,
            System.currentTimeMillis(),
            playbackPosition, duration, isCompleted
        ))
        val trimmed = history.take(providerSettings.watchHistorySize)
        cache.edit().putString(KEY_WATCH_HISTORY, json.encodeToString(trimmed)).apply()
    }

    fun getWatchHistory(): List<WatchedItem> {
        val historyJson = cache.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<WatchedItem>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getWatchHistoryForContentType(contentType: String): List<MediaItem> {
        val mediaType = contentTypeToMediaType(contentType)
        return getWatchHistory()
            .filter { it.contentType == contentType }
            .map { watched ->
                MediaItem(
                    id = watched.itemId,
                    name = watched.itemName,
                    mediaType = mediaType,
                    categoryId = watched.categoryId,
                    providerData = buildMap {
                        put("playbackPosition", watched.playbackPosition.toString())
                        put("duration", watched.duration.toString())
                        put("isCompleted", watched.isCompleted.toString())
                    }
                )
            }
    }

    fun clearWatchHistory() {
        cache.edit().remove(KEY_WATCH_HISTORY).apply()
    }

    fun addFavorite(itemId: String, itemName: String, categoryId: String, contentType: String): Boolean {
        val favorites = getFavoriteItems().toMutableList()
        if (favorites.any { it.itemId == itemId && it.contentType == contentType }) {
            return false
        }
        favorites.add(0, FavoriteItem(itemId, itemName, categoryId, contentType))
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)
        cache.edit().putString(KEY_FAVORITES, json.encodeToString(trimmed)).apply()
        return true
    }

    fun removeFavorite(itemId: String, contentType: String): Boolean {
        val favorites = getFavoriteItems().toMutableList()
        val removed = favorites.removeAll { it.itemId == itemId && it.contentType == contentType }
        cache.edit().putString(KEY_FAVORITES, json.encodeToString(favorites)).apply()
        return removed
    }

    private fun getFavoriteItems(): List<FavoriteItem> {
        val favJson = cache.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FavoriteItem>>(favJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getFavoritesForContentType(contentType: String): List<MediaItem> {
        val mediaType = contentTypeToMediaType(contentType)
        return getFavoriteItems()
            .filter { it.contentType == contentType }
            .map { fav ->
                MediaItem(
                    id = fav.itemId,
                    name = fav.itemName,
                    mediaType = mediaType,
                    categoryId = fav.categoryId
                )
            }
    }

    fun isFavorite(itemId: String, contentType: String): Boolean {
        return getFavoriteItems().any { it.itemId == itemId && it.contentType == contentType }
    }

    fun clearFavorites() {
        cache.edit().remove(KEY_FAVORITES).apply()
    }

    fun savePlaybackPosition(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String,
        position: Long,
        duration: Long
    ) {
        if (contentType == "LIVE_TV") return
        if (usesServerUserData) return
        val progressPercent = if (duration > 0) {
            (position.toFloat() / duration.toFloat()) * 100f
        } else 0f
        val isCompleted = progressPercent > 95.0f
        addToWatchHistory(itemId, itemName, categoryId, contentType, position, duration, isCompleted)
    }

    fun getPlaybackPosition(itemId: String, contentType: String): WatchedItem? {
        return getWatchHistory()
            .firstOrNull { it.itemId == itemId && it.contentType == contentType }
    }

    fun getInProgressItems(contentType: String): List<MediaItem> {
        val mediaType = contentTypeToMediaType(contentType)
        return getWatchHistory()
            .filter { item ->
                item.contentType == contentType &&
                !item.isCompleted &&
                item.playbackPosition > 0 &&
                item.duration > 0 &&
                run {
                    val progress = (item.playbackPosition.toFloat() / item.duration.toFloat()) * 100f
                    progress in 2.0..95.0
                }
            }
            .map { watched ->
                MediaItem(
                    id = watched.itemId,
                    name = watched.itemName,
                    mediaType = mediaType,
                    categoryId = watched.categoryId,
                    providerData = buildMap {
                        put("playbackPosition", watched.playbackPosition.toString())
                        put("duration", watched.duration.toString())
                        put("isCompleted", watched.isCompleted.toString())
                    }
                )
            }
    }

    // --- Server-aware suspend methods (branch on supportsServerUserData) ---

    suspend fun isFavoriteSuspend(itemId: String, contentType: String): Boolean {
        if (usesServerUserData) {
            return provider?.isFavorite(itemId) ?: false
        }
        return isFavorite(itemId, contentType)
    }

    suspend fun addFavoriteSuspend(
        itemId: String,
        itemName: String,
        categoryId: String,
        contentType: String
    ): Boolean {
        if (usesServerUserData) {
            return provider?.setFavorite(itemId, true)?.isSuccess ?: false
        }
        return addFavorite(itemId, itemName, categoryId, contentType)
    }

    suspend fun removeFavoriteSuspend(itemId: String, contentType: String): Boolean {
        if (usesServerUserData) {
            return provider?.setFavorite(itemId, false)?.isSuccess ?: false
        }
        return removeFavorite(itemId, contentType)
    }

    suspend fun getFavoritesForContentTypeSuspend(contentType: String): List<MediaItem> {
        if (usesServerUserData) {
            return provider?.getFavoriteItems(contentType)?.getOrNull() ?: emptyList()
        }
        return getFavoritesForContentType(contentType)
    }

    suspend fun getInProgressItemsSuspend(contentType: String): List<MediaItem> {
        if (usesServerUserData) {
            return provider?.getResumeItems(contentType)?.getOrNull() ?: emptyList()
        }
        return getInProgressItems(contentType)
    }

    suspend fun getWatchHistoryForContentTypeSuspend(contentType: String): List<MediaItem> {
        if (usesServerUserData) {
            return provider?.getRecentlyPlayed(contentType)?.getOrNull() ?: emptyList()
        }
        return getWatchHistoryForContentType(contentType)
    }

    suspend fun getPlaybackPositionSuspend(itemId: String, contentType: String): WatchedItem? {
        if (usesServerUserData) {
            val (posMs, durMs) = provider?.getPlaybackPosition(itemId) ?: return null
            return WatchedItem(
                itemId = itemId,
                itemName = "",
                categoryId = "",
                contentType = contentType,
                playbackPosition = posMs,
                duration = durMs,
                isCompleted = false
            )
        }
        return getPlaybackPosition(itemId, contentType)
    }

    fun clearPlaybackPosition(itemId: String, contentType: String) {
        val history = getWatchHistory().toMutableList()
        val index = history.indexOfFirst {
            it.itemId == itemId && it.contentType == contentType
        }
        if (index != -1) {
            val item = history[index]
            history[index] = item.copy(playbackPosition = 0L, isCompleted = false)
            cache.edit().putString(KEY_WATCH_HISTORY, json.encodeToString(history)).apply()
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
        return "${timeMs} ms"
    }

    // --- Cache management ---

    fun clearCache() {
        cache.edit().clear().apply()
        payloadSizes.clear()
        fetchTimes.clear()
    }

    fun getCacheSize(): Long {
        var totalSize = 0L
        cache.all.forEach { (_, value) ->
            when (value) {
                is String -> totalSize += value.toByteArray(Charsets.UTF_8).size
                is Long -> totalSize += 8
                is Int -> totalSize += 4
                is Boolean -> totalSize += 1
            }
        }
        return totalSize
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun contentTypeToMediaType(contentType: String): MediaType {
        return when (contentType) {
            "LIVE_TV" -> MediaType.LIVE_CHANNEL
            "MOVIES" -> MediaType.MOVIE
            "TV_SHOWS" -> MediaType.SERIES
            else -> MediaType.LIVE_CHANNEL
        }
    }
}
