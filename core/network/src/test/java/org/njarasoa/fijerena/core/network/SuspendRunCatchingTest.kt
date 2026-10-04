package org.njarasoa.fijerena.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-27. */
class SuspendRunCatchingTest {
    @Test
    fun `success is wrapped`() =
        runTest {
            val result = suspendRunCatching { 42 }
            assertEquals(42, result.getOrNull())
        }

    @Test
    fun `ordinary exception is wrapped as a failure`() =
        runTest {
            val error = IOException("boom")
            val result = suspendRunCatching { throw error }
            assertSame(error, result.exceptionOrNull())
        }

    @Test
    fun `error other than an exception is wrapped like runCatching`() =
        runTest {
            val error = StackOverflowError()
            val result = suspendRunCatching { throw error }
            assertSame(error, result.exceptionOrNull())
        }

    @Test
    fun `cancellation exception is rethrown`() =
        runTest {
            val cancellation = CancellationException("stop")
            var caught: Throwable? = null
            try {
                suspendRunCatching { throw cancellation }
                fail("CancellationException must not be wrapped")
            } catch (e: CancellationException) {
                caught = e
            }
            assertSame(cancellation, caught)
        }

    @Test
    fun `cancelled job stops instead of carrying on`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var carriedOn = false
            val job =
                async(start = CoroutineStart.UNDISPATCHED) {
                    suspendRunCatching {
                        started.complete(Unit)
                        awaitCancellation()
                    }
                    carriedOn = true
                }
            started.await()
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(carriedOn)
        }
}
