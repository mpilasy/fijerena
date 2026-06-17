package org.njarasoa.fijerena.core.player.network

import android.util.Log

/**
 * An engine-agnostic monitor that tracks live stream performance metrics.
 * It identifies terminal degradation (ISP throttling, CDN fatigue) and
 * triggers a connection recycle.
 */
class StreamHealthMonitor(
    private val config: Config = Config(),
    private val onStreamRecycleRequired: () -> Unit
) {

    data class Config(
        val minBufferMs: Long = 8000L,
        val sustainedDegradationWindowMs: Long = 20000L,
        val maxFrameDropRate: Float = 15.0f,
        val evaluationIntervalMs: Long = 5000L
    )

    private var firstFailureTimestamp: Long = 0L
    private var isRecycleTriggered: Boolean = false

    /**
     * Accepts a tick of performance metrics from the active media engine.
     * Evaluates if the current state requires a connection reset.
     */
    fun updateMetrics(
        bufferedDurationMs: Long,
        droppedFramesPerSecond: Float,
        hasReadTimeout: Boolean
    ) {
        val now = System.currentTimeMillis()
        val isBufferLow = bufferedDurationMs < config.minBufferMs
        val isFrameDropHigh = droppedFramesPerSecond > config.maxFrameDropRate
        val isHealthy = !isBufferLow && !isFrameDropHigh && !hasReadTimeout

        if (isRecycleTriggered) {
            // Logic blocked to prevent multiple triggers during reset
        } else if (hasReadTimeout) {
            Log.w(TAG, "Read timeout detected. Immediate recycle required.")
            triggerRecycle()
        } else if (isHealthy) {
            // Reset the degradation timer if the stream recovers naturally
            firstFailureTimestamp = 0L
        } else {
            // Start or continue tracking the degradation window
            if (firstFailureTimestamp == 0L) {
                firstFailureTimestamp = now
            }

            val degradationDuration = now - firstFailureTimestamp
            if (degradationDuration >= config.sustainedDegradationWindowMs) {
                Log.w(TAG, "Sustained degradation detected (${degradationDuration}ms). Triggering recycle.")
                triggerRecycle()
            }
        }
    }

    /**
     * Resets the monitor state after a successful connection recycle.
     */
    fun reset() {
        firstFailureTimestamp = 0L
        isRecycleTriggered = false
        Log.d(TAG, "Health monitor reset.")
    }

    private fun triggerRecycle() {
        isRecycleTriggered = true
        onStreamRecycleRequired.invoke()
    }

    companion object {
        private const val TAG = "StreamHealthMonitor"
    }
}
