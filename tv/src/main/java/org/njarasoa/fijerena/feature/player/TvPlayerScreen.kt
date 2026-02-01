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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.XtreamStream
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
    viewModel: PlaybackViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var streamList by remember { mutableStateOf<List<XtreamStream>>(emptyList()) }
    var currentStreamIndex by remember { mutableIntStateOf(0) }
    var currentStreamId by remember { mutableIntStateOf(streamId) }
    var currentStreamName by remember { mutableStateOf(streamName) }

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
        // First restore the session to initialize the API service
        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                // Session restored, now build stream URL
                val urlResult = if (episodeId != null && episodeExtension != null) {
                    // For TV show episodes, use episode-specific URL builder
                    repository.buildEpisodeStreamUrl(episodeId, episodeExtension)
                } else {
                    // For live TV and movies, use standard URL builder
                    repository.buildStreamUrl(currentStreamId, contentType)
                }

                when (urlResult) {
                    is Result.Success -> {
                        streamUrl = urlResult.data
                        isLoading = false
                    }
                    is Result.Error -> {
                        error = urlResult.message ?: "Failed to load stream"
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

    // Start playback when URL is ready
    LaunchedEffect(streamUrl) {
        streamUrl?.let { url ->
            // Save last played stream
            repository.saveLastPlayedStream(categoryId, currentStreamId)

            val metadata = PlayerMetadata(
                title = currentStreamName,
                channelName = "IPTV.atr",
                streamUrl = url,
                isLive = contentType == "LIVE_TV", // Only live TV is live, movies/shows are VOD
                headers = emptyMap()
            )
            viewModel.playStream(metadata)
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
                onPreviousChannel = { switchToPreviousChannel() }
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
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.padding(16.dp))
            Text(
                text = "Loading stream...",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
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
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.padding(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.padding(24.dp))
            Button(onClick = onBack) {
                Text("Back to Categories")
            }
        }
    }
}
