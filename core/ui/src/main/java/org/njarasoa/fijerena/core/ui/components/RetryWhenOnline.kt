package org.njarasoa.fijerena.core.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

// docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-15: an error screen retries by
// itself, once, when the device comes back online (a TV starting while its network is still coming
// up after wake is the most common case).

private const val TAG = "RetryWhenOnline"

/**
 * Calls [onRetry] once when the device goes from offline to online while this is composed. Put it
 * in an error state: an error shown while online (a server error) is never retried by itself, and
 * leaving the error state (the retry's Loading) ends it, so a failed retry waits for the next
 * offline → online change.
 */
@Composable
fun RetryWhenOnline(onRetry: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(context) {
        onlineStates(context).awaitBackOnline()
        currentOnRetry()
    }
}

/**
 * Suspends until this flow reports online after having reported offline: leading "online" values
 * are skipped, then the first "online" that follows an "offline" returns.
 */
suspend fun Flow<Boolean>.awaitBackOnline() {
    distinctUntilChanged().dropWhile { it }.first { it }
}

/**
 * Whether the default network can reach the internet (`INTERNET` and `VALIDATED`), now and on every
 * change. If the callback can't be registered it reports the current state only, and so never
 * "comes back".
 */
private fun onlineStates(context: Context): Flow<Boolean> =
    callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    trySend(caps.isOnline())
                }

                override fun onLost(network: Network) {
                    trySend(false)
                }
            }
        trySend(cm?.getNetworkCapabilities(cm.activeNetwork)?.isOnline() == true)
        val registered =
            try {
                cm?.registerDefaultNetworkCallback(callback)
                cm != null
            } catch (e: RuntimeException) {
                // SecurityException or TooManyRequestsException: no auto-retry, the Retry button stays.
                Log.w(TAG, "Couldn't watch connectivity", e)
                false
            }
        awaitClose { if (registered) cm?.unregisterNetworkCallback(callback) }
    }

private fun NetworkCapabilities.isOnline(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
