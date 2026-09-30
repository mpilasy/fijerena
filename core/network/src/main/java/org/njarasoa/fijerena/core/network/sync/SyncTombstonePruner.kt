package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** Drops deletion records older than [SyncKind.TOMBSTONE_RETENTION_MS] from both databases. Run at startup. */
suspend fun pruneSyncTombstones(
    context: Context,
    now: Long = System.currentTimeMillis(),
) {
    val cutoff = now - SyncKind.TOMBSTONE_RETENTION_MS
    SettingsDatabase.getInstance(context).providerDao().pruneTombstones(cutoff)
    XtreamDatabase.getInstance(context).syncTombstoneDao().prune(cutoff)
}
