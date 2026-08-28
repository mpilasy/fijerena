@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.finalizeSession
import org.njarasoa.fijerena.core.ui.viewmodels.rememberStableRecentOrder
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.player.ImmutableMediaList
import org.njarasoa.fijerena.ui.player.PlayerScreen
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.core.ui.components.MitohanaLoading

/**
 * TV player screen that integrates stream playback via StreamLoaderViewModel.
 *
 * Features:
 * - Fetches stream URL from MediaRepository (provider-agnostic)
 * - Creates PlayerMetadata with stream info
 * - Delegates to PlayerScreen for playback UI
 * - D-pad friendly controls
 */
@Composable
fun TvPlayerScreen(
    streamId: String,
    streamName: String,
    categoryId: String,
    contentType: String,
    onBack: () -> Unit,
    episodeId: String? = null,
    episodeExtension: String? = null,
    seriesId: String? = null,
    seriesName: String? = null,
    startFromBeginning: Boolean = false,
    playbackViewModel: PlaybackViewModel = viewModel(),
    loaderViewModel: StreamLoaderViewModel =
        viewModel(
            factory =
                StreamLoaderViewModelFactory(
                    context = LocalContext.current,
                    initialStreamId = streamId,
                    initialStreamName = streamName,
                    categoryId = categoryId,
                    contentType = contentType,
                    episodeId = episodeId,
                    episodeExtension = episodeExtension,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    startFromBeginning = startFromBeginning,
                ),
        ),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Observe app focus/lifecycle to pause on background and stop after timeout
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        playbackViewModel.onFocusLost(false)
                    }
                    Lifecycle.Event.ON_RESUME -> playbackViewModel.onFocusRegained()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val streamState by loaderViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.playbackState.collectAsStateWithLifecycle()

    // Remember the last successful stream so a channel change can keep PlayerScreen mounted
    // (and the old video visible) through the Loading window instead of unmounting to a
    // full-screen spinner. The LaunchedEffect below still checks live streamState, not this.
    var lastSuccessState by remember { mutableStateOf<StreamLoaderViewModel.StreamState.Success?>(null) }
    if (streamState is StreamLoaderViewModel.StreamState.Success) {
        lastSuccessState = streamState as StreamLoaderViewModel.StreamState.Success
    }

    // Stop playback when leaving the player screen
    DisposableEffect(Unit) {
        onDispose {
            finalizeSession(playbackViewModel.playbackState.value, loaderViewModel)
            playbackViewModel.stop()
            // Service outlives this screen — drop the listener so it doesn't keep
            // this loaderViewModel (and everything it references) pinned in memory
            // until the next player screen overwrites it.
            StreamingPlaybackService.getInstance()?.setPositionSaveListener(null)
        }
    }

    // Configure player buffer profile based on content type
    LaunchedEffect(contentType) {
        val playerContentType =
            when (contentType) {
                ContentType.LIVE_TV -> PlayerConfigFactory.ContentType.LIVE_TV
                ContentType.MOVIES, ContentType.TV_SHOWS -> PlayerConfigFactory.ContentType.VOD
                else -> PlayerConfigFactory.ContentType.VOD
            }
        StreamingPlaybackService.awaitInstance().setContentType(playerContentType)
    }

    // Set up auto-save listener for playback position
    LaunchedEffect(Unit) {
        StreamingPlaybackService.awaitInstance().setPositionSaveListener { position, duration, isPaused, audioIndex, subtitleIndex ->
            loaderViewModel.recordHistory(position, duration, isPaused, audioIndex, subtitleIndex)
        }
    }

    // Playback Trigger
    // Use derived state or specific key to avoid re-triggering on EPG updates
    val currentStreamId = lastSuccessState?.streamId

    LaunchedEffect(currentStreamId) {
        val state = streamState
        if (state is StreamLoaderViewModel.StreamState.Success) {
            val metadata =
                PlayerMetadata(
                    title = state.streamName,
                    channelName = state.streamName,
                    description = state.description,
                    streamUrl = state.streamUrl,
                    isLive = state.isLive,
                    headers = state.streamHeaders,
                )
            playbackViewModel.playStream(metadata, state.resumePosition)

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

    when (val state = streamState) {
        is StreamLoaderViewModel.StreamState.Loading -> {
            val previous = lastSuccessState
            if (previous == null) {
                LoadingScreen()
            } else {
                PlayerContent(previous, playbackViewModel, loaderViewModel, onBack)
            }
        }
        is StreamLoaderViewModel.StreamState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { loaderViewModel.retryLastLoad() },
                onBack = onBack,
            )
        }
        is StreamLoaderViewModel.StreamState.Success -> {
            PlayerContent(state, playbackViewModel, loaderViewModel, onBack)
        }
    }
}

@Composable
private fun PlayerContent(
    data: StreamLoaderViewModel.StreamState.Success,
    playbackViewModel: PlaybackViewModel,
    loaderViewModel: StreamLoaderViewModel,
    onBack: () -> Unit,
) {
    // The flyout offers channels to switch to, so the one already playing is filtered out —
    // unlike the split preview panel, which keeps it as the highlighted row. Held in display
    // order so watching past the delay doesn't re-sort the list the viewer may have open.
    val publishedRecentStreams by loaderViewModel.recentItems.collectAsStateWithLifecycle()
    val recentStreams = rememberStableRecentOrder(publishedRecentStreams)
    PlayerScreen(
        viewModel = playbackViewModel,
        currentStreamId = data.streamId,
        onBack = {
            finalizeSession(playbackViewModel.playbackState.value, loaderViewModel)
            playbackViewModel.stop()
            onBack()
        },
        onNextChannel = { loaderViewModel.nextChannel() },
        onPreviousChannel = { loaderViewModel.prevChannel() },
        isFavorite = data.isFavorite,
        currentEpgProgram = data.currentEpgProgram,
        nextEpgProgram = data.nextEpgProgram,
        categoryStreams = ImmutableMediaList(data.categoryStreams),
        recentStreams =
            remember(recentStreams, data.streamId) {
                ImmutableMediaList(recentStreams.filter { it.id != data.streamId })
            },
        onStreamSelected = { item ->
            finalizeSession(playbackViewModel.playbackState.value, loaderViewModel)
            loaderViewModel.loadStream(item)
        },
        onToggleFavorite = {
            loaderViewModel.toggleFavorite()
        },
    )
}

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

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError,
            )
            Spacer(modifier = Modifier.padding(Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary,
            )
            Spacer(modifier = Modifier.padding(Spacing.lg))
            CinemaSecondaryButton(
                onClick = onRetry,
                text = stringResource(R.string.player_retry),
            )
            Spacer(modifier = Modifier.padding(Spacing.sm))
            CinemaSecondaryButton(
                onClick = onBack,
                text = stringResource(R.string.player_back_to_categories),
            )
        }
    }
}
