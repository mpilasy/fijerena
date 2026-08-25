package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamSeriesDao {
    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId AND excluded = 0 ORDER BY name ASC")
    fun getSeriesByCategory(
        providerId: Long,
        categoryId: String,
    ): List<XtreamSeriesEntity>

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND excluded = 0 ORDER BY name ASC")
    fun getAllSeries(providerId: Long): List<XtreamSeriesEntity>

    @Query("UPDATE xtream_series SET excluded = COALESCE((SELECT c.excluded FROM xtream_categories c WHERE c.categoryId = xtream_series.categoryId AND c.providerId = xtream_series.providerId AND c.type = 'SERIES'), 0) WHERE providerId = :providerId")
    fun syncExcludedFromCategories(providerId: Long)

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND seriesId = :seriesId LIMIT 1")
    fun getSeriesById(
        providerId: Long,
        seriesId: Int,
    ): XtreamSeriesEntity?

    /** As [XtreamStreamDao.getByTmdbId], for series. */
    @Query(
        "SELECT * FROM xtream_series WHERE providerId = :providerId " +
            "AND tmdbId = :tmdbId AND seriesId != :excludeSeriesId AND excluded = 0 ORDER BY name ASC",
    )
    fun getByTmdbId(
        providerId: Long,
        tmdbId: String,
        excludeSeriesId: Int,
    ): List<XtreamSeriesEntity>

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

    @Query("SELECT seriesId, cover FROM xtream_series WHERE providerId = :providerId AND seriesId IN (:ids) AND cover IS NOT NULL")
    fun getCoversByIds(
        providerId: Long,
        ids: List<Int>,
    ): Map<
        @MapColumn(columnName = "seriesId")
        Int,
        @MapColumn(columnName = "cover")
        String,
    >

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId AND seriesId IN (:ids)")
    fun deleteByIds(
        providerId: Long,
        ids: List<Int>,
    )

    @Query("""
        SELECT s.* FROM xtream_series s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_series_fts WHERE xtream_series_fts MATCH :query
        )
        AND s.providerId = :providerId
        AND (s.excluded = 0 OR :includeExcluded = 1)
        LIMIT 200
    """)
    fun searchByFts(
        providerId: Long,
        query: String,
        includeExcluded: Boolean,
    ): List<XtreamSeriesEntity>

    @Query("""
        SELECT COUNT(*) FROM xtream_series s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_series_fts WHERE xtream_series_fts MATCH :query
        )
        AND s.providerId = :providerId
        AND s.excluded = 1
    """)
    fun countExcludedByFts(
        providerId: Long,
        query: String,
    ): Int

    @Query("INSERT INTO xtream_series_fts(xtream_series_fts) VALUES('rebuild')")
    fun rebuildFts()

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId")
    fun deleteByCategoryId(
        providerId: Long,
        categoryId: String,
    )

    @Query("SELECT COUNT(*) FROM xtream_series WHERE providerId = :providerId")
    fun countSeries(providerId: Long): Int

    @Query(
        """
        UPDATE xtream_series
        SET contentRating = :contentRating,
            tmdbId = :tmdbId,
            detailFetchedAt = :detailFetchedAt
        WHERE seriesId = :seriesId AND providerId = :providerId
    """,
    )
    fun updateDetailCache(
        providerId: Long,
        seriesId: Int,
        contentRating: String?,
        tmdbId: String?,
        detailFetchedAt: Long,
    )
}
