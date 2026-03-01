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
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue

/**
 * Singleton queue manager that processes tasks sequentially based on priority.
 * Ensures that long-running tasks like EPG refresh don't block high-priority tasks forever,
 * but also ensures they don't interrupt each other.
 */
object RefreshQueue {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = PriorityQueue<QueuedTask>()
    private val queueMutex = Mutex()
    private val processChannel = Channel<Unit>(Channel.CONFLATED)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _queuedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val queuedTaskIds = _queuedTaskIds.asStateFlow()

    private var currentJob: Job? = null

    private class QueuedTask(
        val task: RefreshTask,
        val deferred: CompletableDeferred<Unit>
    ) : Comparable<QueuedTask> {
        override fun compareTo(other: QueuedTask): Int {
            return task.compareTo(other.task)
        }
    }

    init {
        startWorker()
    }

    private fun startWorker() {
        scope.launch {
            for (trigger in processChannel) {
                processNext()
            }
        }
    }

    /**
     * Submit a task to the queue.
     * Returns a Deferred that completes when the task finishes.
     * If a task with the same ID already exists, it is replaced and the new Deferred is returned.
     */
    suspend fun submit(task: RefreshTask): Deferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        val queuedTask = QueuedTask(task, deferred)

        queueMutex.withLock {
            // Remove existing task with same ID to ensure we don't queue multiple same tasks
            val existing = queue.find { it.task.id == task.id }
            if (existing != null) {
                queue.remove(existing)
                // Cancel/complete the old deferred?
                // Better to let it be replaced. The caller waiting on the old one
                // might wait forever if we don't handle it, or we can chain it.
                // For simplicity, we'll cancel the old one with a cancellation exception
                // or just let it hang? No, canceling is safer.
                existing.deferred.cancel()
            }
            queue.add(queuedTask)
            _queuedTaskIds.value = queue.map { it.task.id }.toSet()
        }
        processChannel.trySend(Unit)
        return deferred
    }

    private suspend fun processNext() {
        while (true) {
            val queuedTask = queueMutex.withLock {
                val t = queue.poll()
                if (t != null) {
                    _queuedTaskIds.value = queue.map { it.task.id }.toSet()
                }
                t
            } ?: break

            _isProcessing.value = true
            try {
                val job = scope.launch {
                    queuedTask.task.execute()
                }
                currentJob = job
                job.join()
                if (job.isCancelled) {
                    queuedTask.deferred.cancel()
                } else {
                    queuedTask.deferred.complete(Unit)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                queuedTask.deferred.completeExceptionally(e)
            } finally {
                currentJob = null
                _isProcessing.value = false
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
     * Cancel the currently executing task and clear all pending tasks.
     */
    suspend fun cancelAll() {
        currentJob?.cancel()
        clear()
    }
}
