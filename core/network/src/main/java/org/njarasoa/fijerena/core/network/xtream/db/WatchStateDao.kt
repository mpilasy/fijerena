package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.player.domain.RESUMABLE_SQL

/**
 * See the watch state durable storage plan: the statements follow its Write path and read-query
 * sections.
 */
@Dao
interface WatchStateDao {
    /**
     * Playback progress upsert. Owns position, duration, completion and `lastPlayedAt`; leaves
     * metadata columns alone when this call doesn't carry them via `COALESCE`. An empty name
     * counts as none (`NULLIF`): a player opened without the stream's name must not blank the
     * stored one, which showed as "No title" in Recent.
     *
     * `isCompleted` is sticky: `MAX` can only raise it, never lower it, so playing two minutes of a
     * film already marked watched can't silently clear the check — only `setWatched(false)` does that.
     */
    @Query(
        "INSERT INTO watch_state (providerId, profileId, itemId, contentType, itemName, categoryId, positionMs, " +
            "durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId, seriesName, " +
            "episodeExtension, audioTrackIndex, subtitleTrackIndex) " +
            "VALUES (:providerId, :profileId, :itemId, :contentType, :itemName, :categoryId, :positionMs, :durationMs, " +
            ":isCompleted, :now, :now, :seriesId, :episodeId, :seriesName, :episodeExtension, " +
            ":audioTrackIndex, :subtitleTrackIndex) " +
            "ON CONFLICT(providerId, profileId, itemId, contentType) DO UPDATE SET " +
            "positionMs = excluded.positionMs, " +
            "durationMs = excluded.durationMs, " +
            "isCompleted = MAX(watch_state.isCompleted, excluded.isCompleted), " +
            "updatedAt = excluded.updatedAt, " +
            "lastPlayedAt = excluded.lastPlayedAt, " +
            "itemName = COALESCE(NULLIF(excluded.itemName, ''), watch_state.itemName), " +
            "seriesId = COALESCE(excluded.seriesId, watch_state.seriesId), " +
            "episodeId = COALESCE(excluded.episodeId, watch_state.episodeId), " +
            "seriesName = COALESCE(excluded.seriesName, watch_state.seriesName), " +
            "episodeExtension = COALESCE(excluded.episodeExtension, watch_state.episodeExtension), " +
            "audioTrackIndex = COALESCE(excluded.audioTrackIndex, watch_state.audioTrackIndex), " +
            "subtitleTrackIndex = COALESCE(excluded.subtitleTrackIndex, watch_state.subtitleTrackIndex)",
    )
    fun upsertProgress(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        itemName: String?,
        categoryId: String?,
        positionMs: Long,
        durationMs: Long,
        isCompleted: Boolean,
        now: Long,
        seriesId: String?,
        episodeId: String?,
        seriesName: String?,
        episodeExtension: String?,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
    )

    /**
     * Playback-start upsert. Owns recency and metadata; must not touch `positionMs`, `durationMs`
     * or `isCompleted` on conflict, so starting playback never erases progress already stored.
     */
    @Query(
        "INSERT INTO watch_state (providerId, profileId, itemId, contentType, itemName, categoryId, positionMs, " +
            "durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId, seriesName, " +
            "episodeExtension, audioTrackIndex, subtitleTrackIndex) " +
            "VALUES (:providerId, :profileId, :itemId, :contentType, :itemName, :categoryId, 0, 0, 0, :now, :now, " +
            ":seriesId, :episodeId, :seriesName, :episodeExtension, :audioTrackIndex, :subtitleTrackIndex) " +
            "ON CONFLICT(providerId, profileId, itemId, contentType) DO UPDATE SET " +
            "updatedAt = excluded.updatedAt, " +
            "lastPlayedAt = excluded.lastPlayedAt, " +
            "itemName = COALESCE(NULLIF(excluded.itemName, ''), watch_state.itemName), " +
            "seriesId = COALESCE(excluded.seriesId, watch_state.seriesId), " +
            "episodeId = COALESCE(excluded.episodeId, watch_state.episodeId), " +
            "seriesName = COALESCE(excluded.seriesName, watch_state.seriesName), " +
            "episodeExtension = COALESCE(excluded.episodeExtension, watch_state.episodeExtension), " +
            "audioTrackIndex = COALESCE(excluded.audioTrackIndex, watch_state.audioTrackIndex), " +
            "subtitleTrackIndex = COALESCE(excluded.subtitleTrackIndex, watch_state.subtitleTrackIndex)",
    )
    fun upsertRecency(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        itemName: String?,
        categoryId: String?,
        now: Long,
        seriesId: String?,
        episodeId: String?,
        seriesName: String?,
        episodeExtension: String?,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
    )

