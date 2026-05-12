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
            // awaitRefreshOutdatedSources() suspends until the full download + ingestion
            // cycle completes. This keeps WorkManager's wake lock alive for the entire
            // operation so the OS cannot suspend the process mid-download and silently
            // drop the work without updating the DB.
            fileManager.awaitRefreshOutdatedSources()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
