package org.njarasoa.fijerena.core.network.xtream

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

/**
 * WorkManager worker for periodic Xtream catalog sync (all device types).
 *
 * Runs as a foreground service via setForeground() to bypass Android Doze mode, which blocks
 * DNS on Ethernet-connected Shield TVs during overnight maintenance windows — same failure
 * class fixed for EpgSyncWorker. WorkManager holds a wake lock for the full duration of
 * doWork(), so DNS and network remain available even on Shield/TV in Doze.
 */
class XtreamSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.xtream_sync_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.xtream_sync_notification_title))
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

        val providerRepo = ProviderRepository(applicationContext)
        val providers = providerRepo.getAllProvidersList()

        var anyTransientFailure = false
        providers.filter { it.type == "XTREAM" }.forEach { provider ->
            val password = providerRepo.getPassword(provider.id)
            if (password != null) {
                val startTime = System.currentTimeMillis()
                val outcome = ProviderSyncRunner.syncProvider(applicationContext, provider, password)
                if (outcome is ProviderSyncRunner.Outcome.Transient) {
                    anyTransientFailure = true
                }
                val endTime = System.currentTimeMillis()
                val delta = (outcome as? ProviderSyncRunner.Outcome.Success)?.delta
                providerRepo.updateSyncStats(
                    provider.id, endTime, endTime - startTime, outcome.errorOrNull(),
                    inserted = delta?.inserted, updated = delta?.updated, deleted = delta?.deleted,
                )
            }
        }

        // Only a transient failure is worth a WorkManager-backed retry; permanent failures
        // (bad credentials) won't fix themselves, and their error is already recorded.
        return if (anyTransientFailure && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val CHANNEL_ID = "xtream_sync"
        private const val NOTIFICATION_ID = 0x5854_0001
        const val MAX_RETRIES = 5
    }
}
