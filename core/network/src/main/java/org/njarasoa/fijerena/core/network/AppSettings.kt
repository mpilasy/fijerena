package org.njarasoa.fijerena.core.network
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue

/**
 * Manages application settings and preferences.
 */
class AppSettings(
    private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            "app_settings",
            Context.MODE_PRIVATE,
        )
    }

    companion object {
        private const val KEY_DEV_MODE = "dev_mode"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_LAST_PROVIDER = "last_provider"
        private const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
        private const val KEY_WATCH_HISTORY_SIZE = "watch_history_size"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_FAVORITES_MAX_SIZE = "favorites_max_size"
        private const val KEY_AUTO_RESUME = "auto_resume_enabled"
        private const val KEY_CACHE_EXPIRY_HOURS = "cache_expiry_hours"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_UI_STYLE_ID = "ui_style_id"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_EPG_TIMEZONE_OFFSET = "epg_timezone_offset"
        private const val KEY_EPG_AUTO_REFRESH = "epg_auto_refresh"
        private const val KEY_EPG_REFRESH_TIME = "epg_refresh_time"
        private const val KEY_EPG_REFRESH_INTERVAL = "epg_refresh_interval"
        private const val KEY_CONTENT_AUTO_REFRESH = "content_auto_refresh"
        private const val KEY_CONTENT_REFRESH_TIME = "content_refresh_time"
        private const val KEY_CELLULAR_LIVE_MULTIPLIER = "cellular_live_multiplier"
        private const val KEY_CELLULAR_VOD_MULTIPLIER = "cellular_vod_multiplier"
        private const val KEY_HAS_PROVIDER_CACHE = "has_provider_cache"
        private const val KEY_FAVORITE_CATEGORY_ROWS_PURGED = "favorite_category_rows_purged_v1"
        private const val KEY_WATCH_DELAY_SECONDS = "watch_delay_seconds"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_EPG_SEARCH_HISTORY = "epg_search_history"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SHARE_NOW_PLAYING = "share_now_playing"
        private const val KEY_LAST_SHRINK_AT_MS = "last_shrink_at_ms"
        private const val KEY_LAST_SHRINK_DURATION_MS = "last_shrink_duration_ms"
        private const val KEY_LAST_SHRINK_ROWS_REMOVED = "last_shrink_rows_removed"
        private const val KEY_LAST_SHRINK_BYTES_RECLAIMED = "last_shrink_bytes_reclaimed"
        private const val MAX_SEARCH_HISTORY = 20
        const val DEFAULT_WATCH_HISTORY_SIZE = 25
        const val DEFAULT_WATCH_DELAY_SECONDS = 10
        const val MIN_WATCH_DELAY_SECONDS = 5
        const val MAX_WATCH_DELAY_SECONDS = 120
        const val DEFAULT_FAVORITES_MAX_SIZE = 100
        const val DEFAULT_CACHE_EXPIRY_HOURS = 24
        const val DEFAULT_UI_SCALE = 0.8f
        const val DEFAULT_EPG_URL = ""
        const val DEFAULT_EPG_REFRESH_TIME = "02:00"
        const val DEFAULT_EPG_REFRESH_INTERVAL = 24
        const val DEFAULT_CONTENT_REFRESH_TIME = "04:00"
        const val DEFAULT_CELLULAR_MULTIPLIER = 1.0f

        /** Settings kept the same on every device by live sync; those in [PER_PROFILE_SETTING_KEYS] are per profile. */
        val SYNCED_SETTING_KEYS =
            listOf(
                KEY_THEME_ID,
                KEY_DEV_MODE,
                KEY_EPG_AUTO_REFRESH,
                KEY_EPG_REFRESH_TIME,
                KEY_EPG_REFRESH_INTERVAL,
                KEY_LAST_PROVIDER,
                KEY_AUTOPLAY_NEXT_EPISODE,
            )
        val PER_PROFILE_SETTING_KEYS = setOf(KEY_DEV_MODE, KEY_LAST_PROVIDER, KEY_AUTOPLAY_NEXT_EPISODE)
        const val MIN_CELLULAR_MULTIPLIER = 0.5f
        const val MAX_CELLULAR_MULTIPLIER = 3.0f
    }

    /**
     * The profile using this device. Per device and never synced: the TV and a phone are often in
     * different hands at the same time. See docs/plans/20260929_live-sync-plan.md → User profiles.
     */
    var activeProfileId: String
        get() = prefs.getString(KEY_ACTIVE_PROFILE_ID, null) ?: ProfileEntity.DEFAULT_ID
        set(value) = prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, value) }

    /**
     * Whether live sync tells the group what this device is playing. Per device — not per profile
     * and never synced (not in [SYNCED_SETTING_KEYS]): turning it on for the kids' TV must not turn
     * it on everywhere. Off until turned on. See docs/plans/20261001_live-sync-now-playing-plan.md.
     */
    var shareNowPlaying: Boolean
        get() = prefs.getBoolean(KEY_SHARE_NOW_PLAYING, false)
        set(value) = prefs.edit { putBoolean(KEY_SHARE_NOW_PLAYING, value) }

    /**
     * Developer mode of the active profile — each profile has its own, off until turned on. See
     * docs/plans/20260930_profile-scoped-settings-plan.md. Falls back to the install-wide flag
     * this replaced until [copyLegacyDevModeToProfiles] has run.
     */
    var isDevMode: Boolean
        get() = prefs.getBoolean(devModeKey(activeProfileId), prefs.getBoolean(KEY_DEV_MODE, false))
        set(value) {
            prefs.edit { putBoolean(devModeKey(activeProfileId), value) }
            SettingsSyncQueue.setting(context, KEY_DEV_MODE, activeProfileId)
        }

    /**
     * One-time upgrade: gives every profile in [profileIds] the install-wide developer-mode flag
     * (unless it already has its own), then drops that flag so profiles added later start off.
     */
    fun copyLegacyDevModeToProfiles(profileIds: List<String>) {
        if (!prefs.contains(KEY_DEV_MODE)) return
        val legacy = prefs.getBoolean(KEY_DEV_MODE, false)
        prefs.edit {
            profileIds.filterNot { prefs.contains(devModeKey(it)) }.forEach { putBoolean(devModeKey(it), legacy) }
            remove(KEY_DEV_MODE)
        }
    }

    /**
     * Whether the active profile's TV-show episodes roll on to the next one, after a short
     * countdown, when they end. Per profile and synced, like [isDevMode]; off until turned on.
     */
    var autoplayNextEpisode: Boolean
        get() = prefs.getBoolean(profileKey(KEY_AUTOPLAY_NEXT_EPISODE, activeProfileId), false)
        set(value) {
            prefs.edit { putBoolean(profileKey(KEY_AUTOPLAY_NEXT_EPISODE, activeProfileId), value) }
            SettingsSyncQueue.setting(context, KEY_AUTOPLAY_NEXT_EPISODE, activeProfileId)
        }

    /**
     * The `providerKey` of the provider [profileId] last picked, on any device — synced, unlike
     * `providers.isActive`, which is the provider this device is on. A profile switch moves the
     * device to it. See docs/plans/20261002_profile-last-provider-plan.md.
     */
    fun lastProviderKey(profileId: String): String? = prefs.getString(profileKey(KEY_LAST_PROVIDER, profileId), null)

    fun setLastProviderKey(
        profileId: String,
        providerKey: String,
    ) {
        if (lastProviderKey(profileId) == providerKey) return
        prefs.edit { putString(profileKey(KEY_LAST_PROVIDER, profileId), providerKey) }
        SettingsSyncQueue.setting(context, KEY_LAST_PROVIDER, profileId)
    }

    /**
     * A setting received from another device (live sync): written straight to prefs, not through
     * the setters, which would queue it to be sent back. Keys other than [SYNCED_SETTING_KEYS] are
     * ignored — a newer app version may sync more.
     */
    fun applyRemoteSetting(
        key: String,
        profileId: String,
        value: kotlinx.serialization.json.JsonPrimitive,
    ) {
        prefs.edit {
            when (key) {
                KEY_DEV_MODE -> value.booleanOrNull?.let { putBoolean(devModeKey(profileId), it) }
                KEY_AUTOPLAY_NEXT_EPISODE -> value.booleanOrNull?.let { putBoolean(profileKey(key, profileId), it) }
                KEY_LAST_PROVIDER -> if (value.isString) putString(profileKey(key, profileId), value.content)
                KEY_THEME_ID, KEY_EPG_REFRESH_TIME -> if (value.isString) putString(key, value.content)
                KEY_EPG_AUTO_REFRESH -> value.booleanOrNull?.let { putBoolean(key, it) }
                KEY_EPG_REFRESH_INTERVAL -> value.intOrNull?.let { putInt(key, it) }
            }
        }
    }

    /** The value of a synced setting as it is sent — [profileId] matters only for [PER_PROFILE_SETTING_KEYS]. */
    fun syncedSetting(
        key: String,
        profileId: String,
    ): kotlinx.serialization.json.JsonPrimitive? {
        val stored = if (key in PER_PROFILE_SETTING_KEYS) profileKey(key, profileId) else key
        if (!prefs.contains(stored)) return null
        return when (key) {
            KEY_DEV_MODE, KEY_EPG_AUTO_REFRESH, KEY_AUTOPLAY_NEXT_EPISODE -> {
                kotlinx.serialization.json.JsonPrimitive(
                    prefs.getBoolean(stored, false),
                )
            }

            KEY_THEME_ID, KEY_EPG_REFRESH_TIME, KEY_LAST_PROVIDER -> {
                kotlinx.serialization.json.JsonPrimitive(prefs.getString(stored, null))
            }

            KEY_EPG_REFRESH_INTERVAL -> {
                kotlinx.serialization.json.JsonPrimitive(prefs.getInt(stored, DEFAULT_EPG_REFRESH_INTERVAL))
            }

            else -> {
                null
            }
        }
    }

    /** Drops a deleted profile's developer-mode flag. */
    fun removeDevMode(profileId: String) = prefs.edit { remove(devModeKey(profileId)) }

    /** Drops a deleted profile's autoplay-next-episode choice. */
    fun removeAutoplayNextEpisode(profileId: String) = prefs.edit { remove(profileKey(KEY_AUTOPLAY_NEXT_EPISODE, profileId)) }

    /** Drops a deleted profile's last picked provider. */
    fun removeLastProvider(profileId: String) = prefs.edit { remove(profileKey(KEY_LAST_PROVIDER, profileId)) }

    private fun devModeKey(profileId: String) = profileKey(KEY_DEV_MODE, profileId)

    private fun profileKey(
        key: String,
        profileId: String,
    ) = "${key}_$profileId"

    /**
     * Get or set the maximum size of the watch history queue.
     */
    var watchHistorySize: Int
        get() = prefs.getInt(KEY_WATCH_HISTORY_SIZE, DEFAULT_WATCH_HISTORY_SIZE)
        set(value) {
            val clampedValue = value.coerceIn(1, 100)
            prefs.edit { putInt(KEY_WATCH_HISTORY_SIZE, clampedValue) }
        }

    /**
     * Get or set the provider name.
     */
    var providerName: String
        get() = prefs.getString(KEY_PROVIDER_NAME, "My Provider") ?: "My Provider"
        set(value) = prefs.edit { putString(KEY_PROVIDER_NAME, value) }

    /**
     * Get or set the maximum size of the favorites queue.
     */
    var favoritesMaxSize: Int
        get() = prefs.getInt(KEY_FAVORITES_MAX_SIZE, DEFAULT_FAVORITES_MAX_SIZE)
        set(value) {
            val clampedValue = value.coerceIn(10, 500)
            prefs.edit { putInt(KEY_FAVORITES_MAX_SIZE, clampedValue) }
        }

    /**
     * Get or set auto-resume playback setting.
     */
    var autoResumeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESUME, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RESUME, value) }

    /**
     * Get or set cache expiry duration in hours.
     * Default: 24 hours (1 day)
     * Range: 1-168 hours (1 hour to 7 days)
     */
    var cacheExpiryHours: Int
        get() = prefs.getInt(KEY_CACHE_EXPIRY_HOURS, DEFAULT_CACHE_EXPIRY_HOURS)
        set(value) {
            val clampedValue = value.coerceIn(1, 168) // 1 hour to 7 days
            prefs.edit { putInt(KEY_CACHE_EXPIRY_HOURS, clampedValue) }
        }

    /**
     * Get cache expiry duration in milliseconds.
     */
    val cacheExpiryMs: Long
        get() = cacheExpiryHours * 60 * 60 * 1000L

    /**
     * Get or set UI scale for category/grid screens.
     * Values: 0.4f (40%), 0.6f (60%), 0.8f (80%), 1.0f (100%)
     */
    var uiScale: Float
        get() = prefs.getFloat(KEY_UI_SCALE, DEFAULT_UI_SCALE)
        set(value) {
            val clampedValue = value.coerceIn(0.4f, 1.0f)
            prefs.edit { putFloat(KEY_UI_SCALE, clampedValue) }
        }

    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, "deep_night") ?: "deep_night"
        set(value) {
            prefs.edit { putString(KEY_THEME_ID, value) }
            SettingsSyncQueue.setting(context, KEY_THEME_ID)
        }

    /** Platform-inspired look-and-feel preset (shape/type/icon/grid/dialog character), independent of [themeId]'s color. */
    var uiStyleId: String
        get() = prefs.getString(KEY_UI_STYLE_ID, "material") ?: "material"
        set(value) = prefs.edit { putString(KEY_UI_STYLE_ID, value) }

    /**
     * Get or set application language (ISO 639-1 code).
     * Supported: "en", "mg"
     * Default: "en"
     */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    /**
     * Get or set the external XMLTV EPG URL (global setting, applies to all providers).
     * Empty string means no external EPG is configured.
     */
    var epgUrl: String
        get() = prefs.getString(KEY_EPG_URL, DEFAULT_EPG_URL) ?: DEFAULT_EPG_URL
        set(value) = prefs.edit { putString(KEY_EPG_URL, value.trim()) }

    /**
     * Timezone offset override for XMLTV data, in hours (e.g., 8 for UTC+8, -5 for UTC-5).
     * When non-zero, replaces the timezone offset in XMLTV timestamps during parsing.
     * This fixes XMLTV sources that encode local times but mislabel them as UTC (+0000).
     * Default: 0 (use timezone from XMLTV data as-is).
     */
    var epgAutoRefreshEnabled: Boolean
        get() = prefs.getBoolean(KEY_EPG_AUTO_REFRESH, true)
        set(value) {
            prefs.edit { putBoolean(KEY_EPG_AUTO_REFRESH, value) }
            SettingsSyncQueue.setting(context, KEY_EPG_AUTO_REFRESH)
        }

    /**
     * EPG refresh start time (HH:mm format).
     * Default: 02:00
     */
    var epgRefreshTime: String
        get() = prefs.getString(KEY_EPG_REFRESH_TIME, DEFAULT_EPG_REFRESH_TIME) ?: DEFAULT_EPG_REFRESH_TIME
        set(value) {
            prefs.edit { putString(KEY_EPG_REFRESH_TIME, value) }
            SettingsSyncQueue.setting(context, KEY_EPG_REFRESH_TIME)
        }

    /**
     * EPG refresh interval in hours.
     * Options: 4, 8, 12, 24, 48, or -1 (Never).
     * Default: 24 hours.
     */
    var epgRefreshInterval: Int
        get() = prefs.getInt(KEY_EPG_REFRESH_INTERVAL, DEFAULT_EPG_REFRESH_INTERVAL)
        set(value) {
            prefs.edit { putInt(KEY_EPG_REFRESH_INTERVAL, value) }
            SettingsSyncQueue.setting(context, KEY_EPG_REFRESH_INTERVAL)
        }

    /**
     * Enable or disable automatic background refresh of provider content (categories/streams).
     */
    var contentAutoRefreshEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONTENT_AUTO_REFRESH, true)
        set(value) = prefs.edit { putBoolean(KEY_CONTENT_AUTO_REFRESH, value) }

    /**
     * Content refresh start time (HH:mm format).
     * Default: 04:00
     */
    var contentRefreshTime: String
        get() = prefs.getString(KEY_CONTENT_REFRESH_TIME, DEFAULT_CONTENT_REFRESH_TIME) ?: DEFAULT_CONTENT_REFRESH_TIME
        set(value) = prefs.edit { putString(KEY_CONTENT_REFRESH_TIME, value) }

    var epgTimezoneOffsetHours: Int
        get() = prefs.getInt(KEY_EPG_TIMEZONE_OFFSET, 0)
        set(value) {
            val clamped = value.coerceIn(-12, 14)
            prefs.edit { putInt(KEY_EPG_TIMEZONE_OFFSET, clamped) }
        }

    /**
     * Get or set the cellular buffer multiplier for Live TV (0.5x - 3.0x).
     * Default: 1.0x (use baseline values)
     */
    var cellularLiveMultiplier: Float
        get() = prefs.getFloat(KEY_CELLULAR_LIVE_MULTIPLIER, DEFAULT_CELLULAR_MULTIPLIER)
        set(value) {
            val clamped = value.coerceIn(MIN_CELLULAR_MULTIPLIER, MAX_CELLULAR_MULTIPLIER)
            prefs.edit { putFloat(KEY_CELLULAR_LIVE_MULTIPLIER, clamped) }
        }

    /**
     * Get or set the cellular buffer multiplier for VOD (0.5x - 3.0x).
     * Default: 1.0x (use baseline values)
     */
    var cellularVodMultiplier: Float
        get() = prefs.getFloat(KEY_CELLULAR_VOD_MULTIPLIER, DEFAULT_CELLULAR_MULTIPLIER)
        set(value) {
            val clamped = value.coerceIn(MIN_CELLULAR_MULTIPLIER, MAX_CELLULAR_MULTIPLIER)
            prefs.edit { putFloat(KEY_CELLULAR_VOD_MULTIPLIER, clamped) }
        }

    /**
     * Cached flag for whether at least one provider exists.
     * Used for fast startup — avoids Room DB query on cold start.
     * Must be updated whenever providers are added or removed.
     */

    /**
     * Delay in seconds before a live channel is marked as "watched" (added to Last Watched).
     * Range: 5-120 seconds. Default: 30 seconds.
     */
    var watchDelaySeconds: Int
        get() = prefs.getInt(KEY_WATCH_DELAY_SECONDS, DEFAULT_WATCH_DELAY_SECONDS)
        set(value) {
            val clamped = value.coerceIn(MIN_WATCH_DELAY_SECONDS, MAX_WATCH_DELAY_SECONDS)
            prefs.edit { putInt(KEY_WATCH_DELAY_SECONDS, clamped) }
        }

    var hasProviderCache: Boolean
        get() = prefs.getBoolean(KEY_HAS_PROVIDER_CACHE, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_PROVIDER_CACHE, value) }

    /** One-time flag: [FavoriteCategoryRowCleanup] has run on this install. Per device, never synced. */
    var favoriteCategoryRowsPurged: Boolean
        get() = prefs.getBoolean(KEY_FAVORITE_CATEGORY_ROWS_PURGED, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_FAVORITE_CATEGORY_ROWS_PURGED, value) }

    // Search and EPG search history belong to the active profile, like developer mode: stored
    // under `<key>_<profileId>`. Per device and never synced.

    /**
     * Get the active profile's search history as an ordered list (most recent first).
     */
    fun getSearchHistory(): List<String> = readHistory(KEY_SEARCH_HISTORY)

    /**
     * Add a search term to history. Deduplicates (case-insensitive) and caps at [MAX_SEARCH_HISTORY].
     */
    fun addSearchHistory(query: String) = addToHistory(KEY_SEARCH_HISTORY, query)

    /**
     * Remove a single entry from search history.
     */
    fun removeSearchHistory(query: String) = removeFromHistory(KEY_SEARCH_HISTORY, query)

    /**
     * Clear all search history.
     */
    fun clearSearchHistory() {
        prefs.edit { remove(historyKey(KEY_SEARCH_HISTORY, activeProfileId)) }
    }

    /**
     * Get the active profile's EPG search history as an ordered list (most recent first).
     */
    fun getEpgSearchHistory(): List<String> = readHistory(KEY_EPG_SEARCH_HISTORY)

    /**
     * Add a search term to EPG history. Deduplicates (case-insensitive) and caps at [MAX_SEARCH_HISTORY].
     */
    fun addEpgSearchHistory(query: String) = addToHistory(KEY_EPG_SEARCH_HISTORY, query)

    /**
     * Remove a single entry from EPG search history.
     */
    fun removeEpgSearchHistory(query: String) = removeFromHistory(KEY_EPG_SEARCH_HISTORY, query)

    /**
     * Clear all EPG search history.
     */
    fun clearEpgSearchHistory() {
        prefs.edit { remove(historyKey(KEY_EPG_SEARCH_HISTORY, activeProfileId)) }
    }

    /**
     * One-time upgrade: the install-wide search histories from before they were per profile go to
     * the default profile (unless it already has its own), so other profiles start empty.
     */
    fun moveLegacySearchHistoryToDefaultProfile() {
        val legacyKeys = listOf(KEY_SEARCH_HISTORY, KEY_EPG_SEARCH_HISTORY).filter { prefs.contains(it) }
        if (legacyKeys.isEmpty()) return
        prefs.edit {
            legacyKeys.forEach { legacy ->
                val target = historyKey(legacy, ProfileEntity.DEFAULT_ID)
                if (!prefs.contains(target)) putString(target, prefs.getString(legacy, null))
                remove(legacy)
            }
        }
    }

    /** Drops a deleted profile's search and EPG search history. */
    fun removeProfileSearchHistory(profileId: String) =
        prefs.edit {
            remove(historyKey(KEY_SEARCH_HISTORY, profileId))
            remove(historyKey(KEY_EPG_SEARCH_HISTORY, profileId))
        }

    private fun historyKey(
        key: String,
        profileId: String,
    ) = "${key}_$profileId"

    private fun readHistory(key: String): List<String> {
        val joined = prefs.getString(historyKey(key, activeProfileId), null) ?: return emptyList()
        return joined.split("\u001F").filter { it.isNotBlank() }
    }

    private fun addToHistory(
        key: String,
        query: String,
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = readHistory(key).toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = current.take(MAX_SEARCH_HISTORY)
        prefs.edit { putString(historyKey(key, activeProfileId), capped.joinToString("\u001F")) }
    }

    private fun removeFromHistory(
        key: String,
        query: String,
    ) {
        val current = readHistory(key).toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        prefs.edit { putString(historyKey(key, activeProfileId), current.joinToString("\u001F")) }
    }

    /**
     * Reset both cellular buffer multipliers to default (1.0x).
     */
    fun resetCellularBuffers() {
        cellularLiveMultiplier = DEFAULT_CELLULAR_MULTIPLIER
        cellularVodMultiplier = DEFAULT_CELLULAR_MULTIPLIER
    }

    var lastShrinkAtMs: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_AT_MS, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_AT_MS, value) }

    var lastShrinkDurationMs: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_DURATION_MS, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_DURATION_MS, value) }

    var lastShrinkRowsRemoved: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_ROWS_REMOVED, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_ROWS_REMOVED, value) }

    var lastShrinkBytesReclaimed: Long
        get() = prefs.getLong(KEY_LAST_SHRINK_BYTES_RECLAIMED, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SHRINK_BYTES_RECLAIMED, value) }

    fun saveShrinkStats(
        durationMs: Long,
        rowsRemoved: Long,
        bytesReclaimed: Long,
    ) {
        prefs.edit {
            putLong(KEY_LAST_SHRINK_AT_MS, System.currentTimeMillis())
            putLong(KEY_LAST_SHRINK_DURATION_MS, durationMs)
            putLong(KEY_LAST_SHRINK_ROWS_REMOVED, rowsRemoved)
            putLong(KEY_LAST_SHRINK_BYTES_RECLAIMED, bytesReclaimed)
        }
    }
}

/** Interval-hour choices for the EPG auto-refresh setting; -1 means "Never". Labels are localized at the UI layer. */
val EPG_REFRESH_INTERVAL_OPTIONS = listOf(-1, 4, 8, 12, 24, 48)
