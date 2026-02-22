package org.njarasoa.fijerena.core.player.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.exoplayer.upstream.BandwidthMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors network bandwidth specifically on Cellular connections to detect
 * potential carrier throttling (e.g., T-Mobile Binge On limits to ~1.5 Mbps).
 *
 * Usage:
 * 1. Call [startMonitoring] when playback starts, passing the player's BandwidthMeter.
 * 2. Observe [isThrottlingSuspected] to update UI.
 * 3. Call [stopMonitoring] when playback stops.
 */
object ThrottlingMonitor {

    private const val TAG = "ThrottlingMonitor"

    // T-Mobile Binge On typically throttles to 1.5 Mbps (approx 1500 kbps).
    // We set a range to detect this specific pattern.
    private const val THROTTLE_THRESHOLD_LOW_KBPS = 1200L
    private const val THROTTLE_THRESHOLD_HIGH_KBPS = 1800L

    // Duration to sustain the throttled bandwidth before flagging it.
    private const val DETECTION_WINDOW_MS = 10_000L

    private val _isThrottlingSuspected = MutableStateFlow(false)
    val isThrottlingSuspected: StateFlow<Boolean> = _isThrottlingSuspected.asStateFlow()

    private var bandwidthMeter: BandwidthMeter? = null
    private val isMonitoring = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val bandwidthListener = object : BandwidthMeter.EventListener {
        override fun onBandwidthSample(elapsedMs: Int, bytesTransferred: Long, bitrateEstimate: Long) {
            checkThrottling(bitrateEstimate)
        }
    }

    private var sustainedThrottleStartMs: Long = 0

    fun startMonitoring(meter: BandwidthMeter) {
        if (isMonitoring.getAndSet(true)) return // Already monitoring

        Log.d(TAG, "Starting throttling monitoring")
        bandwidthMeter = meter
        // Requires a Handler on the thread where the callback should run. Main thread is safe for UI updates.
        meter.addEventListener(Handler(Looper.getMainLooper()), bandwidthListener)

        // Reset state
        _isThrottlingSuspected.value = false
        sustainedThrottleStartMs = 0
    }

    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) return

        Log.d(TAG, "Stopping throttling monitoring")
        bandwidthMeter?.removeEventListener(bandwidthListener)
        bandwidthMeter = null
        _isThrottlingSuspected.value = false
        sustainedThrottleStartMs = 0
    }

    private fun checkThrottling(bitrateEstimateBitsPerSec: Long) {
        // Only relevant on Cellular networks
        if (NetworkMonitor.currentNetworkType != NetworkType.CELLULAR) {
            if (_isThrottlingSuspected.value) {
                Log.i(TAG, "Network changed from Cellular, clearing throttling flag")
                _isThrottlingSuspected.value = false
            }
            sustainedThrottleStartMs = 0
            return
        }

        val bitrateKbps = bitrateEstimateBitsPerSec / 1000

        // Check if bitrate is within the suspicious "DVD quality" throttle range (1.2 - 1.8 Mbps)
        val isSuspicious = bitrateKbps in THROTTLE_THRESHOLD_LOW_KBPS..THROTTLE_THRESHOLD_HIGH_KBPS

        if (isSuspicious) {
            if (sustainedThrottleStartMs == 0L) {
                sustainedThrottleStartMs = System.currentTimeMillis()
            } else {
                val duration = System.currentTimeMillis() - sustainedThrottleStartMs
                if (duration >= DETECTION_WINDOW_MS && !_isThrottlingSuspected.value) {
                    Log.w(TAG, "Throttling detected! Bitrate sustained at ~${bitrateKbps}kbps for ${duration}ms on Cellular.")
                    _isThrottlingSuspected.value = true
                }
            }
        } else {
            // If bitrate recovers significantly above the threshold, clear the flag.
            if (bitrateKbps > THROTTLE_THRESHOLD_HIGH_KBPS) {
                 if (_isThrottlingSuspected.value) {
                     Log.i(TAG, "Bitrate recovered to ${bitrateKbps}kbps, clearing throttling flag")
                 }
                 _isThrottlingSuspected.value = false
                 sustainedThrottleStartMs = 0
            } else if (bitrateKbps < THROTTLE_THRESHOLD_LOW_KBPS) {
                // If bitrate drops very low, it might be poor signal, not necessarily throttling.
                // We keep the flag if already set (maybe throttling is causing buffering/drops),
                // but we reset the timer for *new* detection.
                if (!_isThrottlingSuspected.value) {
                    sustainedThrottleStartMs = 0
                }
            }
        }
    }
}
