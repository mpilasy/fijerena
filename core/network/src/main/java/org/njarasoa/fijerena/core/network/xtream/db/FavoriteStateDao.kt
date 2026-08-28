package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Favourites storage. Every read here is blocking rather than suspending on purpose: the caller is
 * `MediaRepository`, which serves favourites from an in-memory snapshot because Compose asks
 * `isFavorite()` synchronously during composition. The snapshot is filled from [getAll] inside
 * `setProvider()`, which runs on `Dispatchers.IO`.
 *
 * There is no cap and no trim query. That is the point of the table — see
 * `docs/plans/favorites-durable-storage-plan.md`.
 */
@Dao
interface FavoriteStateDao {
    /** Whole-provider read for the snapshot. Newest first, matching the blob's ordering. */
    @Query("SELECT * FROM favorite_state WHERE providerId = :providerId ORDER BY createdAt DESC")
    fun getAll(providerId: Long): List<FavoriteStateEntity>

    /**
     * Insert-or-replace. Re-favouriting something already favourited refreshes `createdAt`, which
     * moves it to the front of the list — the blob behaved the same way, since `addFavorite`
     * prepended.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: FavoriteStateEntity)

    @Query(
        "DELETE FROM favorite_state WHERE providerId = :providerId AND itemId = :itemId " +
            "AND contentType = :contentType AND kind = :kind",
    )
    fun delete(
        providerId: Long,
        itemId: String,
        contentType: String,
        kind: String,
    )

    /** Backs "Clear All Favorites", which is scoped to streams — categories are left alone. */
    @Query("DELETE FROM favorite_state WHERE providerId = :providerId AND kind = :kind")
    fun deleteAllOfKind(
        providerId: Long,
        kind: String,
    )

    @Query("DELETE FROM favorite_state WHERE providerId = :providerId")
    fun deleteAll(providerId: Long)

    /** Restore path: rewrites `providerId` before insert, so it takes whole rows. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun restoreAll(entities: List<FavoriteStateEntity>)

    @Query("SELECT COUNT(*) FROM favorite_state WHERE providerId = :providerId")
    fun count(providerId: Long): Int
}
