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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.XtreamStream
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.ui.player.PlayerScreen

/**
 * TV player screen that integrates Xtream stream playback.
 *
 * Features:
 * - Fetches stream URL from XtreamRepository
 * - Creates PlayerMetadata with stream info
 * - Delegates to PlayerScreen for playback UI
 * - D-pad friendly controls
 */
@Composable
fun TvPlayerScreen(
    streamId: Int,
    streamName: String,
    categoryId: String,
    contentType: String,
    onBack: () -> Unit,
    episodeId: String? = null,
    episodeExtension: String? = null,
    seriesId: Int? = null,
    seriesName: String? = null,
    viewModel: PlaybackViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }
    val appSettings = remember { repository.getAppSettings() }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var streamList by remember { mutableStateOf<List<XtreamStream>>(emptyList()) }
    var currentStreamIndex by remember { mutableIntStateOf(0) }
    var currentStreamId by remember { mutableIntStateOf(streamId) }
    var currentStreamName by remember { mutableStateOf(streamName) }

    // Favorite state
    var isFavorite by remember(currentStreamId, contentType) {
        mutableStateOf(repository.isFavorite(currentStreamId, contentType))
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
            repository.savePlaybackPosition(
                streamId = currentStreamId,
                streamName = currentStreamName,
                categoryId = categoryId,
                contentType = contentType,
                position = position,
                duration = duration
            )
        }
    }

    // Load stream list for channel switching
    LaunchedEffect(categoryId) {
        println("TvPlayerScreen: Loading stream list for category=$categoryId, contentType=$contentType")
        // Ensure session is restored first
        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                println("TvPlayerScreen: Session restored, fetching streams")
                val result = when (contentType) {
                    "LIVE_TV" -> repository.getStreams(categoryId)
                    "MOVIES" -> repository.getVodStreams(categoryId)
                    "TV_SHOWS" -> repository.getSeries(categoryId)
                    else -> repository.getStreams(categoryId)
                }
                when (result) {
                    is Result.Success -> {
                        streamList = result.data
                        println("TvPlayerScreen: Loaded ${streamList.size} streams")
                        // Find current stream index
                        currentStreamIndex = streamList.indexOfFirst { it.streamId == streamId }
                        if (currentStreamIndex == -1) currentStreamIndex = 0
                        println("TvPlayerScreen: Current stream index=$currentStreamIndex")
                    }
                    is Result.Error -> {
                        println("TvPlayerScreen: Error loading streams: ${result.message}")
                        // Keep empty list, disable navigation
                    }
                }
            }
            is Result.Error -> {
                println("TvPlayerScreen: Session restore failed: ${sessionResult.message}")
            }
        }
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
        currentStreamId = nextStream.streamId
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
        currentStreamId = prevStream.streamId
        currentStreamName = prevStream.name
    }

    // Restore session and fetch stream URL on launch
    LaunchedEffect(currentStreamId, episodeId) {
        isLoading = true
        error = null
        println("TvPlayerScreen: Building URL for contentType=$contentType, streamId=$currentStreamId, episodeId=$episodeId, extension=$episodeExtension")
        // First restore the session to initialize the API service
        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                // Session restored, now build stream URL
                val urlResult = if (episodeId != null && episodeExtension != null) {
                    // For TV show episodes, use episode-specific URL builder
                    println("TvPlayerScreen: Using buildEpisodeStreamUrl with episodeId=$episodeId, extension=$episodeExtension")
                    repository.buildEpisodeStreamUrl(episodeId, episodeExtension)
                } else {
                    // For live TV and movies, use standard URL builder
                    println("TvPlayerScreen: Using buildStreamUrl with streamId=$currentStreamId, contentType=$contentType, extension=$episodeExtension")
                    repository.buildStreamUrl(currentStreamId, contentType, episodeExtension)
                }

                when (urlResult) {
                    is Result.Success -> {
                        streamUrl = urlResult.data
                        println("TvPlayerScreen: Stream URL built successfully: $streamUrl")
                        isLoading = false
                    }
                    is Result.Error -> {
                        error = urlResult.message ?: "Failed to load stream"
                        println("TvPlayerScreen: Error building stream URL: $error")
                        isLoading = false
                    }
                }
            }
            is Result.Error -> {
                error = sessionResult.message ?: "Session expired. Please login again."
                isLoading = false
            }
        }
    }

    // Fetch saved position if VOD content
    val savedPosition = remember(currentStreamId, contentType) {
        if (contentType != "LIVE_TV" && appSettings.autoResumeEnabled) {
            repository.getPlaybackPosition(currentStreamId, contentType)
        } else null
    }

    // Start playback when URL is ready or stream info changes
    LaunchedEffect(streamUrl, currentStreamId, currentStreamName) {
        streamUrl?.let { url ->
            // Save last played stream
            // For TV shows, save the series info (not episode) so "Last Watched" works correctly
            val watchHistoryStreamId = if (contentType == "TV_SHOWS" && seriesId != null) seriesId else currentStreamId
            val watchHistoryStreamName = if (contentType == "TV_SHOWS" && seriesName != null) seriesName else currentStreamName
            repository.saveLastPlayedStream(categoryId, watchHistoryStreamId, watchHistoryStreamName, contentType)

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
                headers = emptyMap()
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
                    viewModel.stop()
                    onBack()
                },
                onNextChannel = { switchToNextChannel() },
                onPreviousChannel = { switchToPreviousChannel() },
                isFavorite = isFavorite,
                onToggleFavorite = {
                    if (isFavorite) {
                        val removed = repository.removeFavorite(currentStreamId, contentType)
                        if (removed) isFavorite = false
                    } else {
                        val added = repository.addFavorite(
                            currentStreamId,
                            currentStreamName,
                            categoryId,
                            contentType
                        )
                        if (added) isFavorite = true
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
