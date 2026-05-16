package org.njarasoa.fijerena.core.network.xmltv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/**
 * WorkManager worker for background EPG sync (all device types).
 *
 * Runs as a foreground service via setForeground() to bypass Android Doze mode,
 * which blocks DNS on Ethernet-connected Shield TVs during overnight maintenance windows.
 * WorkManager holds a wake lock for the full duration of doWork(), so DNS and network
 * remain available even on Shield/TV in Doze. processAllSources is called directly
 * (not via RefreshQueue) to keep work inside this wake-lock-backed coroutine.
 */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EPG Guide Sync", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Refreshing TV guide")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
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
        private const val CHANNEL_ID = "epg_sync"
        private const val NOTIFICATION_ID = 0x4570_0001
    }
}
