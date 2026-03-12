package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "xtream_episode_vectors",
    primaryKeys = ["id", "providerId"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamEpisodeEntity::class,
            parentColumns = ["id", "providerId"],
            childColumns = ["id", "providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class XtreamEpisodeVectorEntity(
    val id: String,
    val providerId: Long,
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamEpisodeVectorEntity

        if (id != other.id) return false
        if (providerId != other.providerId) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