    /**
     * Manual mark watched (Phase 6). The insert path writes `positionMs`/`durationMs` = 0 and
     * `lastPlayedAt` = NULL, as [upsertRecency] does: a mark is not a play, so it stays out of
     * Recent (`lastPlayedAt IS NOT NULL`). The update path changes only `isCompleted`/`updatedAt`,
     * so a later `setWatched(false)` brings the resume bar back where it was.
     *
     * [seriesId]/[episodeId] are insert-only. `MediaRepository.setWatched` resolves [seriesId] from
     * the catalogue for a TV Shows mark; without it, an episode never played drops out of
     * [getSeriesCompletedCounts]'s `seriesId IS NOT NULL` rollup.
     */
    @Query(
        "INSERT INTO watch_state (providerId, profileId, itemId, contentType, itemName, categoryId, positionMs, " +
            "durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId) " +
            "VALUES (:providerId, :profileId, :itemId, :contentType, '', '', 0, 0, 1, :now, NULL, :seriesId, :episodeId) " +
            "ON CONFLICT(providerId, profileId, itemId, contentType) DO UPDATE SET " +
            "isCompleted = 1, updatedAt = excluded.updatedAt",
    )
    suspend fun markWatched(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        now: Long,
        seriesId: String? = null,
        episodeId: String? = null,
    )

    /**
     * Manual mark unwatched for this one row (Phase 6); no-op without a row. The TMDB sibling group
     * (Phase 5) is cleared by a separate call in [MediaRepository.setWatched]: this DAO has no
     * catalogue to join.
     */
    @Query(
        "UPDATE watch_state SET isCompleted = 0, updatedAt = :now " +
            "WHERE providerId = :providerId AND profileId = :profileId AND itemId = :itemId AND contentType = :contentType",
    )
    suspend fun markUnwatched(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        now: Long,
    )

    /**
     * Clears lastPlayedAt, which takes rows out of Recent: one item (film, channel or lone episode)
     * when [seriesId] is its itemId, or every episode of a series for a real series id. `seriesId`
     * is null on rows that are not series, so one query serves both.
     */
    @Query(
        "UPDATE watch_state SET lastPlayedAt = NULL, updatedAt = :now " +
            "WHERE providerId = :providerId AND profileId = :profileId AND (seriesId = :seriesId OR itemId = :seriesId) AND contentType = :contentType",
    )
    suspend fun clearRecentSeries(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
        now: Long,
    )

    /** The rows [clearRecentSeries] takes out of Recent, each with the `lastPlayedAt` it had. */
    @Query(
        "SELECT itemId, lastPlayedAt FROM watch_state " +
            "WHERE providerId = :providerId AND profileId = :profileId AND (seriesId = :seriesId OR itemId = :seriesId) " +
            "AND contentType = :contentType AND lastPlayedAt IS NOT NULL",
    )
    suspend fun getRecentSeriesPlays(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
    ): List<RecentPlay>

    /**
     * [clearRecentSeries], returning what it cleared, so an Undo can put it back with
     * [restoreRecentPlays] (the tv recents favorites plan → A).
     */
    @Transaction
    suspend fun takeRecentSeries(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
        now: Long,
    ): List<RecentPlay> {
        val plays = getRecentSeriesPlays(providerId, profileId, seriesId, contentType)
        clearRecentSeries(providerId, profileId, seriesId, contentType, now)
        return plays
    }

