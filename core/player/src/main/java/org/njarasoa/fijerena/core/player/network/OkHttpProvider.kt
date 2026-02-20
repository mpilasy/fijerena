package org.njarasoa.fijerena.core.player.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton provider for a shared [OkHttpClient] used by Media3 DataSource.
 *
 * Provides:
 * - Connection pooling (HTTP/1.1 & HTTP/2)
 * - Optimized timeouts for streaming
 * - Automatic retries
 */
object OkHttpProvider {

    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC = 30L
    private const val KEEP_ALIVE_DURATION_MIN = 5L
    private const val MAX_IDLE_CONNECTIONS = 10

    /**
     * Lazy-initialized OkHttpClient to avoid startup penalty if not needed immediately.
     */
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            // Connection pooling is critical for HLS/DASH segment downloading
            .connectionPool(
                ConnectionPool(
                    MAX_IDLE_CONNECTIONS,
                    KEEP_ALIVE_DURATION_MIN,
                    TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
