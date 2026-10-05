package org.njarasoa.fijerena.core.player.network

/**
 * Decides when a stream has been buffering with no data arriving for long enough to give up.
 * Without it a stalled connection kept the spinner up for 13 minutes: each attempt waited out
 * Media3's own read-timeout retries, four attempts in all. Any byte restarts the minute, so a
 * slow connection, a recycle or a retry that gets data keeps going. Times are elapsed-realtime ms.
 * See docs/plans/archive/20261004_playback-capability-errors-plan.md → P4.
 */
class StallWatchdog(
    private val limitMs: Long = LIMIT_MS,
) {
    private var bufferingSinceMs = NONE

    // Written from the loader thread (onBytesTransferred), read on the main thread.
    @Volatile private var lastDataMs = NONE

    fun onData(nowMs: Long) {
        lastDataMs = nowMs
    }

    fun reset() {
        bufferingSinceMs = NONE
        lastDataMs = NONE
    }

    /**
     * Called on every health tick. [isBuffering]: the player is buffering and wants to play (not
     * paused, not idle between retries). True once it has gone [limitMs] with no data since
     * buffering began.
     */
    fun isStalled(
        isBuffering: Boolean,
        nowMs: Long,
    ): Boolean {
        if (!isBuffering) {
            bufferingSinceMs = NONE
            return false
        }
        if (bufferingSinceMs == NONE) bufferingSinceMs = nowMs
        return nowMs - maxOf(bufferingSinceMs, lastDataMs) >= limitMs
    }

    companion object {
        const val LIMIT_MS = 60_000L
        private const val NONE = Long.MIN_VALUE
    }
}
