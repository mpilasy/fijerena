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
        const val DEFAULT_WATCH_HISTORY_SIZE = 25
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
}
