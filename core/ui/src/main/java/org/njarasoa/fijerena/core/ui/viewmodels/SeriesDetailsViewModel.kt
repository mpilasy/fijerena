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
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class SeriesDetailsViewModel(
    private val context: android.content.Context,
    private var seriesId: String,
    private var categoryId: String,
    private var seriesName: String,
) : ViewModel() {
    private var repository: MediaRepository? = null

    /** The repository used to load this series, once loaded — needed by episode-list children. */
    val mediaRepository: MediaRepository? get() = repository

    private suspend fun ensureRepo(): MediaRepository {
        if (repository == null) {
            val container =
                org.njarasoa.fijerena.core.ui.di.AppContainer
                    .getInstance(context)
            repository = container.getMediaRepository()
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

    private var relatedTitlesJob: Job? = null
    private var relatedTitlesTmdbId: String? = null
    private var tmdbTitleJob: Job? = null
    private var tmdbTitleTmdbId: String? = null

    /** Other local catalogue entries for the same TMDB id — empty when there are none. */
    private val _alternateStreams = MutableStateFlow<List<MediaItem>>(emptyList())
    val alternateStreams: StateFlow<List<MediaItem>> = _alternateStreams.asStateFlow()

    private var alternateStreamsJob: Job? = null
    private var alternateStreamsTmdbId: String? = null

    init {
        loadSeriesInfo()
    }

    /**
     * Explicit user refresh: drop what the provider cached for this series first, so the reload
     * actually goes back to the server. Without it the refresh action re-serves the cached detail
     * — including an empty one — and appears to do nothing.
     */
    fun refreshSeriesInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ensureRepo().invalidateCachedDetail(seriesId) }
            // Deliberately not from the cache: an explicit refresh that redraws the stored copy
            // first is the "refresh appears to do nothing" bug 6f031cf6 fixed.
            loadSeriesDetail(useCache = false, isSwitch = false)
        }
    }

    fun loadSeriesInfo(useCache: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            loadSeriesDetail(useCache = useCache, isSwitch = false)
        }
    }

    /**
     * Switches this screen to a different local catalogue entry for the same TMDB title —
     * called from the alternate-stream picker. Reuses the screen already on the backstack: no
     * navigation, and [uiState] never passes through [UiState.Loading], so the current content
     * stays on screen until the new stream's detail is ready and swaps in place.
     */
    fun switchToAlternateStream(alternate: MediaItem) {
        if (alternate.id == seriesId) return
        seriesId = alternate.id
        categoryId = alternate.categoryId
        seriesName = alternate.name
        viewModelScope.launch(Dispatchers.IO) {
            loadSeriesDetail(useCache = true, isSwitch = true)
        }
    }

    private suspend fun loadSeriesDetail(
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
        alternateStreamsJob?.cancel()
        alternateStreamsJob = null
        _alternateStreams.value = emptyList()
        try {
            val repo = ensureRepo()

            // Draw what was stored last time before asking the provider. Xtream re-sends the
            // whole episode list on every visit and never caches it server-side, so a show the
            // size of Law & Order left the screen on a spinner for twenty seconds after a
            // restart — showing episodes it already had on disk the whole time. The fetch below
            // still runs: only it can notice episodes added since.
            val cached =
                if (useCache) runCatching { repo.getCachedSeriesDetail(SeriesId(seriesId)) }.getOrNull() else null
            if (cached != null) {
                _uiState.value =
                    UiState.Success(
                        seriesDetail = cached,
                        isFavorite = repo.isFavorite(seriesId, "TV_SHOWS"),
                        categoryName = categoryNameOrNull(repo),
                        categoryId = categoryId,
                        streamName = seriesName,
                    )
                loadRelatedTitles(cached)
                loadTmdbTitle(cached)
                loadAlternateStreams(cached)
            } else if (!isSwitch) {
                // A stream switch never flashes Loading — the previous stream's content stays on
                // screen until this fetch lands (or fails, see below).
                _uiState.value = UiState.Loading
            }

            val result = repo.getSeriesDetail(SeriesId(seriesId))
            result.fold(
                onSuccess = { detail ->
                    _uiState.value =
                        UiState.Success(
                            seriesDetail = detail,
                            isFavorite = repo.isFavorite(seriesId, "TV_SHOWS"),
                            categoryName = categoryNameOrNull(repo),
                            categoryId = categoryId,
                            streamName = seriesName,
                        )

                    loadRelatedTitles(detail)
                    loadTmdbTitle(detail)
                    loadAlternateStreams(detail)
                },
                // A failed refresh must not blank a screen already drawn from the cache — the
                // episodes on it are still playable. A failed switch is different: uiState may
                // still be showing the *previous* stream under the id this just switched to, so
                // it must surface the error rather than silently leave that mismatch on screen.
                onFailure = { e -> if (isSwitch) reportSwitchFailure(e) else reportFailure(e) },
            )
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
                    runCatching {
                        ensureRepo().getRelatedTitles(seriesId, tmdbId, "TV_SHOWS")
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
                    runCatching {
                        ensureRepo().getTmdbTitle(tmdbId, "TV_SHOWS")
                    }.getOrNull()
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
                _alternateStreams.value =
                    runCatching {
                        ensureRepo().getAlternateStreams(seriesId, tmdbId, "TV_SHOWS")
                    }.getOrDefault(emptyList())
            }
    }

    private suspend fun categoryNameOrNull(repo: MediaRepository): String? =
        repo
            .getFilteredCategories("TV_SHOWS")
            .getOrNull()
            ?.firstOrNull { it.id == categoryId }
            ?.name

    private fun reportFailure(e: Throwable) {
        if (_uiState.value is UiState.Success) return
        _uiState.value =
            UiState.Error(e.message ?: context.getString(org.njarasoa.fijerena.core.ui.R.string.series_error_load_failed))
    }

    private fun reportSwitchFailure(e: Throwable) {
        _uiState.value =
            UiState.Error(e.message ?: context.getString(org.njarasoa.fijerena.core.ui.R.string.series_error_load_failed))
    }

    fun toggleFavorite(seriesName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value as? UiState.Success ?: return@launch
            val repo = ensureRepo()

            if (currentState.isFavorite) {
                repo.removeFavorite(seriesId, "TV_SHOWS")
                _uiState.value = currentState.copy(isFavorite = false)
            } else {
                repo.addFavorite(seriesId, seriesName, categoryId, "TV_SHOWS")
                _uiState.value = currentState.copy(isFavorite = true)
            }
        }
    }
}

class SeriesDetailsViewModelFactory(
    private val context: Context,
    private val seriesId: String,
    private val categoryId: String,
    private val seriesName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SeriesDetailsViewModel(context.applicationContext, seriesId, categoryId, seriesName) as T
}
