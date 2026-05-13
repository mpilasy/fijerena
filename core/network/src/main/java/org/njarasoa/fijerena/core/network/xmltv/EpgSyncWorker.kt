package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker for mobile background EPG sync.
 *
 * Scheduled as a periodic task on mobile devices based on AppSettings.
 * TV/fixed devices use coroutine-based auto-refresh instead.
 */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val fileManager = EpgFileManager.getInstance(applicationContext)

        return try {
            // Call processAllSources directly in this coroutine so WorkManager's wake lock
            // is held for the full download + ingestion cycle. Routing through RefreshQueue
            // would transfer work into a separate scope (Dispatchers.IO + SupervisorJob) that
            // is NOT backed by this wake lock — on Shield in Doze mode that causes DNS failures.
            val staleSources = fileManager.getStaleSources()
            if (staleSources.isNotEmpty()) {
                fileManager.processAllSources(staleSources)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
