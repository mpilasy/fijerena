package org.njarasoa.fijerena.core.player.model

/**
 * What a wait for video is made of, for the Expert-mode lines under "Buffering…". Speeds are bits
 * per second; a [streamBps] of null is unknown (most live MPEG-TS). [errors] and [lastError] count
 * this wait only, as does [bytesReceived].
 */
data class BufferingInfo(
    /** Video held, ms; playback starts at [neededMs] (or earlier, once the buffer's size cap fills). */
    val bufferedMs: Long,
    val neededMs: Long,
    val waitingMs: Long,
    val receivingBps: Long,
    val streamBps: Long?,
    val host: String?,
    val errors: Int,
    /** "HTTP 503", "timeout", "DNS"… — the last failed load of this wait. */
    val lastError: String?,
    val bytesReceived: Long,
) {
    /** Getting less than the stream needs: the line turns amber, as the slow-connection banner's test. */
    val tooSlow: Boolean get() = streamBps != null && receivingBps in 1 until streamBps

    /** Line 3 shows only when something went wrong. */
    val hasTrouble: Boolean get() = errors > 0
}
