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

    @Query("DELETE FROM xtream_epg_cache WHERE providerId = :providerId AND streamId = :streamId")
    fun deleteStream(
        providerId: Long,
        streamId: Int,
    )

    @Query("DELETE FROM xtream_epg_cache WHERE providerId = :providerId")
    fun deleteAll(providerId: Long)
}
