@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * Network-aware [LoadErrorHandlingPolicy] that increases retry counts and applies
 * exponential backoff on cellular networks where transient errors from tower
 * handoffs are common.
 *
 * Reads [NetworkMonitor.currentNetworkType] at call time — no state to maintain.
 */
class AdaptiveLoadErrorPolicy : LoadErrorHandlingPolicy {

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return when (NetworkMonitor.currentNetworkType) {
            NetworkType.CELLULAR -> NetworkBufferProfile.CELLULAR_MIN_RETRY_COUNT
            else -> NetworkBufferProfile.WIFI_MIN_RETRY_COUNT
        }
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val errorCount = loadErrorInfo.errorCount
        val maxRetries = getMinimumLoadableRetryCount(loadErrorInfo.mediaLoadData.dataType)

        if (errorCount > maxRetries) return C.TIME_UNSET

        // Exponential backoff: baseDelay * 2^(errorCount-1), capped at maxDelay
        val delay = NetworkBufferProfile.RETRY_BASE_DELAY_MS *
            (1L shl (errorCount - 1).coerceAtMost(30))
        return delay.coerceAtMost(NetworkBufferProfile.RETRY_MAX_DELAY_MS)
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        // Defer to Media3 defaults
        return null
    }
}
