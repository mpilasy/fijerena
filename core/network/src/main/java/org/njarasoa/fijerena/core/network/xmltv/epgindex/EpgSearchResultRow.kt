package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo

data class EpgSearchResultRow(
    val id: Long,

    @ColumnInfo(name = "channel_id")
    val channelId: String,

    val title: String,

    @ColumnInfo(name = "title_lowercase")
    val titleLowercase: String,

    val description: String?,

    val category: String?,

    @ColumnInfo(name = "start_epoch")
    val startEpoch: Long,

    @ColumnInfo(name = "end_epoch")
    val endEpoch: Long,

    @ColumnInfo(name = "channelDisplayName")
    val channelDisplayName: String,

    @ColumnInfo(name = "channelIconUrl")
    val channelIconUrl: String?
)
