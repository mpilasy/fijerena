@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.network.SmartDataSourceFactory

object StreamingMediaSourceFactory {

    /**
     * Creates a network-aware MediaSource using SmartDataSourceFactory.
     * Selects OkHttp for Cellular (speed/robustness) and DefaultHttp for WiFi (compatibility).
     */
    fun createMediaSource(
        context: Context,
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
        onRetry: (() -> Unit)? = null
    ): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()

        val userAgent = "FijerenaPlayer/1.0 (Android)"

        // Use our SmartDataSourceFactory that delegates to OkHttp (Cellular) or DefaultHttp (WiFi)
        val httpDataSourceFactory = SmartDataSourceFactory(userAgent, headers)

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
