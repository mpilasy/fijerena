package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * The Wanted list ([WantedTitleEntity]). Writes go through the methods that keep live sync's
 * tombstones right: adding a title drops an earlier removal's tombstone, and removing one records
 * a tombstone. [XtreamSyncTriggers] queue every insert and update.
 */
@Dao
interface WantedTitleDao {
    /** One profile's list, newest first. */
    @Query("SELECT * FROM wanted_title WHERE profileId = :profileId ORDER BY addedAt DESC")
    suspend fun getAll(profileId: String): List<WantedTitleEntity>

    @Query("SELECT * FROM wanted_title WHERE profileId = :profileId AND contentType = :contentType AND tmdbId = :tmdbId")
    suspend fun get(
        profileId: String,
        contentType: String,
        tmdbId: String,
    ): WantedTitleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WantedTitleEntity)

    @Query("DELETE FROM wanted_title WHERE profileId = :profileId AND contentType = :contentType AND tmdbId = :tmdbId")
    suspend fun delete(
        profileId: String,
        contentType: String,
        tmdbId: String,
    )

    /** Sets [WantedTitleEntity.notifiedAt] where it is still null. */
    @Query(
        "UPDATE wanted_title SET notifiedAt = :at WHERE profileId = :profileId AND contentType = :contentType " +
            "AND tmdbId = :tmdbId AND notifiedAt IS NULL",
    )
    suspend fun markNotified(
        profileId: String,
        contentType: String,
        tmdbId: String,
        at: Long,
    )

    /** Sets [WantedTitleEntity.seenAt] where it is still null. */
    @Query(
        "UPDATE wanted_title SET seenAt = :at WHERE profileId = :profileId AND contentType = :contentType " +
            "AND tmdbId = :tmdbId AND seenAt IS NULL",
    )
    suspend fun markSeen(
        profileId: String,
        contentType: String,
        tmdbId: String,
        at: Long,
    )

    /** The title's release as TMDB gives it now; this device's own, never synced (see [XtreamSyncTriggers]). */
    @Query(
        "UPDATE wanted_title SET releaseDate = :releaseDate, digitalDate = :digitalDate, inCinemas = :inCinemas, " +
            "showStatus = :showStatus, releaseCheckedAt = :checkedAt " +
            "WHERE profileId = :profileId AND contentType = :contentType AND tmdbId = :tmdbId",
    )
    suspend fun setRelease(
        profileId: String,
        contentType: String,
        tmdbId: String,
        releaseDate: String?,
        digitalDate: String?,
        inCinemas: Boolean,
        showStatus: String?,
        checkedAt: Long,
    )

    /** Sets [WantedTitleEntity.askedAt] where it is still null. */
    @Query(
        "UPDATE wanted_title SET askedAt = :at WHERE profileId = :profileId AND contentType = :contentType " +
            "AND tmdbId = :tmdbId AND askedAt IS NULL",
    )
    suspend fun markAsked(
        profileId: String,
        contentType: String,
        tmdbId: String,
        at: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    @Query(
        "DELETE FROM sync_tombstone WHERE providerId = ${SyncKind.WANTED_PROVIDER_ID} AND profileId = :profileId " +
            "AND kind = '${SyncKind.WANTED}' AND itemId = :tmdbId AND contentType = :contentType",
    )
    suspend fun deleteTombstone(
        profileId: String,
        contentType: String,
        tmdbId: String,
    )

    /** [upsert], dropping any tombstone left by an earlier removal of the same title. */
    @Transaction
    suspend fun upsertClearingTombstone(entity: WantedTitleEntity) {
        upsert(entity)
        deleteTombstone(entity.profileId, entity.contentType, entity.tmdbId)
    }

    /**
     * [delete], recording the removal for live sync. [deletedAt] 0 (the default) means "now on the
     * sync clock", stamped by [XtreamSyncTriggers]; tests pass explicit values.
     */
    @Transaction
    suspend fun deleteRecordingTombstone(
        profileId: String,
        contentType: String,
        tmdbId: String,
        deletedAt: Long = 0,
    ) {
        delete(profileId, contentType, tmdbId)
        insertTombstone(SyncTombstoneEntity(SyncKind.WANTED_PROVIDER_ID, profileId, SyncKind.WANTED, tmdbId, contentType, deletedAt))
    }

    /** Profile deletion: that profile's whole list. */
    @Query("DELETE FROM wanted_title WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)
}
