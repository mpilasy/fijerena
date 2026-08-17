package org.njarasoa.fijerena.core.player.network

import android.util.Log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StreamHealthState(
    val isDegraded: Boolean = false,
    val recycleAttempts: Int = 0,
    val degradedAttempts: Int = 0,
    val firstFailureTimestamp: Long = 0L,
    val isHealthy: Boolean = true
)

/**
 * An engine-agnostic monitor that tracks live stream performance metrics.
 * It identifies terminal degradation (ISP throttling, CDN fatigue) and
 * triggers a connection recycle.
 */
class StreamHealthMonitor(
    val config: Config = Config(),
    private val onStreamRecycleRequired: () -> Unit,
    private val onRecoveryExhausted: () -> Unit = {},
) {

    private val _state = MutableStateFlow(StreamHealthState())
    val state: StateFlow<StreamHealthState> = _state.asStateFlow()

    private fun updateStateFlow() {
        _state.value = StreamHealthState(
            isDegraded = isDegraded,
            recycleAttempts = recycleAttempts,
            degradedAttempts = degradedAttempts,
            firstFailureTimestamp = firstFailureTimestamp,
            isHealthy = !isDegraded && firstFailureTimestamp == 0L && recycleAttempts == 0
        )
    }

    data class Config(
        val minBufferMs: Long = 8000L,
        val sustainedDegradationWindowMs: Long = 20000L,
        // 15 dropped frames/sec meant a 25fps stream could lose over half its frames and still
        // report "Healthy" — visibly broken playback the stats panel called fine. 5/sec is still
        // only tripped after sustainedDegradationWindowMs of it, so a brief hiccup won't recycle.
        val maxFrameDropRate: Float = 5.0f,
        val evaluationIntervalMs: Long = 5000L,
        val maxRecycleAttempts: Int = 3,
        val degradedRecycleIntervalMs: Long = 30_000L,
        val maxDegradedAttempts: Int = 5,
    )

    private var firstFailureTimestamp: Long = 0L
    private var isRecycleTriggered: Boolean = false

    // Counts recycles since the last confirmed stable playback. Without a cap, a sustained
    // real outage (e.g. packet loss) makes this recycle forever in silence with no feedback
    // that anything is wrong, unlike the hard-retry path which gives up after MAX_LIVE_RETRIES.
    private var recycleAttempts: Int = 0

    // Once the fast tier is exhausted, keep trying on a slower cadence for a while longer
    // before truly giving up — a connection bad enough to need >maxRecycleAttempts fast
    // recycles can still self-heal given more time, and the old "spin forever" behavior at
    // least never dead-ended the user at a manual-retry error like onRecoveryExhausted does.
    private var isDegraded: Boolean = false
    private var degradedAttempts: Int = 0
    private var lastDegradedRecycleTimestamp: Long = 0L

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
        updateStateFlow()
    }

    /**
     * Resets the monitor state after a connection recycle attempt so the next degradation
     * window can be evaluated. Does not clear the recycle-attempt count — see
     * [notifyStablePlayback] for that.
     */
    fun reset() {
        firstFailureTimestamp = 0L
        isRecycleTriggered = false
        updateStateFlow()
        Log.d(TAG, "Health monitor reset.")
    }

    /**
     * Call once playback is confirmed actually playing again, to clear the recycle-attempt
     * count built up while recovering. A genuinely stable stream should never approach the cap.
     */
    fun notifyStablePlayback() {
        recycleAttempts = 0
        isDegraded = false
        degradedAttempts = 0
        updateStateFlow()
    }

    private fun triggerRecycle() {
        // Once degraded, stay degraded — recycleAttempts must not keep incrementing on every
        // re-check while triggerDegradedRecycle() is just waiting for its own interval to
        // elapse (updateMetrics() calls back in on every evaluation tick until it fires).
        if (isDegraded) {
            triggerDegradedRecycle()
            return
        }
        recycleAttempts++
        if (recycleAttempts > config.maxRecycleAttempts) {
            triggerDegradedRecycle()
            return
        }
        isRecycleTriggered = true
        updateStateFlow()
        onStreamRecycleRequired.invoke()
    }

    /**
     * Slower-cadence fallback once the fast tier is exhausted. Keeps recycling on
     * [Config.degradedRecycleIntervalMs] spacing for up to [Config.maxDegradedAttempts] more
     * attempts before calling [onRecoveryExhausted]. isDegraded/degradedAttempts are
     * deliberately NOT cleared by [reset] (only by [notifyStablePlayback]) — reset() runs
     * after every single recycle attempt, so clearing the degraded budget there would mean
     * this cap could never bite.
     */
    private fun triggerDegradedRecycle() {
        val now = System.currentTimeMillis()
        if (!isDegraded) {
            isDegraded = true
            degradedAttempts = 0
            lastDegradedRecycleTimestamp = 0L
        }
        if (now - lastDegradedRecycleTimestamp < config.degradedRecycleIntervalMs) {
            // Not time for the next slow-cadence attempt yet. Leave isRecycleTriggered false
            // so updateMetrics() keeps evaluating and calls back in once the interval elapses.
            updateStateFlow()
            return
        }
        degradedAttempts++
        if (degradedAttempts > config.maxDegradedAttempts) {
            Log.w(TAG, "Giving up after $recycleAttempts fast + $degradedAttempts degraded recycle attempts.")
            isRecycleTriggered = true
            updateStateFlow()
            onRecoveryExhausted.invoke()
            return
        }
        lastDegradedRecycleTimestamp = now
        isRecycleTriggered = true
        updateStateFlow()
        onStreamRecycleRequired.invoke()
    }

    companion object {
        private const val TAG = "StreamHealthMonitor"
    }
}
