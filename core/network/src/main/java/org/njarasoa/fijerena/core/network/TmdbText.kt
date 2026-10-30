package org.njarasoa.fijerena.core.network

import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.TitleFacts

/**
 * These facts with the plot and genre names of [details], a TMDB details answer in [language]
 * ([org.njarasoa.fijerena.core.network.tmdb.TmdbApiService.textLanguage]). Stored with the facts,
 * so a cached page shows the same text (UX-D4).
 */
internal fun TitleFacts.withTmdbText(
    details: TmdbRecommendation,
    language: String,
): TitleFacts =
    copy(
        overview = details.overview?.takeIf { it.isNotBlank() },
        genres = details.genres.mapNotNull { it.name?.takeIf(String::isNotBlank) },
        textLanguage = language,
    )

/**
 * UX-D4: the plot and genres TMDB sent in the app language ([TitleFacts.overview],
 * [TitleFacts.genres]) when it has them, else the source's. A source's text is often in another
 * language (a Greek plot on a French or Malagasy profile).
 */
internal fun MediaMetadata.preferTmdbText(): MediaMetadata =
    copy(
        plot = facts.overview?.takeIf { it.isNotBlank() } ?: plot,
        genre = facts.genres.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: genre,
    )
