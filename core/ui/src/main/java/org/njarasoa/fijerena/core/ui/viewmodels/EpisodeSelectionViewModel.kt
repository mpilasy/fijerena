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
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class EpisodeSelectionViewModel(
    private val appContext: Context,
    private val providerRepo: ProviderRepository,
    private val seriesId: String
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(
            val seriesDetail: SeriesDetail
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var mediaRepository: MediaRepository? = null

    init {
        loadSeriesDetail()
    }

    fun loadSeriesDetail(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _uiState.value = UiState.Loading

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
                val result = repo.getSeriesDetail(seriesId)
                result.fold(
                    onSuccess = { detail ->
                        _uiState.value = UiState.Success(detail)
                    },
                    onFailure = { e ->
                        _uiState.value = UiState.Error(e.message ?: "Failed to load series info")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Initialization failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun getRepository(): MediaRepository? = mediaRepository
}
