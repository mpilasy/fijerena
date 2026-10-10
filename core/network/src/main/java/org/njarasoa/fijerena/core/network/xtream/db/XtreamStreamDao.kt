package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Bound parameters per statement: SQLite before 3.32 (minSdk 30 ships 3.28) allows 999. */
internal const val SQLITE_MAX_VARIABLES = 900

/**
 * A stream is hidden by category filters when its category is: only `xtream_categories` carries
 * the `excluded` flag (see XtreamCategoryExclusionSync). Queries that hide them filter with
 * `categoryId NOT IN (… excluded categories …)`, looked up once per query — measured within ~10%
 * of the old per-row flag on a 180k-movie catalogue. `xtream_streams.excluded` is unused since
 * schema v24. See docs/plans/archive/20261001_fast-profile-switch-plan.md.
 */
@Dao
interface XtreamStreamDao {
    @Query(
        "SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type AND categoryId = :categoryId AND categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 1) ORDER BY num ASC",
    )
    fun getStreamsByCategory(
        providerId: Long,
        type: String,
        categoryId: String,
    ): List<XtreamStreamEntity>

    @Query(
        "SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type AND categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 1) ORDER BY num ASC",
    )
    fun getAllStreams(
        providerId: Long,
        type: String,
    ): List<XtreamStreamEntity>

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type ORDER BY num ASC")
    fun getAllStreamsIncludingExcluded(
        providerId: Long,
        type: String,
    ): List<XtreamStreamEntity>

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND streamId = :streamId LIMIT 1")
    fun getStreamById(
        providerId: Long,
        streamId: Int,
    ): XtreamStreamEntity?

    /**
     * Other VOD entries sharing [tmdbId], for the "other instances of this title" picker on the
     * detail screen. Only ever finds what's already in the local catalogue with tmdbId cached —
     * tmdbId is populated per row from its own detail fetch, so this misses instances the user
     * hasn't opened yet. That's fine: the picker simply doesn't show when nothing turns up.
     */
    @Query(
        "SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type " +
            "AND tmdbId = :tmdbId AND streamId != :excludeStreamId AND categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 1) ORDER BY name ASC",
    )
    fun getByTmdbId(
        providerId: Long,
        type: String,
        tmdbId: String,
        excludeStreamId: Int,
    ): List<XtreamStreamEntity>

    /**
     * Phase 5 dedup (docs/plans/archive/20260828_watch-state-durable-storage-plan.md): item ids in this content type
     * completed by a TMDB sibling — a different catalogue entry for the same title (five language
     * variants, a 4K re-rip) whose own `watch_state` row is completed. `watch_state` never stores
     * a `tmdbId` itself (see "No tmdbId column" in the plan), so this reaches it by joining back
     * to the catalogue. `c.type = :streamType` keeps a completed movie from ever matching a live
     * channel — `xtream_streams` is keyed `(streamId, providerId, type)` with a panel's own type
     * string, while `w.contentType` uses this app's domain constants, so the two must be passed
     * and compared separately. `GROUP BY c.tmdbId` collapses the sibling set before the outer
     * join, so one watched variant can't multiply-match and duplicate a row in the result.
     * The caller unions the result into the `watched` set it already builds from
     * [getPlaybackPositions][org.njarasoa.fijerena.core.network.MediaRepository.getPlaybackPositions].
     */
    @Query(
        "SELECT CAST(s.streamId AS TEXT) AS itemId " +
            "FROM xtream_streams s " +
            "JOIN (" +
            "SELECT c.tmdbId AS tmdbId " +
            "FROM watch_state w " +
            "JOIN xtream_streams c " +
            "ON c.providerId = w.providerId AND c.type = :streamType AND CAST(c.streamId AS TEXT) = w.itemId " +
            "WHERE w.providerId = :providerId AND w.profileId = :profileId AND w.contentType = :contentType " +
            "AND w.isCompleted = 1 " +
            "AND c.tmdbId IS NOT NULL " +
            "GROUP BY c.tmdbId" +
            ") done ON s.tmdbId = done.tmdbId " +
            "WHERE s.providerId = :providerId AND s.type = :streamType " +
            "AND s.categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :streamType AND excluded = 1)",
    )
    suspend fun getSiblingCompletedStreamIds(
        providerId: Long,
        profileId: String,
        contentType: String,
        streamType: String,
    ): List<String>

