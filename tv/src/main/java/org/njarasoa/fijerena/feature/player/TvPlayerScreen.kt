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
    onBack: () -> Unit,
    viewModel: PlaybackViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager)
    }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Restore session and fetch stream URL on launch
    LaunchedEffect(streamId) {
        // First restore the session to initialize the API service
        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                // Session restored, now build stream URL
                when (val urlResult = repository.buildStreamUrl(streamId)) {
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
            val metadata = PlayerMetadata(
                title = "Stream $streamId",
                channelName = "IPTV.atr",
                streamUrl = url,
                isLive = true,
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
