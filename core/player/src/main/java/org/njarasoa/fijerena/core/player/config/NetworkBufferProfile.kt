package org.njarasoa.fijerena.core.player.config

enum class NetworkType {
    WIFI,
    CELLULAR,
    UNKNOWN,
}

/**
 * Constants for network-aware buffer profiles, retry policies and HTTP timeouts. Logic files use
 * only these, no magic numbers.
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

    // After a stall, hold 3 s before playing on: about ExoPlayer's 5 s default, and no more than a
    // 4K remux's byte cap can hold. Repeated stalls seen with 2 s were from the health monitor
    // recycling VOD mid-buffer (fixed).
    const val WIFI_VOD_REBUFFER_MS = 3_000

    // No back buffer for VOD: ExoPlayer counts the back buffer against the byte cap, so on a ~100
    // Mbps 4K remux 10 s of it (~125 MB) filled the cap and starved the forward buffer below 500 ms
    // (a near-stall every 15-25 s on a Shield). A seek back downloads again instead, in about a
    // second.
    const val WIFI_VOD_BACK_BUFFER_MS = 0

    // ── Cellular Live TV ────────────────────────────────────────────
    const val CELLULAR_LIVE_MIN_BUFFER_MS = 50_000
    const val CELLULAR_LIVE_MAX_BUFFER_MS = 50_000
    const val CELLULAR_LIVE_PLAYBACK_MS = 2_500
    const val CELLULAR_LIVE_REBUFFER_MS = 5_000
    const val CELLULAR_LIVE_BACK_BUFFER_MS = 0

    // ── Cellular VOD ────────────────────────────────────────────────
    const val CELLULAR_VOD_MIN_BUFFER_MS = 40_000
    const val CELLULAR_VOD_MAX_BUFFER_MS = 100_000

    // Starts as Wi-Fi VOD and ExoPlayer's defaults do: a longer threshold made every start and seek
    // on mobile data wait for 8 s of video.
    const val CELLULAR_VOD_PLAYBACK_MS = 2_500
    const val CELLULAR_VOD_REBUFFER_MS = 5_000
    const val CELLULAR_VOD_BACK_BUFFER_MS = 0 // see WIFI_VOD_BACK_BUFFER_MS

    // ── Retry policy ────────────────────────────────────────────────
    // More retries, so first-connection failures do not show an error.
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
    // DefaultLoadControl's automatic byte default (a few MB, whatever the bitrate) with
    // prioritizeTimeOverSizeThresholds(true) let a high-bitrate 4K VOD stream buffer far past it
    // while chasing the time target: hundreds of MB of native allocation on 1-2 GB Android TV
    // devices. An explicit cap bounds the worst case.
    //
    // The floor: 64 MB holds 30 s+ of a 1080p stream but only ~5 s of a ~100 Mbps 4K remux, so on
    // devices with the room the cap scales with the app's heap (see [vodTargetBufferBytes]).
    const val VOD_TARGET_BUFFER_BYTES = 64 * 1024 * 1024
    const val VOD_TARGET_BUFFER_BYTES_MAX = 160 * 1024 * 1024

    // The buffer lives on the Java heap; a quarter of it is left to everything else while playing.
    private const val VOD_BUFFER_HEAP_DIVISOR = 4
    private const val BYTES_PER_MB = 1024L * 1024L

    /**
     * The VOD byte cap for a device whose large-heap memory class is [largeMemoryClassMb]
     * (`ActivityManager.largeMemoryClass`; the apps set `largeHeap`): a quarter of it, between
     * [VOD_TARGET_BUFFER_BYTES] and [VOD_TARGET_BUFFER_BYTES_MAX]. 512 MB → 128 MB.
     */
    fun vodTargetBufferBytes(largeMemoryClassMb: Int): Int =
        (largeMemoryClassMb * BYTES_PER_MB / VOD_BUFFER_HEAP_DIVISOR)
            .coerceIn(VOD_TARGET_BUFFER_BYTES.toLong(), VOD_TARGET_BUFFER_BYTES_MAX.toLong())
            .toInt()

    // 16 MB let high-bitrate 4K live (25-40 Mbps) hit the cap in ~3-5 s, far short of the 15-50 s
    // time targets above (see AdaptiveLoadControl.buildDelegate()'s prioritizeTimeOverSize, time
    // first for LIVE_TV). This cap still bounds native memory, with headroom for 4K/60 fps.
    const val LIVE_TARGET_BUFFER_BYTES = 32 * 1024 * 1024

    // ── Cellular buffer multiplier functions (WiFi always uses 1.0x) ──

    fun getCellularLiveMinBuffer(multiplier: Float): Int = (CELLULAR_LIVE_MIN_BUFFER_MS * multiplier).toInt()

    fun getCellularLiveMaxBuffer(multiplier: Float): Int = (CELLULAR_LIVE_MAX_BUFFER_MS * multiplier).toInt()

    fun getCellularLivePlayback(multiplier: Float): Int = (CELLULAR_LIVE_PLAYBACK_MS * multiplier).toInt()

    fun getCellularLiveRebuffer(multiplier: Float): Int = (CELLULAR_LIVE_REBUFFER_MS * multiplier).toInt()

    fun getCellularVodMinBuffer(multiplier: Float): Int = (CELLULAR_VOD_MIN_BUFFER_MS * multiplier).toInt()

    fun getCellularVodMaxBuffer(multiplier: Float): Int = (CELLULAR_VOD_MAX_BUFFER_MS * multiplier).toInt()

    fun getCellularVodPlayback(multiplier: Float): Int = (CELLULAR_VOD_PLAYBACK_MS * multiplier).toInt()

    fun getCellularVodRebuffer(multiplier: Float): Int = (CELLULAR_VOD_REBUFFER_MS * multiplier).toInt()
}
