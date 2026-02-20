package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "xtream_series",
    primaryKeys = ["seriesId", "providerId"],
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["categoryId", "providerId"])
    ]
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
    val contentHash: Int = 0
)
