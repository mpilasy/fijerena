package org.njarasoa.fijerena.core.ui.utils

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-09. */
class LaunchGuardedTest {
    /** Stands in for viewModelScope; anything recorded in [escaped] would have crashed the app. */
    private class Harness(
        testScope: TestScope,
    ) {
        val escaped = mutableListOf<Throwable>()
        val scope =
            CoroutineScope(
                SupervisorJob() + StandardTestDispatcher(testScope.testScheduler) +
                    CoroutineExceptionHandler { _, e -> escaped += e },
            )
    }

    @Test
    fun `a failure goes to onError and never reaches the scope`() =
        runTest {
            val harness = Harness(this)
            val errors = mutableListOf<Throwable>()
            var siblingRan = false

            harness.scope.launchGuarded("test", onError = { errors += it }) { throw IOException("disk") }
            harness.scope.launch { siblingRan = true }
            advanceUntilIdle()

            assertEquals(listOf("disk"), errors.map { it.message })
            assertTrue(harness.escaped.isEmpty())
            assertTrue(siblingRan)
        }

    @Test
    fun `a failing child launched inside the block is caught too`() =
        runTest {
            val harness = Harness(this)
            val errors = mutableListOf<Throwable>()

            harness.scope.launchGuarded("test", onError = { errors += it }) {
                launch { throw IllegalStateException("child") }
                awaitCancellation()
            }
            advanceUntilIdle()

            assertEquals(listOf("child"), errors.map { it.message })
            assertTrue(harness.escaped.isEmpty())
        }

    @Test
    fun `cancellation is not an error`() =
        runTest {
            val harness = Harness(this)
            val errors = mutableListOf<Throwable>()

            val job = harness.scope.launchGuarded("test", onError = { errors += it }) { awaitCancellation() }
            advanceUntilIdle()
            harness.scope.cancel()
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(errors.isEmpty())
            assertTrue(harness.escaped.isEmpty())
        }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `an ATOMIC start still runs a NonCancellable write when the scope is cancelled first`() =
        runTest {
            // StreamLoaderViewModel.stopPlayback relies on this: called from onDispose, its scope
            // can be cancelled before the dispatcher runs it, and the final position must still land.
            val harness = Harness(this)
            var wrote = false

            harness.scope.launchGuarded("test", start = CoroutineStart.ATOMIC) {
                withContext(NonCancellable) { wrote = true }
            }
            harness.scope.cancel()
            advanceUntilIdle()

            assertTrue(wrote)
            assertTrue(harness.escaped.isEmpty())
        }

    @Test
    fun `a block that succeeds never calls onError`() =
        runTest {
            val harness = Harness(this)
            val errors = mutableListOf<Throwable>()
            var ran = false

            harness.scope.launchGuarded("test", onError = { errors += it }) { ran = true }
            advanceUntilIdle()

            assertTrue(ran)
            assertTrue(errors.isEmpty())
        }
}
