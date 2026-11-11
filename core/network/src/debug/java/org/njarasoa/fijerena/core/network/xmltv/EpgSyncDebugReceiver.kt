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
 * Debug only: enqueues an EpgSyncWorker run now, to test the foreground-service Doze bypass without
 * waiting for the periodic schedule.
 *
 * Trigger: adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_EPG_SYNC -p org.njarasoa.fijerena
 *
 * Lives in the `debug` source set, so release builds don't contain it. Its manifest entry requires
 * `android.permission.DUMP`: the adb shell holds it, other apps on the device don't.
 */
class EpgSyncDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        Log.i(TAG, "Debug trigger received — enqueuing force OneTimeWorkRequest for EpgSyncWorker")
        // Explicit backoff: WorkManager's default (~30 s, doubling) suits a blip, but a retry every
        // 30 s keeps a source that rate-limits blocking us.
        val request =
            OneTimeWorkRequestBuilder<EpgSyncWorker>()
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
