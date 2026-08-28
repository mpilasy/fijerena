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
     * plans/watch-state-durable-storage-plan.md) on an episode with no existing `watch_state` row.
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
     * Phase 5 dedup (plans/watch-state-durable-storage-plan.md): episode ids of this series
     * completed by a TMDB sibling. Scoped to [seriesId] on both sides — the completed lookup and
     * the candidate set — since dedup only ever needs to run for the series currently on screen.
     * The `tmdbId` guard against a series-level id copied onto every episode
     * ([org.njarasoa.fijerena.core.network.xtream.manager.XtreamContentManager.getSeriesInfo])
     * is applied at write time, not here: an unguarded repeat would otherwise complete this whole
     * series the moment one episode did.
     */
    @Query(
        "SELECT e2.id AS itemId " +
            "FROM xtream_episodes e2 " +
            "JOIN (" +
            "SELECT e.tmdbId AS tmdbId " +
            "FROM watch_state w " +
            "JOIN xtream_episodes e ON e.providerId = w.providerId AND w.itemId = e.id " +
            "WHERE w.providerId = :providerId AND w.contentType = '${ContentType.TV_SHOWS}' AND w.isCompleted = 1 " +
            "AND e.tmdbId IS NOT NULL AND e.seriesId = :seriesId " +
            "GROUP BY e.tmdbId" +
            ") done ON e2.tmdbId = done.tmdbId " +
            "WHERE e2.providerId = :providerId AND e2.seriesId = :seriesId",
    )
    suspend fun getSiblingCompletedEpisodeIds(
        providerId: Long,
        seriesId: Int,
    ): List<String>

    /**
     * Phase 6 unwatched, episode form of [org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao.clearGroupCompletion]:
     * clears completion on every `watch_state` row sharing [itemId]'s `tmdbId`. No `seriesId` scope
     * is needed here — [guardSeriesLevelEpisodeTmdbIds][org.njarasoa.fijerena.core.network.xtream.manager.guardSeriesLevelEpisodeTmdbIds]
     * already nulls a `tmdbId` that repeats within a series before it ever reaches this table, so a
     * surviving non-null episode `tmdbId` is presumed genuinely episode-specific.
     */
    @Query(
        "UPDATE watch_state SET isCompleted = 0, updatedAt = :now " +
            "WHERE providerId = :providerId AND contentType = '${ContentType.TV_SHOWS}' " +
            "AND itemId IN (" +
            "SELECT e2.id FROM xtream_episodes e2 " +
            "WHERE e2.providerId = :providerId AND e2.tmdbId IS NOT NULL AND e2.tmdbId = (" +
            "SELECT tmdbId FROM xtream_episodes WHERE providerId = :providerId AND id = :itemId" +
            ")" +
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
