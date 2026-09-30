package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // --- Applying records received from another device (live sync phase 5) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(version: SyncVersionEntity)

    /** A received clock value: this device's next change must come after it. */
    @Query("UPDATE sync_clock SET hlc = MAX(hlc, :remote) WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun receive(remote: Long)

    /** While set, [XtreamSyncTriggers] queue nothing — see [SyncClockEntity.applying]. */
    @Query("UPDATE sync_clock SET applying = :applying WHERE id = ${SyncClockEntity.SINGLE_ROW}")
    suspend fun setApplying(applying: Boolean)

    /** A received "clear watch history": drops the watch rows whose own version is older than [before]. */
    @Query(
        "DELETE FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND NOT EXISTS (" +
            "SELECT 1 FROM sync_version v WHERE v.providerId = watch_state.providerId AND v.profileId = watch_state.profileId " +
            "AND v.kind = 'watch' AND v.itemId = watch_state.itemId AND v.contentType = watch_state.contentType " +
            "AND v.hlc >= :before)",
    )
    suspend fun deleteWatchOlderThan(
        providerId: Long,
        profileId: String,
        before: Long,
    )

    @Query("DELETE FROM sync_version WHERE providerId = :providerId AND profileId = :profileId AND kind = 'watch' AND hlc < :before")
    suspend fun deleteWatchVersionsOlderThan(
        providerId: Long,
        profileId: String,
        before: Long,
    )

    /** Versions of a provider being deleted: its own tombstone covers them. */
    @Query("DELETE FROM sync_version WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: Long)

    /** Versions of a profile being deleted: its own tombstone covers them. */
    @Query("DELETE FROM sync_version WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
