package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles

class MovieDetailsViewModel(
    private val context: Context,
    private var movieId: String,
    private var categoryId: String,
    private var streamName: String,
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

    /** Other local catalogue entries for the same TMDB id — empty when there are none. */
    private val _alternateStreams = MutableStateFlow<List<MediaItem>>(emptyList())
    val alternateStreams: StateFlow<List<MediaItem>> = _alternateStreams.asStateFlow()

    private var relatedTitlesJob: Job? = null
    private var mediaRepository: MediaRepository? = null
    private val appSettings = AppSettings(context)

    init {
        loadMovieInfo()
    }

    /** Explicit user refresh — see SeriesDetailsViewModel.refreshSeriesInfo. */
    fun refreshMovieInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { getRepository().invalidateCachedDetail(movieId) }
            loadMovieInfo()
        }
    }

    fun loadMovieInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            loadMovieDetail()
        }
    }

    /**
     * Switches this screen to a different local catalogue entry for the same TMDB title —
     * called from the alternate-stream picker. Reuses the screen already on the backstack: no
     * navigation, and [uiState] never passes through [UiState.Loading], so the current content
     * stays on screen until the new stream's detail is ready and swaps in place.
     */
    fun switchToAlternateStream(alternate: MediaItem) {
        if (alternate.id == movieId) return
        movieId = alternate.id
        categoryId = alternate.categoryId
        streamName = alternate.name
        viewModelScope.launch(Dispatchers.IO) { loadMovieDetail() }
    }

    private suspend fun loadMovieDetail() {
        // A load already in flight belongs to the previous movie or provider — drop it.
        relatedTitlesJob?.cancel()
        _relatedTitles.value = RelatedTitles()
        _tmdbTitle.value = null
        _alternateStreams.value = emptyList()
        try {
            val repo = getRepository()
            mediaRepository = repo

            val movieResult = repo.getMovieDetail(movieId)
            movieResult.fold(
                onSuccess = { detail ->
                    val resume = resolveResumeState(repo)

                    // Check favorite
                    val isFav = repo.isFavorite(movieId, "MOVIES")

                    val categoryName =
                        repo
                            .getFilteredCategories("MOVIES")
                            .getOrNull()
                            ?.firstOrNull { it.id == categoryId }
                            ?.name

                    _uiState.value =
                        UiState.Success(
                            movieDetail = detail,
                            resumePositionMs = resume.positionMs,
                            resumeDurationMs = resume.durationMs,
                            isFavorite = isFav,
                            isWatched = resume.isWatched,
                            categoryName = categoryName,
                            categoryId = categoryId,
                            streamName = streamName,
                        )

                    loadRelatedTitles(detail)
                    loadTmdbTitle(detail)
                    loadAlternateStreams(detail)
                },
                onFailure = { e ->
                    _uiState.value =
                        UiState.Error(e.message ?: context.getString(org.njarasoa.fijerena.core.ui.R.string.movie_error_loading))
                },
            )
        } catch (e: Exception) {
            _uiState.value =
                UiState.Error(e.message ?: context.getString(org.njarasoa.fijerena.core.ui.R.string.movie_error_loading))
        }
    }

    private fun loadRelatedTitles(detail: MovieDetail) {
        relatedTitlesJob =
            viewModelScope.launch(Dispatchers.IO) {
                _relatedTitles.value =
                    runCatching {
                        getRepository().getRelatedTitles(movieId, detail.metadata.tmdbId, "MOVIES")
                    }.getOrDefault(RelatedTitles())
            }
    }

    private fun loadTmdbTitle(detail: MovieDetail) {
        viewModelScope.launch(Dispatchers.IO) {
            _tmdbTitle.value =
                runCatching {
                    getRepository().getTmdbTitle(detail.metadata.tmdbId, "MOVIES")
                }.getOrNull()
        }
    }

    private fun loadAlternateStreams(detail: MovieDetail) {
        viewModelScope.launch(Dispatchers.IO) {
            _alternateStreams.value =
                runCatching {
                    getRepository().getAlternateStreams(movieId, detail.metadata.tmdbId, "MOVIES")
                }.getOrDefault(emptyList())
        }
    }

    /** Resume-bar state and watched flag, both derived from the same `watch_state` lookup. */
    private data class ResumeState(
        val positionMs: Long,
        val durationMs: Long,
        val isWatched: Boolean,
    )

    private suspend fun resolveResumeState(repo: MediaRepository): ResumeState {
        val watched = repo.getPlaybackPositionSuspend(movieId, "MOVIES")
        var positionMs = 0L
        var durationMs = 0L
        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
            if (progress in 2.0..95.0) {
                positionMs = watched.playbackPosition
                durationMs = watched.duration
            }
        }
        // TMDB dedup (Phase 5, plans/watch-state-durable-storage-plan.md): a different catalogue
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
        return container.getMediaRepository()
    }

    fun toggleFavorite(movieName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch

            if (currentState.isFavorite) {
                repo.removeFavorite(movieId, "MOVIES")
                _uiState.value = currentState.copy(isFavorite = false)
            } else {
                repo.addFavorite(movieId, movieName, categoryId, "MOVIES")
                _uiState.value = currentState.copy(isFavorite = true)
            }
        }
    }

    /**
     * Manual watched/unwatched mark (Phase 6, plans/watch-state-durable-storage-plan.md). Marking
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
            repo.setWatched(movieId, "MOVIES", !currentState.isWatched)
            // Re-derived rather than assumed: setWatched is a suspend call, not fire-and-forget,
            // so its write (if any) has already landed by the time it returns — but it silently
            // no-ops for a server-backed provider (Jellyfin owns this state server-side and has
            // no local write to accept), so blindly flipping isWatched here would show a check
            // that the server was never told about.
            val resume = resolveResumeState(repo)
            _uiState.value =
                currentState.copy(isWatched = resume.isWatched, resumePositionMs = resume.positionMs, resumeDurationMs = resume.durationMs)
        }
    }
}

class MovieDetailsViewModelFactory(
    private val context: Context,
    private val movieId: String,
    private val categoryId: String,
    private val movieName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MovieDetailsViewModel(context.applicationContext, movieId, categoryId, movieName) as T
}
