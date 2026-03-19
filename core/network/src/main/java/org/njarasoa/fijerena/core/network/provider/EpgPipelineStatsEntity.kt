package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_pipeline_stats")
data class EpgPipelineStatsEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "sources_processed")
    val sourcesProcessed: Int,
    val errors: Int,
    @ColumnInfo(name = "total_channels")
    val totalChannels: Int,
    @ColumnInfo(name = "total_programmes")
    val totalProgrammes: Int,
)
