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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.ui.player.PlayerScreen

/**
 * TV player screen that integrates stream playback via MediaRepository.
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
    viewModel: PlaybackViewModel = viewModel()
) {
    val context = LocalContext.current
    val mediaRepository = remember {
        val appContext = context.applicationContext
        val providerRepo = ProviderRepository(appContext)
        val repo = MediaRepository(appContext, 0L)
        kotlinx.coroutines.runBlocking {
            val entity = providerRepo.getActiveProvider()
            if (entity != null) {
                val resolvedRepo = MediaRepository(appContext, entity.id)
                val password = providerRepo.getPassword(entity.id) ?: ""
                val provider = MediaProviderFactory.create(entity, appContext, password)
                resolvedRepo.setProvider(provider)
                resolvedRepo
            } else repo
        }
    }
    val appSettings = remember { AppSettings(context.applicationContext) }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var streamHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var streamList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var currentStreamIndex by remember { mutableIntStateOf(0) }
    var currentStreamId by remember { mutableStateOf(streamId) }
    var currentStreamName by remember { mutableStateOf(streamName) }

    val coroutineScope = rememberCoroutineScope()

    // Stop playback when leaving the player screen
    DisposableEffect(Unit) {
        onDispose {
            // Save final position before leaving (for VOD content)
            if (contentType != "LIVE_TV") {
                val ps = viewModel.playbackState.value
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
                    mediaRepository.savePlaybackPosition(currentStreamId, currentStreamName, categoryId, contentType, pos, dur)
                }
            }
            viewModel.stop()
        }
    }

    // Favorite state (async for server-backed providers)
    var isFavorite by remember { mutableStateOf(false) }
    LaunchedEffect(currentStreamId, contentType) {
        isFavorite = mediaRepository.isFavoriteSuspend(currentStreamId, contentType)
    }

    // Configure player buffer profile based on content type
    LaunchedEffect(contentType) {
        val playerContentType = when (contentType) {
            "LIVE_TV" -> PlayerConfigFactory.ContentType.LIVE_TV
            "MOVIES", "TV_SHOWS" -> PlayerConfigFactory.ContentType.VOD
            else -> PlayerConfigFactory.ContentType.VOD
        }
        println("TvPlayerScreen: Configuring player for $playerContentType")
        StreamingPlaybackService.getInstance()?.setContentType(playerContentType)
    }

    // Set up auto-save listener for playback position
    LaunchedEffect(streamId, streamName, categoryId, contentType) {
        StreamingPlaybackService.getInstance()?.setPositionSaveListener { position, duration ->
            mediaRepository.savePlaybackPosition(
                itemId = currentStreamId,
                itemName = currentStreamName,
                categoryId = categoryId,
                contentType = contentType,
                position = position,
                duration = duration
            )
            coroutineScope.launch {
                mediaRepository.onPlaybackProgress(currentStreamId, position, duration)
            }
        }
    }

    // Load stream list for channel switching
    LaunchedEffect(categoryId) {
        println("TvPlayerScreen: Loading stream list for category=$categoryId, contentType=$contentType")
        val result = mediaRepository.getItems(categoryId, contentType)
        result.fold(
            onSuccess = { items ->
                streamList = items
                println("TvPlayerScreen: Loaded ${streamList.size} streams")
                // Find current stream index
                currentStreamIndex = streamList.indexOfFirst { it.id == streamId }
                if (currentStreamIndex == -1) currentStreamIndex = 0
                println("TvPlayerScreen: Current stream index=$currentStreamIndex")
            },
            onFailure = {
                println("TvPlayerScreen: Error loading streams: ${it.message}")
                // Keep empty list, disable navigation
            }
        )
    }

    // Channel switching functions
    fun switchToNextChannel() {
        println("TvPlayerScreen: switchToNextChannel called, streamList size=${streamList.size}")
        if (streamList.isEmpty()) {
            println("TvPlayerScreen: streamList is empty, ignoring")
            return
        }
        val nextIndex = (currentStreamIndex + 1) % streamList.size
        val nextStream = streamList[nextIndex]
        println("TvPlayerScreen: Switching from index $currentStreamIndex to $nextIndex (${nextStream.name})")
        currentStreamIndex = nextIndex
        currentStreamId = nextStream.id
        currentStreamName = nextStream.name
    }

    fun switchToPreviousChannel() {
        println("TvPlayerScreen: switchToPreviousChannel called, streamList size=${streamList.size}")
        if (streamList.isEmpty()) {
            println("TvPlayerScreen: streamList is empty, ignoring")
            return
        }
        val prevIndex = if (currentStreamIndex == 0) {
            streamList.size - 1
        } else {
            currentStreamIndex - 1
        }
        val prevStream = streamList[prevIndex]
        println("TvPlayerScreen: Switching from index $currentStreamIndex to $prevIndex (${prevStream.name})")
        currentStreamIndex = prevIndex
        currentStreamId = prevStream.id
        currentStreamName = prevStream.name
    }

    // Resolve playable stream URL on launch
    LaunchedEffect(currentStreamId, episodeId) {
        isLoading = true
        error = null
        println("TvPlayerScreen: Building URL for contentType=$contentType, streamId=$currentStreamId, episodeId=$episodeId, extension=$episodeExtension")
        val result = mediaRepository.resolvePlayableStream(
            itemId = currentStreamId,
            contentType = contentType,
            episodeId = episodeId,
            extension = episodeExtension
        )
        result.fold(
            onSuccess = { playable ->
                streamUrl = playable.uri
                streamHeaders = playable.headers
                println("TvPlayerScreen: Stream URL built successfully: ${playable.uri}")
                isLoading = false
            },
            onFailure = { e ->
                error = e.message ?: "Failed to load stream"
                println("TvPlayerScreen: Error building stream URL: $error")
                isLoading = false
            }
        )
    }

    // Fetch saved position if VOD content (async for server-backed providers)
    var savedPosition by remember { mutableStateOf<org.njarasoa.fijerena.core.network.WatchedItem?>(null) }
    var positionLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(currentStreamId, contentType) {
        positionLoaded = false
        savedPosition = if (!startFromBeginning && contentType != "LIVE_TV" && appSettings.autoResumeEnabled) {
            mediaRepository.getPlaybackPositionSuspend(currentStreamId, contentType)
        } else null
        positionLoaded = true
    }

    // Start playback when URL is ready or stream info changes
    LaunchedEffect(streamUrl, currentStreamId, currentStreamName, positionLoaded) {
        if (!positionLoaded) return@LaunchedEffect
        streamUrl?.let { url ->
            // Save last played item
            // For TV shows, save the series info (not episode) so "Last Watched" works correctly
            val watchHistoryItemId = if (contentType == "TV_SHOWS" && seriesId != null) seriesId else currentStreamId
            val watchHistoryItemName = if (contentType == "TV_SHOWS" && seriesName != null) seriesName else currentStreamName
            mediaRepository.saveLastPlayedItem(categoryId, watchHistoryItemId, watchHistoryItemName, contentType)

            println("TvPlayerScreen: Playing stream (streamId=$currentStreamId, name=$currentStreamName)")
            println("TvPlayerScreen: Stream URL: $url")

            // Determine resume position
            val resumePosition = savedPosition?.let { saved ->
                val progressPercent = if (saved.duration > 0) {
                    (saved.playbackPosition.toFloat() / saved.duration.toFloat()) * 100f
                } else 0f

                // Only resume if 2-95% watched
                if (progressPercent in 2.0..95.0 && !saved.isCompleted) {
                    println("TvPlayerScreen: Resuming playback from ${saved.playbackPosition}ms (${progressPercent.toInt()}%)")
                    saved.playbackPosition
                } else {
                    println("TvPlayerScreen: Starting from beginning (progress: ${progressPercent.toInt()}%, completed: ${saved.isCompleted})")
                    0L
                }
            } ?: 0L

            val metadata = PlayerMetadata(
                title = currentStreamName,
                channelName = "IPTV.atr",
                streamUrl = url,
                isLive = contentType == "LIVE_TV", // Only live TV is live, movies/shows are VOD
                headers = streamHeaders
            )
            viewModel.playStream(metadata, resumePosition)
        }
    }

    when {
        isLoading -> {
            LoadingScreen()
        }
        error != null -> {
            ErrorScreen(
                message = error ?: "Unknown error",
                onBack = onBack
            )
        }
        else -> {
            PlayerScreen(
                viewModel = viewModel,
                onBack = {
                    // Save position before stopping (stop sets state to Idle)
                    if (contentType != "LIVE_TV") {
                        val ps = viewModel.playbackState.value
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
                            mediaRepository.savePlaybackPosition(
                                currentStreamId, currentStreamName, categoryId, contentType, pos, dur
                            )
                            coroutineScope.launch {
                                mediaRepository.onPlaybackProgress(currentStreamId, pos, dur)
                            }
                        }
                    }
                    viewModel.stop()
                    onBack()
                },
                onNextChannel = { switchToNextChannel() },
                onPreviousChannel = { switchToPreviousChannel() },
                isFavorite = isFavorite,
                onToggleFavorite = {
                    coroutineScope.launch {
                        if (isFavorite) {
                            val removed = mediaRepository.removeFavoriteSuspend(currentStreamId, contentType)
                            if (removed) isFavorite = false
                        } else {
                            val added = mediaRepository.addFavoriteSuspend(
                                currentStreamId,
                                currentStreamName,
                                categoryId,
                                contentType
                            )
                            if (added) isFavorite = true
                        }
                    }
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
