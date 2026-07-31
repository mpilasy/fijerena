package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamEpisodeDao {
    @Query("SELECT * FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId ORDER BY season ASC, episodeNum ASC")
    fun getEpisodes(
        providerId: Long,
        seriesId: Int,
    ): List<XtreamEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(episodes: List<XtreamEpisodeEntity>)

    @Query("DELETE FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId")
    fun deleteBySeriesId(
        providerId: Long,
        seriesId: Int,
    )

    @Query("SELECT id, contentHash FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId")
    fun getEpisodeHashes(
        providerId: Long,
        seriesId: Int,
    ): Map<
        @MapColumn(columnName = "id")
        String,
        @MapColumn(columnName = "contentHash")
        Int,
    >

    @Query("SELECT COUNT(*) FROM xtream_episodes WHERE providerId = :providerId")
    fun countEpisodes(providerId: Long): Int

    // Only fills a missing plot (Xtream doesn't provide episode synopses) — never overwrites an existing one.
    @Query("UPDATE xtream_episodes SET plot = :plot WHERE id = :id AND providerId = :providerId AND (plot IS NULL OR plot = '')")
    fun updateOverviewIfBlank(
        providerId: Long,
        id: String,
        plot: String,
    )
}
