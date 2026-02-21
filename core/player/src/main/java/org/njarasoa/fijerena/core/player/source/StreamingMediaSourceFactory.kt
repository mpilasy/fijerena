@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

object StreamingMediaSourceFactory {

    /**
     * Creates a robust [MediaSource] using Media3's [DefaultMediaSourceFactory].
     * This factory automatically detects the stream type (HLS, DASH, Progressive)
     * and handles container formats like MPEG-TS, MP4, etc.
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

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(connectTimeout)
            .setReadTimeoutMs(readTimeout)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        // Use DefaultMediaSourceFactory for robust automatic format detection
        // It supports HLS, DASH, and progressive streams (MP4, MKV, TS, etc.)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }

}
