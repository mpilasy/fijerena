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
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer

/**
 * One-shot WorkManager worker that rebuilds the FTS index under a foreground-service
 * wake lock. Scheduled by [EpgFileManager.initialize] when a previous session left
 * fts_stale=true in SharedPreferences (e.g. the process was killed by Doze mid-rebuild).
 * Running as a foreground service prevents Doze from interrupting the rebuild again.
 */
class EpgFtsRebuildWorker(
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
            .setContentTitle("Rebuilding TV guide index")
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
        Log.i(TAG, "doWork: starting FTS rebuild under foreground service wake lock")
        return try {
            val indexer = EpgIndexer.getInstance(applicationContext)
            indexer.rebuildFtsAndUpdateState()
            indexer.incrementalVacuum()
            Log.i(TAG, "doWork: FTS rebuild complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: FTS rebuild failed — ${e.message}", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EpgFtsRebuildWorker"
        private const val CHANNEL_ID = "epg_sync"
        private const val NOTIFICATION_ID = 0x4570_0002
        const val WORK_NAME = "epg_fts_rebuild"
        private const val MAX_RETRIES = 3
    }
}
