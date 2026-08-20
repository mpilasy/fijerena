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
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService

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
    private val startFromBeginning: Boolean = false,
) : ViewModel() {
    // Nav hands these over as plain strings; typed once here so nothing below can mix them up.
    private val episode = episodeId?.let(::EpisodeId)
    private val series = seriesId?.let(::SeriesId)

    sealed class StreamState {
        data object Loading : StreamState()

        data class Success(
            val streamUrl: String,
            val streamHeaders: Map<String, String>,
            val streamName: String,
            val streamId: String,
            val resumePosition: Long,
            val isLive: Boolean,
            val description: String? = null,
            val categoryStreams: List<MediaItem> = emptyList(),
            val currentEpgProgram: EpgProgram? = null,
            val nextEpgProgram: EpgProgram? = null,
            val isFavorite: Boolean = false,
            val savedAudioTrackIndex: Int? = null,
            val savedSubtitleTrackIndex: Int? = null,
        ) : StreamState()

        data class Error(
            val message: String,
        ) : StreamState()
    }

    private val _state = MutableStateFlow<StreamState>(StreamState.Loading)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    // The shared Recent list, mirrored from the repository so the channel flyout shows exactly
    // what the browse row and the preview panel show. Live TV only — no VOD player surface
    // renders it, and collecting it there would cost a fetch nobody reads.
    private val _recentItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentItems: StateFlow<List<MediaItem>> = _recentItems.asStateFlow()

    private var mediaRepository: MediaRepository? = null
    private val appSettings = AppSettings(context)

    private var currentStreamIndex = -1
    private var streamList: List<MediaItem> = emptyList()
    private var currentCategoryId: String = categoryId

    // Avoid re-fetching EPG too often
    private var lastEpgFetchTime = 0L

    // Retain last request for error retry
    private var lastLoadRequest: MediaItem? = null

    /**
     * Stream this loader has most recently been asked to resolve, whether or not that resolution
     * has finished. Callers that re-point the loader when their target changes read it to skip a
     * redundant load of the channel it is already on — the split preview constructs the loader
     * with its target and would otherwise immediately resolve the same stream (and its EPG) a
     * second time.
     */
    var requestedStreamId: String? = initialStreamId
        private set

    // Job to handle delayed history saving (mimics original 5s delay)
    private var historyJob: Job? = null
    private var loadJob: Job? = null

    init {
        currentCategoryId = categoryId
        initializeAndLoad()
    }

    private fun initializeAndLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Initialize Repository
                val container =
                    org.njarasoa.fijerena.core.ui.di.AppContainer
                        .getInstance(context)
                val repo = container.getMediaRepository()
                mediaRepository = repo

                // 2. Load Channel List (Live TV only) and start mirroring the shared Recent list
                var currentStreams: List<MediaItem> = emptyList()

                if (contentType == ContentType.LIVE_TV) {
                    launch { repo.recentItems(contentType).collect { _recentItems.value = it.orEmpty() } }
                    repo.refreshRecentItems(contentType)

                    val result = repo.getItems(currentCategoryId, contentType)
                    result.fold(
                        onSuccess = { items ->
                            currentStreams = items
                            streamList = items
                            currentStreamIndex = items.indexOfFirst { it.id == initialStreamId }
                            if (currentStreamIndex == -1 && items.isNotEmpty()) currentStreamIndex = 0
                        },
                        onFailure = { Log.e("StreamLoader", "Failed to load category streams", it) },
                    )
                }

                // 3. Resolve Initial Stream
                loadStreamInternal(
                    streamId = initialStreamId,
                    streamName = initialStreamName,
                    currentStreams = currentStreams,
                )
            } catch (e: Exception) {
                Log.e("StreamLoader", "Initialization error", e)
                _state.value = StreamState.Error(e.message ?: context.getString(R.string.stream_error_initialization_failed))
            }
        }
    }

    private suspend fun loadStreamInternal(
        streamId: String,
        streamName: String,
        currentStreams: List<MediaItem>,
        // Whether to tell the provider a playback session started. Suppressed for embedded
        // previews: the server counts that as a real session, and a preview re-points on every
        // focus change, so it would open and close sessions as fast as the user scrolls.
        // Watch history is NOT gated on this — a preview that lasts past the watch delay is a
        // real view and is recorded like one (see the history job below).
        notifyProviderStarted: Boolean = true,
    ) {
        val repo = mediaRepository ?: return

        try {
            // Resolve URL
            val result =
                repo.resolvePlayableStream(
                    itemId = streamId,
                    contentType = contentType,
                    episodeId = episodeId,
                    extension = episodeExtension,
                )

            result.fold(
                onSuccess = { playable ->
                    // Determine Resume Position and Track Settings
                    var resumePos = 0L
                    var savedAudioIndex: Int? = null
                    var savedSubtitleIndex: Int? = null

                    val saved = repo.getPlaybackPositionSuspend(streamId, contentType)
                    if (saved != null) {
                        savedAudioIndex = saved.audioTrackIndex
                        savedSubtitleIndex = saved.subtitleTrackIndex

                        if (!startFromBeginning && contentType != ContentType.LIVE_TV && appSettings.autoResumeEnabled) {
                            val progressPercent =
                                if (saved.duration > 0) {
                                    (saved.playbackPosition.toFloat() / saved.duration.toFloat()) * 100f
                                } else {
                                    0f
                                }
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

                    if (contentType == ContentType.LIVE_TV) {
                        val currentItem =
                            currentStreams.find { it.id == streamId }
                                ?: MediaItem(
                                    streamId,
                                    streamName,
                                    org.njarasoa.fijerena.core.player.domain.MediaType.LIVE_CHANNEL,
                                    currentCategoryId,
                                )

                        val epgData = repo.getEpgBulkForItems(listOf(currentItem)).getOrNull()
                        val listings = epgData?.get(streamId)?.listings ?: emptyList()
                        val now = System.currentTimeMillis() / 1000
                        currentProgram = listings.firstOrNull { now in it.startTime..it.endTime }
                        nextProgram =
                            if (currentProgram != null) {
                                listings.firstOrNull { it.startTime >= currentProgram.endTime }
                            } else {
                                null
                            }
                    }

                    // Notify provider that playback started (e.g. for Jellyfin session tracking)
                    if (notifyProviderStarted) {
                        viewModelScope.launch(Dispatchers.IO) {
                            repo.onPlaybackStarted(streamId)
                        }
                    }

                    // Get Description (VOD/Series)
                    var description: String? = null
                    if (contentType != ContentType.LIVE_TV) {
                        // For episodes, the repository.resolvePlayableStream doesn't return metadata
                        // We might need to fetch the item's metadata if we don't have it
                        val currentItem = currentStreams.find { it.id == streamId }
                        description = currentItem?.metadata?.plot

                        // Special case for episodes: if we have episodeId, we should try to get the episode-specific plot
                        if (episodeId != null && contentType == ContentType.TV_SHOWS && seriesId != null) {
                            Log.d("StreamLoader", "Fetching series detail for $seriesId to get episode $episodeId plot")
                            val seriesDetailResult = repo.getSeriesDetail(SeriesId(seriesId))
                            seriesDetailResult.getOrNull()?.let { detail ->
                                // Performance optimization: Use firstNotNullOfOrNull instead of flatten().find()
                                // to avoid creating an intermediate list of all episodes, reducing GC pressure and lookup time
                                val episode = detail.episodes.values.firstNotNullOfOrNull { seasonEpisodes ->
                                    seasonEpisodes.find { it.id == episodeId }
                                }
                                Log.d("StreamLoader", "Found episode: ${episode?.title}, plot present: ${episode?.metadata?.plot != null}")
                                description = episode?.metadata?.plot ?: detail.metadata.plot
                            }
                        } else if (contentType == ContentType.MOVIES) {
                            val movieDetailResult = repo.getMovieDetail(streamId)
                            movieDetailResult.getOrNull()?.let { detail ->
                                description = detail.metadata.plot
                            }
                        }
                    } else if (currentProgram != null) {
                        description = currentProgram.description
                    }

                    Log.d("StreamLoader", "Final description for $streamId: ${description?.take(20)}...")


                    _state.value =
                        StreamState.Success(
                            streamUrl = playable.uri,
                            streamHeaders = playable.headers,
                            streamName = streamName,
                            streamId = streamId,
                            resumePosition = resumePos,
                            isLive = contentType == ContentType.LIVE_TV,
                            description = description,
                            categoryStreams = currentStreams,
                            currentEpgProgram = currentProgram,
                            nextEpgProgram = nextProgram,
                            isFavorite = isFav,
                            savedAudioTrackIndex = savedAudioIndex,
                            savedSubtitleTrackIndex = savedSubtitleIndex,
                        )

                    // Schedule history update (Recent) after the configured delay — LIVE TV ONLY.
                    // For VOD, history is recorded via recordHistory() once a threshold (%) is
                    // reached. Previews schedule this too: the delay is what separates a real
                    // view from a channel the user merely passed over, and re-pointing to another
                    // channel cancels the pending write below, so scrolling never records
                    // anything. What a preview must not do is reorder the list while the user is
                    // looking at it — that's handled where the list is displayed, not here.
                    historyJob?.cancel()
                    if (contentType == ContentType.LIVE_TV) {
                        historyJob =
                            viewModelScope.launch(Dispatchers.IO) {
                                delay(AppSettings(context).watchDelaySeconds * 1000L)
                                repo.saveLastPlayedItem(
                                    categoryId = currentCategoryId,
                                    itemId = streamId,
                                    itemName = streamName,
                                    contentType = contentType,
                                    episodeId = episode,
                                    episodeExtension = episodeExtension,
                                    seriesId = series,
                                    seriesName = seriesName,
                                )

                                // Republish the shared Recent list so every surface showing it —
                                // this player's flyout, the preview panel behind it, the browse
                                // row — picks the channel up at the same moment.
                                repo.refreshRecentItems(contentType)
                            }
                    }
                },
                onFailure = { e ->
                    _state.value = StreamState.Error(e.message ?: context.getString(R.string.stream_error_resolve_failed))
                },
            )
        } catch (e: Exception) {
            _state.value = StreamState.Error(e.message ?: context.getString(R.string.stream_error_unknown))
        }
    }

    fun loadStream(item: MediaItem) {
        lastLoadRequest = item
        requestedStreamId = item.id
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val repo = mediaRepository ?: return@launch

            // Capture current state before setting Loading, so we preserve categoryStreams
            val previousState = _state.value
            _state.value = StreamState.Loading

            // 1. Update Category if it changed (e.g. from Last Watched)
            var currentStreams = if (previousState is StreamState.Success) previousState.categoryStreams else streamList
            if (item.categoryId != currentCategoryId && contentType == ContentType.LIVE_TV) {
                currentCategoryId = item.categoryId
                val result = repo.getItems(currentCategoryId, contentType)
                result.fold(
                    onSuccess = { items ->
                        currentStreams = items
                        streamList = items
                    },
                    onFailure = { Log.e("StreamLoader", "Failed to refresh category streams", it) },
                )
            }

            // 2. Update index
            currentStreamIndex = streamList.indexOfFirst { it.id == item.id }

            loadStreamInternal(item.id, item.name, currentStreams)
        }
    }

    /**
     * Lean resolution for embedded previews (e.g. the Live TV split preview pane): resolves the
     * stream URL + EPG only, skipping the channel-switcher category list refresh that [loadStream]
     * does — a small preview doesn't render it. That call can be an expensive full-category fetch;
     * doing it on every focus-driven preview change (potentially once per few hundred ms while
     * scrolling) saturated CPU and caused ANRs. Watch history is still recorded on the usual
     * delay, so a channel previewed long enough counts as watched.
     */
    fun loadStreamLight(item: MediaItem) {
        lastLoadRequest = item
        requestedStreamId = item.id
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (mediaRepository == null) return@launch
                _state.value = StreamState.Loading
                loadStreamInternal(
                    item.id,
                    item.name,
                    currentStreams = emptyList(),
                    notifyProviderStarted = false,
                )
            }
    }

    fun retryLastLoad() {
        if (lastLoadRequest != null) {
            loadStream(lastLoadRequest!!)
        } else {
            // Failed on the very first load before a stream was selected
            initializeAndLoad()
        }
    }

    fun nextChannel() {
        if (streamList.isEmpty()) return
        currentStreamIndex = (currentStreamIndex + 1) % streamList.size
        loadStream(streamList[currentStreamIndex])
    }

    fun prevChannel() {
        if (streamList.isEmpty()) return
        currentStreamIndex = if (currentStreamIndex <= 0) streamList.size - 1 else currentStreamIndex - 1
        loadStream(streamList[currentStreamIndex])
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
                if (repo.addFavoriteSuspend(currentState.streamId, currentState.streamName, currentCategoryId, contentType)) {
                    _state.value = currentState.copy(isFavorite = true)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Flush any pending watch history writes so position isn't lost
        mediaRepository?.flushWatchHistory()
    }

    fun recordHistory(
        position: Long,
        duration: Long,
        isPaused: Boolean = false,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _state.value as? StreamState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch

            // Save playback position (Resume Point) - Only for VOD/Series
            if (contentType != ContentType.LIVE_TV) {
                val progressPercent = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100f else 0f

                // VOD Rules: Only add to history once > 2% threshold is reached to avoid cluttering
                if (progressPercent >= 2.0f) {
                    repo.saveLastPlayedItem(
                        categoryId = currentCategoryId,
                        itemId = currentState.streamId,
                        itemName = currentState.streamName,
                        contentType = contentType,
                        episodeId = episode,
                        episodeExtension = episodeExtension,
                        seriesId = series,
                        seriesName = seriesName,
                    )
                }

                // Metadata goes with every position write, not only the ones past the threshold
                // above: this call creates the row for a session too short to reach it, and a row
                // without it is an episode that cannot say which show it belongs to.
                repo.savePlaybackPosition(
                    currentState.streamId,
                    currentState.streamName,
                    currentCategoryId,
                    contentType,
                    position,
                    duration,
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                    episodeId = episode,
                    episodeExtension = episodeExtension,
                    seriesId = series,
                    seriesName = seriesName,
                )
            }

            // Always notify provider of progress (e.g. for session tracking/scrobbling)
            repo.onPlaybackProgress(currentState.streamId, position, duration, isPaused)
        }
    }

    /**
     * Call this when playback is stopped/exited to finalize session state.
     */
    fun stopPlayback(
        position: Long,
        duration: Long,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _state.value as? StreamState.Success ?: return@launch
            val repo = mediaRepository ?: return@launch

            // Final save - Only for VOD/Series
            if (contentType != ContentType.LIVE_TV) {
                val progressPercent = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100f else 0f

                // Final check to see if we reached threshold before exiting
                if (progressPercent >= 2.0f) {
                    repo.saveLastPlayedItem(
                        categoryId = currentCategoryId,
                        itemId = currentState.streamId,
                        itemName = currentState.streamName,
                        contentType = contentType,
                        episodeId = episode,
                        episodeExtension = episodeExtension,
                        seriesId = series,
                        seriesName = seriesName,
                    )
                }

                // Metadata goes with every position write, not only the ones past the threshold
                // above: this call creates the row for a session too short to reach it, and a row
                // without it is an episode that cannot say which show it belongs to.
                repo.savePlaybackPosition(
                    currentState.streamId,
                    currentState.streamName,
                    currentCategoryId,
                    contentType,
                    position,
                    duration,
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                    episodeId = episode,
                    episodeExtension = episodeExtension,
                    seriesId = series,
                    seriesName = seriesName,
                )
            }

            // Final notification to provider (e.g. reportPlaybackStopped to Jellyfin/Xtream)
            repo.onPlaybackStopped(currentState.streamId, position, duration)

            // Flush to disk immediately to ensure history is committed
            repo.flushWatchHistory()
        }
    }
}

