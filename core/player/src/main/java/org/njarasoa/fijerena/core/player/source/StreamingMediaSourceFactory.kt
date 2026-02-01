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

        return when {
            streamUrl.endsWith(".m3u8", ignoreCase = true) -> {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            streamUrl.endsWith(".mpd", ignoreCase = true) -> {
                DashMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            streamUrl.endsWith(".ts", ignoreCase = true) ||
            streamUrl.endsWith(".mpeg", ignoreCase = true) ||
            streamUrl.endsWith(".mp4", ignoreCase = true) ||
            streamUrl.endsWith(".mkv", ignoreCase = true) ||
            streamUrl.endsWith(".avi", ignoreCase = true) ||
            streamUrl.endsWith(".mov", ignoreCase = true) ||
            streamUrl.endsWith(".flv", ignoreCase = true) ||
            streamUrl.endsWith(".webm", ignoreCase = true) -> {
                ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                // Default to HLS for live streams without extension
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
        }
    }
}
