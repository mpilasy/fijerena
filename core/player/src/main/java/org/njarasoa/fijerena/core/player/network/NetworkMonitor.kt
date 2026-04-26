package org.njarasoa.fijerena.core.player.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.player.config.NetworkType

/**
 * Singleton that observes network connectivity changes via ConnectivityManager.
 *
 * Provides:
 * - [networkType] StateFlow for coroutine-based collection
 * - [currentNetworkType] @Volatile field for hot-path synchronous reads (LoadControl, error policy)
 *
 * Safe to call [init] multiple times (idempotent). Call [release] when done.
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"

    private val _networkType = MutableStateFlow(NetworkType.WIFI)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    /** Hot-path read for LoadControl and error policy — no suspension needed. */
    @Volatile
    var currentNetworkType: NetworkType = NetworkType.WIFI
        private set

    private var connectivityManager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var initialized = false

    /**
     * Register a NetworkCallback. Idempotent — calling again after init is a no-op.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        // Seed with current network state
        val initial = resolveNetworkType(cm.getNetworkCapabilities(cm.activeNetwork))
        updateType(initial)
        Log.i(TAG, "Initialized. Current network: $initial")

        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    val type = resolveNetworkType(caps)
                    if (type != currentNetworkType) {
                        Log.i(TAG, "Network changed: $currentNetworkType -> $type")
                        updateType(type)
                    }
                }

                override fun onLost(network: Network) {
                    // Network lost — fall back to UNKNOWN (will use WIFI-like defaults)
                    Log.i(TAG, "Network lost, falling back to UNKNOWN")
                    updateType(NetworkType.UNKNOWN)
                }
            }
        callback = networkCallback
        cm.registerNetworkCallback(request, networkCallback)
    }

    /**
     * Unregister the callback. Safe to call even if not initialized.
     */
    fun release() {
        callback?.let { cb ->
            try {
                connectivityManager?.unregisterNetworkCallback(cb)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        callback = null
        connectivityManager = null
        initialized = false
    }

    private fun resolveNetworkType(caps: NetworkCapabilities?): NetworkType {
        if (caps == null) return NetworkType.UNKNOWN
        return when {
            // Ethernet and WiFi both map to WIFI profile (stable, high bandwidth)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.UNKNOWN
        }
    }

    private fun updateType(type: NetworkType) {
        currentNetworkType = type
        _networkType.value = type
    }
}
