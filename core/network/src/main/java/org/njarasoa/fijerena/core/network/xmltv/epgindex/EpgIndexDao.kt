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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammes(programmes: List<EpgProgrammeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: EpgIndexMetadata)

    // --------------- Stale data cleanup (Clear and Load strategy) ---------------

    @Query("DELETE FROM epg_programme WHERE end_epoch < :cutoffEpoch")
    suspend fun deleteStaleProgrammes(cutoffEpoch: Long)

    // --------------- Search queries ---------------

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_programme_fts fts ON fts.rowid = p.id
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE epg_programme_fts MATCH :query
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        LIMIT :maxResults
        """
    )
    suspend fun searchByTitleFts(
        query: String,
        windowStart: Long,
        windowEnd: Long,
        maxResults: Int = 500
    ): List<EpgSearchResultRow>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.title_lowercase LIKE '%' || :queryLower || '%'
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        LIMIT :maxResults
        """
    )
    suspend fun searchByTitleLike(
        queryLower: String,
        windowStart: Long,
        windowEnd: Long,
        maxResults: Int = 500
    ): List<EpgSearchResultRow>

    // --------------- Now Playing query (uses composite start_epoch + end_epoch index) ---------------

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.start_epoch <= :nowEpoch AND p.end_epoch > :nowEpoch
        ORDER BY c.display_name ASC
        """
    )
    suspend fun getNowPlaying(nowEpoch: Long): List<EpgSearchResultRow>

    // --------------- Paged queries for large datasets (2M+ rows) ---------------

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        """
    )
    fun getPagedProgrammes(
        windowStart: Long,
        windowEnd: Long
    ): PagingSource<Int, EpgSearchResultRow>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.start_epoch <= :nowEpoch AND p.end_epoch > :nowEpoch
        ORDER BY c.display_name ASC
        """
    )
    fun getPagedNowPlaying(nowEpoch: Long): PagingSource<Int, EpgSearchResultRow>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.channel_id = :channelId
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        """
    )
    fun getPagedProgrammesForChannel(
        channelId: String,
        windowStart: Long,
        windowEnd: Long
    ): PagingSource<Int, EpgSearchResultRow>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_programme_fts fts ON fts.rowid = p.id
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE epg_programme_fts MATCH :query
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        """
    )
    fun searchByTitleFtsPaged(
        query: String,
        windowStart: Long,
        windowEnd: Long
    ): PagingSource<Int, EpgSearchResultRow>

    // --------------- Channel queries ---------------

    @Query("SELECT * FROM epg_channel")
    suspend fun getAllChannels(): List<EpgChannelEntity>

    @Query(
        """
        SELECT p.*, c.display_name AS channelDisplayName, c.icon_url AS channelIconUrl
        FROM epg_programme p
        INNER JOIN epg_channel c ON c.xmltv_id = p.channel_id
        WHERE p.channel_id IN (:channelIds)
          AND p.start_epoch >= :windowStart AND p.end_epoch <= :windowEnd
        ORDER BY p.start_epoch ASC
        """
    )
    suspend fun getProgrammesForChannels(
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): List<EpgSearchResultRow>

    // --------------- Metadata & cleanup ---------------

    @Query("SELECT * FROM epg_index_metadata WHERE id = 1")
    suspend fun getMetadata(): EpgIndexMetadata?

    @Query("DELETE FROM epg_programme")
    suspend fun deleteAllProgrammes()

    @Query("DELETE FROM epg_channel")
    suspend fun deleteAllChannels()

    @Query("DELETE FROM epg_index_metadata")
    suspend fun deleteAllMetadata()

    @Query("SELECT COUNT(*) FROM epg_programme")
    suspend fun getProgrammeCount(): Int

    @Query("SELECT COUNT(*) FROM epg_channel")
    suspend fun getChannelCount(): Int

    // --------------- Transactional ingestion ---------------

    @Transaction
    suspend fun replaceAllData(
        channels: List<EpgChannelEntity>,
        programmes: List<EpgProgrammeEntity>,
        metadata: EpgIndexMetadata
    ) {
        deleteAllProgrammes()
        deleteAllChannels()
        deleteAllMetadata()
        insertChannels(channels)
        insertProgrammes(programmes)
        insertMetadata(metadata)
    }

    @Transaction
    suspend fun clearAndLoadBatch(
        channels: List<EpgChannelEntity>,
        programmes: List<EpgProgrammeEntity>,
        staleCutoffEpoch: Long,
        metadata: EpgIndexMetadata
    ) {
        deleteStaleProgrammes(staleCutoffEpoch)
        insertChannels(channels)
        insertProgrammes(programmes)
        insertMetadata(metadata)
    }
}