/**
 * Saves the final playback position/duration and selected audio/subtitle track
 * for the current session, so it's called identically whenever a player screen
 * leaves a stream (back, switching to a new stream, or the composable leaving
 * composition) rather than each call site re-deriving it slightly differently.
 */
fun finalizeSession(
    playbackState: PlaybackState,
    loaderViewModel: StreamLoaderViewModel,
) {
    val service = StreamingPlaybackService.getInstance()
    // PlaybackState carries a snapshot taken the last time the player raised an event
    // (state change, pause, seek, rebuffer). An uninterrupted stretch of playback raises
    // none, so that snapshot can be many minutes behind by the time the user backs out —
    // and writing it here would overwrite the fresher position the periodic save loop
    // already stored. Ask the live player instead, and only fall back to the snapshot when
    // it's gone (Ended tears the player down, so its position reads 0).
    val livePosition =
        service?.getPlayer()?.let { player ->
            val position = player.currentPosition
            val duration = player.duration
            if (position > 0L && duration > 0L) position to duration else null
        }
    val pos =
        livePosition?.first
            ?: when (playbackState) {
                is PlaybackState.Playing -> playbackState.position
                is PlaybackState.Paused -> playbackState.position
                // Played to the end: report the full duration so the >95% rule marks it completed.
                is PlaybackState.Ended -> playbackState.duration
                else -> 0L
            }
    val dur =
        livePosition?.second
            ?: when (playbackState) {
                is PlaybackState.Playing -> playbackState.duration
                is PlaybackState.Paused -> playbackState.duration
                is PlaybackState.Ended -> playbackState.duration
                else -> 0L
            }
    val audioIdx = service?.getAudioTracks()?.indexOfFirst { it.isSelected }?.takeIf { it >= 0 }
    val subIdx = service?.getSubtitleTracks()?.indexOfFirst { it.isSelected }?.let { if (it >= 0) it else -1 }
    loaderViewModel.stopPlayback(pos, dur, audioIdx, subIdx)
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
    private val startFromBeginning: Boolean = false,
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
                startFromBeginning,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
