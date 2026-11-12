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
import kotlinx.coroutines.CancellationException
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.isDiskFull
import org.njarasoa.fijerena.core.network.trySetForeground
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.PlaybackPause
import org.njarasoa.fijerena.core.network.xmltv.epgindex.isMediaPlaying
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * One-shot WorkManager worker that rebuilds the FTS index under a foreground-service wake lock.
 * [EpgFileManager.initialize] schedules it when an earlier session left fts_stale=true in
 * SharedPreferences (the process was killed mid-rebuild, by Doze for example). As a foreground
 * service, Doze does not stop the rebuild again.
 */
class EpgFtsRebuildWorker(
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
                .setContentTitle(applicationContext.getString(R.string.epg_fts_rebuild_notification_title))
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
        Log.i(TAG, "doWork: starting FTS rebuild under foreground service wake lock")
        return try {
            val indexer = EpgIndexer.getInstance(applicationContext)
            // An incremental update a killed run left continues where it stopped, waiting while
            // something plays (the pausable guide indexing plan, IX2); a stale index is rebuilt
            // whole.
            val rebuild = indexer.isFtsStale() || !indexer.resumeIncrementalIndex(PlaybackPause({ isMediaPlaying(applicationContext) }))
            // Not enough space for the rebuild: it does not start, and the worker is not tried again
            // (LS-Q5). The index stays stale, and the next start of the app schedules the worker.
            val shortfall = if (rebuild) EpgIndexer.ftsRebuildShortfall(applicationContext) else null
            if (rebuild && shortfall == null) indexer.rebuildFtsAndUpdateState()
            indexer.incrementalVacuum()
            if (shortfall == null) {
                Log.i(TAG, "doWork: FTS rebuild complete")
                Result.success()
            } else {
                Log.w(TAG, "doWork: FTS rebuild not started: ${shortfall.raw}")
                CrashLog.note("guide search index rebuild (after restart) not started: storage low", shortfall.raw.orEmpty())
                Result.failure()
            }
        } catch (e: CancellationException) {
            // WorkManager stopped this run — not a rebuild failure.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "doWork: FTS rebuild failed — ${e.message}", e)
            CrashLog.record("guide search index rebuild (after restart) failed", e)
            failedRunResult(e.isDiskFull(), runAttemptCount, MAX_RETRIES)
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
