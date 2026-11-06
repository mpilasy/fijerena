package org.njarasoa.fijerena.core.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.sync.SyncProgress
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.utils.timeAgo
import java.text.NumberFormat

/**
 * Where live sync stands: syncing (with its progress once a pass runs long, the sync progress
 * plan), failed, never synced, or when it last did; Off while not linked.
 */
@Composable
fun liveSyncStatusLine(status: SyncManager.Status): String =
    when {
        !status.linked -> stringResource(R.string.live_sync_off)
        status.syncing -> status.progress?.let { liveSyncProgressText(it) } ?: stringResource(R.string.live_sync_status_syncing)
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
        timeAgo(time, now)
    }
}

/**
 * "Receiving changes…" (nothing yet), "Receiving 1,500 of 4,200", "Receiving… 1,500 so far" (no total
 * from the server) or "Sending 200 of 640".
 */
@Composable
fun liveSyncProgressText(progress: SyncProgress): String {
    val number = NumberFormat.getIntegerInstance()
    val done = number.format(progress.done)
    val total = progress.total?.let { number.format(it) }
    return when {
        progress.stage == SyncProgress.Stage.SENDING -> stringResource(R.string.live_sync_sending, done, total ?: done)

        // The first page is often the longest wait: no "0 so far" while nothing has come yet.
        progress.done == 0 && total == null -> stringResource(R.string.live_sync_receiving_start)

        total != null -> stringResource(R.string.live_sync_receiving, done, total)

        else -> stringResource(R.string.live_sync_receiving_count, done)
    }
}

/** The running pass's bar on the Live Sync page: filling while the total is known, moving otherwise; nothing between passes. */
@Composable
fun LiveSyncProgressBar(
    status: SyncManager.Status,
    modifier: Modifier = Modifier,
) {
    val progress = status.progress?.takeIf { status.syncing } ?: return
    val fraction = progress.total?.takeIf { it > 0 }?.let { total -> (progress.done.toFloat() / total).coerceIn(0f, 1f) }
    CinemaProgressBar(progress = fraction?.let { f -> { f } }, modifier = modifier.fillMaxWidth())
}

/**
 * The socket's state under the last sync (the sync progress plan, SP-D5): connected, connecting,
 * or reconnecting in m:ss (counting down); null while not linked.
 */
@Composable
fun liveSyncConnectionLine(status: SyncManager.Status): String? {
    if (!status.linked) return null
    if (status.connected) return stringResource(R.string.live_sync_connected)
    val at = status.reconnectAt ?: return stringResource(R.string.live_sync_connecting)
    val now by produceState(System.currentTimeMillis(), at) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return stringResource(R.string.live_sync_reconnecting, DateUtils.formatElapsedTime(((at - now) / 1_000).coerceAtLeast(0)))
}
