package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamSeriesDao {
    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId ORDER BY name ASC")
    fun getSeriesByCategory(
        providerId: Long,
        categoryId: String,
    ): List<XtreamSeriesEntity>

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId ORDER BY name ASC")
    fun getAllSeries(providerId: Long): List<XtreamSeriesEntity>

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND seriesId = :seriesId LIMIT 1")
    fun getSeriesById(
        providerId: Long,
        seriesId: Int,
    ): XtreamSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(series: List<XtreamSeriesEntity>)

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId")
    fun deleteAll(providerId: Long)

    @Query("SELECT seriesId FROM xtream_series WHERE providerId = :providerId")
    fun getSeriesIds(providerId: Long): List<Int>

    @Query("SELECT seriesId, contentHash FROM xtream_series WHERE providerId = :providerId")
    fun getSeriesHashes(
        providerId: Long,
    ): Map<
        @MapColumn(columnName = "seriesId")
        Int,
        @MapColumn(columnName = "contentHash")
        Int,
    >

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId AND seriesId IN (:ids)")
    fun deleteByIds(
        providerId: Long,
        ids: List<Int>,
    )

    @Query("""
        SELECT s.* FROM xtream_series s
        LEFT JOIN xtream_categories c ON s.categoryId = c.categoryId AND s.providerId = c.providerId AND c.type = 'SERIES'
        WHERE (
            s.rowid IN (
                SELECT docid FROM xtream_series_fts WHERE xtream_series_fts MATCH :query
            )
            OR (c.categoryName LIKE :categoryQuery)
        )
        AND s.providerId = :providerId
        LIMIT 200
    """)
    fun searchByFts(
        providerId: Long,
        query: String,
        categoryQuery: String,
    ): List<XtreamSeriesEntity>

    @Query("INSERT INTO xtream_series_fts(xtream_series_fts) VALUES('rebuild')")
    fun rebuildFts()

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId")
    fun deleteByCategoryId(
        providerId: Long,
        categoryId: String,
    )

    @Query("SELECT COUNT(*) FROM xtream_series WHERE providerId = :providerId")
    fun countSeries(providerId: Long): Int
}