    /**
     * Puts one row back in Recent at the time it was last played. A row played again since its
     * removal keeps its newer time. `updatedAt` moves to [now] so live sync carries the change.
     */
    @Query(
        "UPDATE watch_state SET lastPlayedAt = :lastPlayedAt, updatedAt = :now " +
            "WHERE providerId = :providerId AND profileId = :profileId AND itemId = :itemId AND contentType = :contentType " +
            "AND lastPlayedAt IS NULL",
    )
    suspend fun restoreLastPlayedAt(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
        lastPlayedAt: Long,
        now: Long,
    )

    /** Undo of [takeRecentSeries]: every row it cleared, in one transaction. */
    @Transaction
    suspend fun restoreRecentPlays(
        providerId: Long,
        profileId: String,
        contentType: String,
        plays: List<RecentPlay>,
        now: Long,
    ) {
        plays.forEach { restoreLastPlayedAt(providerId, profileId, it.itemId, contentType, it.lastPlayedAt, now) }
    }

    /** Clear Recent for one content type: every row leaves the Recent list, progress and completion stay. */
    @Query(
        "UPDATE watch_state SET lastPlayedAt = NULL, updatedAt = :now " +
            "WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType AND lastPlayedAt IS NOT NULL",
    )
    suspend fun clearRecent(
        providerId: Long,
        profileId: String,
        contentType: String,
        now: Long,
    )

    /** Tier 2: every row for the content type, uncapped. Position/completion are stream attributes, not history. */
    @Query("SELECT * FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType")
    suspend fun getByContentType(
        providerId: Long,
        profileId: String,
        contentType: String,
    ): List<WatchStateEntity>

