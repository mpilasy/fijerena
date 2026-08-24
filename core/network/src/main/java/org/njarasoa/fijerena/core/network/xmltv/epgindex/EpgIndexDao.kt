package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EpgIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannelsIgnore(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannelsStagingIgnore(channels: List<EpgChannelStagingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammes(programmes: List<EpgProgrammeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammesStaging(programmes: List<EpgProgrammeStagingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: EpgIndexMetadata)

    // --------------- Staging management & Atomic Swap ---------------

    @Query("DELETE FROM epg_programme_staging")
    suspend fun clearStagingProgrammes()

    @Query("DELETE FROM epg_channel_staging")
    suspend fun clearStagingChannels()

    @Transaction
    suspend fun clearStaging() {
        clearStagingProgrammes()
        clearStagingChannels()
    }

    @Query("INSERT INTO epg_channel (xmltv_id, display_name, icon_url, source_id) SELECT xmltv_id, display_name, icon_url, source_id FROM epg_channel_staging")
    suspend fun transferChannelsFromStaging()

    @Query("INSERT INTO epg_programme (channel_id, title, title_lowercase, description, category, start_epoch, end_epoch, source_id) SELECT channel_id, title, title_lowercase, description, category, start_epoch, end_epoch, source_id FROM epg_programme_staging")
    suspend fun transferProgrammesFromStaging()

    /**
     * Performs the atomic swap:
     * 1. Deletes existing data for the specified sources in the primary tables.
     * 2. Moves everything from staging to primary.
     * 3. Clears staging.
     */
    @Transaction
    suspend fun executeSwap(sourceIds: List<Long>) {
        deleteBySourceIds(sourceIds)
        transferChannelsFromStaging()
        transferProgrammesFromStaging()
        clearStaging()
    }

    // --------------- Stale data cleanup (Clear and Load strategy) ---------------

    @Query("DELETE FROM epg_programme WHERE end_epoch < :cutoffEpoch")
    suspend fun deleteStaleProgrammes(cutoffEpoch: Long)

    @Query("SELECT COUNT(*) FROM epg_programme WHERE end_epoch < :cutoffEpoch")
    suspend fun countStaleProgrammes(cutoffEpoch: Long): Int

    // --------------- Search queries ---------------

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM (
            SELECT p2.*
            FROM epg_programme p2
            INNER JOIN epg_programme_fts fts ON fts.rowid = p2.id
            WHERE epg_programme_fts MATCH :query
              AND p2.source_id IN (:sourceIds)
              AND p2.end_epoch > :windowStart AND p2.start_epoch <= :windowEnd
            ORDER BY p2.start_epoch ASC
            LIMIT :maxResults
        ) p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id AND c.source_id = p.source_id
        ORDER BY p.start_epoch ASC
        """,
    )
    suspend fun searchByTitleFts(
        query: String,
        sourceIds: List<Long>,
        windowStart: Long,
        windowEnd: Long,
        maxResults: Int = 500,
    ): List<EpgSearchResultRow>

    // --------------- Paged queries for large datasets (2M+ rows) ---------------

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id AND c.source_id = p.source_id
        WHERE p.source_id IN (:sourceIds)
          AND p.start_epoch <= :nowEpoch AND p.end_epoch > :nowEpoch
        ORDER BY c.display_name ASC
        """,
    )
    fun getPagedNowPlaying(nowEpoch: Long, sourceIds: List<Long>): PagingSource<Int, EpgSearchResultRow>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_programme_fts fts ON fts.rowid = p.id
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id AND c.source_id = p.source_id
        WHERE epg_programme_fts MATCH :query
          AND p.source_id IN (:sourceIds)
          AND p.end_epoch > :windowStart AND p.start_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        """,
    )
    fun searchByTitleFtsPaged(
        query: String,
        sourceIds: List<Long>,
        windowStart: Long,
        windowEnd: Long,
    ): PagingSource<Int, EpgSearchResultRow>

    // --------------- Channel queries ---------------

    @Query(
        """
        SELECT * FROM epg_channel
        WHERE LOWER(display_name) LIKE '%' || :queryLower || '%'
          AND source_id IN (:sourceIds)
        ORDER BY display_name ASC
        """,
    )
    suspend fun searchChannelsByName(queryLower: String, sourceIds: List<Long>): List<EpgChannelEntity>

    @Query("SELECT * FROM epg_channel WHERE source_id IN (:sourceIds)")
    suspend fun getChannelsForSources(sourceIds: List<Long>): List<EpgChannelEntity>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id AND c.source_id = p.source_id
        WHERE p.channel_id IN (:channelIds)
          AND p.end_epoch > :windowStart AND p.start_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        """,
    )
    suspend fun getProgrammesForChannels(
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long,
    ): List<EpgSearchResultRow>

    // --------------- Now Playing for specific channels ---------------

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id AND c.source_id = p.source_id
        WHERE p.channel_id IN (:channelIds)
          AND p.start_epoch <= :nowEpoch AND p.end_epoch > :nowEpoch
        """,
    )
    suspend fun getNowPlayingForChannels(
        channelIds: List<String>,
        nowEpoch: Long,
    ): List<EpgSearchResultRow>

    // --------------- Source-scoped cleanup ---------------

    @Query("DELETE FROM epg_programme WHERE source_id = :sourceId")
    suspend fun deleteProgrammesBySourceId(sourceId: Long)

    @Query("DELETE FROM epg_programme WHERE source_id IN (:sourceIds)")
    suspend fun deleteProgrammesBySourceIds(sourceIds: List<Long>)

    @Query("DELETE FROM epg_channel WHERE source_id = :sourceId")
    suspend fun deleteChannelsBySourceId(sourceId: Long)

    @Query("DELETE FROM epg_channel WHERE source_id IN (:sourceIds)")
    suspend fun deleteChannelsBySourceIds(sourceIds: List<Long>)

    @Transaction
    suspend fun deleteBySourceId(sourceId: Long) {
        deleteProgrammesBySourceId(sourceId)
        deleteChannelsBySourceId(sourceId)
    }

    @Transaction
    suspend fun deleteBySourceIds(sourceIds: List<Long>) {
        deleteProgrammesBySourceIds(sourceIds)
        deleteChannelsBySourceIds(sourceIds)
    }

    // --------------- Metadata & cleanup ---------------

    @Query("SELECT * FROM epg_index_metadata WHERE id = 1")
    suspend fun getMetadata(): EpgIndexMetadata?

    @Query("SELECT COUNT(*) FROM epg_programme")
    suspend fun getProgrammeCount(): Int

    @Query("SELECT COUNT(*) FROM epg_channel")
    suspend fun getChannelCount(): Int

    @Query("SELECT MAX(end_epoch) FROM epg_programme WHERE source_id = :sourceId")
    suspend fun getLatestProgrammeEndTimeForSource(sourceId: Long): Long?
}
