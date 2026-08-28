package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * See `plans/watch-state-durable-storage-plan.md`. Statements mirror the plan's Write path /
 * read-query sections exactly rather than re-deriving SQL at each call site.
 */
@Dao
interface WatchStateDao {
    /**
     * Playback progress upsert. Owns position, duration, completion and `lastPlayedAt`; leaves
     * metadata columns alone when this call doesn't carry them via `COALESCE`.
     *
     * `isCompleted` is sticky since Phase 6: `MAX` can only raise it, never lower it, so playing
     * two minutes of a film already marked watched can't silently clear the check — only
     * `setWatched(false)` does that. Safe because Phase 6 also adds the manual escape hatch; before
     * it existed a title that crossed 95% by accident could never be un-marked.
     */
    @Query(
        "INSERT INTO watch_state (providerId, itemId, contentType, itemName, categoryId, positionMs, " +
            "durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId, seriesName, " +
            "episodeExtension, audioTrackIndex, subtitleTrackIndex) " +
            "VALUES (:providerId, :itemId, :contentType, :itemName, :categoryId, :positionMs, :durationMs, " +
            ":isCompleted, :now, :now, :seriesId, :episodeId, :seriesName, :episodeExtension, " +
            ":audioTrackIndex, :subtitleTrackIndex) " +
            "ON CONFLICT(providerId, itemId, contentType) DO UPDATE SET " +
            "positionMs = excluded.positionMs, " +
            "durationMs = excluded.durationMs, " +
            "isCompleted = MAX(watch_state.isCompleted, excluded.isCompleted), " +
            "updatedAt = excluded.updatedAt, " +
            "lastPlayedAt = excluded.lastPlayedAt, " +
            "itemName = COALESCE(excluded.itemName, watch_state.itemName), " +
            "seriesId = COALESCE(excluded.seriesId, watch_state.seriesId), " +
            "episodeId = COALESCE(excluded.episodeId, watch_state.episodeId), " +
            "seriesName = COALESCE(excluded.seriesName, watch_state.seriesName), " +
            "episodeExtension = COALESCE(excluded.episodeExtension, watch_state.episodeExtension), " +
            "audioTrackIndex = COALESCE(excluded.audioTrackIndex, watch_state.audioTrackIndex), " +
            "subtitleTrackIndex = COALESCE(excluded.subtitleTrackIndex, watch_state.subtitleTrackIndex)",
    )
    fun upsertProgress(
        providerId: Long,
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
        "INSERT INTO watch_state (providerId, itemId, contentType, itemName, categoryId, positionMs, " +
            "durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId, seriesName, " +
            "episodeExtension, audioTrackIndex, subtitleTrackIndex) " +
            "VALUES (:providerId, :itemId, :contentType, :itemName, :categoryId, 0, 0, 0, :now, :now, " +
            ":seriesId, :episodeId, :seriesName, :episodeExtension, :audioTrackIndex, :subtitleTrackIndex) " +
            "ON CONFLICT(providerId, itemId, contentType) DO UPDATE SET " +
            "updatedAt = excluded.updatedAt, " +
            "lastPlayedAt = excluded.lastPlayedAt, " +
            "itemName = COALESCE(excluded.itemName, watch_state.itemName), " +
            "seriesId = COALESCE(excluded.seriesId, watch_state.seriesId), " +
            "episodeId = COALESCE(excluded.episodeId, watch_state.episodeId), " +
            "seriesName = COALESCE(excluded.seriesName, watch_state.seriesName), " +
            "episodeExtension = COALESCE(excluded.episodeExtension, watch_state.episodeExtension), " +
            "audioTrackIndex = COALESCE(excluded.audioTrackIndex, watch_state.audioTrackIndex), " +
            "subtitleTrackIndex = COALESCE(excluded.subtitleTrackIndex, watch_state.subtitleTrackIndex)",
    )
    fun upsertRecency(
        providerId: Long,
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
     * Manual mark watched (Phase 6). Insert path supplies `positionMs`/`durationMs` = 0 and
     * `lastPlayedAt` = NULL, same discipline as [upsertRecency] — a manual mark is not a play, so
     * it must not enter the Recent row (filtered on `lastPlayedAt IS NOT NULL`). Update path
     * touches only `isCompleted`/`updatedAt`, leaving position, duration, `lastPlayedAt` and
     * metadata exactly as playback left them, so a later `setWatched(false)` brings the resume bar
     * back where it was rather than erasing it.
     *
     * [seriesId]/[episodeId] are insert-only, same as everything else here — an episode with no
     * existing row has none to carry forward on conflict. `MediaRepository.setWatched` resolves
     * [seriesId] from the catalogue before calling this for a TV Shows mark; without it, a
     * never-played episode marked watched directly would silently drop out of
     * [getSeriesCompletedCounts]'s `seriesId IS NOT NULL` rollup.
     */
    @Query(
        "INSERT INTO watch_state (providerId, itemId, contentType, itemName, categoryId, positionMs, " +
            "durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId) " +
            "VALUES (:providerId, :itemId, :contentType, '', '', 0, 0, 1, :now, NULL, :seriesId, :episodeId) " +
            "ON CONFLICT(providerId, itemId, contentType) DO UPDATE SET " +
            "isCompleted = 1, updatedAt = excluded.updatedAt",
    )
    suspend fun markWatched(
        providerId: Long,
        itemId: String,
        contentType: String,
        now: Long,
        seriesId: String? = null,
        episodeId: String? = null,
    )

    /**
     * Manual mark unwatched for this one row (Phase 6). A no-op if the row doesn't exist — nothing
     * to unmark. Spreading this across a completed TMDB sibling group (Phase 5) is a separate,
     * Xtream-specific call from [MediaRepository.setWatched]; this DAO has no catalogue to join.
     */
    @Query(
        "UPDATE watch_state SET isCompleted = 0, updatedAt = :now " +
            "WHERE providerId = :providerId AND itemId = :itemId AND contentType = :contentType",
    )
    suspend fun markUnwatched(
        providerId: Long,
        itemId: String,
        contentType: String,
        now: Long,
    )

    /** Tier 2: every row for the content type, uncapped. Position/completion are stream attributes, not history. */
    @Query("SELECT * FROM watch_state WHERE providerId = :providerId AND contentType = :contentType")
    suspend fun getByContentType(
        providerId: Long,
        contentType: String,
    ): List<WatchStateEntity>

    /** Tier 1: Recent row for Movies/Live TV — plain recency, capped. */
    @Query(
        "SELECT * FROM watch_state WHERE providerId = :providerId AND contentType = :contentType " +
            "AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    suspend fun getRecent(
        providerId: Long,
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
            "WHERE providerId = :providerId AND contentType = :contentType AND lastPlayedAt IS NOT NULL" +
            ") WHERE rn = 1 ORDER BY lastPlayedAt DESC LIMIT :limit",
    )
    suspend fun getRecentSeriesCollapsed(
        providerId: Long,
        contentType: String,
        limit: Int,
    ): List<WatchStateEntity>

    @Query(
        "SELECT seriesId, COUNT(DISTINCT COALESCE(episodeId, itemId)) AS completed " +
            "FROM watch_state WHERE providerId = :providerId AND contentType = :contentType " +
            "AND isCompleted = 1 AND seriesId IS NOT NULL GROUP BY seriesId",
    )
    suspend fun getSeriesCompletedCounts(
        providerId: Long,
        contentType: String,
    ): List<SeriesCompletedCount>

    /** Single-item lookup, replacing the blob's O(1) `(itemId, contentType)` map hit. */
    @Query("SELECT * FROM watch_state WHERE providerId = :providerId AND itemId = :itemId AND contentType = :contentType")
    suspend fun getItem(
        providerId: Long,
        itemId: String,
        contentType: String,
    ): WatchStateEntity?

    /** Every row for this provider, all content types, unbounded. See `getWatchHistory` in MediaRepository. */
    @Query("SELECT * FROM watch_state WHERE providerId = :providerId")
    suspend fun getAll(providerId: Long): List<WatchStateEntity>

    @Query("DELETE FROM watch_state WHERE providerId = :providerId")
    suspend fun deleteAll(providerId: Long)

    /**
     * Bulk restore from a settings-export import: the whole row is known and authoritative, so a
     * straight replace is correct here — unlike the production writers, which only ever know part
     * of a row and use the field-targeted upserts above instead.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(entities: List<WatchStateEntity>)
}

data class SeriesCompletedCount(
    val seriesId: String,
    val completed: Int,
)
