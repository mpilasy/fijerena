@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.source

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import org.njarasoa.fijerena.core.player.network.NetworkMonitor
import android.util.Log
import java.util.concurrent.Executors

object StreamingMediaSourceFactory {

    // Use a standard Chrome User-Agent to avoid carrier throttling (e.g., T-Mobile Binge On)
    // which often targets generic "MediaPlayer" or "ExoPlayer" agents.
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var cronetEngine: CronetEngine? = null
    private val cronetExecutor = Executors.newSingleThreadExecutor()

    /**
     * Initialize Cronet engine. Call from service onCreate().
     * Cronet provides QUIC/HTTP/3 support which can significantly improve
     * streaming performance on cellular networks.
     */
    fun initCronet(context: Context) {
        try {
            CronetProviderInstaller.installProvider(context)
            cronetEngine = CronetEngine.Builder(context)
                .setUserAgent(USER_AGENT)
                .enableQuic(true)
                .enableHttp2(true)
                .build()
            Log.i("StreamingMedia", "Cronet engine initialized with QUIC+HTTP/2")
        } catch (e: Exception) {
            Log.w("StreamingMedia", "Cronet unavailable, falling back to DefaultHttpDataSource", e)
            cronetEngine = null
        }
    }

    /**
     * Release Cronet engine. Call from service onDestroy().
     */
    fun releaseCronet() {
        cronetEngine?.shutdown()
        cronetEngine = null
    }

    /**
     * Creates a [MediaSource] using Cronet (QUIC/HTTP/3) when available,
     * falling back to [DefaultHttpDataSource] otherwise.
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

        val engine = cronetEngine
        if (engine != null) {
            Log.i("StreamingMedia", "Using CronetDataSource (QUIC/HTTP/3)")
            return CronetDataSource.Factory(engine, cronetExecutor)
                .setConnectionTimeoutMs(connectTimeoutMs)
                .setReadTimeoutMs(readTimeoutMs)
                .setDefaultRequestProperties(allHeaders)
        }

        // Fallback to DefaultHttpDataSource if Cronet is unavailable
        Log.w("StreamingMedia", "Cronet unavailable, using DefaultHttpDataSource")
        return DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(connectTimeoutMs)
            .setReadTimeoutMs(readTimeoutMs)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(allHeaders)
    }
}
