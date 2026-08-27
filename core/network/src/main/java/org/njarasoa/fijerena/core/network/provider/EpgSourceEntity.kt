package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "epg_source", indices = [Index("provider_id")])
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
)
