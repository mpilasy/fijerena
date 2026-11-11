package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.njarasoa.fijerena.core.player.domain.ContentType

@Dao
interface XtreamEpisodeDao {
    @Query("SELECT * FROM xtream_episodes WHERE providerId = :providerId AND seriesId = :seriesId ORDER BY season ASC, episodeNum ASC")
    fun getEpisodes(
        providerId: Long,
        seriesId: Int,
    ): List<XtreamEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(episodes: List<XtreamEpisodeEntity>)

    @Query("DELETE FROM xtream_episodes WHERE providerId = :providerId")
    fun deleteAll(providerId: Long)

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

    @Query("SELECT * FROM xtream_episodes WHERE providerId = :providerId AND id = :episodeId LIMIT 1")
    suspend fun getEpisode(
        providerId: Long,
        episodeId: String,
    ): XtreamEpisodeEntity?

    /**
     * An episode's series, for a manual watched mark (Phase 6, the watch state durable storage plan)
     * on an episode with no `watch_state` row: without a `seriesId`, the `seriesId IS NOT NULL`
     * filter of `getSeriesCompletedCounts` drops the row from the series' progress.
     */
    @Query("SELECT seriesId FROM xtream_episodes WHERE providerId = :providerId AND id = :episodeId LIMIT 1")
    suspend fun getSeriesIdForEpisode(
        providerId: Long,
        episodeId: String,
    ): Int?

    // Denominator for the series-row watch bar: how many episodes each cached series has.
    // Only covers series whose detail has been opened at least once, which is exactly the set
    // that can have completed episodes in watch history.
    @Query("SELECT seriesId, COUNT(*) AS episodeCount FROM xtream_episodes WHERE providerId = :providerId GROUP BY seriesId")
    fun countEpisodesBySeries(
        providerId: Long,
    ): Map<
        @MapColumn(columnName = "seriesId")
        Int,
        @MapColumn(columnName = "episodeCount")
        Int,
    >

    /**
     * Phase 5 dedup (the watch state durable storage plan). Language variants of a show are
     * separate `xtream_series` rows, each with its own episode list, so the key is
     * `(season, episodeNum)` under a sibling series with *this* series' `tmdbId`, not
     * `XtreamEpisodeEntity.tmdbId` (panels leave it NULL or fill it with the series id).
     * `xtream_series` finds the siblings, then `xtream_episodes` their matching rows. The sibling
     * set includes [seriesId] itself, so a directly completed episode matches too. A series with no
     * `tmdbId` gives no dedup.
     */
    @Query(
        "SELECT e2.id AS itemId " +
            "FROM xtream_episodes e2 " +
            "WHERE e2.providerId = :providerId AND e2.seriesId = :seriesId " +
            "AND EXISTS (" +
            "SELECT 1 FROM xtream_episodes sib " +
            "JOIN watch_state w ON w.providerId = sib.providerId AND w.profileId = :profileId AND w.itemId = sib.id " +
            "WHERE sib.providerId = :providerId " +
            "AND sib.season = e2.season AND sib.episodeNum = e2.episodeNum " +
            "AND sib.seriesId IN (" +
            "SELECT s2.seriesId FROM xtream_series s2 " +
            "WHERE s2.providerId = :providerId AND s2.tmdbId IS NOT NULL " +
            "AND s2.tmdbId = (" +
            "SELECT s1.tmdbId FROM xtream_series s1 WHERE s1.providerId = :providerId AND s1.seriesId = :seriesId" +
            ")" +
            ") " +
            "AND w.contentType = '${ContentType.TV_SHOWS}' AND w.isCompleted = 1" +
            ")",
    )
    suspend fun getSiblingCompletedEpisodeIds(
        providerId: Long,
        profileId: String,
        seriesId: Int,
    ): List<String>

