package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MovieDetail

class MovieDetailsViewModel(
    private val appContext: Context,
    private val providerRepo: ProviderRepository,
    private val movieId: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val movieDetail: MovieDetail,
            val resumePositionMs: Long = 0L,
            val resumeDurationMs: Long = 0L
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private var mediaRepository: MediaRepository? = null

    init {
        loadMovieDetail()
    }

    fun loadMovieDetail() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            try {
                if (mediaRepository == null) {
                    withContext(Dispatchers.IO) {
                        val entity = providerRepo.getActiveProvider()
                        val resolvedId = entity?.id ?: 0L
                        val settings = providerRepo.getProviderSettings(resolvedId)
                        val repo = MediaRepository(appContext, resolvedId, settings)

                        if (entity != null) {
                            val password = providerRepo.getPassword(entity.id) ?: ""
                            val provider = MediaProviderFactory.create(entity, appContext, password)
                            provider.connect()
                            repo.setProvider(provider)
                        }
                        mediaRepository = repo
                    }
                }

                val repo = mediaRepository!!
                _isFavorite.value = repo.isFavoriteSuspend(movieId, "MOVIES")

                val detailResult = repo.getMovieDetail(movieId)
                detailResult.fold(
                    onSuccess = { detail ->
                        val watched = repo.getPlaybackPositionSuspend(movieId, "MOVIES")
                        var resumePos = 0L
                        var resumeDur = 0L
                        if (watched != null && !watched.isCompleted && watched.playbackPosition > 0 && watched.duration > 0) {
                            val progress = (watched.playbackPosition.toFloat() / watched.duration.toFloat()) * 100f
                            if (progress in 2.0..95.0) {
                                resumePos = watched.playbackPosition
                                resumeDur = watched.duration
                            }
                        }
                        _uiState.value = UiState.Success(detail, resumePos, resumeDur)
                    },
                    onFailure = { e ->
                        _uiState.value = UiState.Error(e.message ?: "Failed to load movie info")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Initialization failed")
            }
        }
    }

    fun toggleFavorite(movieName: String, categoryId: String) {
        viewModelScope.launch {
            val repo = mediaRepository ?: return@launch
            if (_isFavorite.value) {
                if (repo.removeFavoriteSuspend(movieId, "MOVIES")) {
                    _isFavorite.value = false
                }
            } else {
                if (repo.addFavoriteSuspend(movieId, movieName, categoryId, "MOVIES")) {
                    _isFavorite.value = true
                }
            }
        }
    }
}
