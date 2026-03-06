package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamStreamDao {
    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type AND categoryId = :categoryId ORDER BY num ASC")
    fun getStreamsByCategory(providerId: Long, type: String, categoryId: String): List<XtreamStreamEntity>

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type ORDER BY num ASC")
    fun getAllStreams(providerId: Long, type: String): List<XtreamStreamEntity>

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND streamId = :streamId LIMIT 1")
    fun getStreamById(providerId: Long, streamId: Int): XtreamStreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(streams: List<XtreamStreamEntity>)

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun deleteAll(providerId: Long, type: String)

    @Query("SELECT streamId FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun getStreamIds(providerId: Long, type: String): List<Int>

    @MapInfo(keyColumn = "streamId", valueColumn = "contentHash")
    @Query("SELECT streamId, contentHash FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun getStreamHashes(providerId: Long, type: String): Map<Int, Int>

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type AND streamId IN (:ids)")
    fun deleteByIds(providerId: Long, type: String, ids: List<Int>)

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type AND name LIKE '%' || :query || '%'")
    fun searchStreams(providerId: Long, type: String, query: String): List<XtreamStreamEntity>

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type AND categoryId = :categoryId")
    fun deleteByCategoryId(providerId: Long, type: String, categoryId: String)

    @Query("SELECT COUNT(*) FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun countStreams(providerId: Long, type: String): Int
}
