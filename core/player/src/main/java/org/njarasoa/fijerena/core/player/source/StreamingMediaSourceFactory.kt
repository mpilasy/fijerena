@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

object StreamingMediaSourceFactory {

    private val tsExtensionRegex = Regex("\\.ts(\\?|#|$)")

    fun createMediaSource(
        context: Context,
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false
    ): MediaSource {
        val isCellular = NetworkMonitor.currentNetworkType == NetworkType.CELLULAR

        // For live streams on cellular, prefer HLS over raw .ts for adaptive bitrate
        val effectiveUrl = if (isLive && isCellular) forceLiveHls(streamUrl) else streamUrl

        val mediaItem = MediaItem.Builder()
            .setUri(effectiveUrl)
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

        val errorPolicy = AdaptiveLoadErrorPolicy()

        // Strip query parameters before checking extension
        val urlPath = effectiveUrl.substringBefore("?").substringBefore("#")

        return when {
            urlPath.endsWith(".m3u8", ignoreCase = true) -> {
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(mediaItem)
            }
            urlPath.endsWith(".mpd", ignoreCase = true) -> {
                DashMediaSource.Factory(httpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(mediaItem)
            }
            urlPath.endsWith(".ts", ignoreCase = true) ||
            urlPath.endsWith(".mpeg", ignoreCase = true) ||
            urlPath.endsWith(".mp4", ignoreCase = true) ||
            urlPath.endsWith(".mkv", ignoreCase = true) ||
            urlPath.endsWith(".avi", ignoreCase = true) ||
            urlPath.endsWith(".mov", ignoreCase = true) ||
            urlPath.endsWith(".flv", ignoreCase = true) ||
            urlPath.endsWith(".webm", ignoreCase = true) -> {
                ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(mediaItem)
            }
            else -> {
                // Default to HLS for live streams without extension
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy)
                    .createMediaSource(mediaItem)
            }
        }
    }

    /**
     * On cellular, replace `.ts` extension with `.m3u8` for live streams.
     * HLS gives adaptive bitrate on unstable cellular connections.
     * Already-`.m3u8` URLs are returned unchanged.
     */
    private fun forceLiveHls(url: String): String {
        return tsExtensionRegex.replaceFirst(url, ".m3u8$1")
    }
}
