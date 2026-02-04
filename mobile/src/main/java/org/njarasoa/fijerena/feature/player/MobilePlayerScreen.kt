package org.njarasoa.fijerena.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * Mobile player screen with touch controls and Stats for Nerds overlay.
 */
@Composable
fun MobilePlayerScreen(
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

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(false) }

    val playbackState = viewModel.playbackState.collectAsState().value
    val currentMetadata = viewModel.currentMetadata.collectAsState().value

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls && !showStats) {
            delay(CinemaAnimation.controlsAutoHideMobileMs)
            showControls = false
        }
    }

    // Configure player buffer profile based on content type
    LaunchedEffect(contentType) {
        val playerContentType = when (contentType) {
            "LIVE_TV" -> org.njarasoa.fijerena.core.player.config.PlayerConfigFactory.ContentType.LIVE_TV
            "MOVIES", "TV_SHOWS" -> org.njarasoa.fijerena.core.player.config.PlayerConfigFactory.ContentType.VOD
            else -> org.njarasoa.fijerena.core.player.config.PlayerConfigFactory.ContentType.VOD
        }
        StreamingPlaybackService.getInstance()?.setContentType(playerContentType)
    }

    // Load stream URL
    LaunchedEffect(streamId, episodeId) {
        isLoading = true
        error = null

        when (val sessionResult = repository.restoreSession()) {
            is Result.Success -> {
                val urlResult = if (episodeId != null && episodeExtension != null) {
                    repository.buildEpisodeStreamUrl(episodeId, episodeExtension)
                } else {
                    repository.buildStreamUrl(streamId, contentType, episodeExtension)
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
                error = sessionResult.message ?: "Session expired"
                isLoading = false
            }
        }
    }

    // Start playback when URL is ready
    LaunchedEffect(streamUrl) {
        streamUrl?.let { url ->
            val watchHistoryStreamId = if (contentType == "TV_SHOWS" && seriesId != null) seriesId else streamId
            val watchHistoryStreamName = if (contentType == "TV_SHOWS" && seriesName != null) seriesName else streamName
            repository.saveLastPlayedStream(categoryId, watchHistoryStreamId, watchHistoryStreamName, contentType)

            val metadata = PlayerMetadata(
                title = streamName,
                channelName = "IPTV.atr",
                streamUrl = url,
                isLive = contentType == "LIVE_TV",
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
            ErrorScreen(message = error ?: "Unknown error", onBack = onBack)
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        if (showStats) {
                            showStats = false
                        } else {
                            showControls = !showControls
                        }
                    }
            ) {
                // Video surface
                val playerView = remember {
                    PlayerView(context).apply {
                        useController = false
                        keepScreenOn = true
                    }
                }

                AndroidView(
                    factory = { playerView },
                    modifier = Modifier.fillMaxSize()
                )

                // Bind player to view
                DisposableEffect(Unit) {
                    val service = StreamingPlaybackService.getInstance()
                    playerView.player = service?.getPlayer()

                    onDispose {
                        playerView.player = null
                    }
                }

                // Loading/Error overlays
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (playbackState) {
                        PlaybackState.Buffering -> {
                            CircularProgressIndicator(color = Color.White)
                        }
                        is PlaybackState.Error -> {
                            ErrorOverlay(
                                error = playbackState,
                                onRetry = { viewModel.playStream(currentMetadata) },
                                onBack = onBack
                            )
                        }
                        else -> { /* Playing or paused */ }
                    }
                }

                // Touch controls overlay
                AnimatedVisibility(
                    visible = showControls && !showStats && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ControlsOverlay(
                        playbackState = playbackState,
                        metadata = currentMetadata,
                        onPlayPause = {
                            if (playbackState is PlaybackState.Paused) {
                                viewModel.resume()
                            } else {
                                viewModel.pause()
                            }
                        },
                        onBack = {
                            viewModel.stop()
                            onBack()
                        },
                        onStats = { showStats = true }
                    )
                }

                // Stats overlay
                AnimatedVisibility(
                    visible = showStats,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    MobileStatsOverlay(
                        playbackState = playbackState,
                        metadata = currentMetadata,
                        onClose = { showStats = false }
                    )
                }
            }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(MobileDimensions.iconXLarge),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Loading stream...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    error: PlaybackState.Error,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(32.dp),
        color = Color.Black.copy(alpha = CinemaAlpha.overlayMedium),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun ControlsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onStats: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Consume taps on the overlay background so they don't
                // propagate to the parent Box and toggle controls off
            }
    ) {
        // Top bar with back button and stats button
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(MobileDimensions.iconLarge)
                )
            }
            IconButton(onClick = onStats) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Stats for Nerds",
                    tint = Color.White,
                    modifier = Modifier.size(MobileDimensions.iconMedium)
                )
            }
        }

        // Center play/pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.Center)
                .size(MobileDimensions.iconPlayContainer)
        ) {
            Icon(
                imageVector = if (playbackState is PlaybackState.Paused) {
                    Icons.Default.PlayArrow
                } else {
                    Icons.Default.Pause
                },
                contentDescription = if (playbackState is PlaybackState.Paused) "Play" else "Pause",
                tint = Color.White,
                modifier = Modifier.size(MobileDimensions.iconPlayIcon)
            )
        }

        // Bottom metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = CinemaAlpha.textMedium))
                .padding(16.dp)
        ) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            if (metadata.isLive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(MobileDimensions.liveDotSize)
                            .background(Color.Red, shape = MaterialTheme.shapes.small)
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            } else {
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

                if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { position.toFloat() / duration.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileStatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onClose: () -> Unit
) {
    // Collect stats from player
    var videoCodec by remember { mutableStateOf("N/A") }
    var videoResolution by remember { mutableStateOf("N/A") }
    var videoFrameRate by remember { mutableStateOf("N/A") }
    var videoBitrate by remember { mutableStateOf("N/A") }

    var audioCodec by remember { mutableStateOf("N/A") }
    var audioSampleRate by remember { mutableStateOf("N/A") }
    var audioChannels by remember { mutableStateOf("N/A") }
    var audioBitrate by remember { mutableStateOf("N/A") }

    var bufferedPosition by remember { mutableStateOf(0L) }
    var droppedFrames by remember { mutableStateOf(0L) }
    var networkSpeed by remember { mutableStateOf("N/A") }
    var bufferHealth by remember { mutableStateOf(0) }

    val serviceDroppedFrames = StreamingPlaybackService.getInstance()?.droppedFrames?.collectAsState()
    val serviceTotalFrames = StreamingPlaybackService.getInstance()?.totalFrames?.collectAsState()

    // Update stats every second
    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                bufferedPosition = p.bufferedPosition
                droppedFrames = serviceDroppedFrames?.value ?: 0L

                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                bufferHealth = if (buffered > currentPos) {
                    ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                val tracks = p.currentTracks
                var totalBitrate = 0

                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        videoResolution = "${format.width} x ${format.height}"
                        videoFrameRate = if (format.frameRate > 0) "${format.frameRate.toInt()} fps" else "N/A"
                        videoBitrate = formatBitrate(format.bitrate)
                        if (format.bitrate > 0) totalBitrate += format.bitrate
                    }
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        audioCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        audioSampleRate = if (format.sampleRate > 0) "${format.sampleRate / 1000}kHz" else "N/A"
                        audioChannels = if (format.channelCount > 0) {
                            when (format.channelCount) {
                                1 -> "Mono"
                                2 -> "Stereo"
                                6 -> "5.1"
                                8 -> "7.1"
                                else -> "${format.channelCount}ch"
                            }
                        } else "N/A"
                        audioBitrate = formatBitrate(format.bitrate)
                        if (format.bitrate > 0) totalBitrate += format.bitrate
                    }
                }

                networkSpeed = if (totalBitrate > 0) formatBitrate(totalBitrate) else "N/A"
            }

            delay(CinemaAnimation.statsUpdateMs)
        }
    }

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
    val totalFrames = serviceTotalFrames?.value ?: 0L
    val dropRate = if (totalFrames > 0) (droppedFrames.toFloat() / totalFrames * 100) else 0f
    val dropColor = when {
        dropRate < 0.5f -> CinemaSuccess
        dropRate < 2.0f -> CinemaWarning
        else -> CinemaError
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClose()
            }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .widthIn(max = MobileDimensions.statsOverlayMaxWidth),
            color = Color.Black.copy(alpha = CinemaAlpha.overlayHeavy),
            shape = RoundedCornerShape(CinemaCornerRadius.medium)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stats for Nerds",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(MobileDimensions.iconLarge)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = CinemaAlpha.textMedium),
                            modifier = Modifier.size(MobileDimensions.iconSmall)
                        )
                    }
                }

                // Video
                SectionHeader("VIDEO")
                StatRow("Codec", videoCodec)
                StatRow("Resolution", videoResolution)
                StatRow("Frame Rate", videoFrameRate)
                StatRow("Bitrate", videoBitrate)

                // Audio
                SectionHeader("AUDIO")
                StatRow("Codec", audioCodec)
                StatRow("Sample Rate", audioSampleRate)
                StatRow("Channels", audioChannels)
                StatRow("Bitrate", audioBitrate)

                // Network
                SectionHeader("NETWORK")
                StatRow("Speed", networkSpeed)
                StatRow("Buffer", "${bufferHealth}s")
                StatRow("Buffered", formatTime(bufferedPosition))

                // Playback
                SectionHeader("PLAYBACK")
                StatRow("Position", formatTime(position))
                StatRow("Duration", if (duration > 0) formatTime(duration) else "Live")

                // Performance
                SectionHeader("PERFORMANCE")
                StatRowColored("Dropped", "$droppedFrames / $totalFrames", dropColor)
                if (totalFrames > 0) {
                    StatRowColored("Drop Rate", String.format("%.2f%%", dropRate), dropColor)
                }

                // Stream
                SectionHeader("STREAM")
                StatRow("Type", if (metadata.isLive) "Live" else "VOD")
                StatRow("URL", metadata.streamUrl.substringAfterLast("/").take(25))

                // Device
                SectionHeader("DEVICE")
                StatRow("Model", android.os.Build.MODEL)
                StatRow("API", "${android.os.Build.VERSION.SDK_INT}")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatRowColored(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
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
