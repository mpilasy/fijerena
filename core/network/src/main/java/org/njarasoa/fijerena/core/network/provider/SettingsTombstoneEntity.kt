package org.njarasoa.fijerena.core.network.provider

import androidx.room.Entity
import androidx.room.Index

/**
 * A deleted provider or profile, kept so live sync can tell other devices. [kind] is
 * [org.njarasoa.fijerena.core.network.sync.SyncKind.PROVIDER] (with [itemKey] the provider's
 * `providerKey`) or [org.njarasoa.fijerena.core.network.sync.SyncKind.PROFILE] (with the profile
 * id). Written in the same transaction as the delete. The favourite and watch-history deletions
 * live in `xtream_v2.db`'s own `sync_tombstone`, next to their rows. See
 * `docs/plans/archive/20260929_live-sync-plan.md` → Deletions (tombstones).
 */
@Entity(
    tableName = "sync_tombstone",
    primaryKeys = ["kind", "itemKey"],
    indices = [Index(value = ["deletedAt"])],
)
data class SettingsTombstoneEntity(
    val kind: String,
    val itemKey: String,
    val deletedAt: Long,
)
