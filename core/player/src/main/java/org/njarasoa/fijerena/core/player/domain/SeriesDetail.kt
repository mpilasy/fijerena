package org.njarasoa.fijerena.core.player.domain

data class SeriesDetail(
    val id: String,
    val name: String,
    val metadata: MediaMetadata = MediaMetadata(),
    val coverUrl: String? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: Map<String, List<EpisodeItem>> = emptyMap(),
)

data class SeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int? = null,
    val coverUrl: String? = null,
)

data class EpisodeItem(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val seasonNumber: Int? = null,
    val extension: String? = null,
    val metadata: MediaMetadata = MediaMetadata(),
    val thumbnailUrl: String? = null,
)
