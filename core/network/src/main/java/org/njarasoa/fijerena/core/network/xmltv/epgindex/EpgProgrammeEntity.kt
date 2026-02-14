package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programme",
    indices = [
        Index(value = ["start_epoch"], name = "idx_programme_start"),
        Index(value = ["end_epoch"], name = "idx_programme_end"),
        Index(value = ["start_epoch", "end_epoch"], name = "idx_programme_time_range"),
        Index(value = ["channel_id"], name = "idx_programme_channel"),
        Index(value = ["title_lowercase"], name = "idx_programme_title_lower"),
        Index(value = ["channel_id", "start_epoch"], name = "idx_programme_dedup", unique = true)
    ]
)
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "channel_id")
    val channelId: String,

    val title: String,

    @ColumnInfo(name = "title_lowercase")
    val titleLowercase: String,

    val description: String? = null,

    val category: String? = null,

    @ColumnInfo(name = "start_epoch")
    val startEpoch: Long,

    @ColumnInfo(name = "end_epoch")
    val endEpoch: Long
)

@Fts4(contentEntity = EpgProgrammeEntity::class, tokenizer = "unicode61")
@Entity(tableName = "epg_programme_fts")
data class EpgProgrammeFts(
    val title: String
)
