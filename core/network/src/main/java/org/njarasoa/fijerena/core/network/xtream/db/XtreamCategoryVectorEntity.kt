package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "xtream_category_vectors",
    primaryKeys = ["categoryId", "providerId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamCategoryEntity::class,
            parentColumns = ["categoryId", "providerId", "type"],
            childColumns = ["categoryId", "providerId", "type"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class XtreamCategoryVectorEntity(
    val categoryId: String,
    val providerId: Long,
    val type: String,
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamCategoryVectorEntity

        if (categoryId != other.categoryId) return false
        if (providerId != other.providerId) return false
        if (type != other.type) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = categoryId.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
