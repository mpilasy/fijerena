package org.njarasoa.fijerena.core.ui.components

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.SyncManager

/** Where live sync stands: syncing, failed, never synced, or when it last did; Off while not linked. */
@Composable
fun liveSyncStatusLine(status: SyncManager.Status): String =
    when {
        !status.linked -> stringResource(R.string.live_sync_off)
        status.syncing -> stringResource(R.string.live_sync_status_syncing)
        status.lastError != null -> stringResource(R.string.live_sync_status_failed)
        status.lastSyncAt == 0L -> stringResource(R.string.live_sync_status_never)
        else -> stringResource(R.string.live_sync_status_last, liveSyncRelative(status.lastSyncAt))
    }

/** "just now" under a minute, else "5 minutes ago". */
@Composable
fun liveSyncRelative(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.live_sync_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
