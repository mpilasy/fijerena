@file:OptIn(ExperimentalMaterial3Api::class)

package org.njarasoa.fijerena.feature.player

import android.app.Activity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
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
private const val POST_FIRST_PLAY_BUFFERING_SPINNER_DELAY_MS = 3_000L

/**
 * Nav-route wrapper: owns ViewModel creation (fresh [StreamLoaderViewModel] per back-stack entry,
 * Activity-scoped [PlaybackViewModel] shared with MainActivity for PiP) and delegates to
 * [MobilePlayerContent]. Used by every nav destination that jumps straight to full-screen
 * playback (Movies, TV Shows/episodes, and any Live TV entry not going through the docked
 * mini-player). The Live TV docked screen (`MobileCategoryListScreen`) calls
 * [MobilePlayerContent] directly with its own dock-owned ViewModel pair instead, so promoting to
 * full-screen never creates a second engine connection.
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
    val activity = LocalContext.current as? Activity

    // Use activity-scoped ViewModel so it's shared with MainActivity for PiP updates
    val activityScopedViewModel: PlaybackViewModel =
        viewModel(
            viewModelStoreOwner = (activity as? ViewModelStoreOwner) ?: LocalLifecycleOwner.current as ViewModelStoreOwner
        )

    // This is the standalone full-screen route (Movies, TV Shows, or Live TV reached without
    // the docked mini-player) — unlike the dock's promoted view, actually leaving this screen
    // means the watch session is over, so finalize + stop here. Mirrors TV's
    // TvPlayerScreen.kt, which owns this same responsibility instead of PlayerScreen.kt.
    DisposableEffect(Unit) {
        onDispose {
            if (!activityScopedViewModel.isInPictureInPictureMode.value) {
                finalizeSession(activityScopedViewModel.playbackState.value, loaderViewModel)
                activityScopedViewModel.stop()
            }
        }
    }

    MobilePlayerContent(
        viewModel = activityScopedViewModel,
        loaderViewModel = loaderViewModel,
        contentType = contentType,
        onBack = {
            finalizeSession(activityScopedViewModel.playbackState.value, loaderViewModel)
            activityScopedViewModel.stop()
            onBack()
        },
    )
}

/**
 * The actual full-screen player UI (touch controls, gestures, overlays, PiP wiring). Takes its
 * [PlaybackViewModel]/[StreamLoaderViewModel] as params rather than owning creation, so callers
 * can share a single engine connection across a preview/dock and full-screen — mirrors TV's
 * `PlayerScreen`/`TvPlayerScreen` split (`tv/.../ui/player/PlayerScreen.kt`).
 */