    /**
     * Numerator for the series-row watch bar with Phase 5 dedup: [getSiblingCompletedEpisodeIds]
     * for every cached series at once, so a show watched through another language variant does not
     * read 0% on its row.
     *
     * A CTE, not the `EXISTS` form, as it covers every series: driving from `watch_state` (few
     * completed rows) keeps it index-only. On a 47,552-series catalogue the `EXISTS` shape took
     * 223 ms, this one 0.1 ms.
     *
     * Counts `DISTINCT (season, episodeNum)`, so a series that lists an episode twice cannot exceed
     * its denominator from [countEpisodesBySeries], which counts rows (a guard; none seen).
     *
     * Series with a NULL `tmdbId` are absent and keep the caller's count without dedup.
     */
    @Query(
        "WITH done AS (" +
            "SELECT s.tmdbId AS tmdbId, sib.season AS season, sib.episodeNum AS episodeNum " +
            "FROM watch_state w " +
            "JOIN xtream_episodes sib ON sib.providerId = w.providerId AND sib.id = w.itemId " +
            "JOIN xtream_series s ON s.providerId = sib.providerId AND s.seriesId = sib.seriesId " +
            "WHERE w.providerId = :providerId AND w.profileId = :profileId " +
            "AND w.contentType = '${ContentType.TV_SHOWS}' " +
            "AND w.isCompleted = 1 AND s.tmdbId IS NOT NULL " +
            "GROUP BY s.tmdbId, sib.season, sib.episodeNum" +
            ") " +
            "SELECT e.seriesId AS seriesId, " +
            "COUNT(DISTINCT e.season || '/' || e.episodeNum) AS completed " +
            "FROM xtream_episodes e " +
            "JOIN xtream_series s2 ON s2.providerId = e.providerId AND s2.seriesId = e.seriesId " +
            "JOIN done d ON d.tmdbId = s2.tmdbId AND d.season = e.season AND d.episodeNum = e.episodeNum " +
            "WHERE e.providerId = :providerId " +
            "GROUP BY e.seriesId",
    )
    suspend fun getSiblingCompletedCountsBySeries(
        providerId: Long,
        profileId: String,
    ): Map<
        @MapColumn(columnName = "seriesId")
        Int,
        @MapColumn(columnName = "completed")
        Int,
    >

    /**
     * Phase 6 unwatched, episode form of [org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao.clearGroupCompletion].
     * Same two-level join as [getSiblingCompletedEpisodeIds]: [itemId]'s `(season, episodeNum)` in
     * every sibling series with its series' `tmdbId`. The target is in its own sibling set, so its
     * own row is cleared too, as the direct `markUnwatched` call does.
     */
    @Query(
        "UPDATE watch_state SET isCompleted = 0, updatedAt = :now " +
            "WHERE providerId = :providerId AND profileId = :profileId " +
            "AND contentType = '${ContentType.TV_SHOWS}' " +
            "AND itemId IN (" +
            "SELECT sib.id FROM xtream_episodes target " +
            "JOIN xtream_episodes sib " +
            "ON sib.providerId = target.providerId " +
            "AND sib.season = target.season AND sib.episodeNum = target.episodeNum " +
            "AND sib.seriesId IN (" +
            "SELECT s2.seriesId FROM xtream_series s2 " +
            "WHERE s2.providerId = :providerId AND s2.tmdbId IS NOT NULL " +
            "AND s2.tmdbId = (" +
            "SELECT s1.tmdbId FROM xtream_series s1 WHERE s1.providerId = :providerId AND s1.seriesId = target.seriesId" +
            ")" +
            ") " +
            "WHERE target.providerId = :providerId AND target.id = :itemId" +
            ")",
    )
    suspend fun clearGroupCompletion(
        providerId: Long,
        profileId: String,
        itemId: String,
        now: Long,
    )

    // Fills a missing plot only (Xtream rarely sends episode synopses); never overwrites one.
    @Query(
        "UPDATE xtream_episodes SET plot = :plot, plotFetchedAt = :fetchedAt " +
            "WHERE id = :id AND providerId = :providerId AND (plot IS NULL OR plot = '')",
    )
    fun updateOverviewIfBlank(
        providerId: Long,
        id: String,
        plot: String,
        fetchedAt: Long,
    )
}
