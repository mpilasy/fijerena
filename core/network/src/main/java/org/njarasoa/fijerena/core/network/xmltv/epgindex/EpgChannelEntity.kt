package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "epg_channel",
    primaryKeys = ["xmltv_id", "source_id"],
    indices = [
        Index(value = ["source_id"], name = "idx_channel_source"),
    ],
)
data class EpgChannelEntity(
    @ColumnInfo(name = "xmltv_id")
    val xmltvId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "icon_url")
    val iconUrl: String? = null,
    @ColumnInfo(name = "source_id")
    val sourceId: Long = 0,
)
