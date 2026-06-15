package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programme_staging",
    indices = [
        Index(value = ["channel_id", "source_id", "start_epoch"], name = "idx_programme_staging_dedup", unique = true),
    ],
)
data class EpgProgrammeStagingEntity(
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
    val endEpoch: Long,
    @ColumnInfo(name = "source_id")
    val sourceId: Long = 0,
)

/**
 * Extension to convert staging entity to primary entity.
 */
fun EpgProgrammeStagingEntity.toPrimary() = EpgProgrammeEntity(
    channelId = channelId,
    title = title,
    titleLowercase = titleLowercase,
    description = description,
    category = category,
    startEpoch = startEpoch,
    endEpoch = endEpoch,
    sourceId = sourceId
)

/**
 * Extension to convert primary entity to staging entity.
 */
fun EpgProgrammeEntity.toStaging() = EpgProgrammeStagingEntity(
    channelId = channelId,
    title = title,
    titleLowercase = titleLowercase,
    description = description,
    category = category,
    startEpoch = startEpoch,
    endEpoch = endEpoch,
    sourceId = sourceId
)
