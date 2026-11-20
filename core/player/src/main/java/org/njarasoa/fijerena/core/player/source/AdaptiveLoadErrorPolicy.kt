package org.njarasoa.fijerena.core.player.source

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * Network-aware [LoadErrorHandlingPolicy]: more retries and exponential backoff on cellular
 * networks, where tower handoffs cause transient errors. Reads [NetworkMonitor.currentNetworkType]
 * at call time, so it keeps no state.
 */
@OptIn(UnstableApi::class)
class AdaptiveLoadErrorPolicy(
    private val onRetry: (() -> Unit)? = null,
) : LoadErrorHandlingPolicy {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        when (NetworkMonitor.currentNetworkType) {
            NetworkType.CELLULAR -> NetworkBufferProfile.CELLULAR_MIN_RETRY_COUNT
            else -> NetworkBufferProfile.WIFI_MIN_RETRY_COUNT
        }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        // The account is busy elsewhere: report it at once; the service waits it out.
        val httpStatus = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        if (AccountBusy.isAccountBusy(httpStatus)) return C.TIME_UNSET

        // A playlist the server refuses (4xx) or that doesn't parse: no retry changes that, and a
        // live HLS stream then falls back to MPEG-TS at once, before the screen gives up on it
        // (the mobile data playback plan → Phase 1). A segment 404 on live HLS can be one not
        // written yet, so segments keep their retries.
        if (loadErrorInfo.exception is ParserException) return C.TIME_UNSET
        if (loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MANIFEST && isRefusal(httpStatus)) return C.TIME_UNSET

        val errorCount = loadErrorInfo.errorCount
        val maxRetries = getMinimumLoadableRetryCount(loadErrorInfo.mediaLoadData.dataType)

        if (errorCount > maxRetries) return C.TIME_UNSET

        // Exponential backoff: baseDelay * 2^(errorCount-1), capped at maxDelay.
        val delay =
            NetworkBufferProfile.RETRY_BASE_DELAY_MS *
                (1L shl (errorCount - 1).coerceAtMost(30))
        onRetry?.invoke()
        return delay.coerceAtMost(NetworkBufferProfile.RETRY_MAX_DELAY_MS)
    }

    private fun isRefusal(httpStatus: Int?): Boolean =
        httpStatus != null && httpStatus in 400..499 && httpStatus != 408 && httpStatus != 429

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        // Media3 defaults.
        return null
    }
}
