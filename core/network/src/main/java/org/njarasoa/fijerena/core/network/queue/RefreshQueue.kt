package org.njarasoa.fijerena.core.network.queue

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
        while (true) {
            val queuedTask =
                queueMutex.withLock {
                    val task = queue.poll()
                    if (task != null) {
                        _queuedTaskIds.value = queue.map { it.task.id }.toSet()
                        activeTasks[task.task.id] = ActiveTask(Job(), task.deferred)
                    }
                    task
                } ?: break

            // Launch each task in its own coroutine, governed by the semaphore
            val job =
                scope.launch {
                    semaphore.withPermit {
                        _activeTaskIds.value = _activeTaskIds.value + queuedTask.task.id
                        _isProcessing.value = true
                        try {
                            queuedTask.task.execute()
                            queuedTask.deferred.complete(Unit)
                        } catch (e: Exception) {
                            android.util.Log.e("RefreshQueue", "Error processing task ${queuedTask.task.id}", e)
                            queuedTask.deferred.completeExceptionally(e)
                        } finally {
                            queueMutex.withLock { activeTasks.remove(queuedTask.task.id) }
                            _activeTaskIds.value = _activeTaskIds.value - queuedTask.task.id
                            _isProcessing.value = _activeTaskIds.value.isNotEmpty()
                        }
                    }
                }

            // Registered as active immediately, in the same dequeue step — not after the
            // semaphore permit is acquired — so a task waiting on a full semaphore is still
            // covered by submit()'s de-dup check, with no gap where it's in neither place.
            queueMutex.withLock {
                activeTasks[queuedTask.task.id] = ActiveTask(job, queuedTask.deferred)
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
        }
        clear()
    }
}
