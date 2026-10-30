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
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.isResumableAt
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.MergedLists
import org.njarasoa.fijerena.core.ui.utils.launchGuarded
import org.njarasoa.fijerena.core.ui.wanted.relatedInSources

class MovieDetailsViewModel(
    private val context: Context,
    private var movieId: String,
    private var categoryId: String,
    private var streamName: String,
    // The source the film is on (the route's).
    private val providerId: Long = 0L,
) : ViewModel() {
    sealed class UiState {
        data object Loading : UiState()

        data class Success(
            val movieDetail: MovieDetail,
            val resumePositionMs: Long,
            val resumeDurationMs: Long,
            val isFavorite: Boolean,
            val isWatched: Boolean,
            /** Null when the category can't be resolved (unknown id, or hidden by category filters). */
            val categoryName: String? = null,
            /** Id of the category button above, and of [onCategorySelected][MovieDetailsViewModel] target — tracks [switchToAlternateStream]. */
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
     * Related-title rows for the movie on screen, empty until they resolve and empty when there is
     * nothing to show. Deliberately separate from [uiState]: the rows are a bonus that must never
     * delay the detail screen or fail it.
     */
    private val _relatedTitles = MutableStateFlow(RelatedTitles())
    val relatedTitles: StateFlow<RelatedTitles> = _relatedTitles.asStateFlow()

    /** TMDB's own title for the movie, shown next to the provider's (often raw) stream name. */
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

    /** TV detail hero background: TMDB's backdrop art, falling back to the poster, then no
     * image — movies have no other backdrop source. See the tv detail hero ui plan. */
    private val _backdropUrl = MutableStateFlow<String?>(null)
    val backdropUrl: StateFlow<String?> = _backdropUrl.asStateFlow()

    /**
     * Other local catalogue entries for the same TMDB id, on every Xtream source of the profile,
     * each with its source ([MediaItem.providerId]) — empty when there are none.
     */
    private val _alternateStreams = MutableStateFlow<List<MediaItem>>(emptyList())
    val alternateStreams: StateFlow<List<MediaItem>> = _alternateStreams.asStateFlow()

    /** Each source's name by id when the profile merges more than one source (the alternates show it), else empty. */
    private val _sourceNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sourceNames: StateFlow<Map<Long, String>> = _sourceNames.asStateFlow()

    private var relatedTitlesJob: Job? = null

    // The detail load in flight (init, refresh, retry, switch); each new one cancels it, so a slow
    // load of the copy before a switch cannot publish its details under the new copy. Main only.
    private var loadJob: Job? = null

    // Each viewModelScope.launch(Dispatchers.IO) call may run on a different pool thread, so a
    // write here has no guaranteed visibility to the next one without @Volatile.
    @Volatile
    private var mediaRepository: MediaRepository? = null
    private val appSettings = AppSettings(context)

    init {
        loadMovieInfo()
    }

    /** Explicit user refresh — see SeriesDetailsViewModel.refreshSeriesInfo. */
    fun refreshMovieInfo() {
        val shown = ShownFilm(movieId, categoryId, streamName)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                suspendRunCatching { getRepository().invalidateCachedDetail(shown.id) }
                _uiState.value = UiState.Loading
                _alternateStreams.value = emptyList()
                loadMovieDetail(shown)
            }
    }

    fun loadMovieInfo() {
        val shown = ShownFilm(movieId, categoryId, streamName)
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = UiState.Loading
                _alternateStreams.value = emptyList()
                loadMovieDetail(shown)
            }
    }

    /**
     * Switches this screen to a different local catalogue entry for the same TMDB title —
     * called from the alternate-stream picker. Reuses the screen already on the backstack: no
     * navigation, and [uiState] never passes through [UiState.Loading], so the current content
     * stays on screen until the new stream's detail is ready and swaps in place.
     */

    /** The film shown's category: the route's, or the copy's after [switchToAlternateStream] — what Play records. */
    val currentCategoryId: String get() = categoryId

    fun switchToAlternateStream(alternate: MediaItem) {
        if (alternate.id == movieId || otherSourceOf(alternate) != null) return
        movieId = alternate.id
        categoryId = alternate.categoryId
        streamName = alternate.name
        val shown = ShownFilm(movieId, categoryId, streamName)
        // The copies stay listed until the new ones load: the picker row is focused across the
        // switch, and with no copies it is plain text that cannot hold focus (TV focus went dead).
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) { loadMovieDetail(shown) }
    }

    /** The film a load is for, read on Main when the load started: the fields can change under a slow load. */
    private data class ShownFilm(
        val id: String,
        val categoryId: String,
        val streamName: String,
    )

    /**
     * The source of [alternate] when it is on another one than this screen's, null on this one: a
     * copy on another source opens there as its own screen (its route's source), so its details,
     * Play, progress and favourite are that source's — [switchToAlternateStream] leaves it alone.
     */
    fun otherSourceOf(alternate: MediaItem): Long? {
        val own = mediaRepository?.getProviderId() ?: providerId
        return alternate.providerId.takeIf { it != 0L && it != own }
    }

    private suspend fun loadMovieDetail(shown: ShownFilm) {
        // A load already in flight belongs to the previous movie or provider — drop it.
        relatedTitlesJob?.cancel()
        _relatedTitles.value = RelatedTitles()
        _tmdbTitle.value = null
        _credits.value = null
        _logoUrl.value = null
        _backdropUrl.value = null
        try {
            val repo = getRepository()
            mediaRepository = repo

            val movieResult = repo.getMovieDetail(shown.id)
            // Superseded by a switch or a refresh: leave the screen to the load that replaced this one.
            currentCoroutineContext().ensureActive()
            movieResult.fold(
                onSuccess = { detail ->
                    val resume = resolveResumeState(repo, shown.id)

                    val isFav = repo.isFavorite(shown.id, "MOVIES")

                    val categoryName =
                        repo
                            .getFilteredCategories("MOVIES")
                            .getOrNull()
                            ?.firstOrNull { it.id == shown.categoryId }
                            ?.name

                    currentCoroutineContext().ensureActive()
                    _uiState.value =
                        UiState.Success(
                            movieDetail = detail,
                            resumePositionMs = resume.positionMs,
                            resumeDurationMs = resume.durationMs,
                            isFavorite = isFav,
                            isWatched = resume.isWatched,
                            categoryName = categoryName,
                            categoryId = shown.categoryId,
                            streamName = shown.streamName,
                        )

                    loadRelatedTitles(detail)
                    loadTmdbTitle(detail)
                    loadArtwork(detail)
                    loadTmdbTrailer(detail)
                    loadAlternateStreams(detail)
                },
                onFailure = { e ->
                    _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
                    findCopiesAfterFailure()
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = UiState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
            findCopiesAfterFailure()
        }
    }

    /**
     * The page couldn't load: other copies of the film (the same TMDB id in the catalogue on the
     * device, on this source or another) fill [alternateStreams], for the error to offer.
     */
    private fun findCopiesAfterFailure() {
        val id = movieId
        viewModelScope.launchGuarded("find other copies", Dispatchers.IO) {
            suspendRunCatching {
                val repo = getRepository()
                val tmdbId = repo.getCatalogueTmdbId(id, "MOVIES") ?: return@suspendRunCatching
                val lists = MergedLists.withOthers(AppContainer.getInstance(context), repo)
                val copies = lists.alternateStreams(id, tmdbId, "MOVIES")
                if (id != movieId) return@suspendRunCatching
                _sourceNames.value = lists.sourceNames
                _alternateStreams.value = copies
            }
        }
    }

    private fun loadRelatedTitles(detail: MovieDetail) {
        relatedTitlesJob =
            viewModelScope.launch(Dispatchers.IO) {
                _relatedTitles.value =
                    suspendRunCatching {
                        relatedInSources(context, getRepository().getRelatedTitles(movieId, detail.metadata.tmdbId, "MOVIES"), "MOVIES")
                    }.getOrDefault(RelatedTitles())
            }
    }

    private fun loadTmdbTitle(detail: MovieDetail) {
        viewModelScope.launch(Dispatchers.IO) {
            _tmdbTitle.value =
                suspendRunCatching {
                    getRepository().getTmdbTitle(detail.metadata.tmdbId, "MOVIES")
                }.getOrNull()
            // The same TMDB call as the title, kept by the provider: no extra call.
            _credits.value = suspendRunCatching { getRepository().getTmdbCredits(detail.metadata.tmdbId, "MOVIES") }.getOrNull()
        }
    }

    /**
     * TMDB's trailer, when TMDB has one: the trailer of a film whose source sends none, else the
     * fallback for a source trailer that is no longer on YouTube.
     */
    private fun loadTmdbTrailer(detail: MovieDetail) {
        viewModelScope.launchGuarded("load TMDB trailer", Dispatchers.IO) {
            val url =
                suspendRunCatching { getRepository().getTmdbTrailerUrl(detail.metadata.tmdbId, "MOVIES") }.getOrNull()
                    ?: return@launchGuarded
            _uiState.update { state ->
                val shown = (state as? UiState.Success)?.movieDetail
                if (shown?.id != detail.id) {
                    state
                } else {
                    state.copy(movieDetail = shown.copy(metadata = shown.metadata.withTmdbTrailer(url)))
                }
            }
        }
    }

    /**
     * Sets both [logoUrl] and [backdropUrl] from one job: `getTmdbLogoUrl` and
     * `getTmdbBackdropUrl` read the same TMDB `/images` response (cached in the provider), so
     * fetching them back to back here costs one network round trip, not two. The backdrop falls
     * back to the poster when TMDB has none — see the tv detail hero ui plan.
     */
    private fun loadArtwork(detail: MovieDetail) {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = getRepository()
            val tmdbId = detail.metadata.tmdbId
            _logoUrl.value = suspendRunCatching { repo.getTmdbLogoUrl(tmdbId, "MOVIES") }.getOrNull()
            _backdropUrl.value =
                suspendRunCatching { repo.getTmdbBackdropUrl(tmdbId, "MOVIES") }.getOrNull() ?: detail.coverUrl
        }
    }

    private fun loadAlternateStreams(detail: MovieDetail) {
        viewModelScope.launch(Dispatchers.IO) {
            suspendRunCatching {
                val id = movieId
                val lists = MergedLists.withOthers(AppContainer.getInstance(context), getRepository())
                // Blank id: the film itself comes back too, for its catalogue name below.
                val copies = lists.alternateStreams("", detail.metadata.tmdbId, "MOVIES")
                if (id != movieId) return@suspendRunCatching
                val own = copies.firstOrNull { it.id == id && otherSourceOf(it) == null }
                // The Stream name row names this copy as the catalogue does, wherever the screen was
                // opened from: Home and More like this pass the official or a cleaned-up title.
                own?.let { copy ->
                    streamName = copy.name
                    _uiState.update { if (it is UiState.Success && it.movieDetail.id == detail.id) it.copy(streamName = copy.name) else it }
                }
                _sourceNames.value = lists.sourceNames
                _alternateStreams.value = copies.filterNot { it === own }
            }
        }
    }

    /** Resume-bar state and watched flag, both derived from the same `watch_state` lookup. */
    private data class ResumeState(
        val positionMs: Long,
        val durationMs: Long,
        val isWatched: Boolean,
    )

    private suspend fun resolveResumeState(
        repo: MediaRepository,
        movieId: String,
    ): ResumeState {
        val watched = repo.getPlaybackPositionSuspend(movieId, "MOVIES")
        var positionMs = 0L
        var durationMs = 0L
        if (watched != null && !watched.isCompleted && isResumableAt(watched.playbackPosition, watched.duration)) {
            positionMs = watched.playbackPosition
            durationMs = watched.duration
        }
        // TMDB dedup (Phase 5, the watch state durable storage plan): a different catalogue
        // entry for the same title (a second language track, a 4K re-rip) completed under its own
        // id still has to show this one watched — otherwise the grid checks it (CategoryViewModel
        // does this same union) while its own details page contradicts that.
        val isWatched = watched?.isCompleted == true || movieId in repo.getSiblingCompletedMovieIds()
        return ResumeState(positionMs, durationMs, isWatched = isWatched)
    }

    private suspend fun getRepository(): MediaRepository {
        val container =
            org.njarasoa.fijerena.core.ui.di.AppContainer
                .getInstance(context)
        return container.getMediaRepository(providerId)
    }

    fun toggleFavorite(movieName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch

            // Written over the state as it is now, and only while the same film is on screen.
            val favorite = !currentState.isFavorite
            if (favorite) repo.addFavorite(movieId, movieName, categoryId, "MOVIES") else repo.removeFavorite(movieId, "MOVIES")
            _uiState.update { it.sameFilm(currentState)?.copy(isFavorite = favorite) ?: it }
        }
    }

    /**
     * Whether the film offers "Mark watched / unwatched": not where the source keeps watched state
     * on its server (Jellyfin). Known once the details have loaded.
     */
    val supportsWatchedMark: Boolean
        get() = mediaRepository?.supportsMarkWatched == true

    /**
     * Re-reads the resume position and the watched mark. This ViewModel stays alive while the
     * player is on top, so on the way back the state loaded on entry is out of date: Play would
     * still say Play after a first watch, or name the old resume time.
     */
    fun refreshResumeState() {
        viewModelScope.launchGuarded("MovieDetailsViewModel.refreshResumeState", Dispatchers.IO) {
            val repo = mediaRepository ?: return@launchGuarded
            val currentState = _uiState.value as? UiState.Success ?: return@launchGuarded
            val resume = resolveResumeState(repo, movieId)
            _uiState.update { it.sameFilm(currentState)?.withResume(resume) ?: it }
        }
    }

    /**
     * Manual watched/unwatched mark (Phase 6, the watch state durable storage plan). Marking
     * watched leaves the stored position in `watch_state` alone — a rewatch still resumes — but
     * hides this screen's own resume bar immediately rather than waiting for a reload, matching
     * `WatchedItem.resumeProgress()`'s rule that a completed item never offers one. Marking
     * unwatched re-derives the bar from a fresh lookup instead: the UI state doesn't cache the
     * pre-mark position anywhere once the bar is hidden, so restoring it means asking again.
     */
    fun toggleWatched() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch
            val id = movieId
            repo.setWatched(id, "MOVIES", !currentState.isWatched)
            // Re-derived rather than assumed: setWatched is a suspend call, not fire-and-forget,
            // so its write (if any) has already landed by the time it returns — but it silently
            // no-ops for a server-backed provider (Jellyfin owns this state server-side and has
            // no local write to accept), so blindly flipping isWatched here would show a check
            // that the server was never told about.
            val resume = resolveResumeState(repo, id)
            _uiState.update { it.sameFilm(currentState)?.withResume(resume) ?: it }
        }
    }

    /** This state when it is a Success for the film of [read], else null: a write after a suspend call skips another film's state. */
    private fun UiState.sameFilm(read: UiState.Success): UiState.Success? =
        (this as? UiState.Success)?.takeIf {
            it.movieDetail.id ==
                read.movieDetail.id
        }

    private fun UiState.Success.withResume(resume: ResumeState): UiState.Success =
        copy(isWatched = resume.isWatched, resumePositionMs = resume.positionMs, resumeDurationMs = resume.durationMs)
}

class MovieDetailsViewModelFactory(
    context: Context,
    private val movieId: String,
    private val categoryId: String,
    private val movieName: String,
    private val providerId: Long = 0L,
) : ViewModelProvider.Factory {
    // Store only the application context, not the raw parameter — see CategoryViewModelFactory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MovieDetailsViewModel(appContext, movieId, categoryId, movieName, providerId) as T
}
