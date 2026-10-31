package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * The teams and leagues each profile follows ([FollowedSportEntity]). Writes go through the
 * methods that keep live sync's tombstones right: following drops an earlier unfollow's
 * tombstone, and unfollowing records a tombstone. [XtreamSyncTriggers] queue every insert and
 * update.
 */
@Dao
interface FollowedSportDao {
    /** One profile's followed items, in the order they were followed. */
    @Query("SELECT * FROM followed_sport WHERE profileId = :profileId ORDER BY createdAt, name")
    fun observe(profileId: String): Flow<List<FollowedSportEntity>>

    /** One profile's followed items, in the order they were followed. */
    @Query("SELECT * FROM followed_sport WHERE profileId = :profileId ORDER BY createdAt, name")
    suspend fun getAll(profileId: String): List<FollowedSportEntity>

    @Query("SELECT * FROM followed_sport WHERE profileId = :profileId AND kind = :kind AND source = :source AND itemId = :itemId")
    suspend fun get(
        profileId: String,
        kind: String,
        source: String,
        itemId: String,
    ): FollowedSportEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM followed_sport WHERE profileId = :profileId AND kind = :kind " +
            "AND source = :source AND itemId = :itemId)",
    )
    suspend fun isFollowed(
        profileId: String,
        kind: String,
        source: String,
        itemId: String,
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FollowedSportEntity)

    @Query("DELETE FROM followed_sport WHERE profileId = :profileId AND kind = :kind AND source = :source AND itemId = :itemId")
    suspend fun delete(
        profileId: String,
        kind: String,
        source: String,
        itemId: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    @Query(
        "DELETE FROM sync_tombstone WHERE providerId = ${SyncKind.FOLLOWED_SPORT_PROVIDER_ID} AND profileId = :profileId " +
            "AND kind = '${SyncKind.FOLLOWED_SPORT}' AND itemId = :itemId AND contentType = :contentType",
    )
    suspend fun deleteTombstone(
        profileId: String,
        itemId: String,
        contentType: String,
    )

    /** [upsert], dropping any tombstone left by an earlier unfollow of the same item. */
    @Transaction
    suspend fun upsertClearingTombstone(entity: FollowedSportEntity) {
        upsert(entity)
        deleteTombstone(entity.profileId, entity.itemId, FollowedSportEntity.syncContentType(entity.kind, entity.source))
    }

    /**
     * [delete], recording the unfollow for live sync. [deletedAt] 0 (the default) means "now on the
     * sync clock", stamped by [XtreamSyncTriggers]; tests pass explicit values.
     */
    @Transaction
    suspend fun deleteRecordingTombstone(
        profileId: String,
        kind: String,
        source: String,
        itemId: String,
        deletedAt: Long = 0,
    ) {
        delete(profileId, kind, source, itemId)
        insertTombstone(
            SyncTombstoneEntity(
                SyncKind.FOLLOWED_SPORT_PROVIDER_ID,
                profileId,
                SyncKind.FOLLOWED_SPORT,
                itemId,
                FollowedSportEntity.syncContentType(kind, source),
                deletedAt,
            ),
        )
    }

    /** Profile deletion: everything that profile follows. */
    @Query("DELETE FROM followed_sport WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)
}
