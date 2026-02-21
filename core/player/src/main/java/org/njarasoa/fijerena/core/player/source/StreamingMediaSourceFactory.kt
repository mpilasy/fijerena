@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.CronetEngineProvider
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import java.util.concurrent.Executors

object StreamingMediaSourceFactory {

    /** Single daemon thread for Cronet callbacks. */
    private val cronetCallbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cronet-callback").apply { isDaemon = true }
    }

    /**
     * Creates a robust [MediaSource] using Media3's [DefaultMediaSourceFactory].
     * This factory automatically detects the stream type (HLS, DASH, Progressive)
     * and handles container formats like MPEG-TS, MP4, etc.
     *
     * Uses Cronet (HTTP/2 + QUIC/HTTP3) when available, with per-request fallback
     * to [DefaultHttpDataSource] if Cronet fails. Falls back entirely to
     * [DefaultHttpDataSource] on devices without Play Services Cronet.
     */
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

        // Network-aware HTTP timeouts
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
            headers = headers,
            isCellular = isCellular
        )

        val errorPolicy = AdaptiveLoadErrorPolicy(onRetry = onRetry)

        // Use DefaultMediaSourceFactory for robust automatic format detection
        // It supports HLS, DASH, and progressive streams (MP4, MKV, TS, etc.)
        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(errorPolicy)
            .createMediaSource(mediaItem)
    }

    /**
     * Builds the appropriate [DataSource.Factory] based on Cronet availability.
     *
     * If CronetEngine is available:
     *   - [CronetDataSource.Factory] with [DefaultHttpDataSource.Factory] as fallback
     *     for per-request recovery
     *
     * If Cronet is unavailable:
     *   - [DefaultHttpDataSource.Factory] directly
     */
    private fun buildDataSourceFactory(
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        headers: Map<String, String>,
        isCellular: Boolean
    ): DataSource.Factory {
        // DefaultHttpDataSource as the universal fallback
        val defaultFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val cronetEngine = CronetEngineProvider.get() ?: return defaultFactory

        // Cronet with per-request fallback to DefaultHttpDataSource
        return CronetDataSource.Factory(cronetEngine, cronetCallbackExecutor)
            .setConnectionTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)
            .setDefaultRequestProperties(headers)
            .setFallbackFactory(defaultFactory)
    }
}
