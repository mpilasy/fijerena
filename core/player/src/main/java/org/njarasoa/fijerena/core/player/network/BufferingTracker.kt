package org.njarasoa.fijerena.core.player.network

import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps the per-wait counts behind [org.njarasoa.fijerena.core.player.model.BufferingInfo]: when the
 * current wait began, whether it is a stall (so the player waits for the rebuffer amount, not the
 * start one), and the bytes and load errors since. Bytes arrive on the loader thread; the rest is
 * called on the main thread. Times are elapsed-realtime ms.
 */
class BufferingTracker {
    private val totalBytes = AtomicLong(0L)

    @Volatile private var totalErrors = 0

    @Volatile private var lastErrorLabel: String? = null

    @Volatile private var lastErrorAtErrors = 0

    private var waitStartMs = NONE
    private var bytesAtStart = 0L
    private var errorsAtStart = 0
    private var wasPlaying = false

    /** True when the wait in progress is a stall during playback, false at a start or after a seek. */
    var isRebuffering = false
        private set

    fun onBytes(bytes: Int) {
        totalBytes.addAndGet(bytes.toLong())
    }

    fun onLoadError(label: String) {
        totalErrors++
        lastErrorLabel = label
        lastErrorAtErrors = totalErrors
    }

    /** [afterSeek]: the wait follows a seek, which the player fills to the start amount. */
    fun onStateChanged(
        buffering: Boolean,
        playing: Boolean,
        nowMs: Long,
        afterSeek: Boolean,
    ) {
        if (buffering) {
            if (waitStartMs == NONE) {
                waitStartMs = nowMs
                bytesAtStart = totalBytes.get()
                errorsAtStart = totalErrors
                isRebuffering = wasPlaying && !afterSeek
            }
        } else {
            waitStartMs = NONE
        }
        wasPlaying = playing
    }

    /** A new stream starts from nothing: its first wait is a start, not a stall. */
    fun onNewStream() {
        wasPlaying = false
    }

    fun waitingMs(nowMs: Long): Long = if (waitStartMs == NONE) 0L else nowMs - waitStartMs

    fun bytesThisWait(): Long = if (waitStartMs == NONE) 0L else totalBytes.get() - bytesAtStart

    fun errorsThisWait(): Int = if (waitStartMs == NONE) 0 else totalErrors - errorsAtStart

    /** The last error, only when it happened during this wait. */
    fun lastErrorThisWait(): String? = lastErrorLabel.takeIf { waitStartMs != NONE && lastErrorAtErrors > errorsAtStart }

    private companion object {
        const val NONE = -1L
    }
}
