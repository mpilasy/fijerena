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
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class SeriesDetailsViewModel(
    private val context: android.content.Context,
    private val seriesId: String,
    private val categoryId: String
) : ViewModel() {

    private var repository: org.njarasoa.fijerena.core.network.MediaRepository? = null

    private suspend fun ensureRepo(): org.njarasoa.fijerena.core.network.MediaRepository {
        if (repository == null) {
            val container = org.njarasoa.fijerena.core.ui.di.AppContainer.getInstance(context)
            repository = container.getMediaRepository()
        }
        return repository!!
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val seriesDetail: SeriesDetail,
            val isFavorite: Boolean
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var mediaRepository: MediaRepository? = null

    init {
        loadSeriesInfo()
    }

    fun loadSeriesInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val repo = ensureRepo()
                mediaRepository = repo

                val result = repo.getSeriesDetail(seriesId)
                result.fold(
                    onSuccess = { detail ->
                        val isFav = repo.isFavorite(seriesId, "TV_SHOWS")
                        _uiState.value = UiState.Success(
                            seriesDetail = detail,
                            isFavorite = isFav
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = UiState.Error(e.message ?: "Failed to load series info")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Initialization error")
            }
        }
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
    private val categoryId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SeriesDetailsViewModel(context.applicationContext, seriesId, categoryId) as T
    }
}
