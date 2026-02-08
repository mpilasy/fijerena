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
    const val WIFI_LIVE_PLAYBACK_MS = 250
    const val WIFI_LIVE_REBUFFER_MS = 500
    const val WIFI_LIVE_BACK_BUFFER_MS = 0

    // ── WiFi VOD (existing values) ──────────────────────────────────
    const val WIFI_VOD_MIN_BUFFER_MS = 15_000
    const val WIFI_VOD_MAX_BUFFER_MS = 50_000
    const val WIFI_VOD_PLAYBACK_MS = 2_500
    const val WIFI_VOD_REBUFFER_MS = 5_000
    const val WIFI_VOD_BACK_BUFFER_MS = 10_000

    // ── Cellular Live TV ────────────────────────────────────────────
    const val CELLULAR_LIVE_MIN_BUFFER_MS = 8_000
    const val CELLULAR_LIVE_MAX_BUFFER_MS = 20_000
    const val CELLULAR_LIVE_PLAYBACK_MS = 1_500
    const val CELLULAR_LIVE_REBUFFER_MS = 2_000
    const val CELLULAR_LIVE_BACK_BUFFER_MS = 0

    // ── Cellular VOD ────────────────────────────────────────────────
    const val CELLULAR_VOD_MIN_BUFFER_MS = 40_000
    const val CELLULAR_VOD_MAX_BUFFER_MS = 100_000
    const val CELLULAR_VOD_PLAYBACK_MS = 8_000
    const val CELLULAR_VOD_REBUFFER_MS = 10_000
    const val CELLULAR_VOD_BACK_BUFFER_MS = 10_000

    // ── Retry policy ────────────────────────────────────────────────
    const val WIFI_MIN_RETRY_COUNT = 3
    const val CELLULAR_MIN_RETRY_COUNT = 6
    const val RETRY_BASE_DELAY_MS = 1_000L
    const val RETRY_MAX_DELAY_MS = 10_000L

    // ── HTTP timeouts ───────────────────────────────────────────────
    const val WIFI_CONNECT_TIMEOUT_MS = 30_000
    const val WIFI_READ_TIMEOUT_MS = 60_000
    const val CELLULAR_CONNECT_TIMEOUT_MS = 45_000
    const val CELLULAR_READ_TIMEOUT_MS = 90_000
}
