package org.njarasoa.fijerena.core.network.provider

import androidx.room.Entity
import androidx.room.Index

/**
 * A provider, profile, login, filter set, EPG source or setting that changed locally and hasn't
 * been sent to the sync server yet — only the key, like `xtream_v2.db`'s `sync_outbox`: the sync
 * client reads the current value when it sends. [kind] is a
 * [org.njarasoa.fijerena.core.network.sync.SyncKind]; [profileId] is the profile for per-person
 * kinds and [org.njarasoa.fijerena.core.network.sync.SyncKind.SHARED] otherwise; [itemKey] is the
 * `providerKey`, profile id, `source_key` or setting key.
 *
 * Written by [SettingsSyncTriggers] for rows of this database, and by
 * [org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue] for values kept in SharedPreferences.
 */
@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["kind", "profileId", "itemKey"],
    indices = [Index(value = ["hlc"])],
)
data class SettingsOutboxEntity(
    val kind: String,
    val profileId: String,
    val itemKey: String,
    val hlc: Long,
)
