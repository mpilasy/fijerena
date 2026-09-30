package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Query

/**
 * Housekeeping for [SyncTombstoneEntity]. The tombstones themselves are written by
 * [FavoriteStateDao] and [WatchStateDao], in the same transaction as the delete they record.
 */
@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstone WHERE providerId = :providerId")
    suspend fun getAll(providerId: Long): List<SyncTombstoneEntity>

    @Query(
        "SELECT * FROM sync_tombstone WHERE providerId = :providerId AND profileId = :profileId " +
            "AND kind = :kind AND itemId = :itemId AND contentType = :contentType",
    )
    suspend fun get(
        providerId: Long,
        profileId: String,
        kind: String,
        itemId: String,
        contentType: String,
    ): SyncTombstoneEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstone: SyncTombstoneEntity)

    /** Provider deletion: its own `provider` tombstone in `providers.db` covers everything. */
    @Query("DELETE FROM sync_tombstone WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: Long)

    /** Profile deletion: its own `profile` tombstone in `providers.db` covers everything. */
    @Query("DELETE FROM sync_tombstone WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)

    /** Drops deletions older than [cutoff] — see [org.njarasoa.fijerena.core.network.sync.SyncKind.TOMBSTONE_RETENTION_MS]. */
    @Query("DELETE FROM sync_tombstone WHERE deletedAt < :cutoff")
    suspend fun prune(cutoff: Long)
}
