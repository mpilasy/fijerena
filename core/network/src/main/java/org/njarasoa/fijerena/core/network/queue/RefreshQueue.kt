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
import java.util.concurrent.ConcurrentHashMap

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

    private val activeJobs = ConcurrentHashMap<String, Job>()

    private class QueuedTask(
        val task: RefreshTask,
        val deferred: CompletableDeferred<Unit>,
    ) : Comparable<QueuedTask> {
        override fun compareTo(other: QueuedTask): Int = task.compareTo(other.task)
    }

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
     * Returns a Deferred that completes when the task finishes.
     */
    suspend fun submit(task: RefreshTask): Deferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        val queuedTask = QueuedTask(task, deferred)

        queueMutex.withLock {
            // Remove existing task with same ID to ensure we don't queue multiple same tasks
            val existing = queue.find { it.task.id == task.id }
            if (existing != null) {
                queue.remove(existing)
                existing.deferred.cancel()
            }
            queue.add(queuedTask)
            _queuedTaskIds.value = queue.map { it.task.id }.toSet()
        }
        processChannel.trySend(Unit)
        return deferred
    }

    private suspend fun processAvailable() {
        while (true) {
            val queuedTask =
                queueMutex.withLock {
                    if (queue.isEmpty()) return@withLock null
                    queue.poll()?.also {
                        _queuedTaskIds.value = queue.map { it.task.id }.toSet()
                    }
                } ?: break

            // Launch each task in its own coroutine, governed by the semaphore
            scope.launch {
                semaphore.withPermit {
                    _activeTaskIds.value = _activeTaskIds.value + queuedTask.task.id
                    _isProcessing.value = true

                    val job =
                        launch {
                            try {
                                queuedTask.task.execute()
                                queuedTask.deferred.complete(Unit)
                            } catch (e: Exception) {
                                android.util.Log.e("RefreshQueue", "Error processing task ${queuedTask.task.id}", e)
                                queuedTask.deferred.completeExceptionally(e)
                            }
                        }

                    activeJobs[queuedTask.task.id] = job
                    try {
                        job.join()
                    } finally {
                        activeJobs.remove(queuedTask.task.id)
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
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        clear()
    }
}
