package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_series",
    primaryKeys = ["seriesId", "providerId"],
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["categoryId", "providerId"]),
        Index(value = ["providerId", "categoryId", "excluded"]),
    ],
)
data class XtreamSeriesEntity(
    val seriesId: Int,
    val providerId: Long,
    val num: Int? = null,
    val name: String,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val lastModified: String? = null,
    val rating: String? = null,
    val rating5based: Double? = null,
    val youtubeTrailer: String? = null,
    val episodeRunTime: String? = null,
    val categoryId: String,
    val backdropPath: String? = null, // Comma separated URLs
    val contentHash: Int = 0,
    val excluded: Boolean = false,
) {
    companion object {
        fun computeHash(
            seriesId: Int,
            providerId: Long,
            num: Int?,
            name: String,
            cover: String?,
            plot: String?,
            cast: String?,
            director: String?,
            genre: String?,
            releaseDate: String?,
            lastModified: String?,
            rating: String?,
            rating5based: Double?,
            youtubeTrailer: String?,
            episodeRunTime: String?,
            categoryId: String,
            backdropPath: String?,
        ): Int {
            var result = seriesId
            result = 31 * result + providerId.hashCode()
            result = 31 * result + (num ?: 0)
            result = 31 * result + name.hashCode()
            result = 31 * result + (cover?.hashCode() ?: 0)
            result = 31 * result + (plot?.hashCode() ?: 0)
            result = 31 * result + (cast?.hashCode() ?: 0)
            result = 31 * result + (director?.hashCode() ?: 0)
            result = 31 * result + (genre?.hashCode() ?: 0)
            result = 31 * result + (releaseDate?.hashCode() ?: 0)
            result = 31 * result + (lastModified?.hashCode() ?: 0)
            result = 31 * result + (rating?.hashCode() ?: 0)
            result = 31 * result + (rating5based?.hashCode() ?: 0)
            result = 31 * result + (youtubeTrailer?.hashCode() ?: 0)
            result = 31 * result + (episodeRunTime?.hashCode() ?: 0)
            result = 31 * result + categoryId.hashCode()
            result = 31 * result + (backdropPath?.hashCode() ?: 0)
            return result
        }
    }
}
