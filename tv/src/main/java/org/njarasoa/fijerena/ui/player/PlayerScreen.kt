@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var showAudioTrackSelector by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    var channelSwitchNotification by remember { mutableStateOf<String?>(null) }

    // Auto-hide controls after 10 seconds
    LaunchedEffect(showControls) {
        if (showControls && !showStats) {
            delay(10.seconds)
            showControls = false
        }
    }

    // Auto-hide channel switch notification after 3 seconds
    LaunchedEffect(channelSwitchNotification) {
        channelSwitchNotification?.let {
            delay(3.seconds)
            channelSwitchNotification = null
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Show channel switch notification when metadata changes during playback
    var previousMetadataTitle by remember { mutableStateOf(currentMetadata.title) }
    LaunchedEffect(currentMetadata.title) {
        // Only show notification if we're already playing (not initial load)
        if (previousMetadataTitle.isNotEmpty() &&
            currentMetadata.title != previousMetadataTitle &&
            currentMetadata.title.isNotEmpty() &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)) {
            channelSwitchNotification = currentMetadata.title
        }
        previousMetadataTitle = currentMetadata.title
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                // Only make the player box focusable when stats are not visible
                if (!showStats) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                } else {
                    Modifier
                }
            )
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
                            // Only change channel if stats are not visible
                            if (!showStats) {
                                println("PlayerScreen: UP button pressed")
                                onPreviousChannel()
                                showControls = true
                                println("PlayerScreen: showControls set to true, metadata title=${currentMetadata.title}")
                                true
                            } else {
                                false // Let stats overlay handle it
                            }
                        }
                        Key.DirectionDown -> {
                            // Only change channel if stats are not visible
                            if (!showStats) {
                                println("PlayerScreen: DOWN button pressed")
                                onNextChannel()
                                showControls = true
                                println("PlayerScreen: showControls set to true, metadata title=${currentMetadata.title}")
                                true
                            } else {
                                false // Let stats overlay handle it
                            }
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            // Let stats overlay handle left/right when visible
                            if (showStats) {
                                false
                            } else {
                                false // Not used for anything else
                            }
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
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            StatsOverlay(
                playbackState = playbackState,
                metadata = currentMetadata,
                context = context,
                onHide = {
                    showStats = false
                    showControls = false
                }
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
                onAudioTrack = { showAudioTrackSelector = true },
                onBack = {
                    viewModel.stop()
                    onBack()
                }
            )
        }

        // Audio track selector dialog
        if (showAudioTrackSelector) {
            AudioTrackSelectorDialog(
                viewModel = viewModel,
                onDismiss = { showAudioTrackSelector = false }
            )
        }

        // Channel switch notification
        AnimatedVisibility(
            visible = channelSwitchNotification != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            enter = fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
        ) {
            channelSwitchNotification?.let { channelName ->
                ChannelSwitchNotification(channelName = channelName)
            }
        }
    }
}

@Composable
private fun ChannelSwitchNotification(channelName: String) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 32.dp),
        color = Color.Black.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel icon
            Text(
                text = "📺",
                style = MaterialTheme.typography.headlineSmall
            )

            // Channel name
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val audioTracks = remember { viewModel.getAudioTracks() }
    var selectedIndex by remember { mutableStateOf(audioTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)) }
    val focusRequesters = remember { List(audioTracks.size) { FocusRequester() } }
    val context = LocalContext.current

    // Request focus on selected item
    LaunchedEffect(Unit) {
        if (selectedIndex in focusRequesters.indices) {
            focusRequesters[selectedIndex].requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Center
    ) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .padding(32.dp),
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "🔊 Select Audio Track",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (audioTracks.isEmpty()) {
                    Text(
                        text = "No audio tracks available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(CenterHorizontally)
                    ) {
                        Text("Close")
                    }
                } else {
                    // Track list
                    audioTracks.forEachIndexed { index, track ->
                        val isSelected = index == selectedIndex
                        Button(
                            onClick = {
                                selectedIndex = index
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        selectedIndex = index
                                    }
                                }
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                ),
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else Color(0xFF2A2A2A),
                                contentColor = Color.White
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (track.isSelected) {
                                        Text(
                                            text = "✓ Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "${track.channelCount}ch • ${track.sampleRate / 1000}kHz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Close button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(CenterHorizontally)
                            .width(200.dp)
                    ) {
                        Text("Cancel")
                    }
                }

                // Hint text
                Text(
                    text = "Use D-pad to navigate • OK to select • BACK to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Handle back button
    DisposableEffect(Unit) {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onDismiss()
            }
        }
        val activity = context as? androidx.activity.ComponentActivity
        activity?.onBackPressedDispatcher?.addCallback(callback)

        onDispose {
            callback.remove()
        }
    }
}

enum class QuadrantPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

