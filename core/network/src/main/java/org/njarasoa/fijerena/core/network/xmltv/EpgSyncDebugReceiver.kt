package org.njarasoa.fijerena.core.network.xmltv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Debug-only receiver that enqueues an immediate EpgSyncWorker OneTimeWorkRequest.
 * Used to validate foreground-service Doze bypass without waiting for the periodic schedule.
 *
 * Trigger: adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_EPG_SYNC -p org.njarasoa.fijerena
 *
 * This class is excluded from release builds via BuildConfig.DEBUG guard in the manifest.
 */
class EpgSyncDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Log.i(TAG, "Debug trigger received — enqueuing force OneTimeWorkRequest for EpgSyncWorker")
        // No backoff criteria here previously meant WorkManager's aggressive default (~30s
        // initial, doubling) between retries — fine for a transient blip, hostile to a source
        // that's actively rate-limiting/blocking (retrying every 30s only keeps it blocked).
        val request = OneTimeWorkRequestBuilder<EpgSyncWorker>()
            .setInputData(androidx.work.workDataOf("force" to true))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "epg_sync_debug",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val ACTION = "org.njarasoa.fijerena.DEBUG_EPG_SYNC"
        private const val TAG = "EpgSyncDebugReceiver"
    }
}
