package org.njarasoa.fijerena.core.ui.wanted

import androidx.compose.runtime.staticCompositionLocalOf
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * The page of a source copy of a film or show: the film's details or the show's episodes. Its
 * page lists the other copies with the same TMDB id, so the first copy is enough (wanted titles
 * plan, W-D12).
 */
fun CatalogueHit.route(contentType: String): Screen =
    if (contentType == ContentType.TV_SHOWS) {
        Screen.EpisodeSelection(seriesId = itemId, seriesName = name, categoryId = categoryId, providerId = providerId)
    } else {
        Screen.MovieDetails(movieId = itemId, movieName = name, categoryId = categoryId, providerId = providerId)
    }

fun TmdbTitle.route(): Screen.TmdbTitle = Screen.TmdbTitle(contentType, tmdbId, title, originalTitle, year, posterPath)

fun Screen.TmdbTitle.toTmdbTitle(): TmdbTitle = TmdbTitle(contentType, tmdbId, title, originalTitle, year, posterPath, overview = null)

/**
 * Opens a Wanted card's page ([WantedCard.route]) from deep in a list; each nav host provides it,
 * so the Movies and TV Shows lists' Wanted rows need no callback through every screen between.
 */
val LocalOpenWanted = staticCompositionLocalOf<(Screen) -> Unit> { {} }
