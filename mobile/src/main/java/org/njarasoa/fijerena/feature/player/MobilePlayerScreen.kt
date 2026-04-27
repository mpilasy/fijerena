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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.config.PlayerConfigFactory
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.components.ImmutableMediaList
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
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
    loaderViewModel: StreamLoaderViewModel =
        viewModel(
            factory =
                StreamLoaderViewModelFactory(
                    context = LocalContext.current,
                    initialStreamId = streamId,
                    initialStreamName = streamName,
                    categoryId = categoryId,
                    contentType = contentType,
                    episodeId = episodeId,
                    episodeExtension = episodeExtension,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    startFromBeginning = startFromBeginning,
                ),
        ),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val appSettings = remember { AppSettings(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe app focus/lifecycle to pause on background and stop after timeout
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> viewModel.onFocusLost()
                    Lifecycle.Event.ON_RESUME -> viewModel.onFocusRegained()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Unlock orientation for video playback, restore portrait on exit
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val streamState by loaderViewModel.state.collectAsStateWithLifecycle()
    val currentStreamState by rememberUpdatedState(streamState)

    // UI State
    var showChannelToast by remember { mutableStateOf(false) }
    var showCategoryOverlay by remember { mutableStateOf(false) }
    var showLastWatchedOverlay by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(false) }
    var hasStartedPlaying by remember { mutableStateOf(false) }

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentMetadata by viewModel.currentMetadata.collectAsStateWithLifecycle()
    val isNightModeEnabled by viewModel.nightModeEnabled.collectAsStateWithLifecycle()

    // Auto-show toast on repeated buffer exhaustion
    LaunchedEffect(appSettings.isDevMode, currentMetadata.streamUrl) {
        val exhaustionTimestamps = mutableListOf<Long>()
        var lastSeenCount = 0
        while (true) {
            val currentCount = StreamingPlaybackService.getInstance()?.exhaustionRebufferCount?.value ?: 0
            if (currentCount < lastSeenCount) {
                // Count was reset (likely channel switch)
                lastSeenCount = currentCount
                exhaustionTimestamps.clear()
            } else if (currentCount > lastSeenCount) {
                val now = System.currentTimeMillis()
                repeat(currentCount - lastSeenCount) {
                    exhaustionTimestamps.add(now)
                }
                lastSeenCount = currentCount
                exhaustionTimestamps.removeAll { now - it > 30_000L }
                if (exhaustionTimestamps.size >= 3) {
                    android.widget.Toast.makeText(
                        context,
                        "Excessive buffering is happening",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    exhaustionTimestamps.clear()
                }
            }
            delay(1000L)
        }
    }

    // Live position polling for smooth VOD timer updates
    var livePosition by remember { mutableLongStateOf(0L) }
    var liveDuration by remember { mutableLongStateOf(0L) }

    // Capture delegated properties into local variables for stable smart casting
    val currentPs = playbackState
    val currentMeta = currentMetadata

    LaunchedEffect(currentPs) {
        if (currentPs is PlaybackState.Error) {
            android.util.Log.e("MobilePlayerScreen", "Playback Error: ${currentPs.message}")
        }
        if (currentPs is PlaybackState.Playing || currentPs is PlaybackState.Paused) {
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

    // Track when video first starts playing so we stop showing the center spinner
    LaunchedEffect(currentPs) {
        if (currentPs is PlaybackState.Playing) {
            hasStartedPlaying = true
        }
    }

    // Set up auto-save listener for playback position and track settings
    LaunchedEffect(Unit) {
        StreamingPlaybackService.getInstance()?.setPositionSaveListener { position, duration, isPaused, audioIndex, subtitleIndex ->
            loaderViewModel.recordHistory(position, duration, isPaused, audioIndex, subtitleIndex)
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls && !showStats) {
            delay(CinemaAnimation.controlsAutoHideMobileMs)
            showControls = false
        }
    }

    // Configure player buffer profile based on content type
    LaunchedEffect(contentType) {
        val playerContentType =
            when (contentType) {
                ContentType.LIVE_TV -> PlayerConfigFactory.ContentType.LIVE_TV
                ContentType.MOVIES, ContentType.TV_SHOWS -> PlayerConfigFactory.ContentType.VOD
                else -> PlayerConfigFactory.ContentType.VOD
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

            val metadata =
                PlayerMetadata(
                    title = state.streamName,
                    channelName = appSettings.providerName,
                    description = state.description,
                    streamUrl = state.streamUrl,
                    isLive = state.isLive,
                    headers = state.streamHeaders,
                )
            viewModel.playStream(metadata, state.resumePosition)

            // Restore saved track settings when player is ready
            if (state.savedAudioTrackIndex != null || state.savedSubtitleTrackIndex != null) {
                snapshotFlow { viewModel.playbackState.value }
                    .filter { it is PlaybackState.Playing || it is PlaybackState.Paused }
                    .first() // Wait for first ready state

                val service = StreamingPlaybackService.getInstance()
                if (service != null) {
                    state.savedAudioTrackIndex?.let { audioIdx ->
                        service.selectAudioTrack(audioIdx)
                    }
                    state.savedSubtitleTrackIndex?.let { subIdx ->
                        service.selectSubtitleTrack(subIdx)
                    }
                }
            }
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(org.njarasoa.fijerena.core.ui.theme.CinemaBackground)
                        .pointerInput(showStats, isLiveContent, playbackState) {
                            detectTapGestures(
                                onTap = {
                                    if (!showStats) showControls = !showControls
                                },
                                onDoubleTap = {
                                    if (!showStats && !isLiveContent) {
                                        when (playbackState) {
                                            is PlaybackState.Playing -> viewModel.pause()
                                            is PlaybackState.Paused -> viewModel.resume()
                                            else -> {}
                                        }
                                    }
                                },
                            )
                        }.then(
                            if (isLiveContent) {
                                Modifier.pointerInput(state.categoryStreams, showCategoryOverlay, showLastWatchedOverlay, showStats) {
                                    var verticalAccumulator = 0f
                                    var horizontalAccumulator = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            verticalAccumulator = 0f
                                            horizontalAccumulator = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (showStats) return@detectDragGestures
                                            change.consume()
                                            verticalAccumulator += dragAmount.y
                                            horizontalAccumulator += dragAmount.x
                                            // Vertical: channel switching
                                            if (kotlin.math.abs(verticalAccumulator) > 100f) {
                                                if (verticalAccumulator < 0) {
                                                    loaderViewModel.nextChannel()
                                                } else {
                                                    loaderViewModel.prevChannel()
                                                }
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
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                // Video surface
                val playerView =
                    remember {
                        PlayerView(context).apply {
                            useController = false
                            keepScreenOn = true
                        }
                    }

                AndroidView(
                    factory = { playerView },
                    modifier = Modifier.fillMaxSize(),
                )

                // Bind player to view
                DisposableEffect(Unit) {
                    val service = StreamingPlaybackService.getInstance()
                    playerView.player = service?.getPlayer()

                    onDispose {
                        val currentState = currentStreamState
                        if (currentState is StreamLoaderViewModel.StreamState.Success) {
                            val ps = viewModel.playbackState.value
                            val pos =
                                when (ps) {
                                    is PlaybackState.Playing -> ps.position
                                    is PlaybackState.Paused -> ps.position
                                    else -> 0L
                                }
                            val dur =
                                when (ps) {
                                    is PlaybackState.Playing -> ps.duration
                                    is PlaybackState.Paused -> ps.duration
                                    else -> 0L
                                }
                            val service = StreamingPlaybackService.getInstance()
                            val audioIdx = service?.getAudioTracks()?.indexOfFirst { it.isSelected }?.takeIf { it >= 0 }
                            val subIdx = service?.getSubtitleTracks()?.indexOfFirst { it.isSelected }?.let { if (it >= 0) it else -1 }
                            loaderViewModel.stopPlayback(pos, dur, audioIdx, subIdx)
                        }
                        // Stop playback when leaving the player screen
                        viewModel.stop()
                        playerView.player = null
                    }
                }

                // Loading/Error overlays
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (currentPs) {
                        PlaybackState.Buffering -> {
                            if (!hasStartedPlaying) {
                                CircularProgressIndicator(color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary)
                            }
                        }
                        is PlaybackState.Error -> {
                            ErrorOverlay(
                                error = currentPs,
                                onRetry = { viewModel.playStream(currentMeta) },
                                onBack = onBack,
                            )
                        }
                        else -> { /* Playing or paused */ }
                    }
                }

                // Touch controls overlay
                AnimatedVisibility(
                    visible = showControls && !showStats && (currentPs is PlaybackState.Playing || currentPs is PlaybackState.Paused),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    MobileControlsOverlay(
                        playbackState = currentPs,
                        metadata = currentMeta,
                        viewModel = viewModel,
                        isLive = isLiveContent,
                        isDeveloperMode = appSettings.isDevMode,
                        isFavorite = state.isFavorite,
                        livePosition = livePosition,
                        liveDuration = liveDuration,
                        currentEpgProgram = state.currentEpgProgram,
                        nextEpgProgram = state.nextEpgProgram,
                        onPlayPause = {
                            if (currentPs is PlaybackState.Paused) {
                                viewModel.resume()
                            } else {
                                viewModel.pause()
                            }
                        },
                        onFastForward = if (!isLiveContent) ({ viewModel.seekRelative(300_000L) }) else null,
                        onRewind = if (!isLiveContent) ({ viewModel.seekRelative(-60_000L) }) else null,
                        onBack = {
                            // Finalize session before stopping
                            val ps = viewModel.playbackState.value
                            val pos =
                                when (ps) {
                                    is PlaybackState.Playing -> ps.position
                                    is PlaybackState.Paused -> ps.position
                                    else -> 0L
                                }
                            val dur =
                                when (ps) {
                                    is PlaybackState.Playing -> ps.duration
                                    is PlaybackState.Paused -> ps.duration
                                    else -> 0L
                                }
                            val service = StreamingPlaybackService.getInstance()
                            val audioIdx = service?.getAudioTracks()?.indexOfFirst { it.isSelected }?.takeIf { it >= 0 }
                            val subIdx = service?.getSubtitleTracks()?.indexOfFirst { it.isSelected }?.let { if (it >= 0) it else -1 }
                            loaderViewModel.stopPlayback(pos, dur, audioIdx, subIdx)

                            viewModel.stop()
                            onBack()
                        },
                        onStats = { showStats = true },
                        onAudioTrack = { showAudioTrackSelector = true },
                        onSubtitle = { showSubtitleSelector = true },
                        onQuality = { showQualitySelector = true },
                        onToggleFavorite = {
                            loaderViewModel.toggleFavorite()
                        },
                        onToggleNightMode = {
                            val newValue = !isNightModeEnabled
                            viewModel.setNightMode(newValue)
                        },
                        isNightModeEnabled = isNightModeEnabled,
                    )
                }

                // Stats overlay
                AnimatedVisibility(
                    visible = showStats,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    MobileStatsOverlay(
                        playbackState = currentPs,
                        metadata = currentMeta,
                        onClose = { showStats = false },
                    )
                }

                // Channel switch toast (Live TV only)
                AnimatedVisibility(
                    visible = showChannelToast,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ChannelToast(
                        channelName = state.streamName,
                        currentEpgProgram = state.currentEpgProgram,
                    )
                }
            }

            // Category streams panel — slides in from the left
            AnimatedVisibility(
                visible = showCategoryOverlay,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
            ) {
                MobileChannelListSheet(
                    title = "Category Channels",
                    streams = remember(state.categoryStreams) { ImmutableMediaList(state.categoryStreams) },
                    panelAlignment = Alignment.CenterStart,
                    currentStreamId = state.streamId,
                    onSelect = { item ->
                        // Finalize current session properly before starting new one
                        val ps = viewModel.playbackState.value
                        val pos =
                            when (ps) {
                                is PlaybackState.Playing -> ps.position
                                is PlaybackState.Paused -> ps.position
                                else -> 0L
                            }
                        val dur =
                            when (ps) {
                                is PlaybackState.Playing -> ps.duration
                                is PlaybackState.Paused -> ps.duration
                                else -> 0L
                            }
                        val service = StreamingPlaybackService.getInstance()
                        val audioIdx = service?.getAudioTracks()?.indexOfFirst { it.isSelected }?.takeIf { it >= 0 }
                        val subIdx = service?.getSubtitleTracks()?.indexOfFirst { it.isSelected }?.let { if (it >= 0) it else -1 }
                        loaderViewModel.stopPlayback(pos, dur, audioIdx, subIdx)

                        showCategoryOverlay = false
                        loaderViewModel.loadStream(item)
                    },
                    onDismiss = { showCategoryOverlay = false },
                )
            }

            // Last watched panel — slides in from the right
            AnimatedVisibility(
                visible = showLastWatchedOverlay,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                MobileChannelListSheet(
                    title = "Last Watched",
                    streams =
                        remember(state.lastWatchedStreams, state.streamId) {
                            ImmutableMediaList(state.lastWatchedStreams.filter { it.id != state.streamId })
                        },
                    panelAlignment = Alignment.CenterEnd,
                    onSelect = { item ->
                        // Finalize current session properly before starting new one
                        val ps = viewModel.playbackState.value
                        val pos =
                            when (ps) {
                                is PlaybackState.Playing -> ps.position
                                is PlaybackState.Paused -> ps.position
                                else -> 0L
                            }
                        val dur =
                            when (ps) {
                                is PlaybackState.Playing -> ps.duration
                                is PlaybackState.Paused -> ps.duration
                                else -> 0L
                            }
                        val service = StreamingPlaybackService.getInstance()
                        val audioIdx = service?.getAudioTracks()?.indexOfFirst { it.isSelected }?.takeIf { it >= 0 }
                        val subIdx = service?.getSubtitleTracks()?.indexOfFirst { it.isSelected }?.let { if (it >= 0) it else -1 }
                        loaderViewModel.stopPlayback(pos, dur, audioIdx, subIdx)

                        showLastWatchedOverlay = false
                        loaderViewModel.loadStream(item)
                    },
                    onDismiss = { showLastWatchedOverlay = false },
                )
            }

            // Selector dialogs (outside the clickable Box)
            if (showAudioTrackSelector) {
                AudioTrackSelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showAudioTrackSelector = false },
                )
            }

            if (showSubtitleSelector) {
                SubtitleSelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showSubtitleSelector = false },
                )
            }

            if (showQualitySelector) {
                QualitySelectorDialog(
                    viewModel = viewModel,
                    onDismiss = { showQualitySelector = false },
                )
            }
        }
    }
}
