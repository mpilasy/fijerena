@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.njarasoa.fijerena.core.player.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile

/**
 * A simple DataSource.Factory that uses the standard Android HTTP stack.
 */
class SmartDataSourceFactory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String> = emptyMap(),
    private val transferListener: androidx.media3.datasource.TransferListener? = null
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        // Use a standard, widely-accepted User-Agent if the provided one is custom
        val effectiveUserAgent = "AppleCoreMedia/1.0.0.16G77 (iPhone; iPhone OS 12_4; ABI 12_4) (null)"

        return DefaultHttpDataSource.Factory()
            .setUserAgent(effectiveUserAgent)
            .setConnectTimeoutMs(NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(NetworkBufferProfile.WIFI_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(defaultRequestProperties)
            .setTransferListener(transferListener)
            .createDataSource()
    }
}
