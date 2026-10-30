package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * The limit on TMDB calls (the cast tab plan, CT-D0): at most [maxPerSecond] calls start in any
 * 1-second window, and at most [maxInFlight] run at once. A call over the limit waits.
 *
 * On HTTP 429 the call waits for TMDB's `Retry-After` seconds, then tries once more. The second
 * answer goes back to the caller as it is, so a second 429 is a failed call.
 *
 * [TmdbApiService] holds one for the process. [now] (milliseconds) is the clock of the window, so
 * tests can use virtual time.
 */
class TmdbRateLimiter(
    private val maxPerSecond: Int = 20,
    private val maxInFlight: Int = 4,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val inFlight = Semaphore(maxInFlight)
    private val windowLock = Mutex()

    // The start times of the calls in the last second, oldest first.
    private val starts = ArrayDeque<Long>()

    /**
     * Runs [call] in the limit. When [retryAfter] gives a header value for the answer (the answer is
     * a 429), waits that many seconds and runs [call] once more. [retryAfter] gives null for any
     * other answer.
     */
    suspend fun <T> execute(
        retryAfter: (T) -> RetryAfter?,
        call: suspend () -> T,
    ): T {
        val first = limited(call)
        val wait = retryAfter(first) ?: return first
        delay(retryAfterMs(wait.header))
        return limited(call)
    }

    /** A 429 answer, with its `Retry-After` header value (null when it is missing). */
    data class RetryAfter(
        val header: String?,
    )

    private suspend fun <T> limited(call: suspend () -> T): T =
        inFlight.withPermit {
            awaitWindow()
            call()
        }

    /** Waits until fewer than [maxPerSecond] calls started in the last second, then records a start. */
    private suspend fun awaitWindow() {
        while (true) {
            val wait =
                windowLock.withLock {
                    val time = now()
                    while (starts.isNotEmpty() && time - starts.first() >= WINDOW_MS) starts.removeFirst()
                    if (starts.size < maxPerSecond) {
                        starts.addLast(time)
                        0L
                    } else {
                        starts.first() + WINDOW_MS - time
                    }
                }
            if (wait <= 0L) return
            delay(wait)
        }
    }

    companion object {
        private const val WINDOW_MS = 1_000L
        private const val DEFAULT_RETRY_AFTER_S = 2L
        private const val MAX_RETRY_AFTER_S = 10L

        /**
         * The wait for a `Retry-After` [header] in seconds: 2 seconds when it is missing or not a
         * number (an HTTP date included), and never more than 10 seconds.
         */
        fun retryAfterMs(header: String?): Long {
            val seconds = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: DEFAULT_RETRY_AFTER_S
            return seconds.coerceAtMost(MAX_RETRY_AFTER_S) * 1_000
        }
    }
}
