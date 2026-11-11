package org.njarasoa.fijerena.core.network.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import java.util.PriorityQueue

/**
 * Singleton queue that runs tasks by priority, several at once up to a limit.
 */
object RefreshQueue {
    private val scope = AppScopes.create("RefreshQueue", Dispatchers.IO)
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

    // Tasks taken off `queue` and running (or about to). Guarded by queueMutex like `queue`, so a
    // task is never in neither place: that gap would let submit() enqueue a duplicate of a
    // running task.
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
     * Returns a Deferred that completes when the task finishes. If a task with the same
     * ID is already executing, coalesces into that run's Deferred instead of starting a
     * second, concurrent one.
     */
    suspend fun submit(task: RefreshTask): Deferred<Unit> {
        val deferred =
            queueMutex.withLock {
                activeTasks[task.id]?.let { return@withLock it.deferred }

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
            // Dequeue, launch and register the real Job in one lock: with a placeholder Job()
            // registered first, a cancelAll() before the swap would cancel the placeholder and miss
            // the real job.
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
        // The permit wait is inside the try, so a cancellation while it waits still reaches the
        // catch/finally below: the deferred settles and the id goes on every path, and no awaiter
        // of submit() hangs.
        try {
            semaphore.withPermit {
                // All _activeTaskIds/_isProcessing transitions go through queueMutex so concurrent
                // completions can't race a read-modify-write on the StateFlow and strand an ID.
                queueMutex.withLock {
                    // cancelAll() unregisters this task; a cancelled coroutine can still win the
                    // lock on its fast path, so check registration rather than rely on cancellation.
                    if (activeTasks[queuedTask.task.id]?.deferred !== queuedTask.deferred) {
                        throw CancellationException("RefreshQueue task ${queuedTask.task.id} cancelled before start")
                    }
                    _activeTaskIds.value = _activeTaskIds.value + queuedTask.task.id
                    _isProcessing.value = true
                }
                queuedTask.task.execute()
                queuedTask.deferred.complete(Unit)
            }
        } catch (e: CancellationException) {
            // Not a task failure: the queue's scope (cancelAll()) or the task was cancelled, so no
            // error log.
            queuedTask.deferred.cancel(e)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("RefreshQueue", "Error processing task ${queuedTask.task.id}", e)
            queuedTask.deferred.completeExceptionally(e)
        } finally {
            // NonCancellable: this coroutine may already be cancelling (the catch rethrows), and a
            // contended queueMutex.withLock would then throw and skip the cleanup, leaving the id
            // in activeTaskIds and _isProcessing true.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                queueMutex.withLock {
                    // Only clean up our own registration: after cancelAll() the id may already
                    // belong to a newer run of the same task, which must not be touched.
                    if (activeTasks[queuedTask.task.id]?.deferred === queuedTask.deferred) {
                        activeTasks.remove(queuedTask.task.id)
                        _activeTaskIds.value = _activeTaskIds.value - queuedTask.task.id
                        _isProcessing.value = _activeTaskIds.value.isNotEmpty()
                    }
                }
            }
        }
    }

    suspend fun clear() {
        queueMutex.withLock {
            queue.forEach { it.deferred.cancel() }
            queue.clear()
            _queuedTaskIds.value = emptySet()
        }
    }

    /**
     * Cancels every running and pending task in one critical section, so the poller cannot start
     * a queued task between the two steps.
     */
    suspend fun cancelAll() {
        queueMutex.withLock {
            activeTasks.values.forEach {
                it.job.cancel()
                it.deferred.cancel()
            }
            activeTasks.clear()
            queue.forEach { it.deferred.cancel() }
            queue.clear()
            _queuedTaskIds.value = emptySet()
            // Cleared now: else the sync spinner stays on, and stranded ids block a new submit
            // until each cancelled task runs its finally block.
            _activeTaskIds.value = emptySet()
            _isProcessing.value = false
        }
    }
}
