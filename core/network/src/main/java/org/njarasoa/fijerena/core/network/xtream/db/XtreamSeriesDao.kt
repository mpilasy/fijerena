package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamSeriesDao {
    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId ORDER BY name ASC")
    fun getSeriesByCategory(providerId: Long, categoryId: String): List<XtreamSeriesEntity>

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId ORDER BY name ASC")
    fun getAllSeries(providerId: Long): List<XtreamSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(series: List<XtreamSeriesEntity>)

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId")
    fun deleteAll(providerId: Long)

    @Query("SELECT seriesId FROM xtream_series WHERE providerId = :providerId")
    fun getSeriesIds(providerId: Long): List<Int>

    @MapInfo(keyColumn = "seriesId", valueColumn = "contentHash")
    @Query("SELECT seriesId, contentHash FROM xtream_series WHERE providerId = :providerId")
    fun getSeriesHashes(providerId: Long): Map<Int, Int>

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId AND seriesId IN (:ids)")
    fun deleteByIds(providerId: Long, ids: List<Int>)

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND name LIKE '%' || :query || '%'")
    fun searchSeries(providerId: Long, query: String): List<XtreamSeriesEntity>
}
