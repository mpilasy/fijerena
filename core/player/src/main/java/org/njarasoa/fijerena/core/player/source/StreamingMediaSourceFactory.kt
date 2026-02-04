package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

object StreamingMediaSourceFactory {
    fun createMediaSource(
        context: Context,
        streamUrl: String,
        headers: Map<String, String> = emptyMap()
    ): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(60000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        // Strip query parameters before checking extension
        val urlPath = streamUrl.substringBefore("?").substringBefore("#")

        return when {
            urlPath.endsWith(".m3u8", ignoreCase = true) -> {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            urlPath.endsWith(".mpd", ignoreCase = true) -> {
                DashMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            urlPath.endsWith(".ts", ignoreCase = true) ||
            urlPath.endsWith(".mpeg", ignoreCase = true) ||
            urlPath.endsWith(".mp4", ignoreCase = true) ||
            urlPath.endsWith(".mkv", ignoreCase = true) ||
            urlPath.endsWith(".avi", ignoreCase = true) ||
            urlPath.endsWith(".mov", ignoreCase = true) ||
            urlPath.endsWith(".flv", ignoreCase = true) ||
            urlPath.endsWith(".webm", ignoreCase = true) -> {
                ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                // Default to HLS for live streams without extension
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
        }
    }
}
