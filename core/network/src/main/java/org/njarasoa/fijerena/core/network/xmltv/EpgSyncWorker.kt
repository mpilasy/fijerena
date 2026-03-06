package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase

/**
 * WorkManager worker for mobile background EPG sync.
 *
 * Scheduled as a periodic 24-hour task on mobile devices only.
 * TV/fixed devices use coroutine-based auto-refresh instead.
 */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fileManager = EpgFileManager.getInstance(applicationContext)

        return try {
            fileManager.refreshOutdatedSources()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
