package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpgIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Insert
    suspend fun insertProgrammes(programmes: List<EpgProgrammeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: EpgIndexMetadata)

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

    @Query("SELECT * FROM epg_index_metadata WHERE id = 1")
    suspend fun getMetadata(): EpgIndexMetadata?

    @Query("DELETE FROM epg_programme")
    suspend fun deleteAllProgrammes()

    @Query("DELETE FROM epg_channel")
    suspend fun deleteAllChannels()

    @Query("DELETE FROM epg_index_metadata")
    suspend fun deleteAllMetadata()
}
