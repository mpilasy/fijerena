package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Query

/** Reads of [SyncVersionEntity]; local changes are written by [XtreamSyncTriggers]. */
@Dao
interface SyncVersionDao {
    /** Local changes not yet sent, oldest first — the order the sync client sends them in. */
    @Query("SELECT * FROM sync_version WHERE pending = 1 ORDER BY hlc ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<SyncVersionEntity>

    @Query(
        "SELECT * FROM sync_version WHERE providerId = :providerId AND profileId = :profileId " +
            "AND kind = :kind AND itemId = :itemId AND contentType = :contentType",
    )
    suspend fun get(
        providerId: Long,
        profileId: String,
        kind: String,
        itemId: String,
        contentType: String,
    ): SyncVersionEntity?

    @Query("SELECT hlc FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun clock(): Long

    /** Versions of a provider being deleted: its own tombstone covers them. */
    @Query("DELETE FROM sync_version WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: Long)

    /** Versions of a profile being deleted: its own tombstone covers them. */
    @Query("DELETE FROM sync_version WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
