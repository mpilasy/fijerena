package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram

class StreamLoaderViewModel(
    private val context: Context,
    private val initialStreamId: String,
    private val initialStreamName: String,
    private val categoryId: String,
    private val contentType: String,
    private val episodeId: String? = null,
    private val episodeExtension: String? = null,
    private val seriesId: String? = null,
    private val seriesName: String? = null,
    private val startFromBeginning: Boolean = false
) : ViewModel() {

    sealed class StreamState {
        data object Loading : StreamState()
        data class Success(
            val streamUrl: String,
            val streamHeaders: Map<String, String>,
            val streamName: String,
            val streamId: String,
            val resumePosition: Long,
            val isLive: Boolean,
            val categoryStreams: List<MediaItem> = emptyList(),
            val lastWatchedStreams: List<MediaItem> = emptyList(),
            val currentEpgProgram: EpgProgram? = null,
            val nextEpgProgram: EpgProgram? = null,
            val isFavorite: Boolean = false
        ) : StreamState()
        data class Error(val message: String) : StreamState()
    }

    private val _state = MutableStateFlow<StreamState>(StreamState.Loading)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var mediaRepository: MediaRepository? = null
    private val appSettings = AppSettings(context)

    // Internal state tracking
    private var currentStreamIndex = -1
    private var streamList: List<MediaItem> = emptyList()

    // Avoid re-fetching EPG too often
    private var lastEpgFetchTime = 0L

    // Job to handle delayed history saving (mimics original 5s delay)
    private var historyJob: Job? = null

    init {
        initializeAndLoad()
    }

    private fun initializeAndLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Initialize Repository
                val providerRepo = ProviderRepository(context)
                val entity = providerRepo.getActiveProvider()

                val repo = if (entity != null) {
                    val settings = providerRepo.getProviderSettings(entity.id)
                    val resolvedRepo = MediaRepository(context, entity.id, settings)
                    val password = providerRepo.getPassword(entity.id) ?: ""
                    val provider = MediaProviderFactory.create(entity, context, password)
                    resolvedRepo.setProvider(provider)
                    resolvedRepo
                } else {
                    MediaRepository(context, 0L)
                }
                mediaRepository = repo

                // 2. Load Channel List (if Live TV) & Last Watched
                var currentStreams: List<MediaItem> = emptyList()
                var lastWatched: List<MediaItem> = emptyList()

                if (contentType == "LIVE_TV") {
                    val result = repo.getItems(categoryId, contentType)
                    result.fold(
                        onSuccess = { items ->
                            currentStreams = items
                            streamList = items
                            currentStreamIndex = items.indexOfFirst { it.id == initialStreamId }
                            if (currentStreamIndex == -1 && items.isNotEmpty()) currentStreamIndex = 0
                        },
                        onFailure = { Log.e("StreamLoader", "Failed to load category streams", it) }
                    )

                    lastWatched = repo.getWatchHistoryForContentTypeSuspend(contentType)
                }

                // 3. Resolve Initial Stream
                loadStreamInternal(
                    streamId = initialStreamId,
                    streamName = initialStreamName,
                    currentStreams = currentStreams,
                    lastWatched = lastWatched
                )

            } catch (e: Exception) {
                Log.e("StreamLoader", "Initialization error", e)
                _state.value = StreamState.Error(e.message ?: "Initialization failed")
            }
        }
    }

    private suspend fun loadStreamInternal(
        streamId: String,
        streamName: String,
        currentStreams: List<MediaItem>,
        lastWatched: List<MediaItem>
    ) {
        val repo = mediaRepository ?: return

        try {
            // Resolve URL
            val result = repo.resolvePlayableStream(
                itemId = streamId,
                contentType = contentType,
                episodeId = episodeId,
                extension = episodeExtension
            )

            result.fold(
                onSuccess = { playable ->
                    // Determine Resume Position
                    var resumePos = 0L
                    if (!startFromBeginning && contentType != "LIVE_TV" && appSettings.autoResumeEnabled) {
                        val saved = repo.getPlaybackPositionSuspend(streamId, contentType)
                        if (saved != null) {
                            val progressPercent = if (saved.duration > 0) {
                                (saved.playbackPosition.toFloat() / saved.duration.toFloat()) * 100f
                            } else 0f
                            if (progressPercent in 2.0..95.0 && !saved.isCompleted) {
                                resumePos = saved.playbackPosition
                            }
                        }
                    }

                    // Check Favorite
                    val isFav = repo.isFavoriteSuspend(streamId, contentType)

                    // Get EPG (Live TV)
                    var currentProgram: EpgProgram? = null
                    var nextProgram: EpgProgram? = null

                    if (contentType == "LIVE_TV") {
                        val currentItem = currentStreams.find { it.id == streamId }
                            ?: MediaItem(streamId, streamName, org.njarasoa.fijerena.core.player.domain.MediaType.CHANNEL, categoryId)

                        val epgData = repo.getEpgBulkForItems(listOf(currentItem)).getOrNull()
                        val listings = epgData?.get(streamId)?.listings ?: emptyList()
                        val now = System.currentTimeMillis() / 1000
                        currentProgram = listings.firstOrNull { now in it.startTime..it.endTime }
                        nextProgram = if (currentProgram != null) {
                            listings.firstOrNull { it.startTime >= currentProgram!!.endTime }
                        } else null
                    }

                    _state.value = StreamState.Success(
                        streamUrl = playable.uri,
                        streamHeaders = playable.headers,
                        streamName = streamName,
                        streamId = streamId,
                        resumePosition = resumePos,
                        isLive = contentType == "LIVE_TV",
                        categoryStreams = currentStreams,
                        lastWatchedStreams = lastWatched,
                        currentEpgProgram = currentProgram,
                        nextEpgProgram = nextProgram,
                        isFavorite = isFav
                    )

                    // Schedule history update (Recent Channels) after 5 seconds
                    historyJob?.cancel()
                    historyJob = viewModelScope.launch(Dispatchers.IO) {
                        delay(5000)
                        val watchHistoryStreamId = if (contentType == "TV_SHOWS" && seriesId != null) seriesId else streamId
                        val watchHistoryStreamName = if (contentType == "TV_SHOWS" && seriesName != null) seriesName else streamName
                        repo.saveLastPlayedItem(categoryId, watchHistoryStreamId, watchHistoryStreamName, contentType)
                    }
                },
                onFailure = { e ->
                    _state.value = StreamState.Error(e.message ?: "Failed to resolve stream")
                }
            )
        } catch (e: Exception) {
            _state.value = StreamState.Error(e.message ?: "Unknown error loading stream")
        }
    }

    fun loadStream(item: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = StreamState.Loading

            // Update index
            currentStreamIndex = streamList.indexOfFirst { it.id == item.id }

            val currentState = _state.value
            val currentStreams = if (currentState is StreamState.Success) currentState.categoryStreams else streamList
            val lastWatched = if (currentState is StreamState.Success) currentState.lastWatchedStreams else emptyList()

            loadStreamInternal(item.id, item.name, currentStreams, lastWatched)
        }
    }

    fun nextChannel() {
        if (streamList.isEmpty()) return
        val nextIndex = (currentStreamIndex + 1) % streamList.size
        val nextItem = streamList[nextIndex]
        loadStream(nextItem)
    }

    fun prevChannel() {
        if (streamList.isEmpty()) return
        val prevIndex = if (currentStreamIndex <= 0) streamList.size - 1 else currentStreamIndex - 1
        val prevItem = streamList[prevIndex]
        loadStream(prevItem)
    }

    fun toggleFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _state.value as? StreamState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch

            if (currentState.isFavorite) {
                if (repo.removeFavoriteSuspend(currentState.streamId, contentType)) {
                    _state.value = currentState.copy(isFavorite = false)
                }
            } else {
                if (repo.addFavoriteSuspend(currentState.streamId, currentState.streamName, categoryId, contentType)) {
                    _state.value = currentState.copy(isFavorite = true)
                }
            }
        }
    }

    fun recordHistory(position: Long, duration: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _state.value as? StreamState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch

            // Save playback position (Resume Point)
            if (contentType != "LIVE_TV") {
                repo.savePlaybackPosition(
                    currentState.streamId,
                    currentState.streamName,
                    categoryId,
                    contentType,
                    position,
                    duration
                )
                repo.onPlaybackProgress(currentState.streamId, position, duration)
            }
        }
    }
}

class StreamLoaderViewModelFactory(
    private val context: Context,
    private val initialStreamId: String,
    private val initialStreamName: String,
    private val categoryId: String,
    private val contentType: String,
    private val episodeId: String? = null,
    private val episodeExtension: String? = null,
    private val seriesId: String? = null,
    private val seriesName: String? = null,
    private val startFromBeginning: Boolean = false
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreamLoaderViewModel::class.java)) {
            return StreamLoaderViewModel(
                context.applicationContext,
                initialStreamId,
                initialStreamName,
                categoryId,
                contentType,
                episodeId,
                episodeExtension,
                seriesId,
                seriesName,
                startFromBeginning
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
