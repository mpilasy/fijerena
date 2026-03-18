package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamEpisodeDao {
    @Query("SELECT * FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId ORDER BY season ASC, episodeNum ASC")
    fun getEpisodes(providerId: Long, seriesId: Int): List<XtreamEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(episodes: List<XtreamEpisodeEntity>)

    @Query("DELETE FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId")
    fun deleteBySeriesId(providerId: Long, seriesId: Int)

    @Query("SELECT id, contentHash FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId")
    fun getEpisodeHashes(
        providerId: Long,
        seriesId: Int
    ): Map<@MapColumn(columnName = "id") String, @MapColumn(columnName = "contentHash") Int>

    @Query("SELECT COUNT(*) FROM xtream_episodes WHERE providerId = :providerId")
    fun countEpisodes(providerId: Long): Int

}
