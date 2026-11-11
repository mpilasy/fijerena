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
    val isHealthy: Boolean = true,
)

/**
 * An engine-agnostic monitor of live stream metrics. It finds terminal degradation (ISP throttling,
 * CDN fatigue) and triggers a connection recycle.
 */
class StreamHealthMonitor(
    val config: Config = Config(),
    /** Called with the reason, for Diagnostics. */
    private val onStreamRecycleRequired: (reason: String) -> Unit,
    private val onRecoveryExhausted: () -> Unit = {},
) {
    private val _state = MutableStateFlow(StreamHealthState())
    val state: StateFlow<StreamHealthState> = _state.asStateFlow()

    private fun updateStateFlow() {
        _state.value =
            StreamHealthState(
                isDegraded = isDegraded,
                recycleAttempts = recycleAttempts,
                degradedAttempts = degradedAttempts,
                firstFailureTimestamp = firstFailureTimestamp,
                isHealthy = !isDegraded && firstFailureTimestamp == 0L && recycleAttempts == 0,
            )
    }

    data class Config(
        val minBufferMs: Long = 8000L,
        val sustainedDegradationWindowMs: Long = 20000L,
        // 15 dropped frames/s let a 25 fps stream lose half its frames and still report "Healthy".
        // 5/s only trips after sustainedDegradationWindowMs, so a brief hiccup does not recycle.
        val maxFrameDropRate: Float = 5.0f,
        val evaluationIntervalMs: Long = 5000L,
        val maxRecycleAttempts: Int = 3,
        val degradedRecycleIntervalMs: Long = 30_000L,
        val maxDegradedAttempts: Int = 5,
    )

    private var firstFailureTimestamp: Long = 0L
    private var isRecycleTriggered: Boolean = false

    // How long metrics have been healthy without a break, so the recycle backoff clears only when
    // playback is stable again (see the isHealthy branch in updateMetrics()).
    private var firstHealthyTimestamp: Long = 0L

    // Recycles since the last stable playback. Without a cap, a real outage (for example packet
    // loss) recycles for ever with no feedback, unlike the hard-retry path, which gives up after
    // MAX_LIVE_RETRIES.
    private var recycleAttempts: Int = 0

    // After the fast tier, keep trying at a slower cadence for a while before giving up: a
    // connection that needs more than maxRecycleAttempts fast recycles can still heal with time.
    private var isDegraded: Boolean = false
    private var degradedAttempts: Int = 0
    private var lastDegradedRecycleTimestamp: Long = 0L

    /**
     * Takes one tick of metrics from the active media engine and decides whether the connection
     * needs a reset.
     */
    fun updateMetrics(
        bufferedDurationMs: Long,
        droppedFramesPerSecond: Float,
        hasReadTimeout: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val isBufferLow = bufferedDurationMs < config.minBufferMs
        val isFrameDropHigh = droppedFramesPerSecond > config.maxFrameDropRate
        val isHealthy = !isBufferLow && !isFrameDropHigh && !hasReadTimeout

        if (isRecycleTriggered) {
            // Blocked, so a reset does not trigger more than once.
        } else if (hasReadTimeout) {
            Log.w(TAG, "Read timeout detected. Immediate recycle required.")
            triggerRecycle("the position stopped moving")
        } else if (isHealthy) {
            // Reset the degradation timer when the stream recovers by itself.
            firstFailureTimestamp = 0L

            // Clear the recycle backoff only after metrics stay healthy for a full window. A
            // healthy tick right after a recycle cleared recycleAttempts before it could reach
            // maxRecycleAttempts, so a connection that recovers for a few seconds and then degrades
            // again (the common case) recycled for ever on the fast cadence with no escalation and
            // no error.
            if (firstHealthyTimestamp == 0L) {
                firstHealthyTimestamp = now
            } else if (now - firstHealthyTimestamp >= config.sustainedDegradationWindowMs) {
                if (recycleAttempts != 0 || isDegraded) {
                    Log.i(TAG, "Stream stable for ${config.sustainedDegradationWindowMs}ms — clearing recycle backoff.")
                }
                recycleAttempts = 0
                isDegraded = false
                degradedAttempts = 0
            }
        } else {
            firstHealthyTimestamp = 0L

            if (firstFailureTimestamp == 0L) {
                firstFailureTimestamp = now
            }

            val degradationDuration = now - firstFailureTimestamp
            if (degradationDuration >= config.sustainedDegradationWindowMs) {
                Log.w(TAG, "Sustained degradation detected (${degradationDuration}ms). Triggering recycle.")
                val reason =
                    listOfNotNull(
                        "buffer under ${config.minBufferMs} ms".takeIf { isBufferLow },
                        "over ${config.maxFrameDropRate} dropped frames/s".takeIf { isFrameDropHigh },
                    ).joinToString(" and ")
                triggerRecycle("$reason for ${degradationDuration / 1000} s")
            }
        }
        updateStateFlow()
    }

    /**
     * Resets the monitor after a recycle attempt, so the next degradation window can be evaluated.
     * Keeps the recycle-attempt count (see [notifyStablePlayback]).
     */
    fun reset() {
        firstFailureTimestamp = 0L
        isRecycleTriggered = false
        updateStateFlow()
        Log.d(TAG, "Health monitor reset.")
    }

    /**
     * Call once playback is confirmed playing again, to clear the recycle-attempt count of the
     * recovery.
     */
    fun notifyStablePlayback() {
        recycleAttempts = 0
        isDegraded = false
        degradedAttempts = 0
        updateStateFlow()
    }

    private fun triggerRecycle(reason: String) {
        // Once degraded, stay degraded: recycleAttempts must not grow on each check while
        // triggerDegradedRecycle() waits for its interval (updateMetrics() calls in on every tick
        // until it fires).
        if (isDegraded) {
            triggerDegradedRecycle(reason)
            return
        }
        recycleAttempts++
        if (recycleAttempts > config.maxRecycleAttempts) {
            triggerDegradedRecycle(reason)
            return
        }
        isRecycleTriggered = true
        updateStateFlow()
        onStreamRecycleRequired.invoke("$reason (attempt $recycleAttempts)")
    }

    /**
     * The slower fallback after the fast tier: recycles every [Config.degradedRecycleIntervalMs],
     * for up to [Config.maxDegradedAttempts] more attempts, then calls [onRecoveryExhausted].
     * [reset] does NOT clear isDegraded / degradedAttempts (only [notifyStablePlayback] does):
     * reset() runs after every recycle, so the cap would never apply.
     */
    private fun triggerDegradedRecycle(reason: String) {
        val now = System.currentTimeMillis()
        if (!isDegraded) {
            isDegraded = true
            degradedAttempts = 0
            lastDegradedRecycleTimestamp = 0L
        }
        if (now - lastDegradedRecycleTimestamp < config.degradedRecycleIntervalMs) {
            // Not time for the next slow attempt yet. isRecycleTriggered stays false, so
            // updateMetrics() calls in again once the interval has passed.
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
        onStreamRecycleRequired.invoke("$reason (slow attempt $degradedAttempts)")
    }

    companion object {
        private const val TAG = "StreamHealthMonitor"
    }
}
