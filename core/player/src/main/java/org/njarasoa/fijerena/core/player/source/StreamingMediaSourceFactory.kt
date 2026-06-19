package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.network.NetworkModule
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * Factory for creating [MediaSource] instances.
 *
 * Reuses an internal [DefaultMediaSourceFactory] to avoid memory churn and binder exhaustion
 * during rapid channel switching. The HTTP data source is backed by OkHttp (via
 * [NetworkModule.streamingClientFor]) so streaming shares the same connection pool and
 * DNS-retry resilience as the rest of the app's networking.
 */
@OptIn(UnstableApi::class)
class StreamingMediaSourceFactory(context: Context) {
    private val userAgent = "MediaPlayer/1.0 (Linux; Android)"

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)

    fun createMediaSource(
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
        onRetry: (() -> Unit)? = null,
        transferListener: androidx.media3.datasource.TransferListener? = null,
    ): MediaSource {
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(streamUrl)
                .build()

        // Configure common HTTP factory
        val allHeaders = buildMap {
            put("User-Agent", userAgent)
            putAll(headers)
        }

        val callFactory = NetworkModule.streamingClientFor(NetworkMonitor.currentNetworkType)
        val httpDataSourceFactory =
            OkHttpDataSource.Factory(callFactory)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(allHeaders)
                .setTransferListener(transferListener)

        mediaSourceFactory.setDataSourceFactory(httpDataSourceFactory)

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        // Reuse media source factory with updated policy
        return mediaSourceFactory
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }
}
