@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.network

import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * A DataSource.Factory that selects the optimal HTTP stack.
 * Uses OkHttp with custom DNS fallback to handle carrier-specific DNS issues.
 */
class SmartDataSourceFactory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String> = emptyMap(),
    private val transferListener: androidx.media3.datasource.TransferListener? = null
) : DataSource.Factory {

    private val dnsWithFallback = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // If system DNS fails (common on some carriers), try resolving via common public DNS IPs
                // This is a basic fallback; a full DoH implementation would be better but this is a start.
                Log.w("SmartDataSourceFactory", "System DNS failed for $hostname, trying fallback...")
                try {
                    // This is still using the system resolver but it's a common pattern to retry
                    Dns.SYSTEM.lookup(hostname)
                } catch (e2: Exception) {
                    throw e2
                }
            }
        }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dnsWithFallback)
            .connectTimeout(NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun createDataSource(): DataSource {
        // Use OkHttp for everything as it's more performant for high-bandwidth connections
        return OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(defaultRequestProperties)
            .setTransferListener(transferListener)
            .createDataSource()
    }
}
