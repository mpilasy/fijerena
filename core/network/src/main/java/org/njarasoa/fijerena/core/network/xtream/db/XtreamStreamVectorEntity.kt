package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "xtream_stream_vectors",
    primaryKeys = ["streamId", "providerId", "type"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamStreamEntity::class,
            parentColumns = ["streamId", "providerId", "type"],
            childColumns = ["streamId", "providerId", "type"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class XtreamStreamVectorEntity(
    val streamId: Int,
    val providerId: Long,
    val type: String,
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamStreamVectorEntity

        if (streamId != other.streamId) return false
        if (providerId != other.providerId) return false
        if (type != other.type) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = streamId
        result = 31 * result + providerId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
