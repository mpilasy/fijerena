package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_FAVORITES
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_CONTENT_TYPE
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_LIVE_CATEGORY
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_LIVE_STREAM
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_MOVIES_CATEGORY
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_MOVIES_STREAM
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_TVSHOWS_CATEGORY
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_LAST_TVSHOWS_STREAM
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamCacheKeys.KEY_WATCH_HISTORY
import org.njarasoa.fijerena.core.network.FavoriteStream
import org.njarasoa.fijerena.core.network.WatchedStream

class XtreamUserDataManager(
    private val sharedPreferences: SharedPreferences,
    private val providerSettings: ProviderSettings
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Save last played stream with content-type specific tracking
     */
    fun saveLastPlayedStream(categoryId: String, streamId: Int, streamName: String, contentType: String) {
        val editor = sharedPreferences.edit()

        // Save content-type specific last played
        when (contentType) {
            "LIVE_TV" -> {
                editor.putString(KEY_LAST_LIVE_CATEGORY, categoryId)
                editor.putInt(KEY_LAST_LIVE_STREAM, streamId)
            }
            "MOVIES" -> {
                editor.putString(KEY_LAST_MOVIES_CATEGORY, categoryId)
                editor.putInt(KEY_LAST_MOVIES_STREAM, streamId)
            }
            "TV_SHOWS" -> {
                editor.putString(KEY_LAST_TVSHOWS_CATEGORY, categoryId)
                editor.putInt(KEY_LAST_TVSHOWS_STREAM, streamId)
            }
        }

        // Save global last content type
        editor.putString(KEY_LAST_CONTENT_TYPE, contentType)

        editor.apply()

        // Add to watch history
        addToWatchHistory(streamId, streamName, categoryId, contentType)
    }

    /**
     * Get last played category for a specific content type
     */
    fun getLastCategoryId(contentType: String): String? {
        return when (contentType) {
            "LIVE_TV" -> sharedPreferences.getString(KEY_LAST_LIVE_CATEGORY, null)
            "MOVIES" -> sharedPreferences.getString(KEY_LAST_MOVIES_CATEGORY, null)
            "TV_SHOWS" -> sharedPreferences.getString(KEY_LAST_TVSHOWS_CATEGORY, null)
            else -> null
        }
    }

    /**
     * Get last played stream for a specific content type
     */
    fun getLastStreamId(contentType: String): Int? {
        val streamId = when (contentType) {
            "LIVE_TV" -> sharedPreferences.getInt(KEY_LAST_LIVE_STREAM, -1)
            "MOVIES" -> sharedPreferences.getInt(KEY_LAST_MOVIES_STREAM, -1)
            "TV_SHOWS" -> sharedPreferences.getInt(KEY_LAST_TVSHOWS_STREAM, -1)
            else -> -1
        }
        return if (streamId != -1) streamId else null
    }

    /**
     * Get the last content type that was played
     */
    fun getLastContentType(): String? {
        return sharedPreferences.getString(KEY_LAST_CONTENT_TYPE, null)
    }

    /**
     * Add a stream to watch history (max 25 most recent)
     */
    private fun addToWatchHistory(
        streamId: Int,
        streamName: String,
        categoryId: String,
        contentType: String,
        playbackPosition: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false
    ) {
        val history = getWatchHistory().toMutableList()

        // Remove existing entry if present (to update timestamp/position)
        history.removeAll { it.streamId == streamId && it.contentType == contentType }

        // Add new entry at the beginning
        history.add(0, WatchedStream(
            streamId, streamName, categoryId, contentType,
            System.currentTimeMillis(),
            playbackPosition, duration, isCompleted
        ))

        // Keep only last N items based on settings
        val trimmedHistory = history.take(providerSettings.watchHistorySize)

        // Save to cache
        val historyJson = json.encodeToString(trimmedHistory)
        sharedPreferences.edit().putString(KEY_WATCH_HISTORY, historyJson).apply()
    }

    /**
     * Get watch history (last 25 watched streams)
     */
    fun getWatchHistory(): List<WatchedStream> {
        val historyJson = sharedPreferences.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<WatchedStream>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear watch history
     */
    fun clearWatchHistory() {
        sharedPreferences.edit().remove(KEY_WATCH_HISTORY).apply()
    }

    /**
     * Add a stream to favorites
     */
    fun addFavorite(streamId: Int, streamName: String, categoryId: String, contentType: String): Boolean {
        val favorites = getFavorites().toMutableList()

        // Check for duplicate
        if (favorites.any { it.streamId == streamId && it.contentType == contentType }) {
            return false
        }

        // Add at beginning (newest first)
        favorites.add(0, FavoriteStream(streamId, streamName, categoryId, contentType))

        // Trim to max size
        val trimmed = favorites.take(providerSettings.favoritesMaxSize)

        // Save
        sharedPreferences.edit().putString(KEY_FAVORITES, json.encodeToString(trimmed)).apply()
        return true
    }

    /**
     * Remove a stream from favorites
     */
    fun removeFavorite(streamId: Int, contentType: String): Boolean {
        val favorites = getFavorites().toMutableList()
        val removed = favorites.removeAll { it.streamId == streamId && it.contentType == contentType }
        sharedPreferences.edit().putString(KEY_FAVORITES, json.encodeToString(favorites)).apply()
        return removed
    }

    /**
     * Get all favorites
     */
    fun getFavorites(): List<FavoriteStream> {
        val jsonStr = sharedPreferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FavoriteStream>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if a stream is favorited
     */
    fun isFavorite(streamId: Int, contentType: String): Boolean {
        return getFavorites().any { it.streamId == streamId && it.contentType == contentType }
    }

    /**
     * Clear all favorites
     */
    fun clearFavorites() {
        sharedPreferences.edit().remove(KEY_FAVORITES).apply()
    }

    /**
     * Save playback position for a stream
     */
    fun savePlaybackPosition(
        streamId: Int,
        streamName: String,
        categoryId: String,
        contentType: String,
        position: Long,
        duration: Long
    ) {
        // Skip for Live TV
        if (contentType == "LIVE_TV") return

        // Calculate completion
        val progressPercent = if (duration > 0) {
            (position.toFloat() / duration.toFloat()) * 100f
        } else 0f

        val isCompleted = progressPercent > 95.0f

        addToWatchHistory(
            streamId, streamName, categoryId, contentType,
            position, duration, isCompleted
        )
    }

    /**
     * Get saved playback position for a stream
     */
    fun getPlaybackPosition(streamId: Int, contentType: String): WatchedStream? {
        return getWatchHistory()
            .firstOrNull { it.streamId == streamId && it.contentType == contentType }
    }

    /**
     * Get in-progress streams (for Continue Watching category)
     */
    fun getInProgressStreams(contentType: String): List<WatchedStream> {
        return getWatchHistory()
            .filter {
                it.contentType == contentType &&
                !it.isCompleted &&
                it.playbackPosition > 0 &&
                it.duration > 0
            }
            .filter {
                val progressPercent = (it.playbackPosition.toFloat() / it.duration.toFloat()) * 100f
                progressPercent in 2.0..95.0 // Only 2-95% watched
            }
    }

    /**
     * Clear playback position for a stream (when user manually restarts)
     */
    fun clearPlaybackPosition(streamId: Int, contentType: String) {
        val history = getWatchHistory().toMutableList()
        val index = history.indexOfFirst {
            it.streamId == streamId && it.contentType == contentType
        }

        if (index != -1) {
            val item = history[index]
            history[index] = item.copy(playbackPosition = 0L, isCompleted = false)
            sharedPreferences.edit().putString(KEY_WATCH_HISTORY, json.encodeToString(history)).apply()
        }
    }
}
