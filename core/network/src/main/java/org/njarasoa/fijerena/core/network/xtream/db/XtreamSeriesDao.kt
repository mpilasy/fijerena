package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Series follow their category's `excluded` flag, as streams do — see [XtreamStreamDao]. An allowed
 * series shows too (`OR allowed = 1`). SQLite reads each series row here anyway: all series of a
 * 48k-series catalogue took 12 ms against 11 ms before, so the `rowid` form of [XtreamStreamDao] is
 * not necessary.
 */
@Dao
interface XtreamSeriesDao {
    @Query(
        "SELECT * FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId AND (categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = 'SERIES' AND excluded = 1) OR allowed = 1) ORDER BY name ASC",
    )
    fun getSeriesByCategory(
        providerId: Long,
        categoryId: String,
    ): List<XtreamSeriesEntity>

    @Query(
        "SELECT * FROM xtream_series WHERE providerId = :providerId AND (categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = 'SERIES' AND excluded = 1) OR allowed = 1) ORDER BY name ASC",
    )
    fun getAllSeries(providerId: Long): List<XtreamSeriesEntity>

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND seriesId = :seriesId LIMIT 1")
    fun getSeriesById(
        providerId: Long,
        seriesId: Int,
    ): XtreamSeriesEntity?

    /** As [XtreamStreamDao.getByTmdbId], for series. */
    @Query(
        "SELECT * FROM xtream_series WHERE providerId = :providerId " +
            "AND tmdbId = :tmdbId AND seriesId != :excludeSeriesId AND (categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = 'SERIES' AND excluded = 1) OR allowed = 1) ORDER BY name ASC",
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

    @Query(
        "SELECT seriesId, contentRating, tmdbId, detailFetchedAt, posterPath FROM xtream_series " +
            "WHERE providerId = :providerId AND seriesId IN (:ids)",
    )
    fun getDetailCaches(
        providerId: Long,
        ids: List<Int>,
    ): List<XtreamSeriesDetailCache>

    /**
     * [insertAll] for a catalogue sync: rows that already exist keep their detail-screen columns
     * (TMDB rating, poster, fetch time) instead of being reset by REPLACE. `episodesFetchedAt` is
     * still reset, on purpose; see [withDetailCache].
     */
    @Transaction
    fun insertKeepingDetailCache(
        providerId: Long,
        series: List<XtreamSeriesEntity>,
    ) {
        val caches =
            series
                .map { it.seriesId }
                .chunked(SQLITE_MAX_VARIABLES)
                .flatMap { getDetailCaches(providerId, it) }
                .associateBy { it.seriesId }
        insertAll(series.map { it.withDetailCache(caches[it.seriesId]) })
    }

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

    /** As [XtreamStreamDao.matchNames], for series (type `SERIES`). */
    @Query(
        """
        SELECT s.seriesId AS id, 'SERIES' AS type, s.categoryId AS categoryId, s.name AS name FROM xtream_series s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_series_fts WHERE xtream_series_fts MATCH :query
        )
        AND s.providerId = :providerId
    """,
    )
    fun matchNames(
        providerId: Long,
        query: String,
    ): List<NameMatch>

    /** As [XtreamStreamDao.clearAllowed], for series. */
    @Query("UPDATE xtream_series SET allowed = 0 WHERE providerId = :providerId AND allowed = 1")
    fun clearAllowed(providerId: Long)

    /** As [XtreamStreamDao.setAllowed], for series. */
    @Query("UPDATE xtream_series SET allowed = 1 WHERE providerId = :providerId AND seriesId IN (:ids)")
    fun setAllowed(
        providerId: Long,
        ids: List<Int>,
    )

    @Query(
        """
        SELECT s.* FROM xtream_series s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_series_fts WHERE xtream_series_fts MATCH :query
        )
        AND s.providerId = :providerId
        AND (:includeExcluded = 1 OR s.categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = 'SERIES' AND excluded = 1) OR s.allowed = 1)
        LIMIT 200
    """,
    )
    fun searchByFts(
        providerId: Long,
        query: String,
        includeExcluded: Boolean,
    ): List<XtreamSeriesEntity>

    /** The unary `+` keeps SQLite off the categoryId index — see [XtreamStreamDao.countExcludedByFts]. */
    @Query(
        """
        SELECT COUNT(*) FROM xtream_series s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_series_fts WHERE xtream_series_fts MATCH :query
        )
        AND s.providerId = :providerId
        AND +s.categoryId IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = 'SERIES' AND excluded = 1)
        AND s.allowed = 0
    """,
    )
    fun countExcludedByFts(
        providerId: Long,
        query: String,
    ): Int

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId AND categoryId = :categoryId")
    fun deleteByCategoryId(
        providerId: Long,
        categoryId: String,
    )

    @Query("SELECT COUNT(*) FROM xtream_series WHERE providerId = :providerId")
    fun countSeries(providerId: Long): Int

    /** Whether the catalogue holds any series for [providerId] — an index probe, no count. */
    @Query("SELECT EXISTS(SELECT 1 FROM xtream_series WHERE providerId = :providerId)")
    fun hasSeries(providerId: Long): Boolean

    @Query(
        """
        UPDATE xtream_series
        SET contentRating = :contentRating,
            tmdbId = :tmdbId,
            detailFetchedAt = :detailFetchedAt,
            posterPath = COALESCE(:posterPath, posterPath)
        WHERE seriesId = :seriesId AND providerId = :providerId
    """,
    )
    fun updateDetailCache(
        providerId: Long,
        seriesId: Int,
        contentRating: String?,
        tmdbId: String?,
        detailFetchedAt: Long,
        posterPath: String? = null,
    )

    /** Stamped every time `get_series_info` successfully persists this series' episode list. */
    @Query("UPDATE xtream_series SET episodesFetchedAt = :episodesFetchedAt WHERE seriesId = :seriesId AND providerId = :providerId")
    fun updateEpisodesFetchedAt(
        providerId: Long,
        seriesId: Int,
        episodesFetchedAt: Long,
    )
}
