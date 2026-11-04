package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "epg_source",
    indices = [Index(value = ["source_key"], unique = true)],
)
data class EpgSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val label: String = "",
    @ColumnInfo(name = "timezone_offset_hours")
    val timezoneOffsetHours: Int = 0,
    @ColumnInfo(name = "added_at_ms")
    val addedAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_ingested_at_ms")
    val lastIngestedAtMs: Long = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,
    @ColumnInfo(name = "last_channels", defaultValue = "0")
    val lastChannels: Int = 0,
    @ColumnInfo(name = "last_programmes", defaultValue = "0")
    val lastProgrammes: Int = 0,
    @ColumnInfo(name = "last_download_bytes", defaultValue = "0")
    val lastDownloadBytes: Long = 0,
    @ColumnInfo(name = "ingest_method", defaultValue = "DOWNLOADED")
    val ingestMethod: String = "DOWNLOADED", // "STREAMED" or "DOWNLOADED"
    @ColumnInfo(name = "last_ingestion_duration_ms", defaultValue = "0")
    val lastIngestionDurationMs: Long = 0,
    @ColumnInfo(name = "last_download_duration_ms", defaultValue = "0")
    val lastDownloadDurationMs: Long = 0,
    /** SHA-256 of the last ingested payload (decompressed for `.gz` sources). Null = never hashed. */
    @ColumnInfo(name = "last_content_sha256")
    val lastContentSha256: String? = null,
    /** Response validators from the last download, sent back as `If-None-Match`/`If-Modified-Since`. */
    @ColumnInfo(name = "etag")
    val etag: String? = null,
    @ColumnInfo(name = "last_modified_header")
    val lastModifiedHeader: String? = null,
    /** Random UUID naming this source in live sync, like `providers.providerKey` (added v14). */
    @ColumnInfo(name = "source_key")
    val sourceKey: String = UUID.randomUUID().toString(),
    /**
     * Hours between automatic refreshes of this source, [REFRESH_OFF] = never (added v16). Null =
     * not set (a row from before v16, or received from an older version): it uses the retired
     * device-wide interval — see `xmltv.EpgRefreshSchedule`. A source added on this version starts
     * at [DEFAULT_REFRESH_INTERVAL_HOURS].
     */
    @ColumnInfo(name = "refresh_interval_hours")
    val refreshIntervalHours: Int? = DEFAULT_REFRESH_INTERVAL_HOURS,
    /** When the last failed refresh happened; null after a success (added v18). Local, never synced. */
    @ColumnInfo(name = "last_failed_at_ms")
    val lastFailedAtMs: Long? = null,
    /** Failed refreshes since the last success; 0 after a success (added v18). Local, never synced. */
    @ColumnInfo(name = "failures_since_success", defaultValue = "0")
    val failuresSinceSuccess: Int = 0,
    /**
     * The UTC offset, in minutes, the last import found on every programme time of the file; null
     * when the times have different offsets, or before the first import (added v19). Local.
     */
    @ColumnInfo(name = "file_utc_offset_minutes")
    val fileUtcOffsetMinutes: Int? = null,
    /**
     * The UTC offset, in minutes, the source's programmes in the guide index were read with; null
     * when not known (added v19). Local. See `xmltv.EpgOffsetShift`.
     */
    @ColumnInfo(name = "applied_utc_offset_minutes")
    val appliedUtcOffsetMinutes: Int? = null,
    /**
     * How long the guide index took to take in the last import: the swap and the search index
     * rebuild (added v21). A run that imports several sources rebuilds the index once, so each of
     * its sources gets the same time. Local.
     */
    @ColumnInfo(name = "last_index_duration_ms", defaultValue = "0")
    val lastIndexDurationMs: Long = 0,
) {
    companion object {
        const val REFRESH_OFF = -1
        const val DEFAULT_REFRESH_INTERVAL_HOURS = 24
    }
}
