package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "epg_source",
    indices = [Index("provider_id"), Index(value = ["source_key"], unique = true)],
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
    @ColumnInfo(name = "provider_id")
    val providerId: Long,
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
) {
    companion object {
        const val REFRESH_OFF = -1
        const val DEFAULT_REFRESH_INTERVAL_HOURS = 24
    }
}
