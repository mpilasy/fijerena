package org.njarasoa.fijerena.core.network.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for the RefreshQueue state races (round-2 plan, finding 1). RefreshQueue is a
 * process-wide singleton on Dispatchers.IO, so these run on real threads with real contention
 * rather than virtual time; every test uses unique task ids and [tearDown] drains the queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshQueueConcurrencyTest {
    private class GatedTask(
        override val id: String,
        override val priority: Int = RefreshPriority.HIGH,
        private val onRun: suspend () -> Unit = {},
    ) : RefreshTask {
        private val gate = CompletableDeferred<Unit>()
        val runs = AtomicInteger(0)

        fun release() {
            gate.complete(Unit)
        }

        override suspend fun execute() {
            runs.incrementAndGet()
            gate.await()
            onRun()
        }
    }

    @After
    fun tearDown() =
        runBlocking {
            RefreshQueue.cancelAll()
            awaitIdle()
        }

    private suspend fun awaitIdle() =
        withTimeout(TIMEOUT_MS) {
            while (RefreshQueue.activeTaskIds.value.isNotEmpty() || RefreshQueue.isProcessing.value) delay(5)
        }

    /** Idle and nothing queued, continuously for 100ms, so a task mid-hand-off is not mistaken for idle. */
    private suspend fun awaitStableIdle() =
        withTimeout(TIMEOUT_MS) {
            var calm = 0
            while (calm < 20) {
                val idle =
                    RefreshQueue.activeTaskIds.value.isEmpty() &&
                        !RefreshQueue.isProcessing.value &&
                        RefreshQueue.queuedTaskIds.value.isEmpty()
                calm = if (idle) calm + 1 else 0
                delay(5)
            }
        }

    /** Waits until the 3 semaphore permits are all held, i.e. exactly 3 of [tasks] have started. */
    private suspend fun awaitPermitsTaken(tasks: List<GatedTask>) =
        withTimeout(TIMEOUT_MS) {
            while (tasks.count { it.runs.get() > 0 } < 3) delay(5)
        }

    private suspend fun awaitActive(ids: List<String>) =
        withTimeout(TIMEOUT_MS) {
            while (!RefreshQueue.activeTaskIds.value.containsAll(ids)) delay(5)
        }

    @Test
    fun simultaneousCompletionsNeverLeaveDanglingActiveIds() =
        runBlocking(Dispatchers.Default) {
            repeat(20) { round ->
                val tasks = List(30) { GatedTask("complete-$round-$it") }
                val deferreds = tasks.map { RefreshQueue.submit(it) }
                // Only 3 run at once; release everything so completions pile up on the mutex.
                tasks.forEach { it.release() }
                withTimeout(TIMEOUT_MS) { deferreds.awaitAll() }
                awaitIdle()
                assertTrue(RefreshQueue.activeTaskIds.value.isEmpty())
                assertFalse(RefreshQueue.isProcessing.value)
                assertTrue(RefreshQueue.queuedTaskIds.value.isEmpty())
                assertTrue(tasks.all { it.runs.get() == 1 })
            }
        }

    @Test
    fun failingTasksCompletingTogetherDoNotStrandIds() =
        runBlocking(Dispatchers.Default) {
            val tasks = List(30) { GatedTask("fail-$it", onRun = { error("boom") }) }
            val deferreds = tasks.map { RefreshQueue.submit(it) }
            tasks.forEach { it.release() }
            deferreds.forEach { d ->
                withTimeout(TIMEOUT_MS) { d.join() }
                assertTrue(d.getCompletionExceptionOrNull() is IllegalStateException)
            }
            awaitIdle()
            assertTrue(RefreshQueue.activeTaskIds.value.isEmpty())
            assertFalse(RefreshQueue.isProcessing.value)
        }

    @Test
    fun cancelAllWhileTasksRunLeavesNoActiveIdsAndAllowsResubmission() =
        runBlocking(Dispatchers.Default) {
            val tasks = List(8) { GatedTask("cancel-$it") }
            val deferreds = tasks.map { RefreshQueue.submit(it) }
            awaitPermitsTaken(tasks)
            // Every task is off the queue (running or parked on the semaphore) before cancelling;
            // a task still queued can start between cancelAll()'s two critical sections.
            withTimeout(TIMEOUT_MS) { while (RefreshQueue.queuedTaskIds.value.isNotEmpty()) delay(5) }

            RefreshQueue.cancelAll()
            awaitStableIdle()

            assertTrue(RefreshQueue.activeTaskIds.value.isEmpty())
            assertFalse(RefreshQueue.isProcessing.value)
            assertTrue(RefreshQueue.queuedTaskIds.value.isEmpty())
            tasks.zip(deferreds).filter { (t, _) -> t.runs.get() > 0 }.forEach { (_, d) ->
                withTimeout(TIMEOUT_MS) { d.join() }
                assertTrue(d.isCancelled)
            }

            // The id is usable again: a stranded id would coalesce into the dead run's deferred.
            val again = GatedTask("cancel-0")
            again.release()
            withTimeout(TIMEOUT_MS) { RefreshQueue.submit(again).await() }
            assertEquals(1, again.runs.get())
        }

    @Ignore(
        "bug: RefreshQueue.runTask - a task already polled off the queue but still waiting for a " +
            "semaphore permit when cancelAll() runs is cancelled inside semaphore.withPermit, before " +
            "the try block, so its CompletableDeferred is never cancelled/completed and anything " +
            "awaiting the Deferred returned by submit() hangs forever",
    )
    @Test
    fun cancelAllCompletesDeferredsOfTasksStillWaitingForAPermit() =
        runBlocking(Dispatchers.Default) {
            val tasks = List(5) { GatedTask("permit-$it") }
            val deferreds = tasks.map { RefreshQueue.submit(it) }
            awaitPermitsTaken(tasks)
            withTimeout(TIMEOUT_MS) { while (RefreshQueue.queuedTaskIds.value.isNotEmpty()) delay(5) }

            RefreshQueue.cancelAll()

            tasks.zip(deferreds).filter { (t, _) -> t.runs.get() == 0 }.forEach { (_, d) ->
                withTimeout(2_000) { d.join() }
                assertTrue(d.isCancelled)
            }
        }

    @Test
    fun cancellationRacingCompletionsLeavesQueueIdle() =
        runBlocking(Dispatchers.Default) {
            repeat(20) { round ->
                val tasks = List(12) { GatedTask("race-$round-$it") }
                tasks.forEach { RefreshQueue.submit(it) }
                coroutineScope {
                    val cancel = async { RefreshQueue.cancelAll() }
                    val release = async { tasks.forEach { it.release() } }
                    cancel.await()
                    release.await()
                }
                // cancelAll() empties the pending queue in a second critical section, so a
                // still-queued task can legitimately start in between and run to completion; wait
                // for that to drain before checking the invariant.
                awaitStableIdle()
                assertTrue(RefreshQueue.activeTaskIds.value.isEmpty())
                assertFalse(RefreshQueue.isProcessing.value)
                assertTrue(RefreshQueue.queuedTaskIds.value.isEmpty())
            }
        }

    @Test
    fun parallelSubmissionsOfARunningTaskCoalesceIntoOneRun() =
        runBlocking(Dispatchers.Default) {
            val task = GatedTask("dedup-running")
            val first = RefreshQueue.submit(task)
            awaitActive(listOf(task.id))

            val results = (1..50).map { async { RefreshQueue.submit(GatedTask("dedup-running")) } }.awaitAll()

            assertTrue(results.all { it === first })
            assertTrue(RefreshQueue.queuedTaskIds.value.isEmpty())
            task.release()
            withTimeout(TIMEOUT_MS) { first.await() }
            awaitIdle()
            assertEquals(1, task.runs.get())
        }

    @Test
    fun parallelSubmissionsOfAQueuedTaskReplaceEarlierOnes() =
        runBlocking(Dispatchers.Default) {
            // Saturate the 3 permits so nothing else starts running underneath the contested id.
            val blockers = List(3) { GatedTask("dedup-blocker-$it") }
            blockers.forEach { RefreshQueue.submit(it) }
            awaitActive(blockers.map { it.id })

            val contenders = List(40) { GatedTask("dedup-queued", priority = RefreshPriority.LOW) }
            val deferreds = contenders.map { c -> async { RefreshQueue.submit(c) } }.awaitAll()
            contenders.forEach { it.release() }
            blockers.forEach { it.release() }

            val settled = deferreds.map { d -> runCatching { withTimeout(TIMEOUT_MS) { d.await() } } }
            awaitIdle()

            // Replaced submissions are cancelled; whichever one survived ran exactly once.
            assertTrue(settled.any { it.isSuccess })
            assertTrue(settled.filter { it.isFailure }.all { it.exceptionOrNull() is CancellationException })
            assertEquals(1, contenders.sumOf { it.runs.get() })
            assertTrue(RefreshQueue.activeTaskIds.value.isEmpty())
            assertTrue(RefreshQueue.queuedTaskIds.value.isEmpty())
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
