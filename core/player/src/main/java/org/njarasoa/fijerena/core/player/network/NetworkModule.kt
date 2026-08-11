package org.njarasoa.fijerena.core.player.network

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Dns
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Singleton providing a shared OkHttpClient to prevent memory leaks and thread exhaustion.
 * All HTTP clients (Ktor, OkHttp) should reuse this underlying engine or create shallow
 * copies using [OkHttpClient.newBuilder].
 */
object NetworkModule {
    private var applicationContext: Context? = null

    /**
     * Initialize the network module with an application context.
     * This allows the DNS resolver to use Android-specific network APIs for better reliability.
     */
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
    }

    /**
     * The global OkHttpClient instance.
     * Sharing this ensures that we reuse the same connection pool and thread pool (Dispatcher),
     * which is critical for avoiding OutOfMemoryErrors on low-memory Android devices.
     */
    val okHttpClient: OkHttpClient by lazy {
        // Restrict concurrency to prevent OOM but allow enough for concurrent tasks
        val dispatcher =
            Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 4
            }

        OkHttpClient
            .Builder()
            .dispatcher(dispatcher)
            .dns(AndroidAwareDns)
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

    /**
     * Explicitly clear all connections in the pool.
     * Used by the stream health system to bypass ISP/CDN shaping by forcing fresh sockets.
     */
    fun evictConnectionPool() {
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * Per-network-type OkHttp clients for streaming, derived from [okHttpClient] via
     * [OkHttpClient.newBuilder] (shares connection pool, dispatcher, DNS, and redirect settings).
     * Connect/read timeouts are baked in at construction since OkHttpDataSource has no per-call
     * timeout setters (unlike DefaultHttpDataSource.Factory) — timeouts must be chosen at the
     * client level, hence one client per network type rather than one mutable shared client.
     */
    private val wifiStreamingClient: OkHttpClient by lazy {
        okHttpClient
            .newBuilder()
            .retryOnConnectionFailure(false)
            .connectTimeout(NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NetworkBufferProfile.WIFI_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private val cellularStreamingClient: OkHttpClient by lazy {
        okHttpClient
            .newBuilder()
            .retryOnConnectionFailure(false)
            .connectTimeout(NetworkBufferProfile.CELLULAR_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NetworkBufferProfile.CELLULAR_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    /** Returns the pre-built streaming client whose timeouts match [networkType]. */
    fun streamingClientFor(networkType: NetworkType): OkHttpClient =
        if (networkType == NetworkType.CELLULAR) cellularStreamingClient else wifiStreamingClient

    private object AndroidAwareDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (isIpAddress(hostname)) {
                try { return listOf(InetAddress.getByName(hostname)) } catch (_: Exception) { }
            }

            // Single attempt only — this runs synchronously on whatever thread triggers the
            // OkHttpClient's first use (can be the main thread), so blocking here with retry
            // sleeps risks an ANR. Transient DNS failure is already handled by the outer
            // AdaptiveLoadErrorPolicy, which retries the whole request with backoff.
            return resolveOnce(hostname)
        }

        private fun resolveOnce(hostname: String): List<InetAddress> {
            val context = applicationContext
            if (context != null) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = cm?.activeNetwork
                    if (activeNetwork != null) {
                        val addresses = activeNetwork.getAllByName(hostname).toList()
                        if (addresses.isNotEmpty()) {
                            return addresses.sortedBy { it is java.net.Inet6Address }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "DNS via ActiveNetwork failed for $hostname, trying system resolver", e)
                }
            }
            return Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
        }

        private fun isIpAddress(hostname: String): Boolean =
            hostname.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || hostname.contains(":")

        private const val TAG = "NetworkModule"
    }
}
