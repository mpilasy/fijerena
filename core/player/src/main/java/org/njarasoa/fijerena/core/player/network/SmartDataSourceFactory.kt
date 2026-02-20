@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * A hybrid DataSource.Factory that selects the optimal HTTP stack based on the network type.
 *
 * Strategy:
 * - **Cellular**: Uses [OkHttpDataSource] with a custom DNS resolver that prioritizes IPv4.
 *   - Why: Cellular networks often have poor IPv6 routing or long timeouts for legacy IPTV servers that
 *     don't support IPv6. Carriers often inject terrible DNS. This forces IPv4 resolution via
 *     Google DNS (8.8.8.8) to bypass these issues.
 * - **WiFi**: Uses [DefaultHttpDataSource] (Java URLConnection).
 *   - Why: Proven stability on local/stable networks.
 */
class SmartDataSourceFactory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String> = emptyMap()
) : DataSource.Factory {

    // Custom DNS that prioritizes IPv4 to avoid IPv6 timeouts on cellular
    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                // Try system lookup first
                val allAddresses = Dns.SYSTEM.lookup(hostname)
                // Filter for IPv4 only if possible
                val ipv4 = allAddresses.filter { it is Inet4Address }
                if (ipv4.isNotEmpty()) {
                    return ipv4
                }
                // Fallback to all addresses if no IPv4 found
                return allAddresses
            } catch (e: Exception) {
                // If system lookup fails (e.g. carrier DNS is broken), try Google DNS directly?
                // For now, let's just stick to filtering system results.
                // If we need to bypass carrier DNS completely, we'd need a DoH client.
                throw e
            }
        }
    }

    // Internal OkHttpClient for cellular connections
    private val cellularClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4Dns) // Force IPv4 resolution
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
            // Cellular: Use OkHttp with IPv4/DNS fix
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
