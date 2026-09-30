package org.njarasoa.fijerena.core.network.provider

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a media provider configuration.
 *
 * @property id Auto-generated primary key
 * @property name User-friendly display name
 * @property url Server URL or connection string
 * @property username Username for authentication
 * @property type Provider type: XTREAM, JELLYFIN, SMB, or LOCAL
 * @property config JSON blob for type-specific configuration (SMB host/share, Local paths)
 * @property providerSettings JSON blob for per-provider settings (cache, history, filters)
 * @property createdAt Timestamp when provider was created
 * @property lastUsedAt Timestamp when provider was last used
 * @property isActive Whether this is the currently active provider
 * @property providerKey Random UUID naming this provider in live sync. Unlike [id] (a local
 *   autoincrement) it's the same on every device, and unlike URL + username it survives edits.
 *   See `docs/plans/20260929_live-sync-plan.md` → Record model.
 */
@Entity(tableName = "providers", indices = [Index(value = ["providerKey"], unique = true)])
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val username: String,
    val type: String = "XTREAM",
    val config: String = "",
    val providerSettings: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    val lastSyncedAtMs: Long = 0,
    val lastSyncDurationMs: Long = 0,
    val lastSyncError: String? = null,
    /** Rows inserted/updated/deleted by the last sync. All zero means the catalog didn't change. */
    val lastSyncInserted: Int = 0,
    val lastSyncUpdated: Int = 0,
    val lastSyncDeleted: Int = 0,
    val providerKey: String = UUID.randomUUID().toString(),
)
