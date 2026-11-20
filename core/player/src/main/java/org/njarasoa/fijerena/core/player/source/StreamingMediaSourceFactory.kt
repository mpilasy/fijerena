package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.network.CustomUserAgent
import org.njarasoa.fijerena.core.player.network.NetworkModule
import org.njarasoa.fijerena.core.player.network.NetworkMonitor

/**
 * Reuses one [DefaultMediaSourceFactory] against memory churn and binder exhaustion during fast
 * channel switching. The HTTP data source uses OkHttp (via [NetworkModule.streamingClientFor]), so
 * streaming shares the app's connection pool and DNS retry.
 */
@OptIn(UnstableApi::class)
class StreamingMediaSourceFactory(
    private val context: Context,
    /** A live MPEG-TS connection dropped or ended: start the channel afresh ([LiveDropDataSource]); loader thread. */
    private val onLiveDrop: (reason: String) -> Unit = {},
) {
    private val mediaSourceFactory = DefaultMediaSourceFactory(context)

    @Synchronized
    fun createMediaSource(
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
        onRetry: (() -> Unit)? = null,
        transferListener: androidx.media3.datasource.TransferListener? = null,
        metadata: PlayerMetadata? = null,
    ): MediaSource {
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(streamUrl)
                .apply { if (metadata != null) setMediaMetadata(metadata.toMediaMetadata()) }
                .build()

        val allHeaders =
            buildMap {
                put(CustomUserAgent.HEADER, CustomUserAgent.current)
                putAll(headers)
            }

        val callFactory = NetworkModule.streamCalls.wrap(NetworkModule.streamingClientFor(NetworkMonitor.currentNetworkType))
        val httpDataSourceFactory =
            OkHttpDataSource
                .Factory(callFactory)
                .setUserAgent(CustomUserAgent.current)
                .setDefaultRequestProperties(allHeaders)
                .setTransferListener(transferListener)

        // A live progressive stream (MPEG-TS) starts afresh when its connection drops. HLS loads
        // short pieces that Media3's error policy retries; a film resumes at its byte position.
        val upstreamFactory: DataSource.Factory =
            if (isLive && !isHls(streamUrl)) {
                DataSource.Factory { LiveDropDataSource(httpDataSourceFactory.createDataSource(), onLiveDrop) }
            } else {
                httpDataSourceFactory
            }

        // OkHttpDataSource only reads http(s)://; as the root factory it threw "Expected HTTP
        // scheme" for every file:// (LOCAL provider) or content:// URI. DefaultDataSource.Factory
        // dispatches by scheme: http(s) to httpDataSourceFactory, file / content / asset to
        // Media3's local data sources.
        mediaSourceFactory.setDataSourceFactory(DefaultDataSource.Factory(context, upstreamFactory))

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        return mediaSourceFactory
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }

    private fun isHls(url: String): Boolean = url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)

    private fun PlayerMetadata.toMediaMetadata(): MediaMetadata =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .setDisplayTitle(showTitle ?: title)
            .setAlbumTitle(showTitle)
            .setSubtitle(episodeLabel)
            .setStation(channelName.ifEmpty { null })
            .setDescription(description)
            .build()
}
