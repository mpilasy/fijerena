package org.njarasoa.fijerena.core.player.network

import org.njarasoa.fijerena.core.player.model.SlowConnection

/**
 * Decides when to show the "connection too slow for this video" banner. On a rebuffer that isn't a
 * seek: at once when the stream's bitrate is known and the measured throughput is below it; without
 * numbers on the second rebuffer within [unknownBitrateWindowMs] when it isn't. The banner goes
 * after [clearAfterMs] without a rebuffer. Times are elapsed-realtime ms.
 * See docs/plans/20261004_playback-capability-errors-plan.md → P4.
 */
class SlowConnectionDetector(
    private val unknownBitrateWindowMs: Long = 120_000L,
    private val clearAfterMs: Long = 60_000L,
) {
    private var lastRebufferMs = NONE
    private var current: SlowConnection? = null

    /** [streamBitrate] is ≤ 0 when unknown; [bandwidthEstimate] is the measured throughput, bits per second. */
    fun onRebuffer(
        nowMs: Long,
        streamBitrate: Int,
        bandwidthEstimate: Long,
    ): SlowConnection? {
        val previous = lastRebufferMs
        lastRebufferMs = nowMs
        if (streamBitrate > 0) {
            if (bandwidthEstimate in 1 until streamBitrate) {
                current = SlowConnection(streamBitrate.toLong(), bandwidthEstimate)
            }
        } else if (previous != NONE && nowMs - previous <= unknownBitrateWindowMs) {
            current = SlowConnection(null, null)
        }
        return current
    }

    fun onTick(nowMs: Long): SlowConnection? {
        if (current != null && nowMs - lastRebufferMs >= clearAfterMs) current = null
        return current
    }

    fun reset() {
        lastRebufferMs = NONE
        current = null
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