    /** Tier 1: Recent row for Movies/Live TV — plain recency, capped. */
    @Query(
        "SELECT * FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType " +
            "AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    suspend fun getRecent(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity>

    /** Tier 1: Recent row for TV Shows — one card per series, collapsed before the limit. */
    @androidx.room.RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM (" +
            "SELECT *, ROW_NUMBER() OVER (" +
            "PARTITION BY COALESCE(seriesId, itemId) ORDER BY lastPlayedAt DESC, itemId DESC" +
            ") AS rn FROM watch_state " +
            "WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType AND lastPlayedAt IS NOT NULL" +
            ") WHERE rn = 1 ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    suspend fun getRecentSeriesCollapsed(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity>

    /**
     * "Jump Back In" shelf, Movies half: in-progress rows only, by the rule of
     * `WatchedItem.resumeProgress` ([RESUMABLE_SQL]), in SQL so the LIMIT lands on the right rows.
     */
    @Query(
        "SELECT * FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType " +
            "AND lastPlayedAt IS NOT NULL AND isCompleted = 0 AND " + RESUMABLE_SQL + " " +
            "ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    suspend fun getResumable(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity>

    /**
     * "Jump Back In" shelf, TV Shows half — same series-collapse as [getRecentSeriesCollapsed],
     * but the resumable-band filter applies before partitioning: a series whose most recently
     * played episode was already finished (and no other episode is mid-watch) has nothing to
     * resume, so it doesn't appear here even though it would in the plain Recent row.
     */
    @androidx.room.RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT * FROM (" +
            "SELECT *, ROW_NUMBER() OVER (" +
            "PARTITION BY COALESCE(seriesId, itemId) ORDER BY lastPlayedAt DESC, itemId DESC" +
            ") AS rn FROM watch_state " +
            "WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType AND lastPlayedAt IS NOT NULL " +
            "AND isCompleted = 0 AND " + RESUMABLE_SQL +
            ") WHERE rn = 1 ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    suspend fun getResumableSeriesCollapsed(
        providerId: Long,
        profileId: String,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity>

    @Query(
        "SELECT seriesId, COUNT(DISTINCT COALESCE(episodeId, itemId)) AS completed " +
            "FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType " +
            "AND isCompleted = 1 AND seriesId IS NOT NULL GROUP BY seriesId",
    )
    suspend fun getSeriesCompletedCounts(
        providerId: Long,
        profileId: String,
        contentType: String,
    ): List<SeriesCompletedCount>

    @Query(
        "SELECT * FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND itemId = :itemId AND contentType = :contentType",
    )
    suspend fun getItem(
        providerId: Long,
        profileId: String,
        itemId: String,
        contentType: String,
    ): WatchStateEntity?

    /**
     * Track fallback for an episode with no saved choice: the series' most recently changed row
     * with a track choice, by the `(providerId, profileId, seriesId)` index. Rows with both indices
     * null are left out, so a series with no choice returns nothing.
     */
    @Query(
        "SELECT * FROM watch_state WHERE providerId = :providerId AND profileId = :profileId AND seriesId = :seriesId AND " +
            "contentType = :contentType AND (audioTrackIndex IS NOT NULL OR subtitleTrackIndex IS NOT NULL) " +
            "ORDER BY updatedAt DESC LIMIT 1",
    )
    suspend fun getLatestSeriesTrackPrefs(
        providerId: Long,
        profileId: String,
        seriesId: String,
        contentType: String,
    ): WatchStateEntity?

    /** Every row for this provider and profile, all content types, unbounded. See `getWatchHistory` in MediaRepository. */
    @Query("SELECT * FROM watch_state WHERE providerId = :providerId AND profileId = :profileId")
    suspend fun getAll(
        providerId: Long,
        profileId: String,
    ): List<WatchStateEntity>

    /** Every row for this provider across every profile — copying a provider copies everyone's history. */
    @Query("SELECT * FROM watch_state WHERE providerId = :providerId")
    suspend fun getAllProfiles(providerId: Long): List<WatchStateEntity>

    /** Backs "Clear watch history", which clears only the profile using the app. */
    @Query("DELETE FROM watch_state WHERE providerId = :providerId AND profileId = :profileId")
    suspend fun deleteAll(
        providerId: Long,
        profileId: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SyncTombstoneEntity)

    /**
     * [deleteAll], recording a `watch_clear` marker for live sync: every watch row for this
     * provider and profile older than [clearedAt] is dropped on the other devices too. 0 (the
     * default) means "now on the sync clock", stamped by [XtreamSyncTriggers].
     */
    @Transaction
    suspend fun deleteAllRecordingClear(
        providerId: Long,
        profileId: String,
        clearedAt: Long = 0,
    ) {
        deleteAll(providerId, profileId)
        insertTombstone(SyncTombstoneEntity(providerId, profileId, SyncKind.WATCH_CLEAR, "", "", clearedAt))
    }

    /** Each profile's latest watch change on this device: how recently it was used. */
    @Query("SELECT profileId, MAX(updatedAt) AS lastAt FROM watch_state GROUP BY profileId")
    suspend fun lastChangeByProfile(): List<ProfileLastChange>

    /** Profile deletion: that profile's rows across every provider. */
    @Query("DELETE FROM watch_state WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    /** Provider deletion: every profile's rows go with it. */
    @Query("DELETE FROM watch_state WHERE providerId = :providerId")
    suspend fun deleteAllProfiles(providerId: Long)

    /**
     * Bulk restore from a settings import: the whole row is known, so a plain replace is correct,
     * unlike the production writers, which know only part of a row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(entities: List<WatchStateEntity>)
}

/** A row's place in Recent: what [WatchStateDao.takeRecentSeries] clears and an Undo restores. */
data class RecentPlay(
    val itemId: String,
    val lastPlayedAt: Long,
)

data class SeriesCompletedCount(
    val seriesId: String,
    val completed: Int,
)

/** A profile and its latest watch change ([WatchStateDao.lastChangeByProfile]). */
data class ProfileLastChange(
    val profileId: String,
    val lastAt: Long,
)
