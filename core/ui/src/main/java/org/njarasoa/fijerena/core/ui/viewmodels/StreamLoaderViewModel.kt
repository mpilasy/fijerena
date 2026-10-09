package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.flattenedEpisodes
import org.njarasoa.fijerena.core.player.domain.sortedSeasons
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

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
    private var currentEpisodeId: String? = episodeId
    private var currentEpisodeExtension: String? = episodeExtension
    private val episode: EpisodeId?
        get() = currentEpisodeId?.let(::EpisodeId)
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
            // Set for episode playback only — the OSD uses it as the big title, demoting
            // [streamName] (the episode title) to a subtitle line.
            val seriesName: String? = null,
            // "S{season}:E{episode}", e.g. "S3:E1" — set once the episode's series detail
            // resolves; null for movies, Live TV, and until that lookup finishes.
            val episodeLabel: String? = null,
            // TMDB's transparent-PNG wordmark art, for the OSD title treatment. Null for Live TV,
            // until the TMDB lookup finishes, or when TMDB has no logo for this title.
            val logoUrl: String? = null,
            val nextEpisode: EpisodeItem? = null,
            // Whether the provider lets episodes roll on to [nextEpisode] (autoplay next episode):
            // Xtream yes, Jellyfin no. Set with [nextEpisode].
            val supportsAutoplayNext: Boolean = false,
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

    // Live TV channel list and the position in it, as one snapshot: written from IO coroutines,
    // read by the D-pad handlers on Main. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-13.
    private val channels = MutableStateFlow(ChannelCursor())
    private var currentCategoryId: String = categoryId

    // Avoid re-fetching EPG too often
    private var lastEpgFetchTime = 0L

    // Retain last request for error retry
    private var lastLoadRequest: MediaItem? = null
    private var lastLoadZapped = false

    /**
     * Stream this loader has most recently been asked to resolve, whether or not that resolution
     * has finished. Callers that re-point the loader when their target changes read it to skip a
     * redundant load of the channel it is already on — the split preview constructs the loader
     * with its target and would otherwise immediately resolve the same stream (and its EPG) a
     * second time.
     */
    var requestedStreamId: String? = initialStreamId
        private set

    // Job to handle delayed history saving
    private var historyJob: Job? = null

    // The load in flight (resolve the URL, publish Success). A new load cancels the previous one.
    private var loadJob: Job? = null

    // EPG / plot enrichment of the stream that is playing; its own job so starting it never
    // cancels the load that launched it.
    private var enrichJob: Job? = null

    // Live TV category channel list refresh after a channel pick from another category.
    private var categoryListJob: Job? = null

    init {
        currentCategoryId = categoryId
        initializeAndLoad()
    }

    private fun initializeAndLoad() {
        viewModelScope.launchGuarded(
            "StreamLoaderViewModel.init",
            Dispatchers.IO,
            onError = { _state.value = StreamState.Error(friendlyErrorMessage(it, context, appSettings.isDevMode)) },
        ) {
            val container =
                org.njarasoa.fijerena.core.ui.di.AppContainer
                    .getInstance(context)
            val repo = container.getMediaRepository()
            mediaRepository = repo

            if (contentType == ContentType.LIVE_TV) {
                launch { repo.recentItems(contentType).collect { _recentItems.value = it.orEmpty() } }
                repo.refreshRecentItems(contentType)
            }

            // The initial stream first (fast path); the category channel list follows in the background.
            loadStreamInternal(
                streamId = initialStreamId,
                streamName = initialStreamName,
                currentStreams = emptyList(),
            )

            if (contentType == ContentType.LIVE_TV) {
                launch {
                    val result = repo.getItems(currentCategoryId, contentType)
                    result.fold(
                        onSuccess = { items ->
                            channels.value = ChannelCursor.at(items, initialStreamId)
                            updateCategoryStreams(items)
                        },
                        onFailure = { Log.e("StreamLoader", "Failed to load category streams", it) },
                    )
                }
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
        // Reached by zapping (Up/Down, a swipe) rather than chosen: Recent waits longer for it.
        zapped: Boolean = false,
    ) {
        val repo = mediaRepository ?: return

        try {
            val result =
                repo.resolvePlayableStream(
                    itemId = streamId,
                    contentType = contentType,
                    episodeId = currentEpisodeId,
                    extension = currentEpisodeExtension,
                )

            result.fold(
                onSuccess = { playable ->
                    var resumePos = 0L
                    var savedAudioIndex: Int? = null
                    var savedSubtitleIndex: Int? = null

                    val saved = repo.getPlaybackPositionSuspend(streamId, contentType)
                    if (saved != null) {
                        savedAudioIndex = saved.audioTrackIndex
                        savedSubtitleIndex = saved.subtitleTrackIndex

                        if (!startFromBeginning && contentType != ContentType.LIVE_TV && repo.isAutoResumeEnabled()) {
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

                    // An episode with no saved choice of its own inherits the series' most
                    // recent one — a fresh episode of a show you've already picked a language/
                    // subtitle track for shouldn't reset to nothing. Only when this episode has
                    // neither: one already set (even alone) means the user made a choice for it
                    // specifically, and that sticks over the series fallback.
                    if (contentType == ContentType.TV_SHOWS &&
                        series != null &&
                        savedAudioIndex == null &&
                        savedSubtitleIndex == null
                    ) {
                        repo.getSeriesTrackPrefs(series, contentType)?.let { (audioIdx, subtitleIdx) ->
                            savedAudioIndex = audioIdx
                            savedSubtitleIndex = subtitleIdx
                        }
                    }

                    val isFav = repo.isFavoriteSuspend(streamId, contentType)
                    val activeStreams = if (currentStreams.isNotEmpty()) currentStreams else channels.value.items

                    // A superseded load must not publish over the load that replaced it.
                    currentCoroutineContext().ensureActive()

                    // Emit Success immediately so player begins network buffering & decoding right away
                    _state.value =
                        StreamState.Success(
                            streamUrl = playable.uri,
                            streamHeaders = playable.headers,
                            streamName = streamName,
                            streamId = streamId,
                            resumePosition = resumePos,
                            isLive = contentType == ContentType.LIVE_TV,
                            description = null,
                            categoryStreams = activeStreams,
                            currentEpgProgram = null,
                            nextEpgProgram = null,
                            isFavorite = isFav,
                            savedAudioTrackIndex = savedAudioIndex,
                            savedSubtitleTrackIndex = savedSubtitleIndex,
                            seriesName = if (contentType == ContentType.TV_SHOWS) seriesName else null,
                        )

                    // Notify provider that playback started (e.g. for Jellyfin session tracking)
                    if (notifyProviderStarted) {
                        viewModelScope.launchGuarded("StreamLoaderViewModel.onPlaybackStarted", Dispatchers.IO) {
                            repo.onPlaybackStarted(streamId)
                        }
                    }

                    // Schedule history update (Recent) after the configured delay — LIVE TV ONLY.
                    historyJob?.cancel()
                    if (contentType == ContentType.LIVE_TV) {
                        historyJob =
                            viewModelScope.launchGuarded("StreamLoaderViewModel.liveHistory", Dispatchers.IO) {
                                delay(recentRecordDelayMs(AppSettings(context).watchDelaySeconds, zapped))
                                repo.saveLastPlayedItem(
                                    categoryId = currentCategoryId,
                                    itemId = streamId,
                                    itemName = streamName,
                                    contentType = contentType,
                                    episodeId = episode,
                                    episodeExtension = currentEpisodeExtension,
                                    seriesId = series,
                                    seriesName = seriesName,
                                )

                                repo.refreshRecentItems(contentType)
                            }
                    }

                    // Enrich metadata (EPG & Plot description) asynchronously in background
                    enrichJob?.cancel()
                    enrichJob =
                        viewModelScope.launchGuarded("StreamLoaderViewModel.enrich", Dispatchers.IO) {
                            enrichStreamMetadata(streamId, streamName, activeStreams)
                            if (contentType == ContentType.LIVE_TV) followProgrammes(streamId, streamName, activeStreams)
                        }
                },
                onFailure = { error ->
                    _state.value = StreamState.Error(friendlyErrorMessage(error, context, appSettings.isDevMode))
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("StreamLoader", "Failed to load stream $streamId", e)
            _state.value = StreamState.Error(friendlyErrorMessage(e, context, appSettings.isDevMode))
        }
    }

    private suspend fun enrichStreamMetadata(
        streamId: String,
        streamName: String,
        currentStreams: List<MediaItem>,
    ) {
        val repo = mediaRepository ?: return
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

        var description: String? = null
        var episodeLabel: String? = null
        var logoUrl: String? = null
        var nextEpisode: EpisodeItem? = null
        var plotlessEpisode: EpisodeItem? = null
        if (contentType != ContentType.LIVE_TV) {
            val currentItem = currentStreams.find { it.id == streamId }
            description = currentItem?.metadata?.plot

            val curEpisodeId = currentEpisodeId
            if (curEpisodeId != null && contentType == ContentType.TV_SHOWS && seriesId != null) {
                val seriesDetailResult = repo.getSeriesDetail(SeriesId(seriesId))
                seriesDetailResult.getOrNull()?.let { detail ->
                    val curEp =
                        detail.episodes.values.firstNotNullOfOrNull { seasonEpisodes ->
                            seasonEpisodes.find { it.id == curEpisodeId }
                        }
                    // Never the series' synopsis: it would read as this episode's.
                    description = episodeDescription(curEp)
                    plotlessEpisode = curEp?.takeIf { description == null }
                    curEp?.seasonNumber?.let { season ->
                        episodeLabel = "S$season:E${curEp.episodeNumber}"
                    }
                    logoUrl = repo.getTmdbLogoUrl(detail.metadata.tmdbId, contentType)

                    val sorted = detail.sortedSeasons { num -> context.getString(R.string.series_season_name_format, num) }
                    val flat = detail.flattenedEpisodes(sorted)
                    val curIdx = flat.indexOfFirst { it.id == curEpisodeId }
                    if (curIdx >= 0 && curIdx + 1 < flat.size) {
                        nextEpisode = flat[curIdx + 1]
                    }
                }
            } else if (contentType == ContentType.MOVIES) {
                val movieDetailResult = repo.getMovieDetail(streamId)
                movieDetailResult.getOrNull()?.let { detail ->
                    description = detail.metadata.plot
                    logoUrl = repo.getTmdbLogoUrl(detail.metadata.tmdbId, contentType)
                }
            }
        } else if (currentProgram != null) {
            description = currentProgram.description
        }

        val currentState = _state.value
        if (currentState is StreamState.Success && currentState.streamId == streamId) {
            _state.value =
                currentState.copy(
                    currentEpgProgram = currentProgram,
                    nextEpgProgram = nextProgram,
                    logoUrl = logoUrl,
                    description = description,
                    episodeLabel = episodeLabel,
                    nextEpisode = nextEpisode,
                    supportsAutoplayNext = repo.supportsAutoplayNextEpisode,
                )
        }

        // The episode's synopsis hasn't been fetched yet: ask for it now and patch it in on arrival.
        val missing = plotlessEpisode
        val seriesRaw = seriesId
        if (missing != null && seriesRaw != null) {
            val plot = repo.fetchEpisodePlot(SeriesId(seriesRaw), missing)
            val latest = _state.value
            if (plot != null && latest is StreamState.Success && latest.streamId == streamId) {
                _state.value = latest.copy(description = plot)
            }
        }
    }

    /**
     * Live TV: looks the guide up again once the programme on air ends, so the OSD — and, through
     * the player's metadata, live sync's "now playing" — roll over with it. Runs inside [enrichJob],
     * so a channel change or leaving the player ends it.
     */
    private suspend fun followProgrammes(
        streamId: String,
        streamName: String,
        currentStreams: List<MediaItem>,
    ) {
        while (true) {
            val endsAtSec =
                ((_state.value as? StreamState.Success)?.takeIf { it.streamId == streamId }?.currentEpgProgram?.endTime) ?: break
            delay((endsAtSec * 1000 - System.currentTimeMillis()).coerceAtLeast(0L) + PROGRAMME_ROLLOVER_MARGIN_MS)
            enrichStreamMetadata(streamId, streamName, currentStreams)
        }
    }

    private fun updateCategoryStreams(items: List<MediaItem>) {
        val currentState = _state.value
        if (currentState is StreamState.Success) {
            _state.value = currentState.copy(categoryStreams = items)
        }
    }

    /** Cancels the load in flight and its enrichment; the category list refresh is kept. */
    private fun cancelLoads() {
        loadJob?.cancel()
        enrichJob?.cancel()
    }

    /**
     * Plays [item]. [zapped] is true for a channel reached by stepping through the list (Up/Down
     * in full screen, a swipe on the phone) rather than chosen: it goes into Recent only once it
     * has settled (see [recentRecordDelayMs]).
     */
    fun loadStream(
        item: MediaItem,
        zapped: Boolean = false,
    ) {
        lastLoadRequest = item
        lastLoadZapped = zapped
        requestedStreamId = item.id
        cancelLoads()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                val repo = mediaRepository ?: return@launch

                val previousState = _state.value
                _state.value = StreamState.Loading

                val currentStreams = if (previousState is StreamState.Success) previousState.categoryStreams else channels.value.items

                // Fast path: start loading stream immediately
                loadStreamInternal(item.id, item.name, currentStreams, zapped = zapped)
                // Superseded while resolving: leave the category state to the load that replaced us.
                ensureActive()

                // If category changed, refresh category stream list asynchronously in background
                if (item.categoryId != currentCategoryId && contentType == ContentType.LIVE_TV) {
                    currentCategoryId = item.categoryId
                    categoryListJob?.cancel()
                    categoryListJob =
                        viewModelScope.launch(Dispatchers.IO) {
                            val result = repo.getItems(currentCategoryId, contentType)
                            result.fold(
                                onSuccess = { items ->
                                    channels.value = ChannelCursor.at(items, item.id)
                                    updateCategoryStreams(items)
                                },
                                onFailure = { Log.e("StreamLoader", "Failed to refresh category streams", it) },
                            )
                        }
                } else {
                    channels.update { it.pointingAt(item.id) }
                }
            }
    }

    fun playNextEpisode(nextEpisode: EpisodeItem) {
        currentEpisodeId = nextEpisode.id
        currentEpisodeExtension = nextEpisode.extension
        requestedStreamId = nextEpisode.id
        cancelLoads()
        loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (mediaRepository == null) return@launch
                _state.value = StreamState.Loading
                loadStreamInternal(
                    streamId = nextEpisode.id,
                    streamName = nextEpisode.title,
                    currentStreams = emptyList(),
                )
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
        lastLoadZapped = false
        requestedStreamId = item.id
        cancelLoads()
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
            loadStream(lastLoadRequest!!, lastLoadZapped)
        } else {
            // Failed on the very first load before a stream was selected
            initializeAndLoad()
        }
    }

    fun nextChannel() {
        channels.updateAndGet { it.next() }.current?.let { loadStream(it, zapped = true) }
    }

    fun prevChannel() {
        channels.updateAndGet { it.previous() }.current?.let { loadStream(it, zapped = true) }
    }

    fun toggleFavorite() {
        viewModelScope.launchGuarded("StreamLoaderViewModel.toggleFavorite", Dispatchers.IO) {
            val currentState = _state.value as? StreamState.Success ?: return@launchGuarded
            val repo = mediaRepository ?: return@launchGuarded

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
        viewModelScope.launchGuarded("StreamLoaderViewModel.recordHistory", Dispatchers.IO) {
            val currentState = _state.value as? StreamState.Success ?: return@launchGuarded
            val repo = mediaRepository ?: return@launchGuarded

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
                        episodeExtension = currentEpisodeExtension,
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
                    episodeExtension = currentEpisodeExtension,
                    seriesId = series,
                    seriesName = seriesName,
                )
            }

            // Always notify provider of progress (e.g. for session tracking/scrobbling)
            repo.onPlaybackProgress(currentState.streamId, position, duration, isPaused)
        }
    }

    /**
     * Call this when playback is stopped/exited to finalize session state. Fire-and-forget on
     * IO, not awaited — the right choice for a caller that isn't about to navigate away and read
     * this same data back (e.g. a `DisposableEffect.onDispose`, which has no coroutine scope that
     * outlives it to await from anyway). A caller that back-navigates to a screen reading this
     * write's result — the episode-selection screen's resume anchor — needs
     * [stopPlaybackAwaited] instead: see its kdoc and
     * docs/plans/archive/20260908_episode-selection-fragility-plan.md.
     */
    @kotlin.OptIn(DelicateCoroutinesApi::class) // CoroutineStart.ATOMIC
    fun stopPlayback(
        position: Long,
        duration: Long,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
    ) {
        // ATOMIC: NonCancellable below only protects the write once it has started. Called from
        // a screen's onDispose, this launch can be cancelled with viewModelScope before the IO
        // dispatcher ever runs it — a DEFAULT start then never runs the body at all, and the
        // final position is lost. See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-29.
        viewModelScope.launchGuarded("StreamLoaderViewModel.stopPlayback", Dispatchers.IO, CoroutineStart.ATOMIC) {
            // NonCancellable: this coroutine is a child of viewModelScope, which gets cancelled
            // the moment the screen popping back (e.g. Back press) clears this ViewModel —
            // without this, that cancellation could land mid-write and truncate the
            // watch-history/position commit doStopPlayback() is in the middle of. The outer
            // launch still responds to cancellation normally the instant this block returns.
            withContext(NonCancellable) {
                doStopPlayback(position, duration, audioTrackIndex, subtitleTrackIndex)
            }
        }
    }

    /**
     * Same write as [stopPlayback], but suspends until it's actually committed instead of firing
     * it off unawaited. Use this before navigating back to a screen that reads the result right
     * back (the episode-selection screen's watch-history-derived resume anchor) — otherwise the
     * screen's own read can win the race against this write, land on stale watch history, and
     * silently reset to the wrong season/episode. See
     * docs/plans/archive/20260908_episode-selection-fragility-plan.md.
     */
    suspend fun stopPlaybackAwaited(
        position: Long,
        duration: Long,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
    ) {
        withContext(Dispatchers.IO) {
            // Caught here, not by the caller: it navigates back once this returns, and a failed
            // write must neither crash the app nor strand the viewer on the player.
            try {
                doStopPlayback(position, duration, audioTrackIndex, subtitleTrackIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StreamLoader", "Final playback write failed", e)
                CrashLog.record("StreamLoaderViewModel.stopPlaybackAwaited", e)
            }
        }
    }

    private suspend fun doStopPlayback(
        position: Long,
        duration: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
    ) {
        val currentState = _state.value as? StreamState.Success
        val repo = mediaRepository
        if (currentState != null && repo != null) {
            if (contentType != ContentType.LIVE_TV) {
                val progressPercent = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100f else 0f

                if (progressPercent >= 2.0f) {
                    repo.saveLastPlayedItem(
                        categoryId = currentCategoryId,
                        itemId = currentState.streamId,
                        itemName = currentState.streamName,
                        contentType = contentType,
                        episodeId = episode,
                        episodeExtension = currentEpisodeExtension,
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
                    episodeExtension = currentEpisodeExtension,
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

/** After a programme's end time, so the guide lookup lands on the next one. */
private const val PROGRAMME_ROLLOVER_MARGIN_MS = 5_000L

@OptIn(UnstableApi::class)
private class FinalizeSessionSnapshot(
    val position: Long,
    val duration: Long,
    val audioTrackIndex: Int?,
    val subtitleTrackIndex: Int?,
) {
    companion object {
        /**
         * PlaybackState carries a snapshot taken the last time the player raised an event
         * (state change, pause, seek, rebuffer). An uninterrupted stretch of playback raises
         * none, so that snapshot can be many minutes behind by the time the user backs out —
         * and writing it here would overwrite the fresher position the periodic save loop
         * already stored. Ask the live player instead, and only fall back to the snapshot when
         * it's gone (Ended tears the player down, so its position reads 0).
         */
        fun capture(playbackState: PlaybackState): FinalizeSessionSnapshot {
            val service = StreamingPlaybackService.getInstance()
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
            return FinalizeSessionSnapshot(pos, dur, audioIdx, subIdx)
        }
    }
}

/**
 * Saves the final playback position/duration and selected audio/subtitle track
 * for the current session, so it's called identically whenever a player screen
 * leaves a stream (back, switching to a new stream, or the composable leaving
 * composition) rather than each call site re-deriving it slightly differently.
 *
 * Fire-and-forget (see [StreamLoaderViewModel.stopPlayback]) — use
 * [finalizeSessionAndAwait] instead when the caller is about to navigate back to a screen
 * that reads this write's result right away.
 */
@OptIn(UnstableApi::class)
fun finalizeSession(
    playbackState: PlaybackState,
    loaderViewModel: StreamLoaderViewModel,
) {
    val snapshot = FinalizeSessionSnapshot.capture(playbackState)
    loaderViewModel.stopPlayback(snapshot.position, snapshot.duration, snapshot.audioTrackIndex, snapshot.subtitleTrackIndex)
}

/**
 * [finalizeSession], but suspends until the write actually commits — see
 * [StreamLoaderViewModel.stopPlaybackAwaited] and docs/plans/archive/20260908_episode-selection-fragility-plan.md.
 * Use this before navigating back to a screen (episode selection) whose own read of this same
 * data can otherwise win the race against the unawaited version.
 */
@OptIn(UnstableApi::class)
suspend fun finalizeSessionAndAwait(
    playbackState: PlaybackState,
    loaderViewModel: StreamLoaderViewModel,
) {
    val snapshot = FinalizeSessionSnapshot.capture(playbackState)
    loaderViewModel.stopPlaybackAwaited(snapshot.position, snapshot.duration, snapshot.audioTrackIndex, snapshot.subtitleTrackIndex)
}

class StreamLoaderViewModelFactory(
    context: Context,
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
    // Store only the application context, not the raw parameter — see CategoryViewModelFactory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreamLoaderViewModel::class.java)) {
            return StreamLoaderViewModel(
                appContext,
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

/** How long a channel reached by zapping must play before it goes into Recent. */
internal const val ZAP_SETTLE_MS = 60_000L

/**
 * How long a live channel plays before it goes into Recent: the watch delay for a channel chosen
 * on purpose; for one reached by zapping, [ZAP_SETTLE_MS] or the watch delay if that is longer,
 * so zapping through a category doesn't fill Recent.
 */
internal fun recentRecordDelayMs(
    watchDelaySeconds: Int,
    zapped: Boolean,
): Long {
    val watchDelayMs = watchDelaySeconds * 1000L
    return if (zapped) maxOf(watchDelayMs, ZAP_SETTLE_MS) else watchDelayMs
}

/** The synopsis to show for [episode]: its own plot, or null — never the series' plot. */
internal fun episodeDescription(episode: EpisodeItem?): String? = episode?.metadata?.plot?.takeIf { it.isNotBlank() }

/**
 * A channel list and the position in it, replaced as one value so a reader never pairs a new list
 * with an old index. Stepping clamps the index to the list it came with.
 */
internal data class ChannelCursor(
    val items: List<MediaItem> = emptyList(),
    val index: Int = -1,
) {
    /** The channel at [index], or null when the list is empty or nothing is selected. */
    val current: MediaItem?
        get() = items.getOrNull(index)

    /** One channel down the list, wrapping past the last. */
    fun next(): ChannelCursor = if (items.isEmpty()) this else copy(index = (clamped() + 1) % items.size)

    /** One channel up the list, wrapping past the first. */
    fun previous(): ChannelCursor = if (items.isEmpty()) this else copy(index = clamped().let { if (it <= 0) items.lastIndex else it - 1 })

    /** The same list, positioned on [id] (-1 when it isn't in it). */
    fun pointingAt(id: String): ChannelCursor = copy(index = items.indexOfFirst { it.id == id })

    private fun clamped(): Int = index.coerceIn(-1, items.lastIndex)

    companion object {
        /** [items] positioned on [id], or on the first channel when [id] isn't in it. */
        fun at(
            items: List<MediaItem>,
            id: String,
        ): ChannelCursor = ChannelCursor(items, items.indexOfFirst { it.id == id }.takeIf { it >= 0 || items.isEmpty() } ?: 0)
    }
}
