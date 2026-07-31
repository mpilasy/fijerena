package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface XtreamStreamDao {
    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type AND categoryId = :categoryId AND excluded = 0 ORDER BY num ASC")
    fun getStreamsByCategory(
        providerId: Long,
        type: String,
        categoryId: String,
    ): List<XtreamStreamEntity>

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type AND excluded = 0 ORDER BY num ASC")
    fun getAllStreams(
        providerId: Long,
        type: String,
    ): List<XtreamStreamEntity>

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND type = :type ORDER BY num ASC")
    fun getAllStreamsIncludingExcluded(
        providerId: Long,
        type: String,
    ): List<XtreamStreamEntity>

    @Query("UPDATE xtream_streams SET excluded = COALESCE((SELECT c.excluded FROM xtream_categories c WHERE c.categoryId = xtream_streams.categoryId AND c.providerId = xtream_streams.providerId AND c.type = :type), 0) WHERE providerId = :providerId AND type = :type")
    fun syncExcludedFromCategories(
        providerId: Long,
        type: String,
    )

    @Query("SELECT * FROM xtream_streams WHERE providerId = :providerId AND streamId = :streamId LIMIT 1")
    fun getStreamById(
        providerId: Long,
        streamId: Int,
    ): XtreamStreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(streams: List<XtreamStreamEntity>)

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun deleteAll(
        providerId: Long,
        type: String,
    )

    @Query("SELECT streamId FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun getStreamIds(
        providerId: Long,
        type: String,
    ): List<Int>

    @Query("SELECT streamId, contentHash FROM xtream_streams WHERE providerId = :providerId AND type = :type")
    fun getStreamHashes(
        providerId: Long,
        type: String,
    ): Map<
        @MapColumn(columnName = "streamId")
        Int,
        @MapColumn(columnName = "contentHash")
        Int,
    >

    @Query("SELECT streamId, streamIcon FROM xtream_streams WHERE providerId = :providerId AND type = :type AND streamId IN (:ids) AND streamIcon IS NOT NULL")
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

    @Query("DELETE FROM xtream_streams WHERE providerId = :providerId AND type = :type AND streamId IN (:ids)")
    fun deleteByIds(
        providerId: Long,
        type: String,
        ids: List<Int>,
    )

    @Query("""
        SELECT s.* FROM xtream_streams s
        LEFT JOIN xtream_categories c ON s.categoryId = c.categoryId AND s.providerId = c.providerId AND c.type = :type
        WHERE (
            s.rowid IN (
                SELECT docid FROM xtream_streams_fts WHERE xtream_streams_fts MATCH :query
            )
            OR (c.categoryName LIKE :categoryQuery)
        )
        AND s.providerId = :providerId AND s.type = :type
        AND (s.excluded = 0 OR :includeExcluded = 1)
        LIMIT 200
    """)
    fun searchByFts(
        providerId: Long,
        type: String,
        query: String,
        categoryQuery: String,
        includeExcluded: Boolean,
    ): List<XtreamStreamEntity>

    @Query("INSERT INTO xtream_streams_fts(xtream_streams_fts) VALUES('rebuild')")
    fun rebuildFts()

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
            detailFetchedAt = :detailFetchedAt
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
    )
}
