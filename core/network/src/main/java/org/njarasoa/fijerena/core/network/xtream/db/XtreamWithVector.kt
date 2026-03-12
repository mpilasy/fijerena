package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class XtreamCategoryWithVector(
    @Embedded val category: XtreamCategoryEntity,
    @ColumnInfo(name = "embedding") val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamCategoryWithVector

        if (category != other.category) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

data class XtreamStreamWithVector(
    @Embedded val stream: XtreamStreamEntity,
    @ColumnInfo(name = "embedding") val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamStreamWithVector

        if (stream != other.stream) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stream.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

data class XtreamSeriesWithVector(
    @Embedded val series: XtreamSeriesEntity,
    @ColumnInfo(name = "embedding") val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamSeriesWithVector

        if (series != other.series) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = series.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

data class XtreamEpisodeWithVector(
    @Embedded val episode: XtreamEpisodeEntity,
    @ColumnInfo(name = "embedding") val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as XtreamEpisodeWithVector

        if (episode != other.episode) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = episode.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
