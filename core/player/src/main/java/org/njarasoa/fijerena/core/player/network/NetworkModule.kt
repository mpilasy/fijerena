package org.njarasoa.fijerena.core.player.network

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.player.config.NetworkType
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * One shared OkHttpClient, against memory leaks and thread exhaustion. All HTTP clients (Ktor,
 * OkHttp) reuse this engine or make shallow copies with [OkHttpClient.newBuilder].
 */
object NetworkModule {
    private var applicationContext: Context? = null

    /**
     * Gives the module the application context, so the DNS resolver can use Android network APIs.
     */
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
    }

    /**
     * The global OkHttpClient. One connection pool and one thread pool (Dispatcher) for the app,
     * which avoids OutOfMemoryErrors on low-memory devices.
     */
    val okHttpClient: OkHttpClient by lazy {
        // Limited concurrency against OOM, but enough for parallel tasks.
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    /**
     * Clears all connections in the pool. The stream health system uses it to bypass ISP/CDN
     * shaping with fresh sockets.
     */
    fun evictConnectionPool() {
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * Per-network-type OkHttp clients for streaming, derived from [okHttpClient] via
     * [OkHttpClient.newBuilder] (shared pool, dispatcher, DNS and redirects). OkHttpDataSource has
     * no per-call timeout setters, so timeouts are set per client: one client per network type.
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

    /** The player's calls, cancelled when it stops (see [StreamCallTracker]). */
    val streamCalls = StreamCallTracker()

    /** Returns the pre-built streaming client whose timeouts match [networkType]. */
    fun streamingClientFor(networkType: NetworkType): OkHttpClient =
        if (networkType == NetworkType.CELLULAR) cellularStreamingClient else wifiStreamingClient

    private object AndroidAwareDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (isIpAddress(hostname)) {
                try {
                    return listOf(InetAddress.getByName(hostname))
                } catch (_: Exception) {
                }
            }

            // One attempt only: this runs on the thread of the OkHttpClient's first use (can be the
            // main thread), so retry sleeps risk an ANR. AdaptiveLoadErrorPolicy already retries
            // the whole request with backoff.
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

        private fun isIpAddress(hostname: String): Boolean = hostname.matches(IPV4_REGEX) || hostname.contains(":")

        private const val TAG = "NetworkModule"
        private val IPV4_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    }
}
