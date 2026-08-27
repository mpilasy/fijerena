package org.njarasoa.fijerena.core.network.provider

import androidx.room.Entity
import androidx.room.PrimaryKey

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
 */
@Entity(tableName = "providers")
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
)
