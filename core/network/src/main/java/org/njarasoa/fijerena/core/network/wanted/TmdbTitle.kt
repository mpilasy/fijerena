package org.njarasoa.fijerena.core.network.wanted

import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * A film or show as TMDB knows it, for Search's "Not in your sources" section and the TMDB
 * details page. [contentType] is `MOVIES` or `TV_SHOWS`. See
 * the wanted titles plan.
 */
data class TmdbTitle(
    val contentType: String,
    val tmdbId: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val posterPath: String?,
    val overview: String?,
    /** First release (a show's first air date), ISO day, when TMDB gave one: Search's "Coming…" line. */
    val releaseDate: String? = null,
) {
    val key: WantedTitles.Key get() = WantedTitles.Key(contentType, tmdbId)

    fun toWanted(
        profileId: String,
        addedAt: Long,
    ) = WantedTitleEntity(profileId, contentType, tmdbId, title, originalTitle, year, posterPath, addedAt)

    companion object {
        /** Null for a person, or a result with no title. */
        fun of(result: TmdbRecommendation): TmdbTitle? {
            val contentType =
                when (result.mediaType) {
                    "movie" -> ContentType.MOVIES
                    "tv" -> ContentType.TV_SHOWS
                    else -> null
                }
            val title = result.displayTitle
            return if (contentType == null || title == null) {
                null
            } else {
                TmdbTitle(
                    contentType,
                    result.id.toString(),
                    title,
                    result.originalDisplayTitle?.takeIf { it != title },
                    result.year,
                    result.posterPath,
                    result.overview,
                    (result.releaseDate ?: result.firstAirDate)?.takeIf { it.isNotBlank() },
                )
            }
        }

        fun of(wanted: WantedTitleEntity) =
            TmdbTitle(wanted.contentType, wanted.tmdbId, wanted.title, wanted.originalTitle, wanted.year, wanted.posterPath, null)
    }
}
