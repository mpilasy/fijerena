package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_channel")
data class EpgChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "xmltv_id")
    val xmltvId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "icon_url")
    val iconUrl: String? = null
)
