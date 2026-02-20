@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

object StreamingMediaSourceFactory {

    /**
     * Creates a network-aware MediaSource using DefaultHttpDataSource.
     * Reverts to standard Java connection handling for better compatibility and speed on some networks.
     */
    fun createMediaSource(
        context: Context,
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
        onRetry: (() -> Unit)? = null
    ): MediaSource {
        val isCellular = NetworkMonitor.currentNetworkType == NetworkType.CELLULAR
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()

        // Network-aware HTTP timeouts
        val connectTimeout = if (isCellular)
            NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS
        else
            NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS
        val readTimeout = if (isCellular)
            NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS
        else
            NetworkBufferProfile.WIFI_READ_TIMEOUT_MS

        val userAgent = "FijerenaPlayer/1.0 (Android)"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(connectTimeout)
            .setReadTimeoutMs(readTimeout)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)

        // Wrap in DefaultDataSource.Factory to support file://, asset://, etc if needed
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        // Use DefaultMediaSourceFactory to automatically detect content type (HLS, DASH, Progressive/TS)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }

}
