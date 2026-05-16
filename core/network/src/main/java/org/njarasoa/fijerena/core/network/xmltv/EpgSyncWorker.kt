package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker for background EPG sync (all device types).
 *
 * WorkManager holds a wake lock for the full duration of doWork(), so DNS and network
 * remain available even on Shield/TV in Doze. processAllSources is called directly
 * (not via RefreshQueue) to keep work inside this wake-lock-backed coroutine.
 */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork: starting (attempt ${runAttemptCount + 1})")
        val fileManager = EpgFileManager.getInstance(applicationContext)

        return try {
            val staleSources = fileManager.getStaleSources()
            if (staleSources.isEmpty()) {
                Log.i(TAG, "doWork: all sources fresh, nothing to do")
                Result.success()
            } else {
                Log.i(TAG, "doWork: refreshing ${staleSources.size} stale source(s)")
                fileManager.processAllSources(staleSources)

                // If every source failed the worker returns retry so WorkManager backs off
                // and tries again in minutes rather than waiting the full 4-hour period.
                val completed = fileManager.state.value as? EpgFileManager.MultiSourceState.Completed
                if (completed != null && completed.errors == staleSources.size) {
                    Log.w(TAG, "doWork: all ${staleSources.size} source(s) failed, will retry")
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                } else {
                    Log.i(TAG, "doWork: refresh complete")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "doWork: failed — ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EpgSyncWorker"
    }
}
