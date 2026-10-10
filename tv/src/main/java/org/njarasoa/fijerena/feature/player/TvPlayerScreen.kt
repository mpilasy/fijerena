@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.domain.CatchupWindow
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.MitohanaLoading
import org.njarasoa.fijerena.core.ui.sync.RemoteStopEffect
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.finalizeSession
import org.njarasoa.fijerena.core.ui.viewmodels.finalizeSessionAndAwait
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.player.PlayerScreen
import org.njarasoa.fijerena.ui.player.UpNextState
import org.njarasoa.fijerena.ui.theme.*

/**
 * TV player screen that integrates stream playback via StreamLoaderViewModel.
 */
@Composable
fun TvPlayerScreen(
    streamId: String,
    streamName: String,
    categoryId: String,
    contentType: String,
    onBack: () -> Unit,
    /** Leaves the player for Home — after a remote Stop from another device of the sync group. */
    onHome: () -> Unit,
    episodeId: String? = null,
    episodeExtension: String? = null,
    seriesId: String? = null,
    seriesName: String? = null,
    startFromBeginning: Boolean = false,
    // Catch-up: this archive window of channel [streamId], the programme [programTitle]; null
    // plays the stream itself. See docs/plans/archive/20261010_catchup-plan.md.
    catchup: CatchupWindow? = null,
    programTitle: String? = null,
    // Catch-up's Watch live: leaves for the channel live.
    onWatchLive: () -> Unit = {},
    playbackViewModel: PlaybackViewModel = viewModel(),
    loaderViewModel: StreamLoaderViewModel =
        viewModel(
            factory =
                StreamLoaderViewModelFactory(
                    context = LocalContext.current.applicationContext,
                    initialStreamId = streamId,
                    initialStreamName = streamName,
                    categoryId = categoryId,
                    contentType = contentType,
                    episodeId = episodeId,
                    episodeExtension = episodeExtension,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    startFromBeginning = startFromBeginning,
                    catchup = catchup,
                    programTitle = programTitle,
                ),
        ),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val streamState by loaderViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.playbackState.collectAsStateWithLifecycle()

    // Remember the last successful stream so a channel change can keep PlayerScreen mounted
    // (and the old video visible) through the Loading window instead of unmounting to a
    // full-screen spinner. The LaunchedEffect below still checks live streamState, not this.
    // Also what the lifecycle observer below resumes onto after a long screensaver/HDMI-switch
    // absence — declared before it for that reason.
    var lastSuccessState by remember { mutableStateOf<StreamLoaderViewModel.StreamState.Success?>(null) }
    if (streamState is StreamLoaderViewModel.StreamState.Success) {
        lastSuccessState = streamState as StreamLoaderViewModel.StreamState.Success
    }

    // Playback Trigger key — declared here because the screensaver-resume position tracking
    // right below needs to key off it too.
    // Use derived state or specific key to avoid re-triggering on EPG updates
    val currentStreamId = lastSuccessState?.streamId

    // The last position the service's positionSaves reported for the *current* stream, reset to null
    // on a channel/title change. lastSuccessState.resumePosition (used below) only ever reflects
    // where this stream stood when it was first loaded — recordHistory() writes fresh positions
    // to the DB but never back into loaderViewModel's own state, so without this a screensaver
    // resume after watching 45 minutes of a movie would restart from wherever the user was
    // *before this viewing session*, not 45:00.
    var lastKnownPositionMs by remember(currentStreamId) { mutableStateOf<Long?>(null) }

    // Set when catch-up has saved, stopped the player and left (Back, Watch live): the screen it
    // goes to (often the channel's live preview) starts on that same player at once, so from then
    // on this one must not pause, stop or release it.
    var handedOff by remember { mutableStateOf(false) }

    // Observe app focus/lifecycle to pause on background and stop after timeout
    // Live only: whether the stream was playing as the screen paused — see ON_RESUME below.
    var liveWasPlaying by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (!handedOff) {
                            val state = playbackViewModel.playbackState.value
                            liveWasPlaying =
                                lastSuccessState?.isLive == true && (state is PlaybackState.Playing || state is PlaybackState.Buffering)
                            playbackViewModel.onFocusLost(false)
                        }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        playbackViewModel.onFocusRegained()
                        // A long absence (TV screensaver, HDMI input switch) lets onFocusLost's
                        // 30s timer fire stop() while we were away, tearing playback down to Idle
                        // with nothing bringing it back — the screen stayed mounted showing a
                        // black frame forever. Resume with the last known-good stream if that's
                        // what happened. A short absence leaves it Paused instead: right for VOD
                        // (the viewer resumes), but a paused live stream is a frozen, stale frame,
                        // so live that was playing restarts at the live edge too. See
                        // docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-16.
                        val success = lastSuccessState
                        val state = playbackViewModel.playbackState.value
                        val restart = state is PlaybackState.Idle || (liveWasPlaying && state is PlaybackState.Paused)
                        liveWasPlaying = false
                        if (restart && success != null) {
                            playbackViewModel.playStream(success.playerMetadata(), lastKnownPositionMs ?: success.resumePosition)
                        }
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Leaving catch-up (Back, Watch live): the session saved and the player stopped, then [then].
    // Stopped, not released: a release also ends the playback service, and the live preview that
    // starts on it at once is released with it. Stopped now, not through the view model's
    // coroutine, which could land after that preview has started. Both seen on the emulator,
    // 2026-10-10.
    val scope = rememberCoroutineScope()
    val leaveCatchup: (then: () -> Unit) -> Unit = { then ->
        scope.launch {
            finalizeSessionAndAwait(playbackViewModel.playbackState.value, loaderViewModel)
            StreamingPlaybackService.getInstance()?.stop()
            handedOff = true
            then()
        }
    }
    val watchLive: (() -> Unit)? = catchup?.let { { leaveCatchup(onWatchLive) } }
    val catchupBack: (() -> Unit)? = catchup?.let { { leaveCatchup(onBack) } }

    // Stop and fully release playback when leaving the player screen. TV has no
    // background-playback/PiP feature, so the service (and its native decoder/renderer
    // buffers) has no reason to outlive this screen — see stopAndRelease's kdoc. Not once catch-up
    // has handed the player on (see leaveCatchup).
    DisposableEffect(Unit) {
        onDispose {
            if (handedOff) return@onDispose
            // The teardown's own final position save finds no collector by now (the
            // positionSaves effect below has left composition): finalizeSession is that save.
            finalizeSession(playbackViewModel.playbackState.value, loaderViewModel)
            playbackViewModel.stopAndRelease()
        }
    }

    // Another device of the sync group stopped this playback: the explicit Back path (awaited
    // finalise, so the watch position is saved, then release), but landing on Home. See
    // docs/plans/archive/20261001_live-sync-now-playing-plan.md → Remote Stop.
    RemoteStopEffect {
        finalizeSessionAndAwait(playbackViewModel.playbackState.value, loaderViewModel)
        playbackViewModel.stopAndRelease()
        onHome()
    }

    LaunchedEffect(contentType) {
        val playerContentType =
            when (contentType) {
                // Catch-up plays a live channel's archive like a film.
                ContentType.LIVE_TV -> if (catchup == null) PlayerConfigFactory.ContentType.LIVE_TV else PlayerConfigFactory.ContentType.VOD

                ContentType.MOVIES, ContentType.TV_SHOWS -> PlayerConfigFactory.ContentType.VOD

                else -> PlayerConfigFactory.ContentType.VOD
            }
        StreamingPlaybackService.awaitInstanceOrNull()?.setContentType(playerContentType)
    }

    // Save playback position and track choices. Collected from the service's process-wide flow,
    // not a listener set on one instance: Home → return destroys the service (MainActivity.onStop)
    // and ON_RESUME plays on a new one. See docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-04.
    // Keyed on the stream: lastKnownPositionMs is a fresh state per stream, and a collector
    // started for the previous one would keep writing that one's.
    LaunchedEffect(currentStreamId) {
        StreamingPlaybackService.positionSaves.collect { save ->
            lastKnownPositionMs = save.positionMs
            loaderViewModel.recordHistory(save.positionMs, save.durationMs, save.isPaused, save.audioTrackIndex, save.subtitleTrackIndex)
        }
    }

    // Catch-up of a programme still on air: the panel's answer stops at the moment it was asked
    // for (bears), so reaching its end asks again and carries on from there, until the window has
    // been asked for in full. See docs/plans/archive/20261010_catchup-plan.md → Phase 2.
    var catchupAskedAtSec by remember { mutableStateOf(0L) }
    val continueCatchup: (() -> Boolean)? =
        catchup?.let { window ->
            {
                val success = lastSuccessState
                val ended = playbackViewModel.playbackState.value as? PlaybackState.Ended
                val windowEndSec = window.startEpochSec + window.durationSec
                val more = success != null && ended != null && ended.duration > 0L && catchupAskedAtSec < windowEndSec
                if (more) {
                    catchupAskedAtSec = System.currentTimeMillis() / 1000
                    playbackViewModel.playStream(success.playerMetadata(), ended.duration)
                }
                more
            }
        }

    LaunchedEffect(currentStreamId) {
        val state = streamState
        if (state is StreamLoaderViewModel.StreamState.Success) {
            catchupAskedAtSec = System.currentTimeMillis() / 1000
            playbackViewModel.playStream(state.playerMetadata(), state.resumePosition)

            // Restore saved track settings when player is ready
            if (state.savedAudioTrackIndex != null || state.savedSubtitleTrackIndex != null) {
                // playbackState is a plain StateFlow, not Compose snapshot state — wrapping it in
                // snapshotFlow{} never registers an observable read, so it emits once and never
                // again, leaving this stuck waiting forever instead of restoring tracks (this is
                // the bug behind "track choice doesn't stick": it silently never ran). Collect the
                // flow directly instead, same fix already applied on MobilePlayerScreen.
                val readyState =
                    playbackViewModel.playbackState
                        .filter { it is PlaybackState.Playing || it is PlaybackState.Paused || it is PlaybackState.Error }
                        .first() // Wait for first ready state (or bail on Error, so a failed stream doesn't hang this forever)

                if (readyState !is PlaybackState.Error) {
                    val service = StreamingPlaybackService.getInstance()
                    if (service != null) {
                        state.savedAudioTrackIndex?.let { audioIdx ->
                            service.selectAudioTrack(audioIdx)
                        }
                        state.savedSubtitleTrackIndex?.let { subIdx ->
                            service.selectSubtitleTrack(subIdx)
                        }
                    }
                }
            }
        }
    }

    // The series name / episode label / TMDB logo / synopsis above resolve asynchronously (a
    // series-detail and a TMDB lookup) well after the effect above already started playback with
    // whatever was known at that instant — almost always still null. Patch them into the OSD's
    // metadata as they land, without touching playback (see PlaybackViewModel.updateMetadata).
    val enrichedState = streamState as? StreamLoaderViewModel.StreamState.Success
    LaunchedEffect(
        enrichedState?.seriesName,
        enrichedState?.episodeLabel,
        enrichedState?.logoUrl,
        enrichedState?.description,
        enrichedState?.currentEpgProgram?.title,
    ) {
        if (enrichedState != null) {
            playbackViewModel.updateMetadata(enrichedState.streamUrl) {
                it.copy(
                    showTitle = enrichedState.seriesName,
                    episodeLabel = enrichedState.episodeLabel,
                    logoUrl = enrichedState.logoUrl,
                    description = enrichedState.description,
                    programTitle = enrichedState.currentEpgProgram?.title,
                )
            }
        }
    }

    // Autoplay next episode — held here, not in PlayerScreen, which the Loading branch below
    // re-mounts while the next episode loads.
    val upNextState = remember { UpNextState() }
    when (val state = streamState) {
        is StreamLoaderViewModel.StreamState.Loading -> {
            val previous = lastSuccessState
            if (previous == null) {
                LoadingScreen()
            } else {
                PlayerContent(
                    previous,
                    playbackViewModel,
                    loaderViewModel,
                    catchupBack ?: onBack,
                    catchupBack != null,
                    upNextState,
                    continueCatchup,
                    watchLive,
                )
            }
        }

        is StreamLoaderViewModel.StreamState.Error -> {
            TvErrorState(
                message = state.message,
                onRetry = { loaderViewModel.retryLastLoad() },
                title = stringResource(R.string.player_error),
                retryLabel = stringResource(R.string.player_retry),
                onBack = catchupBack ?: onBack,
                backLabel = stringResource(R.string.player_back_to_categories),
            )
        }

        is StreamLoaderViewModel.StreamState.Success -> {
            PlayerContent(
                state,
                playbackViewModel,
                loaderViewModel,
                catchupBack ?: onBack,
                catchupBack != null,
                upNextState,
                continueCatchup,
                watchLive,
            )
        }
    }
}

