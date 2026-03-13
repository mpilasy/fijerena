package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.ai.AiManager
import org.njarasoa.fijerena.core.network.ai.VectorizationState
import org.njarasoa.fijerena.core.network.ai.VectorizationTier
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

class AiSettingsViewModel(
    private val context: Context
) : ViewModel() {

    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    val isPremiumDevice: Boolean = AiManager.detectTier() == VectorizationTier.PREMIUM

    data class AiStats(
        val totalStreams: Int = 0,
        val processedStreams: Int = 0,
        val totalCategories: Int = 0,
        val processedCategories: Int = 0,
        val totalSeries: Int = 0,
        val processedSeries: Int = 0,
        val totalEpisodes: Int = 0,
        val processedEpisodes: Int = 0
    ) {
        val totalItems: Int get() = totalStreams + totalCategories + totalSeries + totalEpisodes
        val totalProcessed: Int get() = processedStreams + processedCategories + processedSeries + processedEpisodes
        val progress: Float get() = if (totalItems > 0) totalProcessed.toFloat() / totalItems else 0f
    }

    private val _stats = MutableStateFlow(AiStats())
    val stats: StateFlow<AiStats> = _stats.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        // Observe AI processing state
        viewModelScope.launch {
            AiManager.getVectorizationState().collect { state ->
                _isAiProcessing.value = state is VectorizationState.Processing
                if (state is VectorizationState.Completed || state is VectorizationState.Idle) {
                    refreshStats()
                }
            }
        }
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val xtreamDb = XtreamDatabase.getInstance(context)
                    val streamDao = xtreamDb.streamDao()
                    val categoryDao = xtreamDb.categoryDao()
                    val seriesDao = xtreamDb.seriesDao()
                    val episodeDao = xtreamDb.episodeDao()
                    val settingsDb = SettingsDatabase.getInstance(context)
                    
                    val providers = settingsDb.providerDao().getAllProvidersList()
                    android.util.Log.i("AiSettingsViewModel", "Found ${providers.size} providers to refresh stats")
                    if (providers.isEmpty()) {
                        android.util.Log.i("AiSettingsViewModel", "No providers found to refresh stats")
                    }
                    var totalStreams = 0
                    var totalCategories = 0
                    var totalSeries = 0
                    var totalEpisodes = 0
                    var processedStreams = 0
                    var processedCategories = 0
                    var processedSeries = 0
                    var processedEpisodes = 0

                    for (p in providers) {
                        val streams = streamDao.countStreams(p.id, "LIVE") + streamDao.countStreams(p.id, "VOD")
                        val categories = categoryDao.getCategories(p.id, "LIVE").size + 
                                          categoryDao.getCategories(p.id, "VOD").size +
                                          categoryDao.getCategories(p.id, "SERIES").size
                        val series = seriesDao.countSeries(p.id)
                        val episodes = episodeDao.countEpisodes(p.id)
                        
                        val procStreams = streamDao.getStreamsWithEmbeddings(p.id).size
                        val procCategories = categoryDao.getCategoriesWithEmbeddings(p.id).size
                        val procSeries = seriesDao.getSeriesWithEmbeddings(p.id).size
                        val procEpisodes = episodeDao.getEpisodesWithEmbeddings(p.id).size
                        
                        android.util.Log.d("AiSettingsViewModel", "Provider ${p.id}: streams=$procStreams/$streams, cats=$procCategories/$categories, series=$procSeries/$series, eps=$procEpisodes/$episodes")
                        
                        totalStreams += streams
                        totalCategories += categories
                        totalSeries += series
                        totalEpisodes += episodes
                        
                        processedStreams += procStreams
                        processedCategories += procCategories
                        processedSeries += procSeries
                        processedEpisodes += procEpisodes
                    }

                    _stats.value = AiStats(
                        totalStreams = totalStreams,
                        processedStreams = processedStreams,
                        totalCategories = totalCategories,
                        processedCategories = processedCategories,
                        totalSeries = totalSeries,
                        processedSeries = processedSeries,
                        totalEpisodes = totalEpisodes,
                        processedEpisodes = processedEpisodes
                    )
                } catch (e: Throwable) {
                    android.util.Log.e("AiSettingsViewModel", "Failed to refresh stats: ${e.message}", e)
                }
            }
        }
    }

    fun scheduleVectorization() {
        if (!isPremiumDevice) {
            _toastMessage.tryEmit("AI features not supported on this device")
            return
        }
        AiManager.getProvider()?.scheduleVectorization()
        _toastMessage.tryEmit("AI vectorization scheduled in background")
    }
}
