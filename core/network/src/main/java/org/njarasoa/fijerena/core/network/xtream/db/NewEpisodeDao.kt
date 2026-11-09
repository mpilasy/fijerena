package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.njarasoa.fijerena.core.player.domain.ContentType

/** What the new episode check knows of a followed show, from `xtream_series`. */
data class NewEpisodeCheckState(
    val seriesId: Int,
    val lastModified: String?,
    val episodesFetchedAt: Long?,
    val hasEpisodes: Boolean,
)

/** The new episodes of a source, and the shows the profiles of this device follow on it. */
@Dao
interface NewEpisodeDao {
    /** An episode found before keeps its first [NewEpisodeEntity.foundAt]. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(rows: List<NewEpisodeEntity>)

    @Query("SELECT * FROM new_episode WHERE providerId = :providerId ORDER BY foundAt DESC")
    fun getAll(providerId: Long): List<NewEpisodeEntity>

    @Query("SELECT * FROM new_episode WHERE providerId = :providerId AND seriesId = :seriesId")
    fun getForSeries(
        providerId: Long,
        seriesId: Int,
    ): List<NewEpisodeEntity>

    @Query("DELETE FROM new_episode WHERE providerId = :providerId AND seriesId = :seriesId AND episodeId IN (:episodeIds)")
    fun deleteEpisodes(
        providerId: Long,
        seriesId: Int,
        episodeIds: List<String>,
    )

    /** The show ids of every profile's favourite shows on this source. */
    @Query(
        "SELECT DISTINCT itemId FROM favorite_state WHERE providerId = :providerId " +
            "AND contentType = '${ContentType.TV_SHOWS}' AND kind = '${FavoriteKind.STREAM}'",
    )
    fun getFavoriteSeriesIds(providerId: Long): List<String>

    /**
     * The shows that a profile played since [since] and has not finished: a stored episode that the
     * profile has not completed, or no stored list yet (nothing says that it is finished). For each
     * profile on its own, as a profile that finished a show does not follow it for another profile.
     */
    @Query(
        "SELECT DISTINCT w.seriesId FROM watch_state w " +
            "WHERE w.providerId = :providerId AND w.contentType = '${ContentType.TV_SHOWS}' " +
            "AND w.seriesId IS NOT NULL AND w.lastPlayedAt >= :since " +
            "AND (NOT EXISTS (SELECT 1 FROM xtream_episodes e " +
            "WHERE e.providerId = :providerId AND e.seriesId = CAST(w.seriesId AS INTEGER)) " +
            "OR EXISTS (SELECT 1 FROM xtream_episodes e " +
            "WHERE e.providerId = :providerId AND e.seriesId = CAST(w.seriesId AS INTEGER) " +
            "AND NOT EXISTS (SELECT 1 FROM watch_state done " +
            "WHERE done.providerId = :providerId AND done.profileId = w.profileId " +
            "AND done.contentType = '${ContentType.TV_SHOWS}' AND done.itemId = e.id AND done.isCompleted = 1)))",
    )
    fun getWatchedUnfinishedSeriesIds(
        providerId: Long,
        since: Long,
    ): List<String>

    /** What the check needs of each followed show among [ids] (at most the SQLite variable limit). */
    @Query(
        "SELECT s.seriesId AS seriesId, s.lastModified AS lastModified, s.episodesFetchedAt AS episodesFetchedAt, " +
            "EXISTS (SELECT 1 FROM xtream_episodes e WHERE e.providerId = s.providerId AND e.seriesId = s.seriesId) AS hasEpisodes " +
            "FROM xtream_series s WHERE s.providerId = :providerId AND s.seriesId IN (:ids)",
    )
    fun getCheckStates(
        providerId: Long,
        ids: List<Int>,
    ): List<NewEpisodeCheckState>

    @Query("DELETE FROM new_episode WHERE providerId = :providerId AND foundAt < :cutoff")
    fun deleteOlderThan(
        providerId: Long,
        cutoff: Long,
    )

    /** Rows of shows that the source no longer lists. */
    @Query(
        "DELETE FROM new_episode WHERE providerId = :providerId " +
            "AND seriesId NOT IN (SELECT seriesId FROM xtream_series WHERE providerId = :providerId)",
    )
    fun deleteOfGoneShows(providerId: Long)

    /** Removes the rows older than [cutoff] and the rows of shows that the source no longer lists. */
    @Transaction
    fun prune(
        providerId: Long,
        cutoff: Long,
    ) {
        deleteOlderThan(providerId, cutoff)
        deleteOfGoneShows(providerId)
    }
}
