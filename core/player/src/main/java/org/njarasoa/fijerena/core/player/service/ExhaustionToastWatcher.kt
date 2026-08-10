package org.njarasoa.fijerena.core.player.service

import kotlinx.coroutines.delay

/**
 * Tracks buffer-exhaustion (rebuffer) events in a sliding time window and decides
 * when they're frequent enough to warrant surfacing a toast to the user.
 */
class ExhaustionToastDebouncer(
    private val windowMs: Long = 30_000L,
    private val threshold: Int = 3,
) {
    private val timestamps = mutableListOf<Long>()
    private var lastSeenCount = 0

    /** Feed the latest cumulative exhaustion count; returns true when the toast should fire. */
    fun onCountUpdate(
        newCount: Int,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (newCount < lastSeenCount) {
            // Count was reset (likely channel switch)
            lastSeenCount = newCount
            timestamps.clear()
            return false
        }
        if (newCount == lastSeenCount) return false

        repeat(newCount - lastSeenCount) { timestamps.add(now) }
        lastSeenCount = newCount
        timestamps.removeAll { now - it > windowMs }
        if (timestamps.size >= threshold) {
            // Clear timestamps to prevent repeated toasts for the same event window
            timestamps.clear()
            return true
        }
        return false
    }
}

/** Polls [StreamingPlaybackService.exhaustionRebufferCount] and invokes [onThresholdReached] per [ExhaustionToastDebouncer]. */
suspend fun watchExhaustionToasts(onThresholdReached: suspend () -> Unit) {
    val debouncer = ExhaustionToastDebouncer()
    while (true) {
        val currentCount = StreamingPlaybackService.getInstance()?.exhaustionRebufferCount?.value ?: 0
        if (debouncer.onCountUpdate(currentCount)) {
            onThresholdReached()
        }
        delay(1000L)
    }
}
