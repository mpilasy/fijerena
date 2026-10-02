package org.njarasoa.fijerena.core.network.xtream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

/**
 * One or more of the catalogue sync tasks failed. What the other tasks fetched is already
 * committed; [ProviderSyncRunner] classifies the run from [failures] (R-08).
 */
class CatalogSyncException(
    val failures: List<Throwable>,
) : Exception("${failures.size} catalog sync task(s) failed: ${failures.first().message}", failures.first())

/**
 * Awaits every task, even after one has failed, and returns the failures. Each task runs on its
 * own in RefreshQueue, so one failing never stops the others; waiting for all of them keeps the
 * delta and the exclusion pass after this covering everything that did arrive.
 * A cancellation is rethrown, never collected: a stopped run isn't a failed one.
 */
internal suspend fun awaitCatalogTasks(tasks: List<Deferred<Unit>>): List<Throwable> {
    val failures = mutableListOf<Throwable>()
    tasks.forEach { task ->
        try {
            task.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += e
        }
    }
    return failures
}
