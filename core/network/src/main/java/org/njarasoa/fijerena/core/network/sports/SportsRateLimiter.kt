package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The limit on the calls to one sports source (the sports teams plan → Gate): at most [maxCalls]
 * calls start in any [windowMs] window. A call over the limit waits. The sources are not ours, so
 * the limit is low: 30 calls a minute by default.
 *
 * [now] (milliseconds) is the clock of the window, so tests can use virtual time.
 */
class SportsRateLimiter(
    private val maxCalls: Int = 30,
    private val windowMs: Long = 60_000L,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val lock = Mutex()

    // The start times of the calls in the last window, oldest first.
    private val starts = ArrayDeque<Long>()

    /** Runs [call] when fewer than [maxCalls] calls started in the last window. */
    suspend fun <T> execute(call: suspend () -> T): T {
        awaitWindow()
        return call()
    }

    private suspend fun awaitWindow() {
        while (true) {
            val wait =
                lock.withLock {
                    val time = now()
                    while (starts.isNotEmpty() && time - starts.first() >= windowMs) starts.removeFirst()
                    if (starts.size < maxCalls) {
                        starts.addLast(time)
                        0L
                    } else {
                        starts.first() + windowMs - time
                    }
                }
            if (wait <= 0L) return
            delay(wait)
        }
    }
}
