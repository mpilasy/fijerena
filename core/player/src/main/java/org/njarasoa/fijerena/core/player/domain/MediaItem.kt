package org.njarasoa.fijerena.core.player.domain

import androidx.compose.runtime.Immutable

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
    val contentRating: String? = null,
    val duration: String? = null,
    val durationSecs: Int? = null,
    val bitrate: Int? = null,
    val airDate: String? = null,
    val tmdbId: String? = null,
    val year: Int? = null,
)

/**
 * `providerData` is a `Map`, which the Compose compiler cannot prove immutable, so without this
 * annotation the whole class infers unstable and every list row in the app loses its skip. Every
 * construction site builds the map with `mapOf`/`buildMap` and every read site only indexes into
 * it — nothing mutates it after construction, so the promise holds. Keep it that way.
 */
@Immutable
data class MediaItem(
    val id: String,
    val name: String,
    val mediaType: MediaType,
    val categoryId: String,
    val thumbnailUrl: String? = null,
    val streamUri: String? = null,
    val metadata: MediaMetadata = MediaMetadata(),
    val providerData: Map<String, String> = emptyMap(),
    /**
     * What selecting this row opens, when the row knows something its id alone doesn't say — a
     * history row that stands for a whole show, a row that stands for a category. Null on a plain
     * catalogue row, whose content type already answers it; read it through
     * [browseTarget][org.njarasoa.fijerena.core.player.domain.browseTarget] rather than directly.
     */
    val target: BrowseTarget? = null,
)
