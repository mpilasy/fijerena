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
 * `docs/plans/20260828_favorites-durable-storage-plan.md`.
 */
@Dao
interface FavoriteStateDao {
    /** Whole-provider read for one profile's snapshot. Newest first, matching the blob's ordering. */
    @Query("SELECT * FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId ORDER BY createdAt DESC")
    fun getAll(
        providerId: Long,
        profileId: String,
    ): List<FavoriteStateEntity>

    /** Every profile's rows for this provider — copying a provider copies everyone's favourites. */
    @Query("SELECT * FROM favorite_state WHERE providerId = :providerId")
    fun getAllProfiles(providerId: Long): List<FavoriteStateEntity>

    /**
     * Insert-or-replace. Re-favouriting something already favourited refreshes `createdAt`, which
     * moves it to the front of the list — the blob behaved the same way, since `addFavorite`
     * prepended.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: FavoriteStateEntity)

    @Query(
        "DELETE FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId " +
            "AND itemId = :itemId AND contentType = :contentType AND kind = :kind",
    )
    fun delete(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        kind: String,
    )

    /** Backs "Clear All Favorites", which is scoped to streams — categories are left alone. */
    @Query("DELETE FROM favorite_state WHERE providerId = :providerId AND profileId = :profileId AND kind = :kind")
    fun deleteAllOfKind(
        providerId: Long,
        profileId: String,
        kind: String,
    )

    /** Profile deletion: that profile's rows across every provider. */
    @Query("DELETE FROM favorite_state WHERE profileId = :profileId")
    fun deleteProfile(profileId: String)

    /** Provider deletion: every profile's rows go with it. */
    @Query("DELETE FROM favorite_state WHERE providerId = :providerId")
    fun deleteAllProfiles(providerId: Long)

    /** Rows whose provider no longer exists at all — see [org.njarasoa.fijerena.core.network.provider.ProviderRepository.pruneOrphanedCatalogData]. */
    @Query("DELETE FROM favorite_state WHERE providerId NOT IN (:validProviderIds)")
    fun deleteOrphaned(validProviderIds: List<Long>): Int

    /** Restore path: rewrites `providerId` before insert, so it takes whole rows. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun restoreAll(entities: List<FavoriteStateEntity>)

    @Query("SELECT COUNT(*) FROM favorite_state WHERE providerId = :providerId")
    fun count(providerId: Long): Int
}
