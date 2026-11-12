package org.njarasoa.fijerena.core.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug builds only: sets the free space that the storage checks read
 * ([StorageSpace.debugFreeBytes]), so that the low-storage messages can be seen on an emulator
 * without a full disk (the low storage plan, LS-Q6). The value stays until the process stops.
 *
 *     adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_FREE_SPACE -p <app id> --el mb 100
 *     adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_FREE_SPACE -p <app id>
 *
 * The first sets 100 MB free; the second (no `mb`) goes back to the real value. The result goes to
 * logcat under `FreeSpaceDebug`. The receiver requires `android.permission.DUMP`, which the adb
 * shell holds and other apps do not.
 */
class FreeSpaceDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION) {
            val mb = intent.getLongExtra(EXTRA_MB, -1L)
            StorageSpace.debugFreeBytes = if (mb >= 0) mb * MB else null
            Log.i(TAG, if (mb >= 0) "free space set to $mb MB" else "free space is the real value")
        }
    }

    companion object {
        const val ACTION = "org.njarasoa.fijerena.DEBUG_FREE_SPACE"
        private const val EXTRA_MB = "mb"
        private const val MB = 1024L * 1024
        private const val TAG = "FreeSpaceDebug"
    }
}
