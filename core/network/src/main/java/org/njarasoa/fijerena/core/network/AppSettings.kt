package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages application settings and preferences.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_DEV_MODE = "dev_mode"
        private const val KEY_WATCH_HISTORY_SIZE = "watch_history_size"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_FAVORITES_MAX_SIZE = "favorites_max_size"
        private const val KEY_AUTO_RESUME = "auto_resume_enabled"
        private const val KEY_CACHE_EXPIRY_HOURS = "cache_expiry_hours"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_EPG_TIMEZONE_OFFSET = "epg_timezone_offset"
        const val DEFAULT_WATCH_HISTORY_SIZE = 25
        const val DEFAULT_FAVORITES_MAX_SIZE = 100
        const val DEFAULT_CACHE_EXPIRY_HOURS = 24
        const val DEFAULT_UI_SCALE = 1.0f
        const val DEFAULT_EPG_URL = ""
    }

    /**
     * Enable or disable developer mode.
     */
    var isDevMode: Boolean
        get() = prefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_MODE, value).apply()

    /**
     * Get or set the maximum size of the watch history queue.
     */
    var watchHistorySize: Int
        get() = prefs.getInt(KEY_WATCH_HISTORY_SIZE, DEFAULT_WATCH_HISTORY_SIZE)
        set(value) {
            val clampedValue = value.coerceIn(1, 100)
            prefs.edit().putInt(KEY_WATCH_HISTORY_SIZE, clampedValue).apply()
        }

    /**
     * Get or set the provider name.
     */
    var providerName: String
        get() = prefs.getString(KEY_PROVIDER_NAME, "My Provider") ?: "My Provider"
        set(value) = prefs.edit().putString(KEY_PROVIDER_NAME, value).apply()

    /**
     * Get or set the maximum size of the favorites queue.
     */
    var favoritesMaxSize: Int
        get() = prefs.getInt(KEY_FAVORITES_MAX_SIZE, DEFAULT_FAVORITES_MAX_SIZE)
        set(value) {
            val clampedValue = value.coerceIn(10, 500)
            prefs.edit().putInt(KEY_FAVORITES_MAX_SIZE, clampedValue).apply()
        }

    /**
     * Get or set auto-resume playback setting.
     */
    var autoResumeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESUME, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RESUME, value).apply()

    /**
     * Get or set cache expiry duration in hours.
     * Default: 24 hours (1 day)
     * Range: 1-168 hours (1 hour to 7 days)
     */
    var cacheExpiryHours: Int
        get() = prefs.getInt(KEY_CACHE_EXPIRY_HOURS, DEFAULT_CACHE_EXPIRY_HOURS)
        set(value) {
            val clampedValue = value.coerceIn(1, 168) // 1 hour to 7 days
            prefs.edit().putInt(KEY_CACHE_EXPIRY_HOURS, clampedValue).apply()
        }

    /**
     * Get cache expiry duration in milliseconds.
     */
    val cacheExpiryMs: Long
        get() = cacheExpiryHours * 60 * 60 * 1000L

    /**
     * Get or set UI scale for category/grid screens.
     * Values: 0.7f (70%), 0.8f (80%), 0.9f (90%), 1.0f (100%)
     */
    var uiScale: Float
        get() = prefs.getFloat(KEY_UI_SCALE, DEFAULT_UI_SCALE)
        set(value) {
            val clampedValue = value.coerceIn(0.7f, 1.0f)
            prefs.edit().putFloat(KEY_UI_SCALE, clampedValue).apply()
        }

    var themeId: String
        get() = prefs.getString(KEY_THEME_ID, "deep_night") ?: "deep_night"
        set(value) = prefs.edit().putString(KEY_THEME_ID, value).apply()

    /**
     * Get or set the external XMLTV EPG URL (global setting, applies to all providers).
     * Empty string means no external EPG is configured.
     */
    var epgUrl: String
        get() = prefs.getString(KEY_EPG_URL, DEFAULT_EPG_URL) ?: DEFAULT_EPG_URL
        set(value) = prefs.edit().putString(KEY_EPG_URL, value.trim()).apply()

    /**
     * Timezone offset override for XMLTV data, in hours (e.g., 8 for UTC+8, -5 for UTC-5).
     * When non-zero, replaces the timezone offset in XMLTV timestamps during parsing.
     * This fixes XMLTV sources that encode local times but mislabel them as UTC (+0000).
     * Default: 0 (use timezone from XMLTV data as-is).
     */
    var epgTimezoneOffsetHours: Int
        get() = prefs.getInt(KEY_EPG_TIMEZONE_OFFSET, 0)
        set(value) {
            val clamped = value.coerceIn(-12, 14)
            prefs.edit().putInt(KEY_EPG_TIMEZONE_OFFSET, clamped).apply()
        }
}
