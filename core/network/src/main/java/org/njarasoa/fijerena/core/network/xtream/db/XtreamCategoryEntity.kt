package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_categories",
    primaryKeys = ["categoryId", "providerId", "type"],
    indices = [Index(value = ["providerId", "type"])]
)
data class XtreamCategoryEntity(
    val categoryId: String,
    val providerId: Long,
    val categoryName: String,
    val parentId: Int = 0,
    val type: String, // LIVE, VOD, SERIES
    val contentHash: Int = 0
) {
    /** Hash of content fields only, excluding [contentHash] itself to avoid self-referential comparison. */
    fun computeContentHash(): Int {
        var result = categoryId.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + categoryName.hashCode()
        result = 31 * result + parentId
        result = 31 * result + type.hashCode()
        return result
    }

    companion object {
        const val TYPE_LIVE = "LIVE"
        const val TYPE_VOD = "VOD"
        const val TYPE_SERIES = "SERIES"
    }
}
