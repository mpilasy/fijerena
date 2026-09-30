package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/**
 * A deleted favourite, or a "clear watch history", kept so live sync can tell other devices —
 * without it the next device to sync would bring the item back. Written in the same transaction
 * as the delete it records; removed again if the same favourite is re-added. See
 * `docs/plans/20260929_live-sync-plan.md` → Deletions (tombstones).
 *
 * Keyed locally on [providerId] like the rows it stands for; the sync client translates it to the
 * provider's `providerKey` when sending. [kind] is a [org.njarasoa.fijerena.core.network.sync.SyncKind];
 * [itemId] and [contentType] are empty for [org.njarasoa.fijerena.core.network.sync.SyncKind.WATCH_CLEAR].
 * The provider- and profile-level deletions live in `providers.db`'s own `sync_tombstone`.
 */
@Entity(
    tableName = "sync_tombstone",
    primaryKeys = ["providerId", "profileId", "kind", "itemId", "contentType"],
    indices = [Index(value = ["deletedAt"])],
)
data class SyncTombstoneEntity(
    val providerId: Long,
    val profileId: String,
    val kind: String,
    val itemId: String,
    val contentType: String,
    val deletedAt: Long,
)
