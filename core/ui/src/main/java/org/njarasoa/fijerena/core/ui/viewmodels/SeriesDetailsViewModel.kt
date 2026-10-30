package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.wanted.relatedInSources

class SeriesDetailsViewModel(
    private val context: android.content.Context,
    private var seriesId: String,
    private var categoryId: String,
    private var seriesName: String,
    // The source the show is on (the route's).
    private val providerId: Long = 0L,
) : ViewModel() {
    private val appSettings = AppSettings(context)

    // Written from Dispatchers.IO in ensureRepo(), read on Main via mediaRepository by
    // EpisodeSelectionScreen — @Volatile guarantees the write is visible across threads.
    @Volatile
    private var repository: MediaRepository? = null

    /** The repository used to load this series, once loaded — needed by episode-list children. */
    val mediaRepository: MediaRepository? get() = repository

    private suspend fun ensureRepo(): MediaRepository {
        if (repository == null) {
            val container =
                org.njarasoa.fijerena.core.ui.di.AppContainer
                    .getInstance(context)
            repository = container.getMediaRepository(providerId)
        }
        return repository!!
    }

    sealed class UiState {
        data object Loading : UiState()

        data class Success(
            val seriesDetail: SeriesDetail,
            val isFavorite: Boolean,
            /** Null when the category can't be resolved (unknown id, or hidden by category filters). */
            val categoryName: String? = null,
            /** Id of the category button above — tracks [switchToAlternateStream]. */
            val categoryId: String,
            /** The catalogue's raw name for the stream on screen — tracks [switchToAlternateStream]. */
            val streamName: String,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Related-title rows for the series on screen, empty until they resolve and empty when there
     * is nothing to show. Deliberately separate from [uiState]: the rows are a bonus that must
     * never delay the detail screen or fail it.
     */
    private val _relatedTitles = MutableStateFlow(RelatedTitles())
    val relatedTitles: StateFlow<RelatedTitles> = _relatedTitles.asStateFlow()

    /** TMDB's own title for the series, shown next to the provider's (often raw) stream name. */
    private val _tmdbTitle = MutableStateFlow<String?>(null)
    val tmdbTitle: StateFlow<String?> = _tmdbTitle.asStateFlow()

    /**
     * TMDB's cast and crew for the Cast tab (cast tab plan); null without a TMDB id or key, or when
     * TMDB fails, and the tab then shows the source's cast names.
     */
    private val _credits = MutableStateFlow<TitleCredits?>(null)
    val credits: StateFlow<TitleCredits?> = _credits.asStateFlow()

    /** TMDB's transparent-PNG wordmark art, shown in place of the plain-text headline when present. */
    private val _logoUrl = MutableStateFlow<String?>(null)
    val logoUrl: StateFlow<String?> = _logoUrl.asStateFlow()

    /** TV detail hero background: TMDB's backdrop art, falling back to the provider's own
     * backdrop, then the poster, then no image. See the tv detail hero ui plan. */
    private val _backdropUrl = MutableStateFlow<String?>(null)
    val backdropUrl: StateFlow<String?> = _backdropUrl.asStateFlow()

    private var relatedTitlesJob: Job? = null
    private var relatedTitlesTmdbId: String? = null
    private var tmdbTitleJob: Job? = null
    private var tmdbTitleTmdbId: String? = null
    private var artworkJob: Job? = null
    private var artworkTmdbId: String? = null
    private var tmdbTrailerJob: Job? = null
    private var tmdbTrailerTmdbId: String? = null

    /** TMDB's trailer for [tmdbTrailerTmdbId], once [loadTmdbTrailer] has it. */
    private var tmdbTrailerUrl: String? = null

    /**
     * Other local catalogue entries for the same TMDB id, on every Xtream source of the profile,
     * each with its source ([MediaItem.providerId]) — empty when there are none.
     */
    private val _alternateStreams = MutableStateFlow<List<MediaItem>>(emptyList())
    val alternateStreams: StateFlow<List<MediaItem>> = _alternateStreams.asStateFlow()

    /** Each source's name by id when the profile merges more than one source (the alternates show it), else empty. */
    private val _sourceNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sourceNames: StateFlow<Map<Long, String>> = _sourceNames.asStateFlow()

    private var alternateStreamsJob: Job? = null
    private var alternateStreamsTmdbId: String? = null

    // The detail load in flight (init, refresh, switch); each new one cancels it, so a slow load
    // of the copy before a switch cannot publish its episodes under the new copy. Main only.
    private var loadJob: Job? = null

    init {
        loadSeriesInfo()
    }

    /**
     * Explicit user refresh: drop what the provider cached for this series first, so the reload
     * actually goes back to the server. Without it the refresh action re-serves the cached detail
     * — including an empty one — and appears to do nothing.
     */
    fun refreshSeriesInfo() {
        val shown = PlayedSeries(seriesId, seriesName, categoryId)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                suspendRunCatching { ensureRepo().invalidateCachedDetail(shown.seriesId) }
                // Deliberately not from the cache: an explicit refresh that redraws the stored copy
                // first is the "refresh appears to do nothing" bug 6f031cf6 fixed.
                loadSeriesDetail(shown, useCache = false, isSwitch = false)
            }
    }

    fun loadSeriesInfo(useCache: Boolean = true) {
        val shown = PlayedSeries(seriesId, seriesName, categoryId)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                loadSeriesDetail(shown, useCache = useCache, isSwitch = false)
            }
    }

    /**
     * Switches this screen to a different local catalogue entry for the same TMDB title —
     * called from the alternate-stream picker. Reuses the screen already on the backstack: no
     * navigation, and [uiState] never passes through [UiState.Loading], so the current content
     * stays on screen until the new stream's detail is ready and swaps in place.
     */

    /** The show shown: the route's, or the copy's after [switchToAlternateStream] — what Play records. */
    fun playedSeries(): PlayedSeries = PlayedSeries(seriesId, seriesName, categoryId)

    fun switchToAlternateStream(alternate: MediaItem) {
        if (alternate.id == seriesId || otherSourceOf(alternate) != null) return
        seriesId = alternate.id
        categoryId = alternate.categoryId
        seriesName = alternate.name
        val shown = PlayedSeries(seriesId, seriesName, categoryId)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                loadSeriesDetail(shown, useCache = true, isSwitch = true)
            }
    }

    /**
     * The source of [alternate] when it is on another one than this screen's, null on this one: a
     * copy on another source opens there as its own screen (its route's source), so its episodes,
     * Play, progress and favourite are that source's — [switchToAlternateStream] leaves it alone.
     */
    fun otherSourceOf(alternate: MediaItem): Long? {
        val own = repository?.getProviderId() ?: providerId
        return alternate.providerId.takeIf { it != 0L && it != own }
    }

    /** Loads [shown], read on Main when the load started: the fields can change under a slow load. */
    private suspend fun loadSeriesDetail(
        shown: PlayedSeries,
        useCache: Boolean,
        isSwitch: Boolean,
    ) {
        // A load already in flight belongs to the previous series or provider — drop it.
        relatedTitlesJob?.cancel()
        relatedTitlesJob = null
        _relatedTitles.value = RelatedTitles()
        tmdbTitleJob?.cancel()
        tmdbTitleJob = null
        _tmdbTitle.value = null
        _credits.value = null
        artworkJob?.cancel()
        artworkJob = null
        tmdbTrailerJob?.cancel()
        tmdbTrailerJob = null
        tmdbTrailerUrl = null
        _logoUrl.value = null
        _backdropUrl.value = null
        alternateStreamsJob?.cancel()
        alternateStreamsJob = null
        // Kept across a switch until the new copies load: the focused picker row is plain text,
        // unable to hold focus, while the list is empty (TV focus went dead).
        if (!isSwitch) _alternateStreams.value = emptyList()
        try {
            val repo = ensureRepo()

            // Draw what was stored last time before asking the provider. Xtream re-sends the
            // whole episode list on every visit and never caches it server-side, so a show the
            // size of Law & Order left the screen on a spinner for twenty seconds after a
            // restart — showing episodes it already had on disk the whole time. The fetch below
            // still runs: only it can notice episodes added since.
            val cached =
                if (useCache) suspendRunCatching { repo.getCachedSeriesDetail(SeriesId(shown.seriesId)) }.getOrNull() else null
            if (cached != null) {
                val state =
                    UiState.Success(
                        seriesDetail = withTmdbTrailer(cached),
                        isFavorite = repo.isFavorite(shown.seriesId, "TV_SHOWS"),
                        categoryName = categoryNameOrNull(repo, shown.categoryId),
                        categoryId = shown.categoryId,
                        streamName = shown.seriesName,
                    )
                // Superseded by a switch or a refresh: leave the screen to the load that replaced this one.
                currentCoroutineContext().ensureActive()
                _uiState.value = state
                loadRelatedTitles(cached)
                loadTmdbTitle(cached)
                loadArtwork(cached)
                loadTmdbTrailer(cached)
                loadAlternateStreams(cached)
            } else if (!isSwitch) {
                // A stream switch never flashes Loading — the previous stream's content stays on
                // screen until this fetch lands (or fails, see below).
                _uiState.value = UiState.Loading
            }

            val result = repo.getSeriesDetail(SeriesId(shown.seriesId))
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { detail ->
                    val state =
                        UiState.Success(
                            seriesDetail = withTmdbTrailer(detail),
                            isFavorite = repo.isFavorite(shown.seriesId, "TV_SHOWS"),
                            categoryName = categoryNameOrNull(repo, shown.categoryId),
                            categoryId = shown.categoryId,
                            streamName = shown.seriesName,
                        )
                    currentCoroutineContext().ensureActive()
                    _uiState.value = state

                    loadRelatedTitles(detail)
                    loadTmdbTitle(detail)
                    loadArtwork(detail)
                    loadTmdbTrailer(detail)
                    loadAlternateStreams(detail)
                },
                // A failed refresh must not blank a screen already drawn from the cache — the
                // episodes on it are still playable. A failed switch is different: uiState may
                // still be showing the *previous* stream under the id this just switched to, so
                // it must surface the error rather than silently leave that mismatch on screen.
                onFailure = { e -> if (isSwitch) reportSwitchFailure(e) else reportFailure(e) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isSwitch) reportSwitchFailure(e) else reportFailure(e)
        }
    }

    /**
     * The cached detail draws first and the fetched one follows, so this runs twice per load with
     * the same TMDB id — fetch the rows only for the first of them.
     */
    private fun loadRelatedTitles(detail: SeriesDetail) {
        val tmdbId = detail.metadata.tmdbId
        if (relatedTitlesJob != null && tmdbId == relatedTitlesTmdbId) return
        relatedTitlesJob?.cancel()
        relatedTitlesTmdbId = tmdbId
        relatedTitlesJob =
            viewModelScope.launch(Dispatchers.IO) {
                _relatedTitles.value =
                    suspendRunCatching {
                        relatedInSources(context, ensureRepo().getRelatedTitles(seriesId, tmdbId, "TV_SHOWS"), "TV_SHOWS")
                    }.getOrDefault(RelatedTitles())
            }
    }

    /**
     * As [loadRelatedTitles]: the cached detail draws first and the fetched one follows with the
     * same TMDB id, so fetch only for the first of them.
     */
    private fun loadTmdbTitle(detail: SeriesDetail) {
        val tmdbId = detail.metadata.tmdbId
        if (tmdbTitleJob != null && tmdbId == tmdbTitleTmdbId) return
        tmdbTitleJob?.cancel()
        tmdbTitleTmdbId = tmdbId
        tmdbTitleJob =
            viewModelScope.launch(Dispatchers.IO) {
                _tmdbTitle.value =
                    suspendRunCatching {
                        ensureRepo().getTmdbTitle(tmdbId, "TV_SHOWS")
                    }.getOrNull()
                // The same TMDB call as the title, kept by the provider: no extra call.
                _credits.value = suspendRunCatching { ensureRepo().getTmdbCredits(tmdbId, "TV_SHOWS") }.getOrNull()
            }
    }

    /**
     * TMDB's trailer, when TMDB has one: the trailer of a show whose source sends none, else the
     * fallback for a source trailer that is no longer on YouTube. As [loadRelatedTitles],
     * fetch only for the first of the cached and the fetched detail; [withTmdbTrailer] keeps the
     * trailer on the fetched one when it lands after this.
     */
    private fun loadTmdbTrailer(detail: SeriesDetail) {
        val tmdbId = detail.metadata.tmdbId
        if (tmdbTrailerJob != null && tmdbId == tmdbTrailerTmdbId) return
        tmdbTrailerJob?.cancel()
        tmdbTrailerTmdbId = tmdbId
        tmdbTrailerUrl = null
        tmdbTrailerJob =
            viewModelScope.launchGuarded("load TMDB trailer", Dispatchers.IO) {
                val url =
                    suspendRunCatching { ensureRepo().getTmdbTrailerUrl(tmdbId, "TV_SHOWS") }.getOrNull()
                        ?: return@launchGuarded
                tmdbTrailerUrl = url
                _uiState.update { state ->
                    if (state is UiState.Success) state.copy(seriesDetail = withTmdbTrailer(state.seriesDetail)) else state
                }
            }
    }

    /** [detail] with TMDB's trailer, when it has come and is for the same title. */
    private fun withTmdbTrailer(detail: SeriesDetail): SeriesDetail {
        val url = tmdbTrailerUrl
        if (url == null || detail.metadata.tmdbId != tmdbTrailerTmdbId) return detail
        return detail.copy(metadata = detail.metadata.withTmdbTrailer(url))
    }

    /**
     * As [loadRelatedTitles]: the cached detail draws first and the fetched one follows with the
     * same TMDB id, so fetch only for the first of them.
     *
     * Sets both [logoUrl] and [backdropUrl] from one job: `getTmdbLogoUrl` and
     * `getTmdbBackdropUrl` read the same TMDB `/images` response (cached in the provider), so
     * fetching them back to back here costs one network round trip, not two. The backdrop falls
     * back to the provider's own backdrop, then the poster, when TMDB has none — see
     * the tv detail hero ui plan.
     */
    private fun loadArtwork(detail: SeriesDetail) {
        val tmdbId = detail.metadata.tmdbId
        if (artworkJob != null && tmdbId == artworkTmdbId) return
        artworkJob?.cancel()
        artworkTmdbId = tmdbId
        artworkJob =
            viewModelScope.launch(Dispatchers.IO) {
                val repo = ensureRepo()
                _logoUrl.value = suspendRunCatching { repo.getTmdbLogoUrl(tmdbId, "TV_SHOWS") }.getOrNull()
                _backdropUrl.value =
                    suspendRunCatching { repo.getTmdbBackdropUrl(tmdbId, "TV_SHOWS") }.getOrNull()
                        ?: detail.backdropUrl
                        ?: detail.coverUrl
            }
    }

    /**
     * As [loadRelatedTitles]: the cached detail draws first and the fetched one follows with the
     * same TMDB id, so fetch only for the first of them.
     */
    private fun loadAlternateStreams(detail: SeriesDetail) {
        val tmdbId = detail.metadata.tmdbId
        if (alternateStreamsJob != null && tmdbId == alternateStreamsTmdbId) return
        alternateStreamsJob?.cancel()
        alternateStreamsTmdbId = tmdbId
        alternateStreamsJob =
            viewModelScope.launch(Dispatchers.IO) {
                suspendRunCatching {
                    val id = seriesId
                    val lists = MergedLists.withOthers(AppContainer.getInstance(context), ensureRepo())
                    // Blank id: the show itself comes back too, for its catalogue name below.
                    val copies = lists.alternateStreams("", tmdbId, "TV_SHOWS")
                    if (id != seriesId) return@suspendRunCatching
                    val own = copies.firstOrNull { it.id == id && otherSourceOf(it) == null }
                    // As on the film page: the Stream name row names this copy as the catalogue does.
                    own?.let { copy ->
                        seriesName = copy.name
                        _uiState.update {
                            if (it is UiState.Success &&
                                it.seriesDetail.id == detail.id
                            ) {
                                it.copy(streamName = copy.name)
                            } else {
                                it
                            }
                        }
                    }
                    _sourceNames.value = lists.sourceNames
                    _alternateStreams.value = copies.filterNot { it === own }
                }
            }
    }

    private suspend fun categoryNameOrNull(
        repo: MediaRepository,
        categoryId: String,
    ): String? =
        repo
            .getFilteredCategories("TV_SHOWS")
            .getOrNull()
            ?.firstOrNull { it.id == categoryId }
            ?.name

    private fun reportFailure(e: Throwable) {
        if (_uiState.value is UiState.Success) return
        _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
        findCopiesAfterFailure()
    }

    private fun reportSwitchFailure(e: Throwable) {
        _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
        findCopiesAfterFailure()
    }

    /**
     * The page couldn't load: other copies of the show (the same TMDB id in the catalogue on the
     * device, on this source or another) fill [alternateStreams], for the error to offer.
     */
    private fun findCopiesAfterFailure() {
        val id = seriesId
        viewModelScope.launchGuarded("find other copies", Dispatchers.IO) {
            suspendRunCatching {
                val repo = ensureRepo()
                val tmdbId = repo.getCatalogueTmdbId(id, "TV_SHOWS") ?: return@suspendRunCatching
                val lists = MergedLists.withOthers(AppContainer.getInstance(context), repo)
                val copies = lists.alternateStreams(id, tmdbId, "TV_SHOWS")
                if (id != seriesId) return@suspendRunCatching
                _sourceNames.value = lists.sourceNames
                _alternateStreams.value = copies
            }
        }
    }

    fun toggleFavorite(seriesName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val id = seriesId
            val catId = categoryId
            val repo = ensureRepo()

            // Written over the state as it is now, and only while the same show is on screen.
            val favorite = !currentState.isFavorite
            if (favorite) repo.addFavorite(id, seriesName, catId, "TV_SHOWS") else repo.removeFavorite(id, "TV_SHOWS")
            _uiState.update {
                if (it is UiState.Success && it.seriesDetail.id == currentState.seriesDetail.id) it.copy(isFavorite = favorite) else it
            }
        }
    }
}

class SeriesDetailsViewModelFactory(
    context: Context,
    private val seriesId: String,
    private val categoryId: String,
    private val seriesName: String,
    private val providerId: Long = 0L,
) : ViewModelProvider.Factory {
    // Store only the application context, not the raw parameter — see CategoryViewModelFactory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SeriesDetailsViewModel(appContext, seriesId, categoryId, seriesName, providerId) as T
}

/** The show an episode is played from, as Play records it in Recent and progress. */
data class PlayedSeries(
    val seriesId: String,
    val seriesName: String,
    val categoryId: String,
)