private fun getQuadrantAlignment(position: QuadrantPosition): Alignment {
    return when (position) {
        QuadrantPosition.TOP_LEFT -> Alignment.TopStart
        QuadrantPosition.TOP_RIGHT -> Alignment.TopEnd
        QuadrantPosition.BOTTOM_LEFT -> Alignment.BottomStart
        QuadrantPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
}

@Composable
private fun StatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    context: android.content.Context,
    onHide: () -> Unit = {}
) {
    val player = StreamingPlaybackService.getInstance()?.getPlayer()
    val configuration = LocalConfiguration.current

    var quadrantPosition by remember { mutableStateOf(QuadrantPosition.BOTTOM_RIGHT) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var lastClickTime by remember { mutableStateOf(0L) }

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
    var droppedFrames by remember { mutableStateOf(0L) }
    var networkSpeed by remember { mutableStateOf("N/A") }
    var bufferHealth by remember { mutableStateOf(0) }

    // Update stats periodically
    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                // Update buffered position
                bufferedPosition = p.bufferedPosition

                // Dropped frames tracking (would require custom analytics listener)
                droppedFrames = 0L // Placeholder for future implementation

                // Calculate buffer health (percentage of buffer vs target)
                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                bufferHealth = if (buffered > currentPos) {
                    ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                // Estimate network speed from bitrate
                val tracks = p.currentTracks
                var totalBitrate = 0

                // Get video track
                for (i in 0 until tracks.groups.size) {
                    val group = tracks.groups[i]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.length > 0) {
                        val format = group.getTrackFormat(0)
                        videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
                        videoResolution = "${format.width} × ${format.height}"
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

            delay(1.seconds)
        }
    }

    // Calculate overlay size (30% width × 50% height)
    val overlayWidth = (configuration.screenWidthDp * 0.30).dp
    val overlayHeight = (configuration.screenHeightDp * 0.50).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .width(overlayWidth)
                .height(overlayHeight)
                .align(getQuadrantAlignment(quadrantPosition))
                .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                .then(
                    if (isFocused) Modifier.border(
                        3.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    ) else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionCenter -> {
                                val currentTime = System.currentTimeMillis()
                                val timeDiff = currentTime - lastClickTime

                                if (timeDiff < 600) {
                                    // Double tap detected - hide overlay
                                    onHide()
                                }

                                lastClickTime = currentTime
                                true
                            }
                            Key.DirectionUp -> {
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.TOP_LEFT
                                    QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.TOP_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_LEFT -> QuadrantPosition.BOTTOM_LEFT
                                    QuadrantPosition.TOP_RIGHT -> QuadrantPosition.BOTTOM_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_RIGHT -> QuadrantPosition.TOP_LEFT
                                    QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.BOTTOM_LEFT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_LEFT -> QuadrantPosition.TOP_RIGHT
                                    QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.BOTTOM_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = "📊 Stats for Nerds",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.primary,
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

                // Two-column layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Video stats
                        SectionHeader("VIDEO")
                        CompactStatRow("Codec", videoCodec)
                        CompactStatRow("Res", videoResolution)
                        CompactStatRow("FPS", videoFrameRate)
                        CompactStatRow("Bitrate", videoBitrate)

                        // Audio stats
                        SectionHeader("AUDIO")
                        CompactStatRow("Codec", audioCodec)
                        CompactStatRow("Rate", audioSampleRate)
                        CompactStatRow("Ch", audioChannels)
                        CompactStatRow("Bitrate", audioBitrate)

                        // Network stats
                        SectionHeader("NETWORK")
                        CompactStatRow("Speed", networkSpeed)
                        CompactStatRow("Buffer", "${bufferHealth}s")
                        CompactStatRow("Buffered", formatTime(bufferedPosition))
                    }

                    // Right Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Playback stats
                        SectionHeader("PLAYBACK")
                        CompactStatRow("Pos", formatTime(position))
                        CompactStatRow("Dur", if (duration > 0) formatTime(duration) else "Live")
                        if (droppedFrames > 0) {
                            CompactStatRow("Dropped", "$droppedFrames frames")
                        }

                        // Stream info
                        SectionHeader("STREAM")
                        CompactStatRow("Type", if (metadata.isLive) "Live" else "VOD")
                        CompactStatRow("URL", metadata.streamUrl.substringAfterLast("/").take(20))

                        // Device info
                        SectionHeader("DEVICE")
                        CompactStatRow("Model", android.os.Build.MODEL.take(15))
                        CompactStatRow("API", "${android.os.Build.VERSION.SDK_INT}")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isFocused) {
                    Text(
                        text = "D-pad to move • Double-tap center to hide",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            }
        }
    }

    // Auto-request focus when overlay becomes visible
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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

@Composable
private fun CompactStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
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
    onAudioTrack: (() -> Unit)? = null,
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

                Button(onClick = { onAudioTrack?.invoke() }) {
                    Text("🔊 Audio")
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
    val context = LocalContext.current
    val appSettings = remember { org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext) }
    val isDevMode = appSettings.isDevMode

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier
                .padding(48.dp)
                .width(600.dp)
        ) {
            // Error icon/title
            Text(
                text = "⚠️ Playback Error",
                color = MaterialTheme.colorScheme.error,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User-friendly error message
            Text(
                text = error.message,
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Technical details in dev mode
            if (isDevMode && error.exception != null) {
                Spacer(modifier = Modifier.height(32.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Technical Details (Dev Mode):",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val exception = error.exception
                        val errorDetails = buildString {
                            append("Type: ${exception?.javaClass?.simpleName ?: "Unknown"}\n")
                            exception?.message?.let { msg ->
                                append("Message: $msg\n")
                            }
                            // Get stack trace preview (first 5 lines)
                            val stackTrace = exception?.stackTraceToString()
                                ?.lines()
                                ?.take(5)
                                ?.joinToString("\n") ?: "No stack trace available"
                            append("\nStack Trace:\n$stackTrace")
                        }

                        Text(
                            text = errorDetails,
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Action buttons
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.width(120.dp).height(56.dp)
                ) {
                    Text("Retry", fontSize = 16.sp)
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.width(120.dp).height(56.dp)
                ) {
                    Text("Back", fontSize = 16.sp)
                }
            }
        }
    }
}
