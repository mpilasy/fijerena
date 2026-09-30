package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/**
 * A favourite, watch row or deletion that changed locally and hasn't been sent to the sync server
 * yet. Only the key is kept: the sync client reads the current row (or its [SyncTombstoneEntity])
 * when it sends, so any number of changes to one item collapse into one entry.
 *
 * Written by SQLite triggers ([XtreamSyncTriggers]) in the same transaction as the change, never
 * by app code. [hlc] is the sync clock at the latest change. See
 * `docs/plans/20260929_live-sync-plan.md` → Flow, Conflicts.
 */
@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["providerId", "profileId", "kind", "itemId", "contentType"],
    indices = [Index(value = ["hlc"])],
)
data class SyncOutboxEntity(
    val providerId: Long,
    val profileId: String,
    val kind: String,
    val itemId: String,
    val contentType: String,
    val hlc: Long,
)
