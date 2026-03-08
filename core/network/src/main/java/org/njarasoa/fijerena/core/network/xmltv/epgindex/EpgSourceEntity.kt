package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_source")
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
    val ingestMethod: String = "DOWNLOADED",  // "STREAMED" or "DOWNLOADED"

    @ColumnInfo(name = "last_ingestion_duration_ms", defaultValue = "0")
    val lastIngestionDurationMs: Long = 0,

    @ColumnInfo(name = "last_download_duration_ms", defaultValue = "0")
    val lastDownloadDurationMs: Long = 0
)
