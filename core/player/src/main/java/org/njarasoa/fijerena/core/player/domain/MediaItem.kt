package org.njarasoa.fijerena.core.player.domain

enum class MediaType {
    LIVE_CHANNEL,
    MOVIE,
    SERIES,
    EPISODE,
    VIDEO_FILE,
}

data class MediaMetadata(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val durationSecs: Int? = null,
    val bitrate: Int? = null,
    val airDate: String? = null,
    val tmdbId: String? = null,
    val year: Int? = null,
)

data class MediaItem(
    val id: String,
    val name: String,
    val mediaType: MediaType,
    val categoryId: String,
    val thumbnailUrl: String? = null,
    val streamUri: String? = null,
    val metadata: MediaMetadata = MediaMetadata(),
    val providerData: Map<String, String> = emptyMap(),
)
