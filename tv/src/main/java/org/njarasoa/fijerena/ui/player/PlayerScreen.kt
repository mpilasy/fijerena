@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.player.components.BufferingContent
import org.njarasoa.fijerena.ui.player.components.EndedContent
import org.njarasoa.fijerena.ui.player.components.ErrorContent
import org.njarasoa.fijerena.ui.player.components.dialogs.AudioTrackSelectorDialog
import org.njarasoa.fijerena.ui.player.components.dialogs.ChapterSelectorDialog
import org.njarasoa.fijerena.ui.player.components.dialogs.QualitySelectorDialog
import org.njarasoa.fijerena.ui.player.components.dialogs.SubtitleSelectorDialog
import org.njarasoa.fijerena.ui.player.components.overlays.ChannelListOverlay
import org.njarasoa.fijerena.ui.player.components.overlays.ControlHintsOverlay
import org.njarasoa.fijerena.ui.player.components.overlays.PlayerControlsOverlay
import org.njarasoa.fijerena.ui.player.components.overlays.StatsOverlay
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.Spacing
import java.util.Calendar
import java.util.Date

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel = viewModel(),
    onBack: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onPreviousChannel: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    currentEpgProgram: EpgProgram? = null,
    nextEpgProgram: EpgProgram? = null,
    categoryStreams: List<MediaItem> = emptyList(),
    lastWatchedStreams: List<MediaItem> = emptyList(),
    onStreamSelected: ((MediaItem) -> Unit)? = null
) {
    val playbackState = viewModel.playbackState.collectAsState().value
    val currentMetadata = viewModel.currentMetadata.collectAsState().value
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val isDeveloperMode = remember { appSettings.isDevMode }

    var showControls by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showAudioTrackSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }
    var showCategoryOverlay by remember { mutableStateOf(false) }
    var showLastWatchedOverlay by remember { mutableStateOf(false) }
    var showChapterSelector by remember { mutableStateOf(false) }
    var showTopOfHourClock by remember { mutableStateOf(false) }
    var clockTick by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    // Auto-show stats on repeated buffer exhaustion (dev mode only)
    LaunchedEffect(isDeveloperMode) {
        if (!isDeveloperMode) return@LaunchedEffect
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
                // Remove timestamps older than 30 seconds
                rebufferTimestamps.removeAll { now - it > 30_000L }
                // Show stats if 3+ rebuffers in 30s window
                if (rebufferTimestamps.size >= 3 && !showStats) {
                    showStats = true
                }
            }
            delay(1000L)
        }
    }

    // Live position polling for smooth VOD timer updates
    var livePosition by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var liveDuration by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    LaunchedEffect(playbackState) {
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

    // Keep last displayed metadata to show during channel transitions
    var displayedMetadata by remember { mutableStateOf(currentMetadata) }

    // Control hints for first-time users (currently disabled)
    val prefs = remember { context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE) }
    var showControlHints by remember { mutableStateOf(false) } // Disabled: was !prefs.getBoolean("hints_dismissed", false)

    // Auto-dismiss hints after 7 seconds
    LaunchedEffect(showControlHints) {
        if (showControlHints) {
            delay(CinemaAnimation.hintsDismissMs)
            showControlHints = false
        }
    }

    // Auto-hide overlays
    LaunchedEffect(showControls, showStreamInfo) {
        if (showControls && showStreamInfo) {
            // Both visible (OK press) - hide after 15 seconds
            delay(CinemaAnimation.controlsAutoHideTvMs)
            showControls = false
            showStreamInfo = false
        } else if (showStreamInfo) {
            // Stream info alone (channel switch or menu) - hide after 3 seconds
            delay(CinemaAnimation.toastDismissMs)
            showStreamInfo = false
        } else if (showControls) {
            // Controls alone (shouldn't happen but handle it) - hide after 15 seconds
            delay(CinemaAnimation.controlsAutoHideTvMs)
            showControls = false
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Top-of-hour clock: show 30s before the hour, hide 90s after
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val totalSecondsIntoHour = now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
            val showAtSecond = 59 * 60 + 30 // 3570s into the hour
            showTopOfHourClock = totalSecondsIntoHour >= showAtSecond || totalSecondsIntoHour < 90
            clockTick = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Update displayed metadata when stream actually starts playing
    LaunchedEffect(currentMetadata.title, playbackState) {
        // Only update displayed metadata when stream is actually playing/buffering
        if (currentMetadata.title.isNotEmpty() &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)) {
            displayedMetadata = currentMetadata
        }
    }

    // Show only stream info when stream starts from menu
    var previousMetadataTitle by remember { mutableStateOf<String?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(currentMetadata.title, playbackState) {
        // Show only stream info when title changes on initial load from menu
        if (currentMetadata.title.isNotEmpty() &&
            currentMetadata.title != previousMetadataTitle &&
            (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)) {

            if (isInitialLoad) {
                // From menu selection - show only stream info
                showStreamInfo = true
                isInitialLoad = false
            }
            // Note: Channel switching sets showStreamInfo directly in key handler
            previousMetadataTitle = currentMetadata.title
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .then(
                // Only make the player box focusable when overlays are not visible
                if (!showStats && !showControls) {
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
                            // When controls are visible, let ENTER pass through to buttons
                            if (showControls) {
                                false
                            } else if (!showStats) {
                                // Show controls overlay only — never pause on OK
                                showControls = true
                                showStreamInfo = true
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            println("PlayerScreen: UP key pressed - showControls=$showControls, isLive=${currentMetadata.isLive}")
                            // Let D-pad navigate inside overlays when they are open
                            if (showCategoryOverlay || showLastWatchedOverlay) {
                                false
                            } else if (!showControls && currentMetadata.isLive) {
                                println("PlayerScreen: Switching to previous channel")
                                onPreviousChannel()
                                showStreamInfo = true
                                true
                            } else if (!showControls && !currentMetadata.isLive) {
                                println("PlayerScreen: VOD - Showing controls on UP")
                                showControls = true
                                showStreamInfo = true
                                true
                            } else {
                                println("PlayerScreen: Not switching channel (controls visible or not live)")
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            println("PlayerScreen: DOWN key pressed - showControls=$showControls, isLive=${currentMetadata.isLive}")
                            // Let D-pad navigate inside overlays when they are open
                            if (showCategoryOverlay || showLastWatchedOverlay) {
                                false
                            } else if (!showControls) {
                                if (currentMetadata.isLive) {
                                    println("PlayerScreen: Switching to next channel")
                                    onNextChannel()
                                    showStreamInfo = true
                                    true
                                } else {
                                    println("PlayerScreen: VOD - Showing controls without pausing")
                                    showControls = true
                                    showStreamInfo = true
                                    true
                                }
                            } else {
                                println("PlayerScreen: Controls visible, letting D-pad navigate")
                                false
                            }
                        }
                        Key.DirectionLeft -> {
                            if (!showControls && currentMetadata.isLive) {
                                // Live TV: Left opens category overlay (or closes last-watched)
                                when {
                                    showLastWatchedOverlay -> showLastWatchedOverlay = false
                                    else -> showCategoryOverlay = true
                                }
                                true
                            } else if (!showControls && !currentMetadata.isLive) {
                                // VOD: seek backward 10s
                                val position = when (val ps = playbackState) {
                                    is PlaybackState.Playing -> ps.position
                                    is PlaybackState.Paused -> ps.position
                                    else -> null
                                }
                                if (position != null) {
                                    val newPosition = (position - 10_000L).coerceAtLeast(0L)
                                    viewModel.seekTo(newPosition)
                                    showStreamInfo = true
                                }
                                true
                            } else {
                                // When controls are visible, let D-pad navigate between buttons
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!showControls && currentMetadata.isLive) {
                                // Live TV: Right opens last-watched overlay (or closes category)
                                when {
                                    showCategoryOverlay -> showCategoryOverlay = false
                                    else -> showLastWatchedOverlay = true
                                }
                                true
                            } else if (!showControls && !currentMetadata.isLive) {
                                // VOD: seek forward 10s
                                val position = when (val ps = playbackState) {
                                    is PlaybackState.Playing -> ps.position
                                    is PlaybackState.Paused -> ps.position
                                    else -> null
                                }
                                val duration = when (val ps = playbackState) {
                                    is PlaybackState.Playing -> ps.duration
                                    is PlaybackState.Paused -> ps.duration
                                    else -> null
                                }
                                if (position != null && duration != null) {
                                    val newPosition = (position + 10_000L).coerceAtMost(duration)
                                    viewModel.seekTo(newPosition)
                                    showStreamInfo = true
                                }
                                true
                            } else {
                                // When controls are visible, let D-pad navigate between buttons
                                false
                            }
                        }
                        Key.Back -> {
                            // Close any visible overlays first, then exit
                            when {
                                showCategoryOverlay -> { showCategoryOverlay = false; true }
                                showLastWatchedOverlay -> { showLastWatchedOverlay = false; true }
                                showStats || showControls || showStreamInfo -> {
                                    showStats = false
                                    showControls = false
                                    showStreamInfo = false
                                    true
                                }
                                else -> {
                                    viewModel.stop()
                                    onBack()
                                    true
                                }
                            }
                        }
                        Key(AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) -> {
                            if (!currentMetadata.isLive) {
                                when (playbackState) {
                                    is PlaybackState.Playing -> viewModel.pause()
                                    is PlaybackState.Paused -> viewModel.resume()
                                    else -> {}
                                }
                            }
                            true
                        }
                        Key(AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -> {
                            if (!currentMetadata.isLive) viewModel.seekRelative(60_000L)
                            true
                        }
                        Key(AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -> {
                            if (!currentMetadata.isLive) viewModel.seekRelative(-30_000L)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Use metadata title as key to force AndroidView recreation on stream change
        val streamKey = currentMetadata.title + currentMetadata.streamUrl

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Capture stream key to trigger re-creation when it changes
                @Suppress("UNUSED_VARIABLE")
                val capturedStreamKey = streamKey

                // Ensure resize mode is set
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                // Bind player
                val service = StreamingPlaybackService.getInstance()
                if (view.player == null) {
                    view.player = service?.getPlayer()
                }
            }
        )

        // Loading/Error overlays (always show, except Idle which is handled silently)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Center
        ) {
            when (playbackState) {
                PlaybackState.Idle -> { /* Silent - no UI flash before stream loads */ }
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
        // Visible whenever showStats is true, regardless of playbackState (survives channel switches)
        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            StatsOverlay(
                playbackState = playbackState,
                metadata = currentMetadata,
                onHide = {
                    // Just close stats, leave controls as they are
                    showStats = false
                }
            )
        }

        // Modern unified controls overlay (mobile-style)
        AnimatedVisibility(
            visible = (showControls || showStreamInfo) && !showStats,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControlsOverlay(
                playbackState = playbackState,
                metadata = displayedMetadata,
                viewModel = viewModel,
                livePosition = livePosition,
                liveDuration = liveDuration,
                currentEpgProgram = currentEpgProgram,
                nextEpgProgram = nextEpgProgram,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                showFullControls = showControls,
                onShowAudioTrackSelector = { showAudioTrackSelector = true },
                onShowSubtitleSelector = { showSubtitleSelector = true },
                onShowQualitySelector = { showQualitySelector = true },
                onShowChapterSelector = { showChapterSelector = true },
                onShowStats = { showStats = !showStats },
                clockTick = clockTick
            )
        }

        // Audio track selector dialog
        if (showAudioTrackSelector) {
            AudioTrackSelectorDialog(
                viewModel = viewModel,
                onDismiss = { showAudioTrackSelector = false }
            )
        }

        // Subtitle selector dialog
        if (showSubtitleSelector) {
            SubtitleSelectorDialog(
                viewModel = viewModel,
                onDismiss = { showSubtitleSelector = false }
            )
        }

        // Quality selector dialog
        if (showQualitySelector) {
            QualitySelectorDialog(
                viewModel = viewModel,
                onDismiss = { showQualitySelector = false }
            )
        }

        // Chapter selector dialog
        if (showChapterSelector) {
            ChapterSelectorDialog(
                viewModel = viewModel,
                onDismiss = { showChapterSelector = false }
            )
        }

        // Autonomous top-of-hour clock
        AnimatedVisibility(
            visible = showTopOfHourClock && !showControls && !showStreamInfo && !showStats,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = clockTick
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = TimeFormat.formatClockTime(Date()),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White.copy(alpha = CinemaAlpha.textDisabled),
                    modifier = Modifier.height(screenHeight * 0.1f)
                )
            }
        }

        // Control hints for first-time users
        if (showControlHints && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused)) {
            ControlHintsOverlay(
                onDismiss = {
                    showControlHints = false
                },
                onDontShowAgain = {
                    prefs.edit().putBoolean("hints_dismissed", true).apply()
                    showControlHints = false
                }
            )
        }

        // Category streams overlay — slides in from the left
        AnimatedVisibility(
            visible = showCategoryOverlay,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            ChannelListOverlay(
                title = "Category Channels",
                streams = categoryStreams,
                panelAlignment = Alignment.CenterStart,
                onSelect = { item ->
                    showCategoryOverlay = false
                    onStreamSelected?.invoke(item)
                },
                onDismiss = { showCategoryOverlay = false }
            )
        }

        // Last watched overlay — slides in from the right
        AnimatedVisibility(
            visible = showLastWatchedOverlay,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            ChannelListOverlay(
                title = "Last Watched",
                streams = lastWatchedStreams,
                panelAlignment = Alignment.CenterEnd,
                emptyMessage = "No recently watched channels yet",
                onSelect = { item ->
                    showLastWatchedOverlay = false
                    onStreamSelected?.invoke(item)
                },
                onDismiss = { showLastWatchedOverlay = false }
            )
        }
    }
}