@Composable
private fun PlayerContent(
    data: StreamLoaderViewModel.StreamState.Success,
    playbackViewModel: PlaybackViewModel,
    loaderViewModel: StreamLoaderViewModel,
    onBack: () -> Unit,
    // Catch-up: [onBack] saves and stops itself (leaveCatchup in TvPlayerScreen).
    onBackStops: Boolean,
    upNextState: UpNextState,
    continueCatchup: (() -> Boolean)?,
    onWatchLive: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val isCatchup = data.catchupLabel != null
    PlayerScreen(
        viewModel = playbackViewModel,
        currentStreamId = data.streamId,
        onBack = {
            // Awaited, not fire-and-forget: this is the explicit Back path, which navigates
            // straight back to the episode-selection screen — its own watch-history read can
            // otherwise win the race against an unawaited write and land on the wrong resume
            // season. See finalizeSessionAndAwait's kdoc and
            // docs/plans/archive/20260908_episode-selection-fragility-plan.md.
            if (onBackStops) {
                onBack()
            } else {
                scope.launch {
                    finalizeSessionAndAwait(playbackViewModel.playbackState.value, loaderViewModel)
                    playbackViewModel.stopAndRelease()
                    onBack()
                }
            }
        },
        onNextChannel = { loaderViewModel.nextChannel() },
        onPreviousChannel = { loaderViewModel.prevChannel() },
        isFavorite = data.isFavorite,
        currentEpgProgram = data.currentEpgProgram,
        nextEpgProgram = data.nextEpgProgram,
        // Not on catch-up: the favourite would be the channel, under the programme's name.
        onToggleFavorite =
            if (isCatchup) {
                null
            } else {
                { loaderViewModel.toggleFavorite() }
            },
        nextEpisode = data.nextEpisode,
        onPlayNextEpisode = { nextEp ->
            // Awaited: playNextEpisode() flips loaderViewModel's state to Loading in its own
            // coroutine, which races an unawaited finalizeSession()'s position-save read of that
            // same state — same hazard as onBack above, see finalizeSessionAndAwait's kdoc.
            scope.launch {
                finalizeSessionAndAwait(playbackViewModel.playbackState.value, loaderViewModel)
                loaderViewModel.playNextEpisode(nextEp)
            }
        },
        autoplayNextSupported = data.supportsAutoplayNext,
        upNextState = upNextState,
        continueOnEnd = continueCatchup,
        onWatchLive = onWatchLive,
    )
}

/** What the player shows and plays for [this] stream. */
private fun StreamLoaderViewModel.StreamState.Success.playerMetadata(): PlayerMetadata =
    PlayerMetadata(
        title = streamName,
        channelName = streamName,
        description = description,
        streamUrl = streamUrl,
        isLive = isLive,
        headers = streamHeaders,
        showTitle = seriesName,
        episodeLabel = episodeLabel,
        logoUrl = logoUrl,
        programTitle = currentEpgProgram?.title,
        catchupLabel = catchupLabel,
    )

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MitohanaLoading(
            style = MaterialTheme.typography.headlineMedium,
            color = CinemaAccent,
        )
    }
}
