package org.njarasoa.fijerena.core.network.queue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val queue = PriorityQueue<RefreshTask>()
    private val queueMutex = Mutex()
    private val processChannel = Channel<Unit>(Channel.CONFLATED)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _queuedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val queuedTaskIds = _queuedTaskIds.asStateFlow()

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
     * If a task with the same ID already exists, it is replaced.
     */
    suspend fun submit(task: RefreshTask) {
        queueMutex.withLock {
            // Remove existing task with same ID to ensure we don't queue multiple same tasks
            // and to update priority if it changed (although replace is usually better)
            val existing = queue.find { it.id == task.id }
            if (existing != null) {
                queue.remove(existing)
            }
            queue.add(task)
            _queuedTaskIds.value = queue.map { it.id }.toSet()
        }
        processChannel.trySend(Unit)
    }

    private suspend fun processNext() {
        while (true) {
            val task = queueMutex.withLock {
                val t = queue.poll()
                if (t != null) {
                    _queuedTaskIds.value = queue.map { it.id }.toSet()
                }
                t
            } ?: break

            _isProcessing.value = true
            try {
                // Execute the task
                task.execute()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Clear all pending tasks.
     */
    suspend fun clear() {
        queueMutex.withLock {
            queue.clear()
            _queuedTaskIds.value = emptySet()
        }
    }
}
