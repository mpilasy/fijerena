package org.njarasoa.fijerena.core.network.provider

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.xtream.db.SyncClockEntity

@Dao
interface EpgSourceDao {
    @Query("SELECT * FROM epg_source ORDER BY added_at_ms ASC")
    fun getAllSources(): Flow<List<EpgSourceEntity>>

    @Query("SELECT * FROM epg_source ORDER BY added_at_ms ASC")
    suspend fun getAllSourcesOnce(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_source WHERE id = :id")
    suspend fun getSourceById(id: Long): EpgSourceEntity?

    // A guide source is linked to the media sources that read it (epg_source_provider, v20).
    @Query(
        "SELECT s.* FROM epg_source s JOIN epg_source_provider l ON l.source_id = s.id " +
            "WHERE l.provider_id = :providerId ORDER BY s.added_at_ms ASC",
    )
    fun getSourcesForProvider(providerId: Long): Flow<List<EpgSourceEntity>>

    @Query(
        "SELECT s.* FROM epg_source s JOIN epg_source_provider l ON l.source_id = s.id " +
            "WHERE l.provider_id = :providerId ORDER BY s.added_at_ms ASC",
    )
    suspend fun getSourcesForProviderOnce(providerId: Long): List<EpgSourceEntity>

    @Query("SELECT source_id FROM epg_source_provider WHERE provider_id = :providerId")
    suspend fun getSourceIdsForProvider(providerId: Long): List<Long>

    @Query(
        "SELECT s.* FROM epg_source s JOIN epg_source_provider l ON l.source_id = s.id " +
            "WHERE s.enabled = 1 AND l.provider_id = :providerId ORDER BY s.added_at_ms ASC",
    )
    suspend fun getEnabledSourcesForProvider(providerId: Long): List<EpgSourceEntity>

    /** The enabled guide sources any of [providerIds] reads, each once. */
    @Query(
        "SELECT * FROM epg_source WHERE enabled = 1 AND id IN " +
            "(SELECT source_id FROM epg_source_provider WHERE provider_id IN (:providerIds)) ORDER BY added_at_ms ASC",
    )
    suspend fun getEnabledSourcesForProviders(providerIds: Collection<Long>): List<EpgSourceEntity>

    /** The media sources that read guide source [sourceId], the first link first. */
    @Query("SELECT provider_id FROM epg_source_provider WHERE source_id = :sourceId ORDER BY linked_at_ms ASC, provider_id ASC")
    suspend fun getProviderIds(sourceId: Long): List<Long>

    @Query("SELECT * FROM epg_source_provider ORDER BY linked_at_ms ASC, provider_id ASC")
    fun getAllLinks(): Flow<List<EpgSourceProviderEntity>>

    @Query("SELECT * FROM epg_source_provider ORDER BY linked_at_ms ASC, provider_id ASC")
    suspend fun getAllLinksOnce(): List<EpgSourceProviderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: EpgSourceProviderEntity)

    /**
     * Media source [providerId] no longer reads guide source [sourceId]. The aliases of that pair
     * go too, each with a tombstone, so an older build deletes its copy for that source.
     */
    @Transaction
    suspend fun unlink(
        sourceId: Long,
        providerId: Long,
    ) {
        tombstoneAliases(sourceId, providerId)
        deleteAliasesFor(sourceId, providerId)
        deleteLink(sourceId, providerId)
    }

    @Query("DELETE FROM epg_source_provider WHERE source_id = :sourceId")
    suspend fun deleteLinksOf(sourceId: Long)

    /** Links to media sources that no longer exist. */
    @Query("DELETE FROM epg_source_provider WHERE provider_id NOT IN (SELECT id FROM providers)")
    suspend fun deleteLinksToMissingProviders(): Int

