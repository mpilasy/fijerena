@file:OptIn(ExperimentalMaterial3Api::class)

package org.njarasoa.fijerena.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Mobile player screen with touch controls, audio/subtitle/quality selectors,
 * favorites, playback resume, and Stats for Nerds overlay.
 * Refactored to use StreamLoaderViewModel.
 */
@Composable
fun MobilePlayerScreen(
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
    viewModel: PlaybackViewModel = viewModel(),
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
    val context = LocalContext.current
    val activity = context as? Activity
    val appSettings = remember { AppSettings(context.applicationContext) }

    // Unlock orientation for video playback, restore portrait on exit
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val streamState by loaderViewModel.state.collectAsState()
    val currentStreamState by rememberUpdatedState(streamState)

    // UI State
    var showChannelToast by remember { mutableStateOf(false) }
    var showCategoryOverlay by remember { mutableStateOf(false) }
    var showLastWatchedOverlay by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(false) }
    var hasStartedPlaying by remember { mutableStateOf(false) }

    // Auto-show stats on repeated buffer exhaustion (dev mode only)
    LaunchedEffect(appSettings.isDevMode) {
        if (!appSettings.isDevMode) return@LaunchedEffect
        val rebufferTimestamps = mutableListOf<Long>()
        var lastSeenCount = 0
        while (true) {
            val currentCount = StreamingPlaybackService.getInstance()?.rebufferCount?.value ?: 0
            if (currentCount > lastSeenCount) {
                val now = System.currentTimeMillis()
                repeat(currentCount - lastSeenCount) {
                    rebufferTimestamps.add(now)
                }
                lastSeenCount = currentCount
                rebufferTimestamps.removeAll { now - it > 30_000L }
                if (rebufferTimestamps.size >= 3 && !showStats) {
                    showStats = true
                }
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Live position polling for smooth VOD timer updates
    var livePosition by remember { mutableLongStateOf(0L) }
    var liveDuration by remember { mutableLongStateOf(0L) }

    val playbackState = viewModel.playbackState.collectAsState().value

    LaunchedEffect(playbackState) {
        if (playbackState is PlaybackState.Error) {
            android.util.Log.e("MobilePlayerScreen", "Playback Error: ${playbackState.message}")
        }
        if (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused) {
            while (true) {
                StreamingPlaybackService.getInstance()?.getPlayer()?.let { player ->
                    livePosition = player.currentPosition
                    liveDuration = player.duration.coerceAtLeast(0L)
                }
                delay(500L)
            }
        }
    }

    // Auto-hide channel toast
    LaunchedEffect(showChannelToast) {
        if (showChannelToast) {
            delay(CinemaAnimation.toastDismissMs)
            showChannelToast = false
        }
    }

    // Selector dialogs
    var showAudioTrackSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showTopOfHourClock by remember { mutableStateOf(false) }
    var clockTick by remember { mutableLongStateOf(0L) }

    val currentMetadata = viewModel.currentMetadata.collectAsState().value

    // Track when video first starts playing so we stop showing the center spinner
    LaunchedEffect(playbackState) {
        if (playbackState is PlaybackState.Playing) {
            hasStartedPlaying = true
        }
    }

    // Save playback position periodically for VOD content
    LaunchedEffect(Unit) {
        if (contentType != "LIVE_TV") {
            while (true) {
                delay(5000L)
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
                    loaderViewModel.recordHistory(pos, dur)
                }
            }
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls && !showStats) {
            delay(CinemaAnimation.controlsAutoHideMobileMs)
            showControls = false
        }
    }

    // Top-of-hour clock: show 30s before the hour, hide 90s after
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val totalSecondsIntoHour = now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
            val showAtSecond = 59 * 60 + 30
            showTopOfHourClock = totalSecondsIntoHour >= showAtSecond || totalSecondsIntoHour < 90
            clockTick = System.currentTimeMillis()
            delay(1000L)
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

    // Start playback when URL is ready or channel changes
    val currentStreamId = (streamState as? StreamLoaderViewModel.StreamState.Success)?.streamId
    LaunchedEffect(currentStreamId) {
        val state = streamState
        if (state is StreamLoaderViewModel.StreamState.Success) {
            // Show toast if channel changed (implicit logic: if ID changed)
            showChannelToast = true

            val metadata = PlayerMetadata(
                title = state.streamName,
                channelName = appSettings.providerName,
                streamUrl = state.streamUrl,
                isLive = state.isLive,
                headers = state.streamHeaders
            )
            viewModel.playStream(metadata, state.resumePosition)
        }
    }

    when (val state = streamState) {
        is StreamLoaderViewModel.StreamState.Loading -> {
            LoadingScreen()
        }
        is StreamLoaderViewModel.StreamState.Error -> {
            ErrorScreen(message = state.message, onBack = onBack)
        }
        is StreamLoaderViewModel.StreamState.Success -> {
            val isLiveContent = state.isLive
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(showStats, isLiveContent, playbackState) {
                        detectTapGestures(
                            onTap = {
                                if (!showStats) showControls = !showControls
                            },
                            onDoubleTap = {
                                if (!isLiveContent) {
                                    when (playbackState) {
                                        is PlaybackState.Playing -> viewModel.pause()
                                        is PlaybackState.Paused -> viewModel.resume()
                                        else -> {}
                                    }
                                }
                            }
                        )
                    }
                    .then(
                        if (isLiveContent) {
                            Modifier.pointerInput(state.categoryStreams, showCategoryOverlay, showLastWatchedOverlay) {
                                var verticalAccumulator = 0f
                                var horizontalAccumulator = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        verticalAccumulator = 0f
                                        horizontalAccumulator = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        verticalAccumulator += dragAmount.y
                                        horizontalAccumulator += dragAmount.x
                                        // Vertical: channel switching
                                        if (kotlin.math.abs(verticalAccumulator) > 100f) {
                                            if (verticalAccumulator < 0) loaderViewModel.nextChannel()
                                            else loaderViewModel.prevChannel()
                                            verticalAccumulator = 0f
                                        }
                                        // Horizontal: overlay panels
                                        if (kotlin.math.abs(horizontalAccumulator) > 80f) {
                                            when {
                                                horizontalAccumulator > 0 && !showLastWatchedOverlay ->
                                                    showCategoryOverlay = true
                                                horizontalAccumulator < 0 && !showCategoryOverlay ->
                                                    showLastWatchedOverlay = true
                                                horizontalAccumulator > 0 && showLastWatchedOverlay ->
                                                    showLastWatchedOverlay = false
                                                horizontalAccumulator < 0 && showCategoryOverlay ->
                                                    showCategoryOverlay = false
                                            }
                                            horizontalAccumulator = 0f
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    )
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
                        val currentState = currentStreamState
                        if (currentState is StreamLoaderViewModel.StreamState.Success && !currentState.isLive) {
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
                                loaderViewModel.recordHistory(pos, dur)
                            }
                        }
                        // Stop playback when leaving the player screen
                        viewModel.stop()
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
                            if (!hasStartedPlaying) {
                                CircularProgressIndicator(color = Color.White)
                            }
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
                        viewModel = viewModel,
                        isLive = isLiveContent,
                        isDeveloperMode = appSettings.isDevMode,
                        isFavorite = state.isFavorite,
                        livePosition = livePosition,
                        liveDuration = liveDuration,
                        clockTick = clockTick,
                        currentEpgProgram = state.currentEpgProgram,
                        nextEpgProgram = state.nextEpgProgram,
                        onPlayPause = {
                            if (playbackState is PlaybackState.Paused) {
                                viewModel.resume()
                            } else {
                                viewModel.pause()
                            }
                        },
                        onFastForward = if (!isLiveContent) ({ viewModel.seekRelative(60_000L) }) else null,
                        onRewind = if (!isLiveContent) ({ viewModel.seekRelative(-30_000L) }) else null,
                        onBack = {
                            // Save position before stopping (stop sets state to Idle)
                            if (!isLiveContent) {
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
                                    loaderViewModel.recordHistory(pos, dur)
                                }
                            }
                            viewModel.stop()
                            onBack()
                        },
                        onStats = { showStats = true },
                        onAudioTrack = { showAudioTrackSelector = true },
                        onSubtitle = { showSubtitleSelector = true },
                        onQuality = { showQualitySelector = true },
                        onToggleFavorite = {
                             loaderViewModel.toggleFavorite()
                        }
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

                // Autonomous top-of-hour clock
                AnimatedVisibility(
                    visible = showTopOfHourClock && !showControls && !showStats,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    @Suppress("UNUSED_VARIABLE")
                    val tick = clockTick
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CinemaSpacing.md),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(Date()),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = CinemaAlpha.textDisabled)
                        )
                    }
                }

                // Channel switch toast (Live TV only)
                AnimatedVisibility(
                    visible = showChannelToast,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ChannelToast(
                        channelName = state.streamName,
                        currentEpgProgram = state.currentEpgProgram
                    )
                }
            }

            // Category streams panel — slides in from the left
            AnimatedVisibility(
                visible = showCategoryOverlay,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it }
            ) {
                MobileChannelListSheet(
                    title = "Category Channels",
                    streams = state.categoryStreams,
                    panelAlignment = Alignment.CenterStart,
                    onSelect = { item ->
                        showCategoryOverlay = false
                        loaderViewModel.loadStream(item)
                    },
                    onDismiss = { showCategoryOverlay = false }
                )
            }

            // Last watched panel — slides in from the right
            AnimatedVisibility(
                visible = showLastWatchedOverlay,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                MobileChannelListSheet(
                    title = "Last Watched",
                    streams = state.lastWatchedStreams.filter { it.id != state.streamId },
                    panelAlignment = Alignment.CenterEnd,
                    onSelect = { item ->
                        showLastWatchedOverlay = false
                        loaderViewModel.loadStream(item)
                    },
                    onDismiss = { showLastWatchedOverlay = false }
                )
            }

            // Selector dialogs (outside the clickable Box)
            if (showAudioTrackSelector) {
                AudioTrackSelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showAudioTrackSelector = false }
                )
            }

            if (showSubtitleSelector) {
                SubtitleSelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showSubtitleSelector = false }
                )
            }

            if (showQualitySelector) {
                QualitySelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showQualitySelector = false }
                )
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
    viewModel: PlaybackViewModel,
    isLive: Boolean,
    isDeveloperMode: Boolean,
    isFavorite: Boolean,
    livePosition: Long,
    liveDuration: Long,
    clockTick: Long = 0L,
    currentEpgProgram: EpgProgram? = null,
    nextEpgProgram: EpgProgram? = null,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onStats: () -> Unit,
    onAudioTrack: () -> Unit,
    onSubtitle: () -> Unit,
    onQuality: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFastForward: (() -> Unit)? = null,
    onRewind: (() -> Unit)? = null
) {
    val audioTrackCount = remember { viewModel.getAudioTracks().size }
    val subtitleTrackCount = remember { viewModel.getSubtitleTracks().size }
    val qualityCount = remember { viewModel.getVideoQualities().size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
    ) {
        // Top bar with title and clock
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(CinemaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CinemaSpacing.xs),
                maxLines = 1
            )
            // Clock
            @Suppress("UNUSED_VARIABLE")
            val tick = clockTick
            Text(
                text = org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(Date()),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        // Center row: Rewind | Play/Pause | FastForward (VOD only shows seek buttons)
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onRewind != null) {
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier.size(MobileDimensions.iconPlayContainer)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 30s",
                            tint = Color.White,
                            modifier = Modifier.size(MobileDimensions.iconLarge)
                        )
                        Text("-30s", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(MobileDimensions.iconPlayContainer)
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
            if (onFastForward != null) {
                IconButton(
                    onClick = onFastForward,
                    modifier = Modifier.size(MobileDimensions.iconPlayContainer)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Fast Forward 1min",
                            tint = Color.White,
                            modifier = Modifier.size(MobileDimensions.iconLarge)
                        )
                        Text("+1m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // Bottom section: progress + controls (scrollable for landscape)
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm)
        ) {
            // VOD progress bar and time info
            if (!isLive) {
                val position = livePosition
                val duration = liveDuration

                if (duration > 0) {
                    // Seek position state for dragging
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPosition by remember { mutableStateOf(0f) }

                    Slider(
                        value = if (isSeeking) seekPosition else position.toFloat() / duration.toFloat(),
                        onValueChange = { newValue ->
                            isSeeking = true
                            seekPosition = newValue
                        },
                        onValueChangeFinished = {
                            val newPositionMs = (seekPosition * duration).toLong()
                            viewModel.seekTo(newPositionMs)
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                        )
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

                    // Remaining time and estimated end time
                    val remainingTime = duration - position
                    val estimatedEndTimeMillis = System.currentTimeMillis() + remainingTime
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remaining: ${formatTime(remainingTime)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ends at ${org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(Date(estimatedEndTimeMillis))}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // Live indicator with EPG info
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    if (currentEpgProgram != null) {
                        val nowStart = formatEpochTime(currentEpgProgram.startTime)
                        val nowEnd = formatEpochTime(currentEpgProgram.endTime)
                        Text(
                            text = "Now: ${currentEpgProgram.title}  ($nowStart – $nowEnd)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = CinemaAlpha.textMedium),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        // Programme progress bar
                        val nowEpoch = System.currentTimeMillis() / 1000
                        val epgProgress = if (currentEpgProgram.duration > 0) {
                            ((nowEpoch - currentEpgProgram.startTime).toFloat() / currentEpgProgram.duration.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { epgProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                        )
                        if (nextEpgProgram != null) {
                            Text(
                                text = "Up Next: ${nextEpgProgram.title}  (${formatEpochTime(nextEpgProgram.startTime)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = CinemaAlpha.tint),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Control buttons row (horizontally scrollable icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio track selector (only if multiple tracks)
                if (audioTrackCount > 1) {
                    IconButton(onClick = onAudioTrack) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Audio", tint = Color.White)
                    }
                }

                // Subtitle selector (only if subtitles available)
                if (subtitleTrackCount > 0) {
                    IconButton(onClick = onSubtitle) {
                        Icon(Icons.Filled.Subtitles, "Subtitles", tint = Color.White)
                    }
                }

                // Quality selector (only if multiple qualities)
                if (qualityCount > 1) {
                    IconButton(onClick = onQuality) {
                        Icon(Icons.Filled.Tune, "Quality", tint = Color.White)
                    }
                }

                // Favorite toggle
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Stats for nerds (always visible)
                IconButton(onClick = onStats) {
                    Icon(Icons.Filled.BarChart, "Stats", tint = Color.White)
                }
            }
        }
        }
    }
}

// --- Channel List Bottom Sheet ---

@Composable
private fun MobileChannelListSheet(
    title: String,
    streams: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    panelAlignment: Alignment = Alignment.CenterStart
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        GlassPanel(
            modifier = Modifier
                .align(panelAlignment)
                .fillMaxWidth(0.72f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks */ },
            backgroundAlpha = 0.5f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CinemaSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                if (streams.isEmpty()) {
                    Text(
                        text = "No channels available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = CinemaAlpha.textMedium)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)
                    ) {
                        items(streams) { stream ->
                            Surface(
                                onClick = { onSelect(stream) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CinemaAlpha.glass),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = stream.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(
                                        horizontal = CinemaSpacing.md,
                                        vertical = CinemaSpacing.sm
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Audio Track Selector Dialog ---

@Composable
private fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val audioTracks = remember { viewModel.getAudioTracks() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Audio Track") },
        text = {
            if (audioTracks.isEmpty()) {
                Text("No audio tracks available")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    audioTracks.forEachIndexed { _, track ->
                        Surface(
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (track.isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (track.isSelected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "${track.channelCount}ch - ${track.sampleRate / 1000}kHz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- Subtitle Selector Dialog ---

@Composable
private fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    val hasActiveSubtitle = subtitleTracks.any { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Subtitles") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Off" option
                Surface(
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!hasActiveSubtitle)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (!hasActiveSubtitle) FontWeight.Bold else FontWeight.Normal
                        )
                        if (!hasActiveSubtitle) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                subtitleTracks.forEachIndexed { _, track ->
                    Surface(
                        onClick = {
                            viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (track.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (track.isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = track.mimeType.substringAfterLast("/").uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- Quality Selector Dialog ---

@Composable
private fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    val hasManualSelection = videoQualities.any { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Quality") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Auto" option
                Surface(
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!hasManualSelection)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto (Adaptive)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!hasManualSelection) FontWeight.Bold else FontWeight.Normal
                            )
                            if (!hasManualSelection) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Adjust quality based on network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                videoQualities.forEachIndexed { _, quality ->
                    Surface(
                        onClick = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (quality.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (quality.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (quality.isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = "${quality.width}x${quality.height} - ${quality.frameRate.toInt()}fps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- Stats Overlay ---

@Composable
private fun MobileStatsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    onClose: () -> Unit
) {
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

    // Collect stream stats from service
    val serviceRetryCount = StreamingPlaybackService.getInstance()?.streamRetryCount?.collectAsState()
    val serviceStartTimeMs = StreamingPlaybackService.getInstance()?.streamStartTimeMs?.collectAsState()
    val serviceRebufferCount = StreamingPlaybackService.getInstance()?.rebufferCount?.collectAsState()
    val serviceRebufferTimeMs = StreamingPlaybackService.getInstance()?.totalRebufferTimeMs?.collectAsState()
    val serviceBandwidth = StreamingPlaybackService.getInstance()?.bandwidthEstimate?.collectAsState()
    val serviceQualitySwitches = StreamingPlaybackService.getInstance()?.qualitySwitchCount?.collectAsState()
    var streamElapsed by remember { mutableStateOf("0:00") }

    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                bufferedPosition = p.bufferedPosition
                droppedFrames = serviceDroppedFrames?.value ?: 0L

                val currentPos = p.currentPosition
                val buffered = p.bufferedPosition
                bufferHealth = if (buffered > currentPos) {
                    ((buffered - currentPos) / 1000).toInt().coerceIn(0, 100)
                } else 0

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
                                1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
                                else -> "${format.channelCount}ch"
                            }
                        } else "N/A"
                        audioBitrate = formatBitrate(format.bitrate)
                        if (format.bitrate > 0) totalBitrate += format.bitrate
                    }
                }
                networkSpeed = if (totalBitrate > 0) formatBitrate(totalBitrate) else "N/A"
            }

            // Update stream elapsed time
            val startTime = serviceStartTimeMs?.value ?: 0L
            if (startTime > 0L) {
                val elapsedSec = (android.os.SystemClock.elapsedRealtime() - startTime) / 1000
                val hours = elapsedSec / 3600
                val minutes = (elapsedSec % 3600) / 60
                val seconds = elapsedSec % 60
                streamElapsed = if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%d:%02d", minutes, seconds)
                }
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
    ) {
        GlassPanel(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CinemaSpacing.md)
                .widthIn(max = MobileDimensions.statsOverlayMaxWidth)
        ) {
            Column(
                modifier = Modifier
                    .padding(CinemaSpacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)
            ) {
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

                SectionHeader("VIDEO")
                StatRow("Codec", videoCodec)
                StatRow("Resolution", videoResolution)
                StatRow("Frame Rate", videoFrameRate)
                StatRow("Bitrate", videoBitrate)

                SectionHeader("AUDIO")
                StatRow("Codec", audioCodec)
                StatRow("Sample Rate", audioSampleRate)
                StatRow("Channels", audioChannels)
                StatRow("Bitrate", audioBitrate)

                SectionHeader("NETWORK")
                StatRow("Speed", networkSpeed)
                val bwEstimate = serviceBandwidth?.value ?: 0L
                StatRow("Bandwidth", if (bwEstimate > 0) formatBitrate(bwEstimate.toInt()) else "N/A")
                StatRow("Buffer", "${bufferHealth}s")
                StatRow("Buffered", formatTime(bufferedPosition))
                val rebuffers = serviceRebufferCount?.value ?: 0
                val rebufferTimeMs = serviceRebufferTimeMs?.value ?: 0L
                val rebufferColor = when {
                    rebuffers == 0 -> CinemaSuccess
                    rebuffers <= 3 -> CinemaWarning
                    else -> CinemaError
                }
                StatRowColored("Rebuffers", "$rebuffers", rebufferColor)
                if (rebufferTimeMs > 0) {
                    StatRowColored("Rebuf Time", "${rebufferTimeMs / 1000}.${(rebufferTimeMs % 1000) / 100}s", rebufferColor)
                }
                val qSwitches = serviceQualitySwitches?.value ?: 0
                if (qSwitches > 0) {
                    StatRow("ABR Switches", "$qSwitches")
                }

                SectionHeader("PLAYBACK")
                StatRow("Position", formatTime(position))
                StatRow("Duration", if (duration > 0) formatTime(duration) else "Live")

                SectionHeader("PERFORMANCE")
                StatRowColored("Dropped", "$droppedFrames / $totalFrames", dropColor)
                if (totalFrames > 0) {
                    StatRowColored("Drop Rate", String.format("%.2f%%", dropRate), dropColor)
                }

                SectionHeader("STREAM")
                StatRow("Type", if (metadata.isLive) "Live" else "VOD")
                StatRow("Retries", "${serviceRetryCount?.value ?: 0}")
                StatRow("Uptime", streamElapsed)
                StatRow("URL", metadata.streamUrl.substringAfterLast("/").take(25))

                SectionHeader("DEVICE")
                StatRow("Model", android.os.Build.MODEL)
                StatRow("API", "${android.os.Build.VERSION.SDK_INT}")
            }
        }
    }
}

// --- Shared Composables ---

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

private fun formatEpochTime(epochSeconds: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}

@Composable
private fun ChannelToast(
    channelName: String,
    currentEpgProgram: EpgProgram? = null
) {
    GlassPanel(
        modifier = Modifier.padding(top = CinemaSpacing.xl)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CinemaSpacing.lg,
                vertical = CinemaSpacing.sm
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = channelName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (currentEpgProgram != null) {
                Text(
                    text = currentEpgProgram.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = CinemaAlpha.textMedium),
                    textAlign = TextAlign.Center
                )
            }
        }
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
