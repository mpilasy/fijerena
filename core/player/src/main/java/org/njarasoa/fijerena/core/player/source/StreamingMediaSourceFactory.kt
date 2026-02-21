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
        transferListener: androidx.media3.datasource.TransferListener? = null,
        onRetry: (() -> Unit)? = null
    ): MediaSource {
        android.util.Log.i("StreamingMediaSourceFactory", "Creating MediaSource for: $streamUrl")
        
        val mimeType = when {
            streamUrl.contains(".m3u8") -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
            streamUrl.contains(".mpd") -> androidx.media3.common.MimeTypes.APPLICATION_MPD
            streamUrl.contains(".ism") -> androidx.media3.common.MimeTypes.APPLICATION_SS
            else -> null // Let DefaultMediaSourceFactory detect for progressive/TS
        }

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .apply {
                if (mimeType != null) setMimeType(mimeType)
            }
            .build()

        val userAgent = "FijerenaPlayer/1.0 (Android)"

        // Use our SmartDataSourceFactory that delegates to OkHttp (Cellular) or DefaultHttp (WiFi)
        val httpDataSourceFactory = SmartDataSourceFactory(userAgent, headers, transferListener)

        // Wrap in DefaultDataSource.Factory to support file://, asset://, etc if needed
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        val factory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)

        // For HLS streams, we create the source directly to ensure optimal settings
        if (mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8) {
            val isCellular = org.njarasoa.fijerena.core.player.network.NetworkMonitor.currentNetworkType == org.njarasoa.fijerena.core.player.config.NetworkType.CELLULAR
            
            // Only use the aggressive/forgiving extractor on cellular where signal/throttling causes issues
            val extractorFactory = if (isCellular) {
                androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory(
                    /* masterPlaylistPacketPayloadType = */ androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
                    /* exposeCaptionFormats = */ true
                )
            } else {
                androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory()
            }

            return androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(extractorFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(errorPolicy)
                .createMediaSource(
                    mediaItem.buildUpon()
                        .setLiveConfiguration(
                            androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(10_000) // 10s from live edge
                                .build()
                        )
                        .build()
                )
        }

        return factory.createMediaSource(mediaItem)
    }

}
