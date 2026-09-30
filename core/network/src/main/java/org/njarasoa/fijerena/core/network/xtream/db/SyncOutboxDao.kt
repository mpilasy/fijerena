package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Query

/** Reads of the change queue [SyncOutboxEntity]; the entries themselves are written by [XtreamSyncTriggers]. */
@Dao
interface SyncOutboxDao {
    /** Oldest change first — the order the sync client sends them in. */
    @Query("SELECT * FROM sync_outbox ORDER BY hlc ASC LIMIT :limit")
    suspend fun getBatch(limit: Int): List<SyncOutboxEntity>

    @Query("SELECT hlc FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun clock(): Long

    /** Queue entries of a provider being deleted: its own tombstone covers them. */
    @Query("DELETE FROM sync_outbox WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: Long)

    /** Queue entries of a profile being deleted: its own tombstone covers them. */
    @Query("DELETE FROM sync_outbox WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
