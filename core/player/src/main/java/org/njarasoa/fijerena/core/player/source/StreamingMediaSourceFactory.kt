@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import okhttp3.CacheControl
import org.njarasoa.fijerena.core.player.network.OkHttpProvider

object StreamingMediaSourceFactory {

    /**
     * Creates a network-aware MediaSource using a shared OkHttp client for connection pooling.
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

        // Create OkHttpDataSource.Factory using the shared OkHttpClient
        // This enables connection pooling (Keep-Alive) across segments and streams
        // Set a default User-Agent to ensure compatibility with providers that block generic/unknown UAs
        val userAgent = "FijerenaPlayer/1.0 (Android)"
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(OkHttpProvider.instance)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)

        // Wrap in DefaultDataSource.Factory to support file://, asset://, etc if needed
        // (though this factory is specifically for 'Streaming', so mostly HTTP)
        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        // Use DefaultMediaSourceFactory to automatically detect content type (HLS, DASH, Progressive/TS)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }

}