@Composable
fun MobilePlayerContent(
    viewModel: PlaybackViewModel,
    loaderViewModel: StreamLoaderViewModel,
    contentType: String,
    onBack: () -> Unit,
    // When provided (by MobileCategoryListScreen, as a movableContentOf node), rendered instead
    // of a fresh EmbeddedPlayerSurface — keeps the same underlying Android View/Surface alive
    // across the dock<->full-screen promotion instead of swapping to a new one. Null for the
    // standalone (MobilePlayerScreen) route, which has no dock to persist a surface from.
    videoSurface: (@Composable () -> Unit)? = null,
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
                    Lifecycle.Event.ON_PAUSE -> {
                        // Pass current PiP state from activity as a safeguard
                        val inPip = activity?.isInPictureInPictureMode ?: false
                        viewModel.onFocusLost(inPip)
                    }
                    Lifecycle.Event.ON_RESUME -> viewModel.onFocusRegained()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Force landscape while in the full-screen player, even though the rest of the app is
    // portrait-locked and even if system auto-rotate is off. SENSOR_LANDSCAPE (not plain
    // LANDSCAPE) still follows the sensor between landscape-left/landscape-right, it just never
    // flips to portrait.
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val streamState by loaderViewModel.state.collectAsStateWithLifecycle()

    // UI State
    var showChannelToast by remember { mutableStateOf(false) }
    var showCategoryOverlay by remember { mutableStateOf(false) }
    var showLastWatchedOverlay by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(false) }
    var hasStartedPlaying by remember { mutableStateOf(false) }
    var showRecoverySpinner by remember { mutableStateOf(false) }

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentMetadata by viewModel.currentMetadata.collectAsStateWithLifecycle()
    val isInPipMode by viewModel.isInPictureInPictureMode.collectAsStateWithLifecycle()

    // Enable/disable PiP auto-enter
    LaunchedEffect(playbackState::class) {
        val ps = playbackState
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val isPlaying = ps is PlaybackState.Playing || ps is PlaybackState.Buffering
            activity?.setPictureInPictureParams(
                android.app.PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(isPlaying)
                    .build()
            )
        }
    }

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
                        context.getString(org.njarasoa.fijerena.core.ui.R.string.buffering_excessive_toast),
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

        if (currentPs is PlaybackState.Buffering) {
            delay(POST_FIRST_PLAY_BUFFERING_SPINNER_DELAY_MS)
            showRecoverySpinner = true
        } else {
            showRecoverySpinner = false
        }
    }

    // Set up auto-save listener for playback position and track settings
    LaunchedEffect(Unit) {
        StreamingPlaybackService.awaitInstance().setPositionSaveListener { position, duration, isPaused, audioIndex, subtitleIndex ->
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
        StreamingPlaybackService.awaitInstance().setContentType(playerContentType)
    }

    // Start playback when URL is ready or channel changes
    val currentStreamId = (streamState as? StreamLoaderViewModel.StreamState.Success)?.streamId
    LaunchedEffect(currentStreamId) {
        val state = streamState
        if (state is StreamLoaderViewModel.StreamState.Success) {
            // Promoting an already-playing docked preview to full screen re-mounts this
            // composable (and thus re-runs this effect) even though nothing actually changed —
            // skip re-issuing playStream() so promoting doesn't restart the stream from scratch.
            val alreadyPlayingThis =
                viewModel.currentMetadata.value.streamUrl == state.streamUrl &&
                    viewModel.playbackState.value !is PlaybackState.Idle &&
                    viewModel.playbackState.value !is PlaybackState.Error
            if (!alreadyPlayingThis) {
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
                    // playbackState is a plain StateFlow, not Compose snapshot state — wrapping it
                    // in snapshotFlow{} never registers an observable read, so it emits once and
                    // never again, leaving this stuck waiting forever instead of restoring tracks.
                    viewModel.playbackState
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
    }

    when (val state = streamState) {
        is StreamLoaderViewModel.StreamState.Loading -> {
            LoadingScreen()
        }
        is StreamLoaderViewModel.StreamState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { loaderViewModel.retryLastLoad() },
                onBack = onBack
            )
        }
        is StreamLoaderViewModel.StreamState.Success -> {
            val isLiveContent = state.isLive
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(org.njarasoa.fijerena.core.ui.theme.CinemaBackground)
                        .then(
                            if (isInPipMode) Modifier else
                            Modifier.pointerInput(showStats, isLiveContent, playbackState::class) {
                                detectTapGestures(
                                    onTap = {
                                        if (!showStats) showControls = !showControls
                                    },
                                    onDoubleTap = {
                                        if (!showStats && !isLiveContent) {
                                            when (viewModel.playbackState.value) {
                                                is PlaybackState.Playing -> viewModel.pause()
                                                is PlaybackState.Paused -> viewModel.resume()
                                                else -> {}
                                            }
                                        }
                                    },
                                )
                            }
                        ).then(
                            if (isInPipMode || !isLiveContent) Modifier else
                            Modifier.pointerInput(state.categoryStreams, showStats) {
                                var verticalAccumulator = 0f
                                var horizontalAccumulator = 0f
                                var hasFiredVerticalThisGesture = false
                                var hasFiredHorizontalThisGesture = false
                                detectDragGestures(
                                    onDragStart = {
                                        verticalAccumulator = 0f
                                        horizontalAccumulator = 0f
                                        hasFiredVerticalThisGesture = false
                                        hasFiredHorizontalThisGesture = false
                                    },
                                    onDragEnd = {
                                        hasFiredVerticalThisGesture = false
                                        hasFiredHorizontalThisGesture = false
                                    },
                                    onDragCancel = {
                                        hasFiredVerticalThisGesture = false
                                        hasFiredHorizontalThisGesture = false
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (showStats) return@detectDragGestures
                                        change.consume()
                                        verticalAccumulator += dragAmount.y
                                        horizontalAccumulator += dragAmount.x
                                        // Vertical: channel switching
                                        if (!hasFiredVerticalThisGesture && kotlin.math.abs(verticalAccumulator) > 100f) {
                                            hasFiredVerticalThisGesture = true
                                            if (verticalAccumulator < 0) {
                                                loaderViewModel.nextChannel()
                                            } else {
                                                loaderViewModel.prevChannel()
                                            }
                                            verticalAccumulator = 0f
                                        }
                                        // Horizontal: overlay panels
                                        if (!hasFiredHorizontalThisGesture && kotlin.math.abs(horizontalAccumulator) > 80f) {
                                            hasFiredHorizontalThisGesture = true
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
                        ),
            ) {
                // Video surface — shared implementation with TV's full-screen/preview surfaces
                // (core/ui/.../EmbeddedPlayerSurface.kt), bound to the same StreamingPlaybackService.
                if (videoSurface != null) {
                    videoSurface()
                } else {
                    EmbeddedPlayerSurface(modifier = Modifier.fillMaxSize())
                }

                // Reset PiP auto-enter when leaving the full-screen player. Finalizing the
                // session and stopping playback is NOT done here — this composable is shared
                // between the standalone full-screen route (which does own that) and the Live
                // TV dock's promoted view (where "leaving" just means shrinking back to the
                // dock, and the stream must keep playing). See MobilePlayerScreen's own
                // DisposableEffect for the standalone case.
                DisposableEffect(Unit) {
                    onDispose {
                        if (viewModel.isInPictureInPictureMode.value) return@onDispose

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            activity?.setPictureInPictureParams(
                                android.app.PictureInPictureParams.Builder()
                                    .setAutoEnterEnabled(false)
                                    .build()
                            )
                        }
                    }
                }

                // Loading/Error overlays
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (currentPs) {
                        PlaybackState.Buffering -> {
                            if (!hasStartedPlaying || showRecoverySpinner) {
                                org.njarasoa.fijerena.core.ui.components.MitohanaLoading(
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                                    color = org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
                                )
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
                    visible = !isInPipMode && showControls && !showStats && (currentPs is PlaybackState.Playing || currentPs is PlaybackState.Paused),
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
                        // Finalizing the session and stopping playback is the caller's call —
                        // see the DisposableEffect note above. Forward as-is.
                        onBack = onBack,
                        onStats = { showStats = true },
                        onAudioTrack = { showAudioTrackSelector = true },
                        onSubtitle = { showSubtitleSelector = true },
                        onQuality = { showQualitySelector = true },
                        onToggleFavorite = {
                            loaderViewModel.toggleFavorite()
                        },
                    )
                }

                // Stats overlay
                AnimatedVisibility(
                    visible = !isInPipMode && showStats,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    MobileStatsOverlay(
                        playbackState = currentPs,
                        metadata = currentMeta,
                        onClose = { showStats = false },
                    )
                }

            }

            // Category streams panel — slides in from the left
            AnimatedVisibility(
                visible = !isInPipMode && showCategoryOverlay,
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
                        finalizeSession(viewModel.playbackState.value, loaderViewModel)

                        showCategoryOverlay = false
                        loaderViewModel.loadStream(item)
                    },
                    onDismiss = { showCategoryOverlay = false },
                )
            }

            // Last watched panel — slides in from the right
            AnimatedVisibility(
                visible = !isInPipMode && showLastWatchedOverlay,
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
                        finalizeSession(viewModel.playbackState.value, loaderViewModel)

                        showLastWatchedOverlay = false
                        loaderViewModel.loadStream(item)
                    },
                    onDismiss = { showLastWatchedOverlay = false },
                )
            }

            // Channel/program info — also shown while the category or last-watched panel is
            // open (above them, since both cover most of the screen) so it's never hidden.
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !isInPipMode && (showChannelToast || showCategoryOverlay || showLastWatchedOverlay),
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

private fun finalizeSession(
    playbackState: PlaybackState,
    loaderViewModel: StreamLoaderViewModel
) {
    val pos =
        when (playbackState) {
            is PlaybackState.Playing -> playbackState.position
            is PlaybackState.Paused -> playbackState.position
            else -> 0L
        }
    val dur =
        when (playbackState) {
            is PlaybackState.Playing -> playbackState.duration
            is PlaybackState.Paused -> playbackState.duration
            else -> 0L
        }
    val service = StreamingPlaybackService.getInstance()
    val audioIdx = service?.getAudioTracks()?.indexOfFirst { it.isSelected }?.takeIf { it >= 0 }
    val subIdx = service?.getSubtitleTracks()?.indexOfFirst { it.isSelected }?.let { if (it >= 0) it else -1 }
    loaderViewModel.stopPlayback(pos, dur, audioIdx, subIdx)
}
