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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.ui.player.PlayerScreen

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
    loaderViewModel: StreamLoaderViewModel = viewModel(
        factory = StreamLoaderViewModelFactory(
            context = LocalContext.current,
            initialStreamId = streamId,
            initialStreamName = streamName,
            categoryId = categoryId,
            contentType = contentType,
            episodeId = episodeId,
            episodeExtension = episodeExtension,
            seriesId = seriesId,
            seriesName = seriesName,
            startFromBeginning = startFromBeginning
        )
    )
) {
    val streamState by loaderViewModel.state.collectAsState()
    val currentStreamState by androidx.compose.runtime.rememberUpdatedState(streamState)

    // Stop playback when leaving the player screen
    DisposableEffect(Unit) {
        onDispose {
            // Save final position before leaving (for VOD content)
            val currentState = currentStreamState
            if (currentState is StreamLoaderViewModel.StreamState.Success && !currentState.isLive) {
                val ps = playbackViewModel.playbackState.value
                val pos = when (ps) {
                    is PlaybackState.Playing -> ps.position
                    is PlaybackState.Paused -> ps.position
                    else -> null
                }
                val dur = when (ps) {
                    is PlaybackState.Playing -> ps.duration
                    is PlaybackState.Paused -> ps.duration
                    else -> null
                }
                if (pos != null && dur != null && dur > 0) {
                    loaderViewModel.recordHistory(pos, dur)
                }
            }
            playbackViewModel.stop()
        }
    }

    // Configure player buffer profile based on content type
    LaunchedEffect(contentType) {
        val playerContentType = when (contentType) {
            "LIVE_TV" -> PlayerConfigFactory.ContentType.LIVE_TV
            "MOVIES", "TV_SHOWS" -> PlayerConfigFactory.ContentType.VOD
            else -> PlayerConfigFactory.ContentType.VOD
        }
        StreamingPlaybackService.getInstance()?.setContentType(playerContentType)
    }

    // Set up auto-save listener for playback position
    LaunchedEffect(Unit) {
        StreamingPlaybackService.getInstance()?.setPositionSaveListener { position, duration ->
             loaderViewModel.recordHistory(position, duration)
        }
    }

    // Playback Trigger
    // Use derived state or specific key to avoid re-triggering on EPG updates
    val currentStreamId = (streamState as? StreamLoaderViewModel.StreamState.Success)?.streamId

    LaunchedEffect(currentStreamId) {
        val state = streamState
        if (state is StreamLoaderViewModel.StreamState.Success) {
            val metadata = PlayerMetadata(
                title = state.streamName,
                channelName = "IPTV.atr", // Could be fetched from provider settings
                streamUrl = state.streamUrl,
                isLive = state.isLive,
                headers = state.streamHeaders
            )
            playbackViewModel.playStream(metadata, state.resumePosition)
        }
    }

    when (val state = streamState) {
        is StreamLoaderViewModel.StreamState.Loading -> {
            LoadingScreen()
        }
        is StreamLoaderViewModel.StreamState.Error -> {
            ErrorScreen(
                message = state.message,
                onBack = onBack
            )
        }
        is StreamLoaderViewModel.StreamState.Success -> {
            PlayerScreen(
                viewModel = playbackViewModel,
                onBack = {
                    // Save position before stopping (stop sets state to Idle)
                    if (!state.isLive) {
                        val ps = playbackViewModel.playbackState.value
                        val pos = when (ps) {
                            is PlaybackState.Playing -> ps.position
                            is PlaybackState.Paused -> ps.position
                            else -> null
                        }
                        val dur = when (ps) {
                            is PlaybackState.Playing -> ps.duration
                            is PlaybackState.Paused -> ps.duration
                            else -> null
                        }
                        if (pos != null && dur != null && dur > 0) {
                            loaderViewModel.recordHistory(pos, dur)
                        }
                    }
                    playbackViewModel.stop()
                    onBack()
                },
                onNextChannel = { loaderViewModel.nextChannel() },
                onPreviousChannel = { loaderViewModel.prevChannel() },
                isFavorite = state.isFavorite,
                currentEpgProgram = state.currentEpgProgram,
                nextEpgProgram = state.nextEpgProgram,
                categoryStreams = state.categoryStreams,
                lastWatchedStreams = state.lastWatchedStreams.filter { it.id != state.streamId },
                onStreamSelected = { item ->
                    loaderViewModel.loadStream(item)
                },
                onToggleFavorite = {
                    loaderViewModel.toggleFavorite()
                }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.padding(Spacing.md))
            Text(
                text = "Loading stream...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )
            Spacer(modifier = Modifier.padding(Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary
            )
            Spacer(modifier = Modifier.padding(Spacing.lg))
            CinemaSecondaryButton(
                onClick = onBack,
                text = "Back to Categories"
            )
        }
    }
}
