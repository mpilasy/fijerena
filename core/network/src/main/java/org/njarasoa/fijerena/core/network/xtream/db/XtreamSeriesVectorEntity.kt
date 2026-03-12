package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "xtream_series_vectors",
    primaryKeys = ["seriesId", "providerId"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamSeriesEntity::class,
            parentColumns = ["seriesId", "providerId"],
            childColumns = ["seriesId", "providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class XtreamSeriesVectorEntity(
    val seriesId: Int,
    val providerId: Long,
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamSeriesVectorEntity

        if (seriesId != other.seriesId) return false
        if (providerId != other.providerId) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = seriesId
        result = 31 * result + providerId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
