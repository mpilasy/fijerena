package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class SeriesDetailsViewModel(
    private val context: android.content.Context,
    private val seriesId: String,
    private val categoryId: String,
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
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
            loadSeriesInfo(useCache = false)
        }
    }

    fun loadSeriesInfo(useCache: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
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
                        )
                } else {
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
                            )
                    },
                    // A failed refresh must not blank a screen already drawn from the cache — the
                    // episodes on it are still playable.
                    onFailure = { e -> reportFailure(e) },
                )
            } catch (e: Exception) {
                reportFailure(e)
            }
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SeriesDetailsViewModel(context.applicationContext, seriesId, categoryId) as T
}
