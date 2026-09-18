package org.njarasoa.fijerena.core.network.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.PriorityQueue

/**
 * Singleton queue manager that processes tasks based on priority.
 * Supports concurrent execution of multiple tasks up to a maximum limit.
 */
object RefreshQueue {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = PriorityQueue<QueuedTask>()
    private val queueMutex = Mutex()
    private val processChannel = Channel<Unit>(Channel.CONFLATED)

    // Max concurrency: 3 tasks (e.g., Live, VOD, and Series sync can overlap)
    private val semaphore = Semaphore(3)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _queuedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val queuedTaskIds = _queuedTaskIds.asStateFlow()

    private val _activeTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val activeTaskIds = _activeTaskIds.asStateFlow()

    private class QueuedTask(
        val task: RefreshTask,
        val deferred: CompletableDeferred<Unit>,
    ) : Comparable<QueuedTask> {
        override fun compareTo(other: QueuedTask): Int = task.compareTo(other.task)
    }

    private class ActiveTask(
        val job: Job,
        val deferred: CompletableDeferred<Unit>,
    )

    // Tasks already polled off `queue` and currently executing (or about to). Guarded by the
    // same queueMutex as `queue` so a task can never be in neither place at once — that gap
    // is exactly what previously let submit() enqueue a concurrent duplicate of a running task.
    private val activeTasks = mutableMapOf<String, ActiveTask>()

    init {
        startWorker()
    }

    private fun startWorker() {
        scope.launch {
            for (trigger in processChannel) {
                processAvailable()
            }
        }
    }

    /**
     * Submit a task to the queue.
     * Returns a Deferred that completes when the task finishes. If a task with the same
     * ID is already executing, coalesces into that run's Deferred instead of starting a
     * second, concurrent one.
     */
    suspend fun submit(task: RefreshTask): Deferred<Unit> {
        val deferred =
            queueMutex.withLock {
                activeTasks[task.id]?.let { return@withLock it.deferred }

                // Remove existing queued task with same ID to ensure we don't queue multiple same tasks
                val existing = queue.find { it.task.id == task.id }
                if (existing != null) {
                    queue.remove(existing)
                    existing.deferred.cancel()
                }
                val newDeferred = CompletableDeferred<Unit>()
                queue.add(QueuedTask(task, newDeferred))
                _queuedTaskIds.value = queue.map { it.task.id }.toSet()
                newDeferred
            }
        processChannel.trySend(Unit)
        return deferred
    }

    private suspend fun processAvailable() {
        var hasMore = true
        while (hasMore) {
            // Dequeue, launch, and register the real Job in activeTasks all inside one lock
            // acquisition. A prior version registered a disconnected placeholder Job() here and
            // swapped in the real one after launching — a cancelAll() landing in that window
            // cancelled the placeholder (a no-op) and cleared activeTasks, then the real job got
            // re-inserted afterward, uncancelled and untouched by the cancellation it should have
            // seen.
            hasMore =
                queueMutex.withLock {
                    val queuedTask = queue.poll()
                    if (queuedTask != null) {
                        _queuedTaskIds.value = queue.map { it.task.id }.toSet()
                        val job = scope.launch { runTask(queuedTask) }
                        activeTasks[queuedTask.task.id] = ActiveTask(job, queuedTask.deferred)
                    }
                    queuedTask != null
                }
        }
    }

    private suspend fun runTask(queuedTask: QueuedTask) {
        semaphore.withPermit {
            // All _activeTaskIds/_isProcessing transitions go through queueMutex so concurrent
            // completions can't race a read-modify-write on the StateFlow and strand an ID (see
            // RefreshQueue finding in the concurrency audit).
            queueMutex.withLock {
                _activeTaskIds.value = _activeTaskIds.value + queuedTask.task.id
                _isProcessing.value = true
            }
            try {
                queuedTask.task.execute()
                queuedTask.deferred.complete(Unit)
            } catch (e: CancellationException) {
                // Not a task failure — the queue's scope was cancelled (e.g. cancelAll()) or the
                // task itself was cancelled. Logging it as an error would misreport a normal
                // pause as a pipeline failure.
                queuedTask.deferred.cancel(e)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("RefreshQueue", "Error processing task ${queuedTask.task.id}", e)
                queuedTask.deferred.completeExceptionally(e)
            } finally {
                // NonCancellable: this task's own coroutine may already be in the process of
                // cancelling here (the catch block above rethrows), and queueMutex.withLock is a
                // suspending call — without this, acquiring a contended lock while cancelling
                // would throw immediately and skip the cleanup below, leaving the ID stranded in
                // activeTaskIds and _isProcessing stuck true, the exact bug this is fixing.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    queueMutex.withLock {
                        activeTasks.remove(queuedTask.task.id)
                        _activeTaskIds.value = _activeTaskIds.value - queuedTask.task.id
                        _isProcessing.value = _activeTaskIds.value.isNotEmpty()
                    }
                }
            }
        }
    }

    /**
     * Clear all pending tasks.
     */
    suspend fun clear() {
        queueMutex.withLock {
            queue.forEach { it.deferred.cancel() }
            queue.clear()
            _queuedTaskIds.value = emptySet()
        }
    }

    /**
     * Cancel all executing and pending tasks.
     */
    suspend fun cancelAll() {
        queueMutex.withLock {
            activeTasks.values.forEach { it.job.cancel() }
            activeTasks.clear()
            // Otherwise the sync spinner stays stuck on and stranded IDs block re-submission
            // until each cancelled task's own coroutine gets scheduled to run its finally block.
            _activeTaskIds.value = emptySet()
            _isProcessing.value = false
        }
        clear()
    }
}
