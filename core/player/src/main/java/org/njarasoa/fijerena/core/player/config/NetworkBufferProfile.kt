package org.njarasoa.fijerena.core.player.config

/**
 * Network type classification for adaptive buffering.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    UNKNOWN,
}

/**
 * Design-token constants for network-aware buffer profiles, retry policies, and HTTP timeouts.
 * All magic numbers live here — logic files reference these only.
 */
object NetworkBufferProfile {
    // ── WiFi Live TV (optimized for fast start + proactive recycle bridge) ─────
    const val WIFI_LIVE_MIN_BUFFER_MS = 15_000
    const val WIFI_LIVE_MAX_BUFFER_MS = 30_000
    const val WIFI_LIVE_PLAYBACK_MS = 500
    const val WIFI_LIVE_REBUFFER_MS = 1_000
    const val WIFI_LIVE_BACK_BUFFER_MS = 0

    // ── WiFi VOD ────────────────────────────────────────────────────
    const val WIFI_VOD_MIN_BUFFER_MS = 30_000
    const val WIFI_VOD_MAX_BUFFER_MS = 60_000
    const val WIFI_VOD_PLAYBACK_MS = 2_500
    const val WIFI_VOD_REBUFFER_MS = 10_000
    const val WIFI_VOD_BACK_BUFFER_MS = 10_000

    // ── Cellular Live TV ────────────────────────────────────────────
    const val CELLULAR_LIVE_MIN_BUFFER_MS = 50_000
    const val CELLULAR_LIVE_MAX_BUFFER_MS = 50_000
    const val CELLULAR_LIVE_PLAYBACK_MS = 2_500
    const val CELLULAR_LIVE_REBUFFER_MS = 5_000
    const val CELLULAR_LIVE_BACK_BUFFER_MS = 0

    // ── Cellular VOD ────────────────────────────────────────────────
    const val CELLULAR_VOD_MIN_BUFFER_MS = 40_000
    const val CELLULAR_VOD_MAX_BUFFER_MS = 100_000
    const val CELLULAR_VOD_PLAYBACK_MS = 8_000
    const val CELLULAR_VOD_REBUFFER_MS = 10_000
    const val CELLULAR_VOD_BACK_BUFFER_MS = 10_000

    // ── Retry policy ────────────────────────────────────────────────
    // Increase retries to handle initial connection failures without showing user errors
    const val WIFI_MIN_RETRY_COUNT = 5
    const val CELLULAR_MIN_RETRY_COUNT = 8
    const val RETRY_BASE_DELAY_MS = 500L
    const val RETRY_MAX_DELAY_MS = 5_000L

    // ── HTTP timeouts ───────────────────────────────────────────────
    const val WIFI_CONNECT_TIMEOUT_MS = 15_000
    const val WIFI_READ_TIMEOUT_MS = 30_000
    const val CELLULAR_CONNECT_TIMEOUT_MS = 45_000
    const val CELLULAR_READ_TIMEOUT_MS = 30_000

    // ── Target buffer byte caps ────────────────────────────────────
    // DefaultLoadControl's automatic byte-size default (a few MB, independent of the stream's
    // actual bitrate) combined with prioritizeTimeOverSizeThresholds(true) let a high-bitrate
    // 4K VOD stream keep buffering well past it while chasing the time-based target, risking
    // hundreds of MB of native allocation on 1-2GB Android TV devices. An explicit cap bounds
    // worst-case memory regardless of stream bitrate.
    const val VOD_TARGET_BUFFER_BYTES = 64 * 1024 * 1024
    // 16MB left high-bitrate 4K live (25-40Mbps) hitting this cap in ~3-5s, well short of the
    // 15-50s time-based targets above — see AdaptiveLoadControl.buildDelegate()'s
    // prioritizeTimeOverSize, which now favors time for LIVE_TV. This cap still bounds worst-case
    // native memory; just doubled for headroom on 4K/60fps streams.
    const val LIVE_TARGET_BUFFER_BYTES = 32 * 1024 * 1024

    // ── Cellular buffer multiplier functions ─────────────────────────
    // Apply multiplier to cellular buffers (WiFi always uses 1.0x)

    fun getCellularLiveMinBuffer(multiplier: Float): Int = (CELLULAR_LIVE_MIN_BUFFER_MS * multiplier).toInt()

    fun getCellularLiveMaxBuffer(multiplier: Float): Int = (CELLULAR_LIVE_MAX_BUFFER_MS * multiplier).toInt()

    fun getCellularLivePlayback(multiplier: Float): Int = (CELLULAR_LIVE_PLAYBACK_MS * multiplier).toInt()

    fun getCellularLiveRebuffer(multiplier: Float): Int = (CELLULAR_LIVE_REBUFFER_MS * multiplier).toInt()

    fun getCellularVodMinBuffer(multiplier: Float): Int = (CELLULAR_VOD_MIN_BUFFER_MS * multiplier).toInt()

    fun getCellularVodMaxBuffer(multiplier: Float): Int = (CELLULAR_VOD_MAX_BUFFER_MS * multiplier).toInt()

    fun getCellularVodPlayback(multiplier: Float): Int = (CELLULAR_VOD_PLAYBACK_MS * multiplier).toInt()

    fun getCellularVodRebuffer(multiplier: Float): Int = (CELLULAR_VOD_REBUFFER_MS * multiplier).toInt()
}
