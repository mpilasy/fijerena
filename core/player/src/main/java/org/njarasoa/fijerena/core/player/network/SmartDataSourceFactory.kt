@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType

/**
 * A DataSource.Factory that selects the optimal HTTP stack based on the network type.
 *
 * WiFi/Other: Uses DefaultHttpDataSource (Java) with standard timeouts.
 * Cellular: Uses DefaultHttpDataSource (Java) with extended timeouts.
 */
class SmartDataSourceFactory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String> = emptyMap()
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val isCellular = NetworkMonitor.currentNetworkType == NetworkType.CELLULAR
        
        val connectTimeout = if (isCellular) 
            NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS 
        else 
            NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS
            
        val readTimeout = if (isCellular) 
            NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS 
        else 
            NetworkBufferProfile.WIFI_READ_TIMEOUT_MS

        return DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(connectTimeout)
            .setReadTimeoutMs(readTimeout)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(defaultRequestProperties)
            .createDataSource()
    }
}
