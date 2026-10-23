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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.trySetForeground
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * WorkManager worker for background EPG sync (all device types).
 *
 * Refreshes the guide sources of every source of the profile in use that Search the guide reads
 * ([GuideScope]), each source's in a run of its own: those due by their own interval
 * ([EpgRefreshSchedule.isDue]). It runs at the shortest interval among every provider's guide
 * sources (`EpgFileManager.scheduleAutoRefresh`).
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
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.epg_sync_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                },
            )
        }
        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
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
        trySetForeground(TAG)

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
            CrashLog.note("guide refresh (background) failed", "No network after 15 s (attempt ${runAttemptCount + 1})")
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }

        Log.i(TAG, "doWork: starting (attempt ${runAttemptCount + 1})")
        val fileManager = EpgFileManager.getInstance(applicationContext)
        val force = inputData.getBoolean("force", false)

        val providerRepo = ProviderRepository(applicationContext)
        val providerIds = GuideScope.searchedProviders(providerRepo.profileSourcesList()).map { it.id }
        if (providerIds.isEmpty()) {
            Log.i(TAG, "doWork: the profile has no source with a guide, nothing to sync")
            return Result.success()
        }

        return try {
            // Routine maintenance: sweep orphaned catalog data left by deleted providers. Never
            // throws, so a full disk can't fail the guide refresh along with it.
            providerRepo.sweepOrphanedCatalogData(onlyIfPending = false)
            if (force) Log.i(TAG, "doWork: force=true, refreshing all sources of providers $providerIds")
            val runs = sourcesToRefresh(providerIds) { if (force) fileManager.getAllSources(it) else fileManager.getStaleSources(it) }
            if (runs.isEmpty()) {
                Log.i(TAG, "doWork: all sources fresh, nothing to do")
                Result.success()
            } else {
                // Inline, one provider after another: a run reports on one provider, and the work
                // must stay inside doWork() to keep the wake lock (Doze kills local work too).
                var sourceCount = 0
                var errorCount = 0
                for (staleSources in runs) {
                    Log.i(TAG, "doWork: refreshing ${staleSources.size} stale source(s) of provider ${staleSources.first().providerId}")
                    fileManager.processAllSources(staleSources)
                    sourceCount += staleSources.size
                    val completed = fileManager.state.value as? EpgFileManager.MultiSourceState.Completed
                    if (completed != null) errorCount += completed.errors
                }

                if (errorCount == sourceCount) {
                    Log.w(TAG, "doWork: all $sourceCount source(s) failed, will retry")
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                } else {
                    Log.i(TAG, "doWork: refresh complete")
                    Result.success()
                }
            }
        } catch (e: CancellationException) {
            // WorkManager stopped this run (constraints lost, timeout, replaced) — not a sync
            // failure. Let it propagate so WorkManager sees a genuine cancellation rather than a
            // logged failure headed for retry/failure classification.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "doWork: failed — ${e.message}", e)
            CrashLog.record("guide refresh (background) failed", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EpgSyncWorker"
        private const val CHANNEL_ID = "epg_sync"
        private const val NOTIFICATION_ID = 0x4570_0001
        const val MAX_RETRIES = 5

        /** The periodic guide refresh's unique work name (`EpgFileManager` schedules it). */
        const val WORK_NAME = "epg_sync"

        /** The guide sources to refresh, one list (run) per provider of [providerIds] that has any. */
        internal suspend fun sourcesToRefresh(
            providerIds: List<Long>,
            sourcesOf: suspend (providerId: Long) -> List<EpgSourceEntity>,
        ): List<List<EpgSourceEntity>> = providerIds.map { sourcesOf(it) }.filter { it.isNotEmpty() }
    }
}
