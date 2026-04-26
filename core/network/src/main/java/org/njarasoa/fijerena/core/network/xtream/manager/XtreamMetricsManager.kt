package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.network.AppSettings
import java.util.concurrent.ConcurrentHashMap

class XtreamMetricsManager(
    private val appSettings: AppSettings,
) {
    // Fetch time tracking (in milliseconds)
    private val fetchTimes = ConcurrentHashMap<String, Long>()

    /**
     * Track fetch time for a specific key
     */
    fun trackFetchTime(
        key: String,
        timeMs: Long,
    ) {
        fetchTimes[key] = timeMs
    }

    /**
     * Get fetch time for a specific key in milliseconds
     */
    fun getFetchTime(key: String): Long? = fetchTimes[key]

    /**
     * Get fetch time for a specific key in human-readable format
     */
    fun getFetchTimeFormatted(key: String): String? {
        if (!appSettings.isDevMode) return null
        val timeMs = fetchTimes[key] ?: return null
        return "$timeMs ms"
    }

    fun removeFetchTime(key: String) {
        fetchTimes.remove(key)
    }

    fun clearFetchTimes() {
        fetchTimes.clear()
    }
}
