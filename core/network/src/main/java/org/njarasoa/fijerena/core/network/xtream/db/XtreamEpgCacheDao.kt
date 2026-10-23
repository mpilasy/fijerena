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
     * Batched deliberately. This database runs `auto_vacuum = FULL`, so every commit that frees
     * pages moves and truncates them there and then — a single unbounded `DELETE` over this table
     * shuffled ~300k pages in one transaction and produced a 1.2 GB WAL that never committed. Small
     * commits keep that page movement bounded and let the file shrink incrementally instead.
     *
     * Not scoped to one provider: a stale row is dead regardless of whose it is, and a provider
     * that is no longer active would otherwise never be swept.
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
