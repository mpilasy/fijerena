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

    /**
     * Resolves an episode's series, for a manual watched mark (Phase 6,
     * docs/plans/watch-state-durable-storage-plan.md) on an episode with no existing `watch_state` row.
     * `markWatched`'s repository-level signature carries no `seriesId` — without this lookup a
     * never-played episode marked watched directly would insert a row with `seriesId` null, and
     * `getSeriesCompletedCounts`'s `seriesId IS NOT NULL` filter would silently drop it from the
     * series' progress rollup.
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
    fun countEpisodesBySeries(providerId: Long): Map<
        @MapColumn(columnName = "seriesId")
        Int,
        @MapColumn(columnName = "episodeCount")
        Int,
    >

    /**
     * Phase 5 dedup (docs/plans/watch-state-durable-storage-plan.md), corrected 2026-08-27 after an
     * on-device check showed the original episode-`tmdbId`-based join was structurally incapable
     * of matching anything: unlike movies, where five language variants are five rows in one flat
     * catalogue, five language variants of a show are five separate `xtream_series` rows, each
     * with its own complete, separately-numbered episode list (confirmed: 25 distinct `seriesId`
     * sharing one series-level `tmdbId` for a single show on one provider). The real correlation
     * key is `(season, episodeNum)` under a sibling series sharing *this* series' `tmdbId` — not
     * `XtreamEpisodeEntity.tmdbId`, which was NULL for effectively every episode checked (543/543
     * and 95/95 on one show; 39/40 and the lone non-null value the *series'* own id leaked onto
     * one episode — the guard's failure mode, occurring naturally). Two-level join: `xtream_series`
     * finds sibling series by shared `tmdbId`, then `xtream_episodes` finds each sibling's matching
     * `(season, episodeNum)` row. The sibling-series subquery includes [seriesId] itself (a
     * series' own `tmdbId` always matches itself), so a directly-completed episode satisfies this
     * via a self-match — the caller's separate `isCompleted` check becomes redundant against this
     * result, not incorrect. A series with no `tmdbId` of its own degrades to no dedup.
     */
    @Query(
        "SELECT e2.id AS itemId " +
            "FROM xtream_episodes e2 " +
            "WHERE e2.providerId = :providerId AND e2.seriesId = :seriesId " +
            "AND EXISTS (" +
            "SELECT 1 FROM xtream_episodes sib " +
            "JOIN watch_state w ON w.providerId = sib.providerId AND w.itemId = sib.id " +
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
        seriesId: Int,
    ): List<String>

    /**
     * Numerator for the series-row watch bar, with Phase 5 dedup applied — the aggregate form of
     * [getSiblingCompletedEpisodeIds], for every cached series at once rather than one series at a
     * time. Without it the bar counts only episodes completed under *this* `seriesId`, so a show
     * watched through a different language variant reads 0% on the row while its own episode list
     * correctly shows those episodes checked. That disagreement is the bug this fixes.
     *
     * Shaped as a CTE rather than the `EXISTS` form [getSiblingCompletedEpisodeIds] uses, because
     * this one is not scoped to a single series: driving from `watch_state` (a handful of completed
     * rows) and joining outwards keeps it index-only, where the correlated-subquery form re-derived
     * each series' `tmdbId` per candidate episode. Measured against a real 47,552-series catalogue:
     * 223ms for the `EXISTS` shape, 0.1ms for this one.
     *
     * Counts `DISTINCT (season, episodeNum)` so a series listing the same episode twice cannot
     * exceed its own denominator from [countEpisodesBySeries], which counts rows. No duplicates
     * were present in the catalogue checked, so this is a guard rather than an observed fix.
     *
     * Series whose `tmdbId` is NULL are absent from the result and keep the undeduplicated count
     * the caller already has — same fail-safe shape as the rest of Phase 5.
     */
    @Query(
        "WITH done AS (" +
            "SELECT s.tmdbId AS tmdbId, sib.season AS season, sib.episodeNum AS episodeNum " +
            "FROM watch_state w " +
            "JOIN xtream_episodes sib ON sib.providerId = w.providerId AND sib.id = w.itemId " +
            "JOIN xtream_series s ON s.providerId = sib.providerId AND s.seriesId = sib.seriesId " +
            "WHERE w.providerId = :providerId AND w.contentType = '${ContentType.TV_SHOWS}' " +
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
    suspend fun getSiblingCompletedCountsBySeries(providerId: Long): Map<
        @MapColumn(columnName = "seriesId")
        Int,
        @MapColumn(columnName = "completed")
        Int,
    >

    /**
     * Phase 6 unwatched, episode form of [org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao.clearGroupCompletion].
     * Same two-level join as [getSiblingCompletedEpisodeIds]: [itemId]'s `(season, episodeNum)`
     * matched across every sibling series sharing its series' `tmdbId`, not by episode `tmdbId`.
     * The target itself is included in its own sibling set (a series' `tmdbId` matches itself), so
     * this clears the one row too — redundant with the direct `markUnwatched` call, not incorrect.
     */
    @Query(
        "UPDATE watch_state SET isCompleted = 0, updatedAt = :now " +
            "WHERE providerId = :providerId AND contentType = '${ContentType.TV_SHOWS}' " +
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
        itemId: String,
        now: Long,
    )

    // Only fills a missing plot (Xtream doesn't provide episode synopses) — never overwrites an existing one.
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
