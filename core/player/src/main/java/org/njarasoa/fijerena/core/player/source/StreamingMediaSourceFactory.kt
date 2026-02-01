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
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        // Add custom headers
        headers.forEach { (key, value) ->
            httpDataSourceFactory.setDefaultRequestProperties(mapOf(key to value))
        }

        return when {
            streamUrl.endsWith(".m3u8", ignoreCase = true) -> {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            streamUrl.endsWith(".mpd", ignoreCase = true) -> {
                DashMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            streamUrl.endsWith(".ts", ignoreCase = true) ||
            streamUrl.endsWith(".mpeg", ignoreCase = true) -> {
                ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                // Default to HLS for unknown formats
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            }
        }
    }
}
