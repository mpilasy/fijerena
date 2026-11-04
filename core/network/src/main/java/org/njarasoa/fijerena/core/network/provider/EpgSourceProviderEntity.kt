package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One media source that reads a guide source (added v20). A guide source added for several sources
 * is one `epg_source` row with one link for each — see the one guide source per URL plan. The first
 * link (lowest [linkedAtMs], then [providerId]) is the guide's source for older builds in live sync.
 */
@Entity(
    tableName = "epg_source_provider",
    primaryKeys = ["source_id", "provider_id"],
    indices = [Index("provider_id")],
)
data class EpgSourceProviderEntity(
    @ColumnInfo(name = "source_id")
    val sourceId: Long,
    @ColumnInfo(name = "provider_id")
    val providerId: Long,
    @ColumnInfo(name = "linked_at_ms")
    val linkedAtMs: Long = System.currentTimeMillis(),
)
