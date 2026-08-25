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
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.RelatedTitles

class MovieDetailsViewModel(
    private val context: Context,
    private val movieId: String,
    private val categoryId: String,
) : ViewModel() {
    sealed class UiState {
        data object Loading : UiState()

        data class Success(
            val movieDetail: MovieDetail,
            val resumePositionMs: Long,
            val resumeDurationMs: Long,
            val isFavorite: Boolean,
            /** Null when the category can't be resolved (unknown id, or hidden by category filters). */
            val categoryName: String? = null,
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
            // A load already in flight belongs to the previous movie or provider — drop it.
            relatedTitlesJob?.cancel()
            _relatedTitles.value = RelatedTitles()
            _tmdbTitle.value = null
            try {
                val repo = getRepository()
                mediaRepository = repo

                val movieResult = repo.getMovieDetail(movieId)
                movieResult.fold(
                    onSuccess = { detail ->
                        // Load resume position
                        var resumePos = 0L
                        var resumeDur = 0L
                        val watched = repo.getPlaybackPositionSuspend(movieId, "MOVIES")
                        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
                            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
                            if (progress in 2.0..95.0) {
                                resumePos = watched.playbackPosition
                                resumeDur = watched.duration
                            }
                        }

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
                                resumePositionMs = resumePos,
                                resumeDurationMs = resumeDur,
                                isFavorite = isFav,
                                categoryName = categoryName,
                            )

                        loadRelatedTitles(detail)
                        loadTmdbTitle(detail)
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
}

class MovieDetailsViewModelFactory(
    private val context: Context,
    private val movieId: String,
    private val categoryId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MovieDetailsViewModel(context.applicationContext, movieId, categoryId) as T
}
