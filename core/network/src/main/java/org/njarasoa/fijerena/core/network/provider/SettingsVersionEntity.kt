package org.njarasoa.fijerena.core.network.provider

import androidx.room.Entity
import androidx.room.Index

/**
 * The sync version of a provider, profile, login, filter set, EPG source or setting — the
 * `providers.db` counterpart of `xtream_v2.db`'s `sync_version`: [hlc] of its latest change,
 * local or received, and [pending] for local changes not yet sent. [kind] is a
 * [org.njarasoa.fijerena.core.network.sync.SyncKind]; [profileId] is the profile for per-person
 * kinds and [org.njarasoa.fijerena.core.network.sync.SyncKind.SHARED] otherwise; [itemKey] is the
 * `providerKey`, profile id, `source_key` or setting key.
 *
 * Local changes are written by [SettingsSyncTriggers] for rows of this database, and by
 * [org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue] for values kept in SharedPreferences.
 */
@Entity(
    tableName = "sync_version",
    primaryKeys = ["kind", "profileId", "itemKey"],
    indices = [Index(value = ["pending", "hlc"])],
)
data class SettingsVersionEntity(
    val kind: String,
    val profileId: String,
    val itemKey: String,
    val hlc: Long,
    val pending: Boolean,
)
