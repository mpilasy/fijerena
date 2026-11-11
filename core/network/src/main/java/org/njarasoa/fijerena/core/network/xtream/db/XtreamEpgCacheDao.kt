package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamEpgCacheDao {
    @Query(
        "SELECT * FROM xtream_epg_cache WHERE providerId = :providerId AND streamId IN (:streamIds) AND updatedAt >= :minUpdatedAt",
    )
    fun getFresh(
        providerId: Long,
        streamIds: List<Int>,
        minUpdatedAt: Long,
    ): List<XtreamEpgCacheEntity>

    @Query(
        "SELECT payload FROM xtream_epg_cache WHERE providerId = :providerId AND streamId = :streamId AND updatedAt >= :minUpdatedAt",
    )
    fun getFreshPayload(
        providerId: Long,
        streamId: Int,
        minUpdatedAt: Long,
    ): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<XtreamEpgCacheEntity>)

    /**
     * Deletes at most [limit] rows that are already past the expiry, returning how many went.
     *
     * Batched on purpose: with `auto_vacuum = FULL`, each commit that frees pages moves them at
     * once, and one unbounded `DELETE` here moved ~300k pages in one transaction (a 1.2 GB WAL that
     * never committed). Small commits bound that and let the file shrink step by step.
     *
     * Not scoped to one provider: an expired row is dead whoever owns it, and the rows of a
     * provider no longer in use would else never go.
     */
    @Query(
        "DELETE FROM xtream_epg_cache WHERE rowid IN " +
            "(SELECT rowid FROM xtream_epg_cache WHERE updatedAt < :cutoff LIMIT :limit)",
    )
    fun deleteStaleBatch(
        cutoff: Long,
        limit: Int,
    ): Int

    @Query("DELETE FROM xtream_epg_cache WHERE providerId = :providerId AND streamId = :streamId")
    fun deleteStream(
        providerId: Long,
        streamId: Int,
    )

    @Query("DELETE FROM xtream_epg_cache WHERE providerId = :providerId")
    fun deleteAll(providerId: Long)
}
