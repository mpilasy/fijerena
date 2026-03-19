package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker for mobile background EPG sync.
 *
 * Scheduled as a periodic 24-hour task on mobile devices only.
 * TV/fixed devices use coroutine-based auto-refresh instead.
 */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val fileManager = EpgFileManager.getInstance(applicationContext)

        return try {
            val started = fileManager.refreshOutdatedSources()
            if (started) Result.success() else Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
