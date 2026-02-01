@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel = viewModel(),
    onBack: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onPreviousChannel: () -> Unit = {}
) {
    val playbackState = viewModel.playbackState.collectAsState().value
    val currentMetadata = viewModel.currentMetadata.collectAsState().value
    val context = LocalContext.current

    var showControls by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Auto-hide controls after 10 seconds
    LaunchedEffect(showControls) {
        if (showControls && !showStats) {
            delay(10.seconds)
            showControls = false
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            val currentTime = System.currentTimeMillis()
                            val timeDiff = currentTime - lastClickTime

                            if (timeDiff < 500) {
                                // Double click detected - toggle stats
                                showStats = !showStats
                                showControls = showStats
                            } else {
                                // Single click - toggle controls
                                if (!showStats) {
                                    showControls = !showControls
                                }
                            }

                            lastClickTime = currentTime
                            true
                        }
                        Key.DirectionUp -> {
                            println("PlayerScreen: UP button pressed")
                            onPreviousChannel()
                            showControls = true
                            println("PlayerScreen: showControls set to true, metadata title=${currentMetadata.title}")
                            true
                        }
                        Key.DirectionDown -> {
                            println("PlayerScreen: DOWN button pressed")
                            onNextChannel()
                            showControls = true
                            println("PlayerScreen: showControls set to true, metadata title=${currentMetadata.title}")
                            true
                        }
                        Key.Back -> {
                            if (showStats) {
                                showStats = false
                                showControls = false
                                true
                            } else if (showControls) {
                                showControls = false
                                true
                            } else {
                                viewModel.stop()
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Video surface
        val playerView = remember {
            PlayerView(context).apply {
                useController = false // We'll use custom controls
                keepScreenOn = true
            }
        }

        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // Bind player to view when service is available
        DisposableEffect(Unit) {
            val service = StreamingPlaybackService.getInstance()
            playerView.player = service?.getPlayer()

            onDispose {
                playerView.player = null
            }
        }

        // Loading/Error overlays (always show)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Center
        ) {
            when (playbackState) {
                PlaybackState.Idle -> IdleContent(onBack)
                PlaybackState.Buffering -> BufferingContent()
                is PlaybackState.Ended -> EndedContent(onBack)
                is PlaybackState.Error -> ErrorContent(
                    error = playbackState,
                    onRetry = { viewModel.playStream(currentMetadata) },
                    onBack = onBack
                )
                else -> { /* Show controls overlay below */ }
            }
        }

        // Stats overlay (double-click to show)
        AnimatedVisibility(
            visible = showStats && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused),
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            StatsOverlay(
                playbackState = playbackState,
                metadata = currentMetadata,
                context = context
            )
        }

        // Metadata overlay (single-click to show)
        val overlayVisible = showControls && !showStats
        println("PlayerScreen: Overlay visible=$overlayVisible, showControls=$showControls, showStats=$showStats, playbackState=$playbackState, metadata.title=${currentMetadata.title}")

        AnimatedVisibility(
            visible = overlayVisible,
            modifier = Modifier.align(BottomCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            println("PlayerScreen: Inside AnimatedVisibility content")
            val isPaused = playbackState is PlaybackState.Paused
            MetadataOverlay(
                playbackState = playbackState,
                metadata = currentMetadata,
                isPaused = isPaused,
                onPause = if (!isPaused) ({ viewModel.pause() }) else null,
                onResume = if (isPaused) ({ viewModel.resume() }) else null,
                onBack = {
                    viewModel.stop()
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun StatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    context: android.content.Context
) {
    val player = StreamingPlaybackService.getInstance()?.getPlayer()

    // Get current track info
    var videoCodec by remember { mutableStateOf("N/A") }
    var videoResolution by remember { mutableStateOf("N/A") }
    var videoFrameRate by remember { mutableStateOf("N/A") }
    var videoBitrate by remember { mutableStateOf("N/A") }

    var audioCodec by remember { mutableStateOf("N/A") }
    var audioSampleRate by remember { mutableStateOf("N/A") }
    var audioChannels by remember { mutableStateOf("N/A") }
    var audioBitrate by remember { mutableStateOf("N/A") }

    var bufferedPosition by remember { mutableStateOf(0L) }

    // Update stats periodically
    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                // Update buffered position
                bufferedPosition = p.bufferedPosition

                val tracks = p.currentTracks

                // Get video track
                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        videoResolution = "${format.width} × ${format.height}"
                        videoFrameRate = if (format.frameRate > 0) "${format.frameRate.toInt()} fps" else "N/A"
                        videoBitrate = formatBitrate(format.bitrate)
                    }
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        audioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        audioSampleRate = if (format.sampleRate > 0) "${format.sampleRate} Hz" else "N/A"
                        audioChannels = if (format.channelCount > 0) "${format.channelCount}" else "N/A"
                        audioBitrate = formatBitrate(format.bitrate)
                    }
                }
            }

            delay(1.seconds)
        }
    }

    Box(
        modifier = Modifier
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
            .width(400.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Text(
                text = "📊 Stats for Nerds",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Video stats
            Text(
                text = "VIDEO",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )

            StatRow("Codec", videoCodec)
            StatRow("Resolution", videoResolution)
            StatRow("Frame Rate", videoFrameRate)
            StatRow("Bitrate", videoBitrate)

            Spacer(modifier = Modifier.height(8.dp))

            // Audio stats
            Text(
                text = "AUDIO",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )

            StatRow("Codec", audioCodec)
            StatRow("Sample Rate", audioSampleRate)
            StatRow("Channels", audioChannels)
            StatRow("Bitrate", audioBitrate)

            Spacer(modifier = Modifier.height(8.dp))

            // Playback stats
            Text(
                text = "PLAYBACK",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )

            val position = when (playbackState) {
                is PlaybackState.Playing -> playbackState.position
                is PlaybackState.Paused -> playbackState.position
                else -> 0L
            }

            val duration = when (playbackState) {
                is PlaybackState.Playing -> playbackState.duration
                is PlaybackState.Paused -> playbackState.duration
                else -> 0L
            }

            StatRow("Position", formatTime(position))
            StatRow("Duration", if (duration > 0) formatTime(duration) else "Live")
            StatRow("Buffered", formatTime(bufferedPosition))

            Spacer(modifier = Modifier.height(8.dp))

            // Stream info
            Text(
                text = "STREAM",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )

            StatRow("Type", if (metadata.isLive) "Live" else "VOD")
            StatRow("URL", metadata.streamUrl.substringAfterLast("/").take(30))

            Spacer(modifier = Modifier.height(8.dp))

            // Device info
            Text(
                text = "DEVICE",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )

            StatRow("Model", android.os.Build.MODEL)
            StatRow("Android", "API ${android.os.Build.VERSION.SDK_INT}")

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Double-click OK to hide",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBitrate(bitrate: Int): String {
    return if (bitrate > 0) {
        val kbps = bitrate / 1000
        if (kbps > 1000) {
            String.format("%.1f Mbps", kbps / 1000f)
        } else {
            "$kbps Kbps"
        }
    } else {
        "Unknown"
    }
}

@Composable
private fun MetadataOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    isPaused: Boolean,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val position = when (playbackState) {
        is PlaybackState.Playing -> playbackState.position
        is PlaybackState.Paused -> playbackState.position
        else -> 0L
    }

    val duration = when (playbackState) {
        is PlaybackState.Playing -> playbackState.duration
        is PlaybackState.Paused -> playbackState.duration
        else -> 0L
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel name and title
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = metadata.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Progress bar (for non-live streams)
            if (duration > 0 && !metadata.isLive) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            } else if (metadata.isLive) {
                // Live indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, shape = RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPaused) {
                    Button(onClick = { onResume?.invoke() }) {
                        Text("▶ Resume")
                    }
                } else {
                    Button(onClick = { onPause?.invoke() }) {
                        Text("⏸ Pause")
                    }
                }

                Button(onClick = onBack) {
                    Text("⬅ Back")
                }
            }

            // Hint text
            Text(
                text = "Press OK to hide controls • Press BACK to exit",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun IdleContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
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
        horizontalAlignment = CenterHorizontally,
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
private fun EndedContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
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
        horizontalAlignment = CenterHorizontally,
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
