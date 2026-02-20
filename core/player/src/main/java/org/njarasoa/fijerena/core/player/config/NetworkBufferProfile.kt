package org.njarasoa.fijerena.core.player.config

/**
 * Network type classification for adaptive buffering.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    UNKNOWN
}

/**
 * Design-token constants for network-aware buffer profiles, retry policies, and HTTP timeouts.
 * All magic numbers live here — logic files reference these only.
 */
object NetworkBufferProfile {

    // ── WiFi Live TV (existing values) ──────────────────────────────
    const val WIFI_LIVE_MIN_BUFFER_MS = 2_000
    const val WIFI_LIVE_MAX_BUFFER_MS = 5_000
    const val WIFI_LIVE_PLAYBACK_MS = 1_500
    const val WIFI_LIVE_REBUFFER_MS = 2_000
    const val WIFI_LIVE_BACK_BUFFER_MS = 0

    // ── WiFi VOD (existing values) ──────────────────────────────────
    const val WIFI_VOD_MIN_BUFFER_MS = 15_000
    const val WIFI_VOD_MAX_BUFFER_MS = 50_000
    const val WIFI_VOD_PLAYBACK_MS = 2_500
    const val WIFI_VOD_REBUFFER_MS = 5_000
    const val WIFI_VOD_BACK_BUFFER_MS = 10_000

    // ── Cellular Live TV ────────────────────────────────────────────
    const val CELLULAR_LIVE_MIN_BUFFER_MS = 15_000
    const val CELLULAR_LIVE_MAX_BUFFER_MS = 50_000
    const val CELLULAR_LIVE_PLAYBACK_MS = 2_000
    const val CELLULAR_LIVE_REBUFFER_MS = 5_000
    const val CELLULAR_LIVE_BACK_BUFFER_MS = 0

    // ── Cellular VOD ────────────────────────────────────────────────
    const val CELLULAR_VOD_MIN_BUFFER_MS = 30_000
    const val CELLULAR_VOD_MAX_BUFFER_MS = 120_000
    const val CELLULAR_VOD_PLAYBACK_MS = 2_000
    const val CELLULAR_VOD_REBUFFER_MS = 5_000
    const val CELLULAR_VOD_BACK_BUFFER_MS = 10_000

    // ── Retry policy ────────────────────────────────────────────────
    // Increase retries to handle initial connection failures without showing user errors
    const val WIFI_MIN_RETRY_COUNT = 5
    const val CELLULAR_MIN_RETRY_COUNT = 8
    const val RETRY_BASE_DELAY_MS = 500L
    const val RETRY_MAX_DELAY_MS = 5_000L

    // ── HTTP timeouts ───────────────────────────────────────────────
    const val WIFI_CONNECT_TIMEOUT_MS = 10_000
    const val WIFI_READ_TIMEOUT_MS = 20_000
    const val CELLULAR_CONNECT_TIMEOUT_MS = 45_000
    const val CELLULAR_READ_TIMEOUT_MS = 60_000

    // ── Cellular buffer multiplier functions ─────────────────────────
    // Apply multiplier to cellular buffers (WiFi always uses 1.0x)

    fun getCellularLiveMinBuffer(multiplier: Float): Int =
        (CELLULAR_LIVE_MIN_BUFFER_MS * multiplier).toInt()

    fun getCellularLiveMaxBuffer(multiplier: Float): Int =
        (CELLULAR_LIVE_MAX_BUFFER_MS * multiplier).toInt()

    fun getCellularLivePlayback(multiplier: Float): Int =
        (CELLULAR_LIVE_PLAYBACK_MS * multiplier).toInt()

    fun getCellularLiveRebuffer(multiplier: Float): Int =
        (CELLULAR_LIVE_REBUFFER_MS * multiplier).toInt()

    fun getCellularVodMinBuffer(multiplier: Float): Int =
        (CELLULAR_VOD_MIN_BUFFER_MS * multiplier).toInt()

    fun getCellularVodMaxBuffer(multiplier: Float): Int =
        (CELLULAR_VOD_MAX_BUFFER_MS * multiplier).toInt()

    fun getCellularVodPlayback(multiplier: Float): Int =
        (CELLULAR_VOD_PLAYBACK_MS * multiplier).toInt()

    fun getCellularVodRebuffer(multiplier: Float): Int =
        (CELLULAR_VOD_REBUFFER_MS * multiplier).toInt()
}
