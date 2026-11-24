package org.njarasoa.fijerena.core.player.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether Android lets this app use the network. Just after a device wakes, Doze and App Standby
 * still block the app for about a second: a request then fails with "Unable to resolve host"
 * although the network is up (a Shield woken by the TV, 2026-11-23).
 */
object NetworkAccess {
    /** How long [awaitOpen] waits at most. A real outage then still fails, and says so. */
    const val MAX_WAIT_MS = 10_000L

    /**
     * Returns when Android reports the app's default network as not blocked, or after [timeoutMs]
     * at most. True when it is open, false when the time ran out.
     */
    suspend fun awaitOpen(
        context: Context,
        timeoutMs: Long = MAX_WAIT_MS,
    ): Boolean = awaitOpen(blockedStatus(context), timeoutMs)

    /** [awaitOpen] on a stream of "blocked" values: the first `false` ends the wait. */
    internal suspend fun awaitOpen(
        blocked: Flow<Boolean>,
        timeoutMs: Long,
    ): Boolean = withTimeoutOrNull(timeoutMs) { blocked.firstOrNull { !it } } != null

    /**
     * The default network's blocked status. Android sends the current value as soon as the
     * callback is registered, and each change after it. Nothing comes while there is no network.
     */
    private fun blockedStatus(context: Context): Flow<Boolean> =
        callbackFlow {
            val manager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)
            if (manager == null) {
                // No connectivity service (never on a phone or a TV): nothing to wait for.
                close()
            } else {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onBlockedStatusChanged(
                            network: Network,
                            blocked: Boolean,
                        ) {
                            trySend(blocked)
                        }
                    }
                manager.registerDefaultNetworkCallback(callback)
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }
        }
}
