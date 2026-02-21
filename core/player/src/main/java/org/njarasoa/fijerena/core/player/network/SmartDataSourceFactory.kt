@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.util.concurrent.TimeUnit

/**
 * A DataSource.Factory that selects the optimal HTTP stack.
 * Uses OkHttp for high-performance data transfer.
 */
class SmartDataSourceFactory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String> = emptyMap(),
    private val transferListener: androidx.media3.datasource.TransferListener? = null
) : DataSource.Factory {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun createDataSource(): DataSource {
        val isCellular = NetworkMonitor.currentNetworkType == NetworkType.CELLULAR

        return if (isCellular) {
            // High-performance Chrome User-Agent for cellular to bypass throttling
            val effectiveUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(effectiveUserAgent)
                .setDefaultRequestProperties(defaultRequestProperties)
                .setTransferListener(transferListener)
                .createDataSource()
        } else {
            // Standard Java stack for WiFi to ensure current performance is not affected
            DefaultHttpDataSource.Factory()
                .setUserAgent("AppleCoreMedia/1.0.0.16G77 (iPhone; iPhone OS 12_4; ABI 12_4) (null)")
                .setConnectTimeoutMs(NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(NetworkBufferProfile.WIFI_READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(defaultRequestProperties)
                .setTransferListener(transferListener)
                .createDataSource()
        }
    }
}
