package com.example.firstvideoplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import com.example.firstvideoplayer.core.player.model.PlaybackState
import com.example.firstvideoplayer.core.player.model.PlayerMetadata
import com.example.firstvideoplayer.core.player.viewmodel.PlaybackViewModel

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val playbackState = viewModel.playbackState.collectAsState().value
    val currentMetadata = viewModel.currentMetadata.collectAsState().value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Center
    ) {
        when (playbackState) {
            PlaybackState.Idle -> IdleContent(onBack)
            PlaybackState.Buffering -> BufferingContent()
            is PlaybackState.Playing -> PlayingContent(
                playbackState = playbackState,
                metadata = currentMetadata,
                onPause = { viewModel.pause() },
                onBack = onBack
            )
            is PlaybackState.Paused -> PausedContent(
                playbackState = playbackState,
                metadata = currentMetadata,
                onResume = { viewModel.resume() },
                onBack = onBack
            )
            PlaybackState.Ended -> EndedContent(onBack)
            is PlaybackState.Error -> ErrorContent(
                error = playbackState,
                onRetry = { viewModel.playStream(currentMetadata) },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun IdleContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Ready to play",
            color = Color.White,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.padding(8.dp)
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun BufferingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(color = Color.White)
        Spacer(modifier = Modifier.padding(16.dp))
        Text(
            text = "Loading...",
            color = Color.White,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun PlayingContent(
    playbackState: PlaybackState.Playing,
    metadata: PlayerMetadata,
    onPause: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        // Header
        Column {
            Text(
                text = metadata.title,
                color = Color.White,
                fontSize = 28.sp
            )
            Text(
                text = metadata.channelName,
                color = Color.Gray,
                fontSize = 16.sp
            )
        }

        // Center - playback info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(0.5f)
        ) {
            Text(
                text = "Playing",
                color = Color.White,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.padding(16.dp))
            val durationSeconds = playbackState.duration / 1000
            val positionSeconds = playbackState.position / 1000
            Text(
                text = "$positionSeconds / $durationSeconds seconds",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }

        // Controls
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Button(onClick = onPause) {
                Text("Pause")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun PausedContent(
    playbackState: PlaybackState.Paused,
    metadata: PlayerMetadata,
    onResume: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = metadata.title,
                color = Color.White,
                fontSize = 28.sp
            )
            Text(
                text = metadata.channelName,
                color = Color.Gray,
                fontSize = 16.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(0.5f)
        ) {
            Text(
                text = "Paused",
                color = Color.White,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.padding(16.dp))
            val durationSeconds = playbackState.duration / 1000
            val positionSeconds = playbackState.position / 1000
            Text(
                text = "$positionSeconds / $durationSeconds seconds",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }

        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Button(onClick = onResume) {
                Text("Resume")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun EndedContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Playback ended",
            color = Color.White,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun ErrorContent(
    error: PlaybackState.Error,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Playback Error",
            color = Color.Red,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Text(
            text = error.message,
            color = Color.Gray,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.padding(32.dp))
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
