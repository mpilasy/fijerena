package org.njarasoa.fijerena.core.network.xmltv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

/**
 * WorkManager worker for background EPG sync (all device types).
 *
 * Refreshes the active provider from its own EPG sources only.
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
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.epg_sync_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.epg_sync_notification_title))
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

        // ConnectivityManager.activeNetwork can return null briefly on cold start while
        // the network stack initialises for the new process. Wait up to 15s before proceeding.
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkDeadline = System.currentTimeMillis() + 15_000L
        while (cm?.activeNetwork == null && System.currentTimeMillis() < networkDeadline) {
            Log.d(TAG, "doWork: waiting for active network...")
            delay(1_000L)
        }
        if (cm?.activeNetwork == null) {
            Log.w(TAG, "doWork: no active network after 15s — returning retry")
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

        Log.i(TAG, "doWork: starting (attempt ${runAttemptCount + 1})")
        val fileManager = EpgFileManager.getInstance(applicationContext)
        val force = inputData.getBoolean("force", false)

        // A sync belongs to the active provider and refreshes its own sources only.
        val providerId = ProviderRepository(applicationContext).getActiveProvider()?.id
        if (providerId == null) {
            Log.i(TAG, "doWork: no active provider, nothing to sync")
            return Result.success()
        }

        return try {
            val staleSources = if (force) {
                Log.i(TAG, "doWork: force=true, refreshing all sources of provider $providerId")
                fileManager.getAllSources(providerId)
            } else {
                fileManager.getStaleSources(providerId)
            }
            if (staleSources.isEmpty()) {
                Log.i(TAG, "doWork: all sources fresh, nothing to do")
                Result.success()
            } else {
                Log.i(TAG, "doWork: refreshing ${staleSources.size} stale source(s)")
                fileManager.processAllSources(staleSources)

                val completed = fileManager.state.value as? EpgFileManager.MultiSourceState.Completed
                if (completed != null && completed.errors == staleSources.size) {
                    Log.w(TAG, "doWork: all ${staleSources.size} source(s) failed, will retry")
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                } else {
                    Log.i(TAG, "doWork: refresh complete")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "doWork: failed — ${e.message}", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EpgSyncWorker"
        private const val CHANNEL_ID = "epg_sync"
        private const val NOTIFICATION_ID = 0x4570_0001
        const val MAX_RETRIES = 5
    }
}
