package org.njarasoa.fijerena.core.network.provider

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Another live sync key of a guide source (added v20). When copies of one guide merge, the guide
 * keeps one key and the others stay here. With a [providerId], the key is an older build's row for
 * that (guide, source) pair; without one, it is the same guide under another key.
 */
@Entity(tableName = "epg_source_alias", indices = [Index("source_id")])
data class EpgSourceAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_key")
    val aliasKey: String,
    @ColumnInfo(name = "source_id")
    val sourceId: Long,
    @ColumnInfo(name = "provider_id")
    val providerId: Long?,
)
