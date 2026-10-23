package org.njarasoa.fijerena.core.player.network

import okhttp3.Call
import java.util.Collections
import java.util.WeakHashMap
import kotlin.concurrent.thread

/**
 * The player's HTTP calls, so a stop can close their sockets at once. A stopped player cancels its
 * loads, but a load blocked reading a stalled stream ignores that until the read timeout (30 s),
 * and an Xtream panel counts the open socket as a stream all that time: reopening on a
 * 1-connection account then fails as busy. Cancelling the call closes the socket mid-read
 * (docs/plans/20261017_xtream-stream-release-plan.md → Phase 3).
 */
class StreamCallTracker(
    private val runInBackground: (Runnable) -> Unit = { thread(name = "StreamCallTracker") { it.run() } },
) {
    // Weak: a finished call is forgotten with no bookkeeping.
    private val calls = Collections.newSetFromMap(WeakHashMap<Call, Boolean>())

    /** [delegate], with every call it makes remembered for [cancelAll]. */
    fun wrap(delegate: Call.Factory): Call.Factory =
        Call.Factory { request ->
            delegate.newCall(request).also { call -> synchronized(calls) { calls += call } }
        }

    /**
     * Cancels every call made so far. The set is taken now, so a call the next stream makes after
     * this returns is never cancelled; the cancels themselves run off the caller's thread, since
     * closing a socket can block.
     */
    fun cancelAll() {
        val open = synchronized(calls) { calls.toList().also { calls.clear() } }
        if (open.isNotEmpty()) runInBackground { open.forEach(Call::cancel) }
    }
}
