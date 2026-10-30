package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The limit on TMDB calls (docs/plans/20261030_cast-tab-plan.md, CT-D0). */
class TmdbRateLimiterTest {
    private fun TestScope.limiter() = TmdbRateLimiter(now = { testScheduler.currentTime })

    /** A fake answer: the HTTP status and the `Retry-After` header. */
    private data class Answer(
        val status: Int,
        val retryAfter: String? = null,
    )

    private val tooMany: (Answer) -> TmdbRateLimiter.RetryAfter? = { answer ->
        if (answer.status == 429) TmdbRateLimiter.RetryAfter(answer.retryAfter) else null
    }

    private suspend fun TmdbRateLimiter.call(answer: suspend () -> Answer): Answer = execute(tooMany, answer)

    @Test
    fun `the 21st call in one second waits for the window`() =
        runTest {
            val limiter = limiter()
            val startedAt = mutableListOf<Long>()
            (1..21)
                .map {
                    async {
                        limiter.call {
                            startedAt += testScheduler.currentTime
                            Answer(200)
                        }
                    }
                }.awaitAll()
            assertEquals(List(20) { 0L }, startedAt.take(20))
            assertEquals(1_000L, startedAt.last())
        }

    @Test
    fun `no more than 4 calls run at once`() =
        runTest {
            val limiter = limiter()
            var running = 0
            var most = 0
            (1..12)
                .map {
                    async {
                        limiter.call {
                            running++
                            most = maxOf(most, running)
                            delay(100)
                            running--
                            Answer(200)
                        }
                    }
                }.awaitAll()
            assertEquals(4, most)
            // 12 calls of 100 ms, 4 at a time.
            assertEquals(300L, testScheduler.currentTime)
        }

    @Test
    fun `a 429 then a success tries once more after Retry-After`() =
        runTest {
            val limiter = limiter()
            val calledAt = mutableListOf<Long>()
            val answers = ArrayDeque(listOf(Answer(429, "5"), Answer(200)))
            val result =
                limiter.call {
                    calledAt += testScheduler.currentTime
                    answers.removeFirst()
                }
            assertEquals(Answer(200), result)
            assertEquals(listOf(0L, 5_000L), calledAt)
        }

    @Test
    fun `a second 429 goes back to the caller with no third try`() =
        runTest {
            val limiter = limiter()
            var calls = 0
            val result =
                limiter.call {
                    calls++
                    Answer(429, "1")
                }
            assertEquals(429, result.status)
            assertEquals(2, calls)
        }

    @Test
    fun `a missing Retry-After waits 2 seconds`() =
        runTest {
            val limiter = limiter()
            val calledAt = mutableListOf<Long>()
            val answers = ArrayDeque(listOf(Answer(429), Answer(200)))
            limiter.call {
                calledAt += testScheduler.currentTime
                answers.removeFirst()
            }
            assertEquals(listOf(0L, 2_000L), calledAt)
        }

    @Test
    fun `Retry-After is 2 seconds when unreadable and at most 10 seconds`() {
        assertEquals(2_000L, TmdbRateLimiter.retryAfterMs(null))
        assertEquals(2_000L, TmdbRateLimiter.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(2_000L, TmdbRateLimiter.retryAfterMs("-3"))
        assertEquals(3_000L, TmdbRateLimiter.retryAfterMs(" 3 "))
        assertEquals(10_000L, TmdbRateLimiter.retryAfterMs("120"))
    }
}
