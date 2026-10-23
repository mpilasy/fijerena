package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.CatalogueLookup
import org.njarasoa.fijerena.core.network.wanted.ReleaseStatus
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.trailerUrl
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.wanted.profileXtreamSources
import org.njarasoa.fijerena.core.ui.wanted.relatedInSources

/**
 * The TMDB details page of a film or show that no source of the profile may have (wanted titles
 * plan → The details page): TMDB's plot, rating and art, whether it is on the Wanted list, and
 * the source copy to open instead once a source has it.
 */
class TmdbTitleViewModel(
    private val context: Context,
    val title: TmdbTitle,
) : ViewModel() {
    /** What TMDB adds to what the route carries; each part null when TMDB has none or fails. */
    data class Details(
        val overview: String? = null,
        val rating: Double? = null,
        val backdropUrl: String? = null,
        val logoUrl: String? = null,
        /** Where the title is in its release (wishlist UX plan, Phase 3); null until TMDB answers. */
        val release: ReleaseStatus? = null,
        // As a film page shows them: genres, a film's length, its first release day, its
        // director or a show's makers, and the cast in billing order.
        val genres: String? = null,
        val runtimeSeconds: String? = null,
        val releaseDate: String? = null,
        val director: String? = null,
        val cast: List<String> = emptyList(),
        /** The trailer on YouTube, which the Trailer button opens as on a film page. */
        val trailerUrl: String? = null,
    )

    private val _details = MutableStateFlow(Details(overview = title.overview))
    val details: StateFlow<Details> = _details.asStateFlow()

    private val wanted = WantedTitles.getInstance(context)

    val isWanted: StateFlow<Boolean> =
        wanted.state
            .map { state -> state.titles.any { it.contentType == title.contentType && it.tmdbId == title.tmdbId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _copy = MutableStateFlow<CatalogueHit?>(null)

    /** The first source copy of the title, when a source has it: the page opens it instead. */
    val copy: StateFlow<CatalogueHit?> = _copy.asStateFlow()

    private val _related = MutableStateFlow(RelatedTitles())

    /**
     * More like this, as a film page has it: titles the profile's first source has, then titles no
     * source has. Each title carries the source it opens on.
     */
    val related: StateFlow<RelatedTitles> = _related.asStateFlow()

    init {
        viewModelScope.launchGuarded("TmdbTitleViewModel.details", Dispatchers.IO) { _details.value = loadDetails() }
        viewModelScope.launchGuarded("TmdbTitleViewModel.related", Dispatchers.IO) { _related.value = loadRelated() }
        // Worked out now and again each time the Wanted state is (after a catalogue sync, say).
        viewModelScope.launchGuarded("TmdbTitleViewModel.copy", Dispatchers.IO) {
            wanted.state.collect { _copy.value = findCopy() }
        }
    }

    private suspend fun loadDetails(): Details {
        val tmdb = TmdbApiService.getInstance(context)
        val id = title.tmdbId.toIntOrNull()
        return if (id == null || !tmdb.hasApiKey()) {
            _details.value
        } else {
            val movie = title.contentType == ContentType.MOVIES
            val info = orNull { tmdb.getDetailsWithCredits(movie, id) }
            val images = orNull { if (movie) tmdb.getMovieImages(id) else tmdb.getTvImages(id) }
            val releaseInfo =
                if (movie) {
                    orNull { tmdb.getMovieReleaseDates(id) }?.let {
                        ReleaseStatus.ofMovie(
                            it,
                            java.util.Locale
                                .getDefault()
                                .country,
                        )
                    }
                } else {
                    info?.let(ReleaseStatus::ofShow)
                }
            Details(
                overview = info?.overview?.takeIf { it.isNotBlank() } ?: title.overview,
                rating = info?.voteAverage?.takeIf { it > 0 },
                backdropUrl = images?.let { TmdbApiService.bestBackdropUrl(it.backdrops) },
                logoUrl = images?.let { TmdbApiService.bestLogoUrl(it.logos) },
                release = releaseInfo?.let { ReleaseStatus.of(title.contentType, it, java.time.LocalDate.now()) },
                genres =
                    info
                        ?.genres
                        ?.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                        ?.joinToString(", ")
                        ?.ifEmpty { null },
                runtimeSeconds = info?.runtime?.takeIf { it > 0 }?.let { (it * SECONDS_PER_MINUTE).toString() },
                releaseDate = (info?.releaseDate ?: info?.firstAirDate)?.takeIf { it.isNotBlank() },
                director = info?.let(::makers),
                cast =
                    info
                        ?.credits
                        ?.cast
                        .orEmpty()
                        .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                        .take(MAX_CAST),
                trailerUrl =
                    info?.videos?.let { videos ->
                        trailerUrl(
                            TmdbApiService.bestTrailerKey(
                                videos.results,
                                java.util.Locale
                                    .getDefault()
                                    .language,
                            ),
                        )
                    },
            )
        }
    }

    // TMDB's extras are a bonus: one call failing leaves its part empty, never an error.
    private suspend fun <T> orNull(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("TmdbTitleViewModel", "TMDB ${title.contentType} ${title.tmdbId}: ${e.message}")
            null
        }

    // A film's director(s), else a show's makers.
    private fun makers(info: TmdbRecommendation): String? =
        (
            info.credits
                ?.crew
                .orEmpty()
                .filter { it.job == DIRECTOR }
                .mapNotNull { it.name } + info.createdBy.mapNotNull { it.name }
        ).filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifEmpty { null }

    private suspend fun loadRelated(): RelatedTitles {
        val source = xtreamSources().firstOrNull() ?: return RelatedTitles()
        val related = AppContainer.getInstance(context).getMediaRepository(source).getRelatedTitles("", title.tmdbId, title.contentType)

        fun List<MediaItem>.onSource() = map { if (it.providerId == 0L) it.copy(providerId = source) else it }
        val onSource = related.copy(collection = related.collection.onSource(), moreLikeThis = related.moreLikeThis.onSource())
        return relatedInSources(context, onSource, title.contentType)
    }

    private suspend fun xtreamSources(): List<Long> = profileXtreamSources(context)

    private suspend fun findCopy(): CatalogueHit? {
        val sources = xtreamSources()
        return CatalogueLookup(XtreamDatabase.getInstance(context))
            .find(sources, title.contentType, title.tmdbId, listOfNotNull(title.title, title.originalTitle), title.year)
            .firstOrNull()
    }

    fun toggleWanted() {
        viewModelScope.launchGuarded("TmdbTitleViewModel.toggleWanted", Dispatchers.IO) {
            if (isWanted.value) {
                wanted.remove(title.key)
            } else {
                wanted.add(title.toWanted(AppSettings(context).activeProfileId, System.currentTimeMillis()))
            }
        }
    }

    /** The page is opening [copy] instead: a wanted title's "available" badge goes. */
    fun openingCopy() {
        if (isWanted.value) viewModelScope.launchGuarded("TmdbTitleViewModel.seen", Dispatchers.IO) { wanted.markSeen(title.key) }
    }

    class Factory(
        context: Context,
        private val title: TmdbTitle,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TmdbTitleViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            return TmdbTitleViewModel(appContext, title) as T
        }
    }

    private companion object {
        const val DIRECTOR = "Director"
        const val SECONDS_PER_MINUTE = 60
        const val MAX_CAST = 20
    }
}
