package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * Factory for creating [MediaSource] instances.
 * 
 * Reuses internal [DefaultMediaSourceFactory] and [DefaultHttpDataSource.Factory]
 * to avoid memory churn and binder exhaustion during rapid channel switching.
 */
@OptIn(UnstableApi::class)
class StreamingMediaSourceFactory(context: Context) {
    private val userAgent = "MediaPlayer/1.0 (Linux; Android)"
    
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true)
        
    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(httpDataSourceFactory)

    fun createMediaSource(
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
        onRetry: (() -> Unit)? = null,
        transferListener: androidx.media3.datasource.TransferListener? = null,
    ): MediaSource {
        val isCellular = NetworkMonitor.currentNetworkType == NetworkType.CELLULAR

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(streamUrl)
                .build()

        val connectTimeout =
            if (isCellular) {
                NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS
            } else {
                NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS
            }
        val readTimeout =
            if (isCellular) {
                NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS
            } else {
                NetworkBufferProfile.WIFI_READ_TIMEOUT_MS
            }

        // Configure common HTTP factory
        val allHeaders = buildMap {
            put("User-Agent", userAgent)
            putAll(headers)
        }
        
        httpDataSourceFactory
            .setConnectTimeoutMs(connectTimeout)
            .setReadTimeoutMs(readTimeout)
            .setDefaultRequestProperties(allHeaders)
            .setTransferListener(transferListener)

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        // Reuse media source factory with updated policy
        return mediaSourceFactory
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }
}
