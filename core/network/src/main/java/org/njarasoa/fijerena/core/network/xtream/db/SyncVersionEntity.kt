package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/**
 * The sync version of a favourite, watch row or deletion: the sync clock ([hlc]) of its latest
 * change, local or received, which is what the merge compares an incoming record against
 * (last writer wins). [pending] marks a local change not yet sent to the server — the outbox. Only
 * the key is kept: the sync client reads the current row (or its [SyncTombstoneEntity]) when it
 * sends, so any number of changes to one item collapse into one entry.
 *
 * Local changes are written by SQLite triggers ([XtreamSyncTriggers]) in the same transaction as
 * the change; received ones by the apply path. See `docs/plans/20260929_live-sync-plan.md` → Flow,
 * Conflicts.
 */
@Entity(
    tableName = "sync_version",
    primaryKeys = ["providerId", "profileId", "kind", "itemId", "contentType"],
    indices = [Index(value = ["pending", "hlc"])],
)
data class SyncVersionEntity(
    val providerId: Long,
    val profileId: String,
    val kind: String,
    val itemId: String,
    val contentType: String,
    val hlc: Long,
    val pending: Boolean,
)
