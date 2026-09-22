package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "epg_channel_staging",
    primaryKeys = ["xmltv_id", "source_id"],
    indices = [
        // Backs EpgIndexDao's per-source staging queries (clearStagingChannelsForSources,
        // transferChannelsFromStaging) — source_id is the second column of the primary key, not
        // the leading one, so those "WHERE source_id IN (...)" queries had no usable index.
        Index(value = ["source_id"], name = "idx_channel_staging_source"),
    ],
)
data class EpgChannelStagingEntity(
    @ColumnInfo(name = "xmltv_id")
    val xmltvId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "icon_url")
    val iconUrl: String? = null,
    @ColumnInfo(name = "source_id")
    val sourceId: Long = 0,
)

/**
 * Extension to convert staging entity to primary entity.
 */
fun EpgChannelStagingEntity.toPrimary() = EpgChannelEntity(
    xmltvId = xmltvId,
    displayName = displayName,
    iconUrl = iconUrl,
    sourceId = sourceId
)

/**
 * Extension to convert primary entity to staging entity.
 */
fun EpgChannelEntity.toStaging() = EpgChannelStagingEntity(
    xmltvId = xmltvId,
    displayName = displayName,
    iconUrl = iconUrl,
    sourceId = sourceId
)