    /**
     * Phase 6 unwatched: clears completion on every `watch_state` row sharing [itemId]'s `tmdbId`
     * within this content type — the other half of Phase 5's dedup, since marking one variant
     * unwatched must not leave a sibling's completion still driving the checkmark. `tmdbId IS NOT
     * NULL` is explicit and load-bearing even though SQL's `= NULL` semantics already prevent a
     * null-tmdb item from matching: an item with no tmdbId at all must clear only itself.
     */
    @Query(
        "UPDATE watch_state SET isCompleted = 0, updatedAt = :now " +
            "WHERE providerId = :providerId AND profileId = :profileId AND contentType = :contentType " +
            "AND itemId IN (" +
            "SELECT CAST(c.streamId AS TEXT) FROM xtream_streams c " +
            "WHERE c.providerId = :providerId AND c.type = :streamType AND c.tmdbId IS NOT NULL " +
            "AND c.tmdbId = (" +
            "SELECT tmdbId FROM xtream_streams " +
            "WHERE providerId = :providerId AND type = :streamType AND streamId = CAST(:itemId AS INTEGER)" +
            ")" +
            ")",
    )
    suspend fun clearGroupCompletion(
        providerId: Long,
        profileId: String,
        contentType: String,
        streamType: String,
        itemId: String,
        now: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(streams: List<XtreamStreamEntity>)

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun deleteAll(
        providerId: Long,
        type: String,
    )

    /** Whether the catalogue holds any stream of [type] for [providerId] — an index probe, no count. */
    @Query("SELECT EXISTS(SELECT 1 FROM xtream_streams WHERE providerId = :providerId AND type = :type)")
    fun hasStreams(
        providerId: Long,
        type: String,
    ): Boolean

    @Query("SELECT streamId FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun getStreamIds(
        providerId: Long,
        type: String,
    ): List<Int>

    @Query("SELECT streamId, num, contentHash FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun getStreamSyncState(
        providerId: Long,
        type: String,
    ): List<XtreamStreamSyncState>

    @Query("UPDATE xtream_streams SET num = :num WHERE streamId = :streamId AND providerId = :providerId AND type = :type")
    fun updateNum(
        providerId: Long,
        type: String,
        streamId: Int,
        num: Int,
    )

    /** New list positions (streamId to num) for rows whose content didn't change; see [XtreamStreamEntity.computeHash]. */
    @Transaction
    fun updateNums(
        providerId: Long,
        type: String,
        nums: Map<Int, Int>,
    ) {
        nums.forEach { (streamId, num) -> updateNum(providerId, type, streamId, num) }
    }

    @Query(
        "SELECT streamId, contentRating, tmdbId, containerExtension, detailFetchedAt, posterPath FROM xtream_streams " +
            "WHERE providerId = :providerId AND type = :type AND streamId IN (:ids)",
    )
    fun getDetailCaches(
        providerId: Long,
        type: String,
        ids: List<Int>,
    ): List<XtreamStreamDetailCache>

    /**
     * [insertAll] for a catalogue sync: rows that already exist keep their detail-screen columns
     * (TMDB rating, poster, container extension, fetch time) instead of being reset by REPLACE.
     */
    @Transaction
    fun insertKeepingDetailCache(
        providerId: Long,
        type: String,
        streams: List<XtreamStreamEntity>,
    ) {
        val caches =
            streams
                .map { it.streamId }
                .chunked(SQLITE_MAX_VARIABLES)
                .flatMap { getDetailCaches(providerId, type, it) }
                .associateBy { it.streamId }
        insertAll(streams.map { it.withDetailCache(caches[it.streamId]) })
    }

    @Query(
        "SELECT streamId, streamIcon FROM xtream_streams WHERE providerId = :providerId AND type = :type AND streamId IN (:ids) AND streamIcon IS NOT NULL",
    )
    fun getIconsByIds(
        providerId: Long,
        type: String,
        ids: List<Int>,
    ): Map<
        @MapColumn(columnName = "streamId")
        Int,
        @MapColumn(columnName = "streamIcon")
        String,
    >

    /** Archive days of the channels among [ids] that have an archive (`tv_archive` = 1). */
    @Query(
        "SELECT streamId, tvArchiveDuration FROM xtream_streams WHERE providerId = :providerId AND type = 'LIVE' AND streamId IN (:ids) AND tvArchive = 1 AND tvArchiveDuration > 0",
    )
    fun getArchiveDaysByIds(
        providerId: Long,
        ids: List<Int>,
    ): Map<
        @MapColumn(columnName = "streamId")
        Int,
        @MapColumn(columnName = "tvArchiveDuration")
        Int,
    >

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type AND streamId IN (:ids)")
    fun deleteByIds(
        providerId: Long,
        type: String,
        ids: List<Int>,
    )

    @Query(
        """
        SELECT s.* FROM xtream_streams s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_streams_fts WHERE xtream_streams_fts MATCH :query
        )
        AND s.providerId = :providerId AND s.type = :type
        AND (:includeExcluded = 1 OR s.categoryId NOT IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 1))
        LIMIT 200
    """,
    )
    fun searchByFts(
        providerId: Long,
        type: String,
        query: String,
        includeExcluded: Boolean,
    ): List<XtreamStreamEntity>

    /**
     * The unary `+` on `s.categoryId` is load-bearing: without it SQLite drives the count from
     * the (providerId, type, categoryId) index and probes every excluded category against every
     * FTS docid — excluded categories × matches index seeks. For a short prefix like "the*" on a
     * 250k-row catalogue that was ~5 s per type on a desktop and tens of seconds on a TV box,
     * all spent before the search results could show.
     */
    @Query(
        """
        SELECT COUNT(*) FROM xtream_streams s
        WHERE s.rowid IN (
            SELECT docid FROM xtream_streams_fts WHERE xtream_streams_fts MATCH :query
        )
        AND s.providerId = :providerId AND s.type = :type
        AND +s.categoryId IN (SELECT categoryId FROM xtream_categories WHERE providerId = :providerId AND type = :type AND excluded = 1)
    """,
    )
    fun countExcludedByFts(
        providerId: Long,
        type: String,
        query: String,
    ): Int

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type AND categoryId = :categoryId")
    fun deleteByCategoryId(
        providerId: Long,
        type: String,
        categoryId: String,
    )

    @Query("SELECT COUNT(*) FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun countStreams(
        providerId: Long,
        type: String,
    ): Int

    @Query("UPDATE xtream_streams SET description = :description WHERE streamId = :streamId AND providerId = :providerId AND type = :type")
    fun updateDescription(
        providerId: Long,
        streamId: Int,
        type: String,
        description: String?,
    )

    @Query(
        """
        UPDATE xtream_streams 
        SET description = :description, 
            cast = :cast, 
            director = :director, 
            genre = :genre, 
            releaseDate = :releaseDate, 
            rating = :rating, 
            duration = :duration, 
            youtubeTrailer = :youtubeTrailer
        WHERE streamId = :streamId AND providerId = :providerId AND type = :type
    """,
    )
    fun updateVodMetadata(
        providerId: Long,
        streamId: Int,
        type: String,
        description: String?,
        cast: String?,
        director: String?,
        genre: String?,
        releaseDate: String?,
        rating: String?,
        duration: String?,
        youtubeTrailer: String?,
    )

    @Query(
        """
        UPDATE xtream_streams
        SET contentRating = :contentRating,
            tmdbId = :tmdbId,
            containerExtension = :containerExtension,
            detailFetchedAt = :detailFetchedAt,
            posterPath = COALESCE(:posterPath, posterPath)
        WHERE streamId = :streamId AND providerId = :providerId AND type = :type
    """,
    )
    fun updateDetailCache(
        providerId: Long,
        streamId: Int,
        type: String,
        contentRating: String?,
        tmdbId: String?,
        containerExtension: String?,
        detailFetchedAt: Long,
        posterPath: String? = null,
    )
}
