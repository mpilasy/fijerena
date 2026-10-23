package org.njarasoa.fijerena.core.network.wanted

import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.MediaType

/**
 * A related title TMDB knows that no source has, as a card in a film or show page's related rows
 * (wishlist UX plan, Phase 4): it opens the title's TMDB page, never a source's. Its id carries
 * [PREFIX], so no catalogue id can match it; its TMDB data rides in `providerData`.
 */
object TmdbOnlyItem {
    const val PREFIX = "tmdb_only_"
    private const val TMDB_ID = "tmdbOnly.tmdbId"
    private const val ORIGINAL_TITLE = "tmdbOnly.originalTitle"
    private const val POSTER_PATH = "tmdbOnly.posterPath"
    private const val RELEASE_DATE = "tmdbOnly.releaseDate"

    fun of(
        result: TmdbRecommendation,
        mediaType: MediaType,
    ): MediaItem =
        MediaItem(
            id = PREFIX + result.id,
            name = result.displayTitle.orEmpty(),
            mediaType = mediaType,
            categoryId = "",
            thumbnailUrl = TmdbApiService.posterUrl(result.posterPath, TmdbApiService.POSTER_SIZE_W342),
            metadata = MediaMetadata(year = result.year, plot = result.overview),
            providerData =
                listOfNotNull(
                    TMDB_ID to result.id.toString(),
                    result.originalDisplayTitle?.let { ORIGINAL_TITLE to it },
                    result.posterPath?.let { POSTER_PATH to it },
                    (result.releaseDate ?: result.firstAirDate)?.let { RELEASE_DATE to it },
                ).toMap(),
        )

    /** [item]'s TMDB title when it is a TMDB-only card, else null. [contentType]: `MOVIES` or `TV_SHOWS`. */
    fun titleOf(
        item: MediaItem,
        contentType: String,
    ): TmdbTitle? =
        item.providerData[TMDB_ID]?.takeIf { item.id.startsWith(PREFIX) }?.let { tmdbId ->
            TmdbTitle(
                contentType = contentType,
                tmdbId = tmdbId,
                title = item.name,
                originalTitle = item.providerData[ORIGINAL_TITLE]?.takeIf { it != item.name },
                year = item.metadata.year,
                posterPath = item.providerData[POSTER_PATH],
                overview = item.metadata.plot,
                releaseDate = item.providerData[RELEASE_DATE],
            )
        }
}
