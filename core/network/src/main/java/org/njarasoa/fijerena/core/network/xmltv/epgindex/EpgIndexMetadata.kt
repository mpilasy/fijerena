package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_index_metadata")
data class EpgIndexMetadata(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "file_last_modified_ms")
    val fileLastModifiedMs: Long,
    @ColumnInfo(name = "indexed_at_ms")
    val indexedAtMs: Long,
    @ColumnInfo(name = "channel_count")
    val channelCount: Int,
    @ColumnInfo(name = "programme_count")
    val programmeCount: Int,
    @ColumnInfo(name = "timezone_offset_hours", defaultValue = "0")
    val timezoneOffsetHours: Int = 0,
)
