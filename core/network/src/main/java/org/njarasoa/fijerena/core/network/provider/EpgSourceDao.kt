package org.njarasoa.fijerena.core.network.provider

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgSourceDao {
    @Query("SELECT * FROM epg_source ORDER BY added_at_ms ASC")
    fun getAllSources(): Flow<List<EpgSourceEntity>>

    @Query("SELECT * FROM epg_source ORDER BY added_at_ms ASC")
    suspend fun getAllSourcesOnce(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_source WHERE enabled = 1 ORDER BY added_at_ms ASC")
    suspend fun getEnabledSources(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_source WHERE id = :id")
    suspend fun getSourceById(id: Long): EpgSourceEntity?

    @Query("SELECT * FROM epg_source WHERE url = :url AND provider_id = :providerId LIMIT 1")
    suspend fun getSourceByUrl(
        url: String,
        providerId: Long,
    ): EpgSourceEntity?

    @Query("SELECT * FROM epg_source WHERE provider_id = :providerId ORDER BY added_at_ms ASC")
    fun getSourcesForProvider(providerId: Long): Flow<List<EpgSourceEntity>>

    @Query("SELECT id FROM epg_source WHERE provider_id = :providerId")
    suspend fun getSourceIdsForProvider(providerId: Long): List<Long>

    @Query("SELECT * FROM epg_source WHERE enabled = 1 AND provider_id = :providerId")
    suspend fun getEnabledSourcesForSearch(providerId: Long): List<EpgSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: EpgSourceEntity): Long

    @Update
    suspend fun updateSource(source: EpgSourceEntity)

    @Query("DELETE FROM epg_source WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("DELETE FROM epg_source WHERE id IN (:ids)")
    suspend fun deleteSources(ids: List<Long>)

    @Query("DELETE FROM epg_source WHERE provider_id = :providerId")
    suspend fun deleteSourcesForProvider(providerId: Long)

    @Query("DELETE FROM epg_source")
    suspend fun deleteAllSources()

    @Query(
        "UPDATE epg_source SET last_ingested_at_ms = :timestamp, last_error = NULL, last_channels = :channels, last_programmes = :programmes, last_download_bytes = :downloadBytes, ingest_method = :ingestMethod, last_ingestion_duration_ms = :ingestionDurationMs, last_download_duration_ms = :downloadDurationMs WHERE id = :id",
    )
    suspend fun markIngested(
        id: Long,
        timestamp: Long,
        channels: Int,
        programmes: Int,
        downloadBytes: Long,
        ingestMethod: String = "DOWNLOADED",
        ingestionDurationMs: Long = 0,
        downloadDurationMs: Long = 0,
    )

    @Query("UPDATE epg_source SET last_error = :error WHERE id = :id")
    suspend fun markError(
        id: Long,
        error: String,
    )

    @Query("SELECT * FROM epg_source WHERE enabled = 1 AND last_error IS NOT NULL ORDER BY added_at_ms ASC")
    suspend fun getFailedSources(): List<EpgSourceEntity>

    @Query(
        "SELECT * FROM epg_source WHERE enabled = 1 AND (last_ingested_at_ms = 0 OR last_ingested_at_ms < :thresholdMs) ORDER BY added_at_ms ASC",
    )
    suspend fun getStaleSources(thresholdMs: Long): List<EpgSourceEntity>

    @Query("SELECT COUNT(*) FROM epg_source")
    suspend fun getSourceCount(): Int

    @Query(
        "UPDATE epg_source SET last_ingested_at_ms = 0, last_channels = 0, last_programmes = 0, last_download_bytes = 0, last_error = NULL, last_ingestion_duration_ms = 0, last_download_duration_ms = 0",
    )
    suspend fun resetAllIngestionState()
}