    /** Guide sources no media source reads. */
    @Query("SELECT * FROM epg_source WHERE id NOT IN (SELECT source_id FROM epg_source_provider)")
    suspend fun getUnlinkedSources(): List<EpgSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: EpgSourceAliasEntity)

    @Query("SELECT * FROM epg_source_alias WHERE alias_key = :key")
    suspend fun getAlias(key: String): EpgSourceAliasEntity?

    @Query("SELECT * FROM epg_source_alias WHERE source_id = :sourceId")
    suspend fun getAliasesOf(sourceId: Long): List<EpgSourceAliasEntity>

    @Query("DELETE FROM epg_source_alias WHERE source_id = :sourceId")
    suspend fun deleteAliasesOf(sourceId: Long)

    /** Adds [source] read by [providerIds], the first of them the first link. Returns its id. */
    @Transaction
    suspend fun insertGuide(
        source: EpgSourceEntity,
        providerIds: List<Long>,
    ): Long {
        val id = insertSource(source)
        val now = System.currentTimeMillis()
        providerIds.distinct().forEachIndexed { i, providerId -> insertLink(EpgSourceProviderEntity(id, providerId, now + i)) }
        return id
    }

    /** Guide source [sourceId] is read by exactly [providerIds]; links kept keep their order. */
    @Transaction
    suspend fun setLinks(
        sourceId: Long,
        providerIds: Collection<Long>,
    ) {
        val wanted = providerIds.toSet()
        val current = getProviderIds(sourceId)
        current.filter { it !in wanted }.forEach { unlink(sourceId, it) }
        val now = System.currentTimeMillis()
        wanted.filter { it !in current }.forEachIndexed {
            i,
            providerId,
            ->
            insertLink(EpgSourceProviderEntity(sourceId, providerId, now + i))
        }
    }

    /**
     * Deletes guide source [id] with its links and aliases. Each alias gets a tombstone, so older
     * builds delete their copies too. Its guide index rows are the caller's.
     */
    @Transaction
    suspend fun deleteGuide(id: Long) {
        tombstoneAliases(id, null)
        deleteLinksOf(id)
        deleteAliasesOf(id)
        deleteSource(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: EpgSourceEntity): Long

    @Update
    suspend fun updateSource(source: EpgSourceEntity)

    @Query("DELETE FROM epg_source WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("DELETE FROM epg_source WHERE id IN (:ids)")
    suspend fun deleteSources(ids: List<Long>)

    @Query("DELETE FROM epg_source")
    suspend fun deleteAllSources()

    @Query(
        """
        UPDATE epg_source SET last_ingested_at_ms = :timestamp, last_error = NULL, last_channels = :channels,
            last_programmes = :programmes, last_download_bytes = :downloadBytes, ingest_method = :ingestMethod,
            last_ingestion_duration_ms = :ingestionDurationMs, last_download_duration_ms = :downloadDurationMs,
            last_content_sha256 = :contentSha256, etag = :etag, last_modified_header = :lastModifiedHeader,
            last_failed_at_ms = NULL, failures_since_success = 0,
            file_utc_offset_minutes = :fileUtcOffsetMinutes, applied_utc_offset_minutes = :appliedUtcOffsetMinutes
        WHERE id = :id
        """,
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
        contentSha256: String? = null,
        etag: String? = null,
        lastModifiedHeader: String? = null,
        fileUtcOffsetMinutes: Int? = null,
        appliedUtcOffsetMinutes: Int? = null,
    )

    /** The guide index took [durationMs] to take in the last import of [ids]. */
    @Query("UPDATE epg_source SET last_index_duration_ms = :durationMs WHERE id IN (:ids)")
    suspend fun setIndexDuration(
        ids: List<Long>,
        durationMs: Long,
    )

    /** The guide index now holds the source's programmes read with [minutes] — see `xmltv.EpgOffsetShift`. */
    @Query("UPDATE epg_source SET applied_utc_offset_minutes = :minutes WHERE id = :id")
    suspend fun setAppliedOffset(
        id: Long,
        minutes: Int,
    )

    /**
     * Drops the source's validators and content hash, so its next refresh imports it again even
     * when the file has not changed.
     */
    @Query("UPDATE epg_source SET etag = NULL, last_modified_header = NULL, last_content_sha256 = NULL WHERE id = :id")
    suspend fun forgetValidators(id: Long)

    /**
     * The source's guide data was cleared: it shows as never refreshed, and its next refresh
     * downloads and imports it again (no validators or content hash). Only local columns change,
     * so nothing is synced.
     */
    @Query(
        """
        UPDATE epg_source SET last_ingested_at_ms = 0, last_error = NULL, last_channels = 0, last_programmes = 0,
            last_download_bytes = 0, last_ingestion_duration_ms = 0, last_download_duration_ms = 0,
            last_index_duration_ms = 0, last_content_sha256 = NULL, etag = NULL, last_modified_header = NULL,
            last_failed_at_ms = NULL, failures_since_success = 0, file_utc_offset_minutes = NULL,
            applied_utc_offset_minutes = NULL
        WHERE id = :id
        """,
    )
    suspend fun forgetIngest(id: Long)

    /**
     * A refresh confirmed the source is unchanged (304, or a matching content hash) — bump the
     * timestamp and clear any prior error, but leave the channel/programme counts and validators
     * as they were. Reusing [markIngested] here would zero the counts, making a genuinely
     * unchanged source look like one that suddenly lost its whole guide. A success: the failure
     * time and count are cleared.
     */
    @Query(
        "UPDATE epg_source SET last_ingested_at_ms = :timestamp, last_error = NULL, last_failed_at_ms = NULL, " +
            "failures_since_success = 0 WHERE id = :id",
    )
    suspend fun markUnchanged(
        id: Long,
        timestamp: Long,
    )

    /**
     * A refresh failed: keep [error], the time of the failure and one more failure since the last
     * success. The last successful refresh ([EpgSourceEntity.lastIngestedAtMs]) stays as it was.
     */
    @Query(
        "UPDATE epg_source SET last_error = :error, last_failed_at_ms = :failedAtMs, " +
            "failures_since_success = failures_since_success + 1 WHERE id = :id",
    )
    suspend fun markError(
        id: Long,
        error: String,
        failedAtMs: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE epg_source SET refresh_interval_hours = :hours WHERE id = :id")
    suspend fun setRefreshInterval(
        id: Long,
        hours: Int,
    )

    /** Only rows without an interval of their own — see `EpgRefreshSchedule.copyLegacyIntervalOnce`. */
    @Query("UPDATE epg_source SET refresh_interval_hours = :hours WHERE refresh_interval_hours IS NULL")
    suspend fun fillUnsetRefreshIntervals(hours: Int)

    @Query("SELECT COUNT(*) FROM epg_source")
    suspend fun getSourceCount(): Int

    @Query(
        """
        UPDATE epg_source SET last_ingested_at_ms = 0, last_channels = 0, last_programmes = 0, last_download_bytes = 0,
            last_error = NULL, last_ingestion_duration_ms = 0, last_download_duration_ms = 0, last_index_duration_ms = 0,
            last_content_sha256 = NULL, etag = NULL, last_modified_header = NULL,
            last_failed_at_ms = NULL, failures_since_success = 0
        """,
    )
    suspend fun resetAllIngestionState()

    /** The enabled guide sources with an error that any of [providerIds] reads, each once. */
    @Query(
        "SELECT * FROM epg_source WHERE enabled = 1 AND last_error IS NOT NULL AND id IN " +
            "(SELECT source_id FROM epg_source_provider WHERE provider_id IN (:providerIds)) ORDER BY added_at_ms ASC",
    )
    suspend fun getFailedSourcesForProviders(providerIds: Collection<Long>): List<EpgSourceEntity>

    @Query("DELETE FROM epg_source_provider WHERE source_id = :sourceId AND provider_id = :providerId")
    suspend fun deleteLink(
        sourceId: Long,
        providerId: Long,
    )

    @Query("DELETE FROM epg_source_alias WHERE source_id = :sourceId AND provider_id = :providerId")
    suspend fun deleteAliasesFor(
        sourceId: Long,
        providerId: Long,
    )

    /**
     * A tombstone (stamped and queued by the `sync_tombstone_insert` trigger) for each alias of
     * guide source [sourceId]: of media source [providerId] only, or all of them when it is null.
     * Not while live sync applies a received record: the device that made the change sends them.
     */
    @Transaction
    suspend fun tombstoneAliases(
        sourceId: Long,
        providerId: Long?,
    ) {
        deleteAliasTombstones(sourceId, providerId)
        insertAliasTombstones(sourceId, providerId)
    }

    @Query(
        "DELETE FROM sync_tombstone WHERE kind = '${SyncKind.EPG_SOURCE}' AND itemKey IN " +
            "(SELECT alias_key FROM epg_source_alias WHERE source_id = :sourceId AND (:providerId IS NULL OR provider_id = :providerId)) " +
            "AND (SELECT applying FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}) = 0",
    )
    suspend fun deleteAliasTombstones(
        sourceId: Long,
        providerId: Long?,
    )

    @Query(
        "INSERT INTO sync_tombstone (kind, itemKey, deletedAt) SELECT '${SyncKind.EPG_SOURCE}', alias_key, 0 FROM epg_source_alias " +
            "WHERE source_id = :sourceId AND (:providerId IS NULL OR provider_id = :providerId) " +
            "AND (SELECT applying FROM sync_clock WHERE id = ${SyncClockEntity.SINGLE_ROW}) = 0",
    )
    suspend fun insertAliasTombstones(
        sourceId: Long,
        providerId: Long?,
    )
}
