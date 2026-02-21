package org.njarasoa.fijerena.core.player.network

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton providing a shared OkHttpClient to prevent memory leaks and thread exhaustion.
 * All HTTP clients (Ktor, OkHttp) should reuse this underlying engine or create shallow
 * copies using [OkHttpClient.newBuilder].
 */
object NetworkModule {
    /**
     * The global OkHttpClient instance.
     * Sharing this ensures that we reuse the same connection pool and thread pool (Dispatcher),
     * which is critical for avoiding OutOfMemoryErrors on low-memory Android devices.
     */
    val okHttpClient: OkHttpClient by lazy {
        // Restrict concurrency to prevent OOM but allow enough for concurrent tasks
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 4
        }

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .followRedirects(true)
            .followSslRedirects(true)
            // Standard timeouts for general API calls
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Automatically clean up idle connections
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }
}
