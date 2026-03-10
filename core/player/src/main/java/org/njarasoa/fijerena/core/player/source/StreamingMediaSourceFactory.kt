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

@OptIn(UnstableApi::class)
object StreamingMediaSourceFactory {

    private const val USER_AGENT = "MediaPlayer/1.0 (Linux; Android)"

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

        val connectTimeout = if (isCellular)
            NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS
        else
            NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS
        val readTimeout = if (isCellular)
            NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS
        else
            NetworkBufferProfile.WIFI_READ_TIMEOUT_MS

        val dataSourceFactory = buildDataSourceFactory(
            connectTimeoutMs = connectTimeout,
            readTimeoutMs = readTimeout,
            headers = headers
        )

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }

    private fun buildDataSourceFactory(
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        headers: Map<String, String>
    ): DataSource.Factory {
        val allHeaders = buildMap {
            put("User-Agent", USER_AGENT)
            putAll(headers)
        }

        return DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(allHeaders)
    }
}
