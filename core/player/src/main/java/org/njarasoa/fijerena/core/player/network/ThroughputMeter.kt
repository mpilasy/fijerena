package org.njarasoa.fijerena.core.player.network

/**
 * Bits per second actually received over the last [windowMs], for the slow-connection banner.
 * Media3's bandwidth estimate only updates when a transfer ends, and a progressive stream is one
 * long transfer, so on a 20 Mbit/s link it still said 2 Mbit/s. Bytes come from the loader
 * thread, reads from the main thread. Times are elapsed-realtime ms.
 * See docs/plans/archive/20261004_playback-capability-errors-plan.md → P4.
 */
class ThroughputMeter(
    private val windowMs: Long = 10_000L,
) {
    // (bucket start, bytes) per BUCKET_MS, oldest first.
    private val buckets = ArrayDeque<LongArray>()

    @Synchronized
    fun onBytes(
        nowMs: Long,
        bytes: Int,
    ) {
        val start = nowMs - nowMs % BUCKET_MS
        val last = buckets.lastOrNull()
        if (last != null && last[0] == start) last[1] += bytes.toLong() else buckets.addLast(longArrayOf(start, bytes.toLong()))
        dropOld(nowMs)
    }

    /** 0 when nothing arrived within the window. */
    @Synchronized
    fun bitsPerSecond(nowMs: Long): Long {
        dropOld(nowMs)
        val first = buckets.firstOrNull() ?: return 0L
        val spanMs = (nowMs - first[0]).coerceAtLeast(MIN_SPAN_MS)
        return buckets.sumOf { it[1] } * 8 * 1000 / spanMs
    }

    @Synchronized
    fun reset() {
        buckets.clear()
    }

    private fun dropOld(nowMs: Long) {
        while (buckets.isNotEmpty() && nowMs - buckets.first()[0] > windowMs) buckets.removeFirst()
    }

    private companion object {
        const val BUCKET_MS = 100L
        const val MIN_SPAN_MS = 1_000L
    }
}
