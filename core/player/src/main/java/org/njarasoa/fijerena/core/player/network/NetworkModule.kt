package org.njarasoa.fijerena.core.player.network

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Dns
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
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
     * A robust DNS resolver that uses Android's [ConnectivityManager.getActiveNetwork] API.
     * This is significantly more reliable than Java's [InetAddress.getAllByName] on modern
     * Android devices with Private DNS (DoT) or complex IPv4/IPv6 configurations, as it
     * leverages the system's current validated network path.
     */
    private object AndroidAwareDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val context = applicationContext
            if (context != null) {
                try {
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = connectivityManager?.activeNetwork
                    if (activeNetwork != null) {
                        // Use Android's network-specific resolver (handles Private DNS/DoT correctly)
                        val addresses = activeNetwork.getAllByName(hostname).toList()
                        if (addresses.isNotEmpty()) {
                            // Prefer IPv4 for better compatibility with IPTV providers
                            return addresses.sortedBy { it is java.net.Inet6Address }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NetworkModule", "DNS resolution failed via ActiveNetwork, falling back", e)
                }
            }

            // Fallback to standard system resolver
            return Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
        }
    }
}
