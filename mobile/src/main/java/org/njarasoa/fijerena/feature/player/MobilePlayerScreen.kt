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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.StreamLoaderViewModelFactory
import org.njarasoa.fijerena.feature.player.components.AudioTrackSelectorDialog
import org.njarasoa.fijerena.feature.player.components.ChannelToast
import org.njarasoa.fijerena.feature.player.components.ErrorOverlay
import org.njarasoa.fijerena.feature.player.components.ErrorScreen
import org.njarasoa.fijerena.feature.player.components.LoadingScreen
import org.njarasoa.fijerena.feature.player.components.MobileChannelListSheet
import org.njarasoa.fijerena.feature.player.components.MobileControlsOverlay
import org.njarasoa.fijerena.feature.player.components.MobileStatsOverlay
import org.njarasoa.fijerena.feature.player.components.QualitySelectorDialog
import org.njarasoa.fijerena.feature.player.components.SubtitleSelectorDialog
import java.util.Calendar
import java.util.Date

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
                    MobileControlsOverlay(
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
