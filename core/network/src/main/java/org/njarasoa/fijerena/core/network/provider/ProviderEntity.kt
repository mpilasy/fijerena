package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * @property url Server URL or connection string
 * @property type Provider type: XTREAM, JELLYFIN, REMOTE_M3U, SMB or LOCAL
 * @property config JSON blob for type-specific configuration (SMB host/share, Local paths)
 * @property providerSettings JSON blob for per-provider settings ([ProviderSettings])
 * @property providerKey Random UUID naming this provider in live sync. Unlike [id] (a local
 *   autoincrement) it's the same on every device, and unlike URL + username it survives edits.
 *   See the live sync plan → Record model.
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
    /**
     * Unused since multi-source Phase 6 (docs/plans/20261012_multi-source-search-history-plan.md):
     * there is no device or current source any more. Kept, never written, so `providers.db` needs
     * no migration; only the one-time upgrade to per-profile sources reads what an older build left
     * here ([ProviderDao.legacyActiveProviderKey]).
     */
    val isActive: Boolean = false,
    /** When the last successful sync ended; 0 = never (a failed sync leaves it, since v18). */
    val lastSyncedAtMs: Long = 0,
    /** How long the last successful sync took (a failed sync leaves it, since v18). */
    val lastSyncDurationMs: Long = 0,
    /** The error of the last sync, null when it succeeded. */
    val lastSyncError: String? = null,
    /** Rows inserted/updated/deleted by the last sync. All zero means the catalog didn't change. */
    val lastSyncInserted: Int = 0,
    val lastSyncUpdated: Int = 0,
    val lastSyncDeleted: Int = 0,
    val providerKey: String = UUID.randomUUID().toString(),
    /** When the last failed sync ended; null after a success (added v18). Local, never synced. */
    val lastFailedAtMs: Long? = null,
    /** Failed syncs since the last success; 0 after a success (added v18). Local, never synced. */
    @ColumnInfo(defaultValue = "0")
    val failuresSinceSuccess: Int = 0,
)
