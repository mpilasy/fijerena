@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.util.concurrent.TimeUnit

/**
 * A hybrid DataSource.Factory that selects the optimal HTTP stack based on the network type.
 *
 * Strategy:
 * - **Cellular**: Uses [OkHttpDataSource] with HTTP/1.1 enforcement and connection pooling.
 *   - Why: Cellular networks are bursty and high-latency. HTTP/1.1 is more robust on legacy IPTV servers,
 *     and connection pooling helps reduce handshake overhead.
 * - **WiFi**: Uses [DefaultHttpDataSource] (Java URLConnection).
 *   - Why: Proven stability on local/stable networks. Avoids potential IPv6/DNS issues that OkHttp might hit on some routers.
 */
class SmartDataSourceFactory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String> = emptyMap()
) : DataSource.Factory {

    // Internal OkHttpClient for cellular connections
    private val cellularClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1)) // Force HTTP/1.1 for stability
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun createDataSource(): DataSource {
        return if (NetworkMonitor.currentNetworkType == NetworkType.CELLULAR) {
            // Cellular: Use OkHttp
            OkHttpDataSource.Factory(cellularClient)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(defaultRequestProperties)
                .createDataSource()
        } else {
            // WiFi/Other: Use DefaultHttpDataSource (Java)
            DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setConnectTimeoutMs(NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(NetworkBufferProfile.WIFI_READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(defaultRequestProperties)
                .createDataSource()
        }
    }
}
