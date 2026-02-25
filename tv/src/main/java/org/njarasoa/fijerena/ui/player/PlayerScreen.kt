@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import org.njarasoa.fijerena.core.player.domain.MediaItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.ui.components.TvGlassPanel as GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaTextTertiary
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel = viewModel(),
    onBack: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onPreviousChannel: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    currentEpgProgram: org.njarasoa.fijerena.core.player.model.EpgProgram? = null,
    nextEpgProgram: org.njarasoa.fijerena.core.player.model.EpgProgram? = null,
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
                context = context,
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
            val isPaused = playbackState is PlaybackState.Paused
            val isLive = currentMetadata.isLive
            val audioTrackCount = viewModel.getAudioTracks().size
            val subtitleTrackCount = viewModel.getSubtitleTracks().size
            val qualityCount = viewModel.getVideoQualities().size

            // Focus requester for the first focusable control
            val controlsFocusRequester = remember { FocusRequester() }

            LaunchedEffect(showControls) {
                if (showControls) {
                    // Small delay to allow composition to complete
                    delay(100)
                    try { controlsFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = CinemaAlpha.focusedTint))
            ) {
                // Top bar with channel name and title
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xl)
                ) {
                    Text(
                        text = displayedMetadata.channelName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = displayedMetadata.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // Center row: Rewind | Play/Pause | FastForward (VOD only, hidden for live)
                if (showControls && !isLive) {
                    Row(
                        modifier = Modifier.align(Center),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind -30s
                        Button(
                            onClick = { viewModel.seekRelative(-30_000L) },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.size(TvDimensions.iconButtonSize)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.FastRewind,
                                    contentDescription = "Rewind 30s",
                                    tint = Color.White,
                                    modifier = Modifier.size(TvDimensions.iconLarge)
                                )
                                Text("-30s", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }

                        // Play/Pause
                        Button(
                            onClick = {
                                if (isPaused) viewModel.resume() else viewModel.pause()
                            },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .size(TvDimensions.iconButtonSizeLarge)
                                .focusRequester(controlsFocusRequester)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (isPaused) "Play" else "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(TvDimensions.iconXLarge)
                            )
                        }

                        // Fast Forward +1min
                        Button(
                            onClick = { viewModel.seekRelative(60_000L) },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.size(TvDimensions.iconButtonSize)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.FastForward,
                                    contentDescription = "Fast Forward 1min",
                                    tint = Color.White,
                                    modifier = Modifier.size(TvDimensions.iconLarge)
                                )
                                Text("+1m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }

                // Bottom section: progress/EPG info + icon controls
                GlassPanel(
                    modifier = Modifier
                        .align(BottomCenter)
                        .fillMaxWidth(),
                    backgroundAlpha = 0.6f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                    ) {
                        // VOD progress bar and time info
                        if (!isLive) {
                            val position = livePosition
                            val duration = liveDuration

                            if (duration > 0) {
                                LinearProgressIndicator(
                                    progress = { position.toFloat() / duration.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(TvDimensions.progressBar),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                                )

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(position),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = formatTime(duration),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }

                                // Remaining time and estimated end time
                                val remainingTime = duration - position
                                val estimatedEndTimeMillis = System.currentTimeMillis() + remainingTime
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Remaining: ${formatTime(remainingTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CinemaAccent
                                    )
                                    Text(
                                        text = "Ends at ${org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(java.util.Date(estimatedEndTimeMillis))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CinemaAccent
                                    )
                                }

                                Spacer(modifier = Modifier.height(Spacing.sm))
                            }
                        } else {
                            // Live indicator with EPG info
                            Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(TvDimensions.statsDotSize)
                                            .background(Color.Red, shape = RoundedCornerShape(TvDimensions.statsDotSize / 2))
                                    )
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (currentEpgProgram != null) {
                                    val nowStart = formatEpochTime(currentEpgProgram.startTime)
                                    val nowEnd = formatEpochTime(currentEpgProgram.endTime)
                                    Text(
                                        text = "Now: ${currentEpgProgram.title}  ($nowStart – $nowEnd)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = CinemaAlpha.textMedium),
                                        modifier = Modifier.padding(top = Spacing.xxs)
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
                                            .padding(top = Spacing.xxs)
                                            .height(TvDimensions.progressBar),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                                    )
                                    if (nextEpgProgram != null) {
                                        Text(
                                            text = "Up Next: ${nextEpgProgram.title}  (${formatEpochTime(nextEpgProgram.startTime)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = CinemaAlpha.tint),
                                            modifier = Modifier.padding(top = Spacing.xxs)
                                        )
                                    }
                                }
                            }
                        }

                        // Icon controls row (only when full controls are visible)
                        if (showControls) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Audio track selector
                                if (audioTrackCount > 1) {
                                    Button(
                                        onClick = { showAudioTrackSelector = true },
                                        colors = androidx.tv.material3.ButtonDefaults.colors(
                                            containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Audio", tint = Color.White)
                                    }
                                }

                                // Subtitle selector
                                if (subtitleTrackCount > 0) {
                                    Button(
                                        onClick = { showSubtitleSelector = true },
                                        colors = androidx.tv.material3.ButtonDefaults.colors(
                                            containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                        )
                                    ) {
                                        Icon(Icons.Filled.Subtitles, "Subtitles", tint = Color.White)
                                    }
                                }

                                // Quality selector
                                if (qualityCount > 1) {
                                    Button(
                                        onClick = { showQualitySelector = true },
                                        colors = androidx.tv.material3.ButtonDefaults.colors(
                                            containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                        )
                                    ) {
                                        Icon(Icons.Filled.Tune, "Quality", tint = Color.White)
                                    }
                                }

                                // Favorite toggle
                                if (onToggleFavorite != null) {
                                    Button(
                                        onClick = { onToggleFavorite() },
                                        colors = androidx.tv.material3.ButtonDefaults.colors(
                                            containerColor = if (isFavorite)
                                                CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                                            else
                                                CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                }

                                // Stats for nerds (always visible)
                                Button(
                                    onClick = { showStats = !showStats },
                                    colors = androidx.tv.material3.ButtonDefaults.colors(
                                        containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                    )
                                ) {
                                    Icon(Icons.Filled.BarChart, "Stats", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
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

@Composable
private fun ControlHintsOverlay(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.glass)),
        contentAlignment = Center
    ) {
        GlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidthLarge)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Header
                Text(
                    text = "🎮 Player Controls",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Control hints
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlHint("OK Button", "Show/hide controls")
                    ControlHint("Double-tap OK", "Toggle stats overlay")
                    ControlHint("BACK Button", "Exit player")
                    ControlHint("D-pad Up/Down", "Change channel (Live TV)")
                    ControlHint("Pause/Resume", "Control playback")
                    ControlHint("Audio Button", "Select audio track")
                    ControlHint("Subtitle Button", "Enable/disable subtitles")
                    ControlHint("Quality Button", "Select video quality")
                    ControlHint("Favorite Button", "Add/remove from favorites")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Got it!")
                    }
                    Button(
                        onClick = onDontShowAgain,
                        modifier = Modifier.weight(1f),
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = CinemaSurfaceVariant
                        )
                    ) {
                        Text("Don't show again")
                    }
                }

                // Auto-dismiss info
                Text(
                    text = "This message will auto-dismiss in 7 seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = CinemaAlpha.textDisabled),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ControlHint(control: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = control,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(TvDimensions.audioTrackSelectorWidth)
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = CinemaAlpha.textDisabled)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

@Composable
private fun ChannelListOverlay(
    title: String,
    streams: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    panelAlignment: Alignment = Alignment.CenterStart,
    emptyMessage: String = "No channels"
) {
    val focusRequesters = remember(streams) { List(streams.size) { FocusRequester() } }

    LaunchedEffect(streams) {
        if (focusRequesters.isNotEmpty()) focusRequesters[0].requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        GlassPanel(
            modifier = Modifier
                .align(panelAlignment)
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .padding(Spacing.xxl),
            backgroundAlpha = 0.5f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(bottom = Spacing.md)
                        .basicMarquee()
                )
                if (streams.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        streams.forEachIndexed { index, stream ->
                            Button(
                                onClick = { onSelect(stream) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[index])
                            ) {
                                Text(
                                    text = stream.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee()
                                )
                            }
                        }
                    }
                }
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Request focus on selected item
    LaunchedEffect(Unit) {
        if (selectedIndex in focusRequesters.indices) {
            focusRequesters[selectedIndex].requestFocus()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)),
        contentAlignment = Center
    ) {
        GlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidth)
                .heightIn(max = screenHeight * 0.8f)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Header
                Text(
                    text = "Select Audio Track",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (audioTracks.isEmpty()) {
                    Text(
                        text = "No audio tracks available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md)
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
                                },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                else CinemaSurfaceVariant,
                                contentColor = CinemaTextPrimary,
                                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                                focusedContentColor = CinemaTextPrimary
                            ),
                            border = androidx.tv.material3.ButtonDefaults.border(
                                border = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) TvDimensions.borderFocused else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                                ),
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = TvDimensions.borderFocused,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
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
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "${track.channelCount}ch • ${track.sampleRate / 1000}kHz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextTertiary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // Close button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(CenterHorizontally)
                            .width(TvDimensions.selectionListWidth)
                    ) {
                        Text("Cancel")
                    }
                }

                // Hint text
                Text(
                    text = "Use D-pad to navigate • OK to select • BACK to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    var selectedIndex by remember { mutableStateOf(subtitleTracks.indexOfFirst { it.isSelected }.coerceAtLeast(-1)) }
    val focusRequesters = remember { List(subtitleTracks.size + 1) { FocusRequester() } } // +1 for "Off" option
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Request focus on selected item or "Off" option
    LaunchedEffect(Unit) {
        val focusIndex = if (selectedIndex >= 0) selectedIndex + 1 else 0 // +1 because "Off" is first
        if (focusIndex in focusRequesters.indices) {
            focusRequesters[focusIndex].requestFocus()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)),
        contentAlignment = Center
    ) {
        GlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidth)
                .heightIn(max = screenHeight * 0.8f)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Header
                Text(
                    text = "Select Subtitles",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // "Off" option
                val isOffSelected = selectedIndex == -1
                Button(
                    onClick = {
                        selectedIndex = -1
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[0])
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                selectedIndex = -1
                            }
                        },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = if (isOffSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                        else CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                        focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                        focusedContentColor = CinemaTextPrimary
                    ),
                    border = androidx.tv.material3.ButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isOffSelected) TvDimensions.borderFocused else 0.dp,
                                color = if (isOffSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                width = TvDimensions.borderFocused,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isOffSelected) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (subtitleTracks.isEmpty()) {
                    Text(
                        text = "No subtitle tracks available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                } else {
                    // Track list
                    subtitleTracks.forEachIndexed { index, track ->
                        val isSelected = index == selectedIndex
                        Button(
                            onClick = {
                                selectedIndex = index
                                viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index + 1]) // +1 because "Off" is first
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        selectedIndex = index
                                    }
                                },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                else CinemaSurfaceVariant,
                                contentColor = CinemaTextPrimary,
                                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                                focusedContentColor = CinemaTextPrimary
                            ),
                            border = androidx.tv.material3.ButtonDefaults.border(
                                border = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) TvDimensions.borderFocused else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                                ),
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = TvDimensions.borderFocused,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
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
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = track.mimeType.substringAfterLast("/").uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .width(TvDimensions.selectionListWidth)
                ) {
                    Text("Cancel")
                }

                // Hint text
                Text(
                    text = "Use D-pad to navigate \u2022 OK to select \u2022 BACK to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    var selectedIndex by remember { mutableStateOf(videoQualities.indexOfFirst { it.isSelected }.coerceAtLeast(-1)) }
    val focusRequesters = remember { List(videoQualities.size + 1) { FocusRequester() } } // +1 for "Auto" option
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Request focus on selected item or "Auto" option
    LaunchedEffect(Unit) {
        val focusIndex = if (selectedIndex >= 0) selectedIndex + 1 else 0 // +1 because "Auto" is first
        if (focusIndex in focusRequesters.indices) {
            focusRequesters[focusIndex].requestFocus()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)),
        contentAlignment = Center
    ) {
        GlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidth)
                .heightIn(max = screenHeight * 0.8f)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Header
                Text(
                    text = "Select Quality",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // "Auto" option
                val isAutoSelected = selectedIndex == -1
                Button(
                    onClick = {
                        selectedIndex = -1
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[0])
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                selectedIndex = -1
                            }
                        },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = if (isAutoSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                        else CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                        focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                        focusedContentColor = CinemaTextPrimary
                    ),
                    border = androidx.tv.material3.ButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isAutoSelected) TvDimensions.borderFocused else 0.dp,
                                color = if (isAutoSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                width = TvDimensions.borderFocused,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto (Adaptive)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Automatically adjust quality based on network",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextTertiary
                            )
                        }
                        if (isAutoSelected) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (videoQualities.isEmpty()) {
                    Text(
                        text = "No quality options available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                } else {
                    // Quality list
                    videoQualities.forEachIndexed { index, quality ->
                        val isSelected = index == selectedIndex
                        Button(
                            onClick = {
                                selectedIndex = index
                                viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index + 1]) // +1 because "Auto" is first
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        selectedIndex = index
                                    }
                                },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                else CinemaSurfaceVariant,
                                contentColor = CinemaTextPrimary,
                                focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.scrim),
                                focusedContentColor = CinemaTextPrimary
                            ),
                            border = androidx.tv.material3.ButtonDefaults.border(
                                border = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) TvDimensions.borderFocused else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                                ),
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = TvDimensions.borderFocused,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = quality.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                                    text = "${quality.width}×${quality.height} • ${quality.frameRate.toInt()}fps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .width(TvDimensions.selectionListWidth)
                ) {
                    Text("Cancel")
                }

                // Hint text
                Text(
                    text = "Use D-pad to navigate • OK to select • BACK to cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

    // Request focus when overlay appears to capture all key events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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

    // Collect dropped frames from service
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

    // Update stats periodically
    LaunchedEffect(Unit) {
        while (true) {
            StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                // Update buffered position
                bufferedPosition = p.bufferedPosition

                // Get dropped frames from analytics
                droppedFrames = serviceDroppedFrames?.value ?: 0L

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

    // Calculate overlay size (35% width × 50% height)
    val overlayWidth = (configuration.screenWidthDp * 0.35).dp
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
                .background(Color.Black.copy(alpha = CinemaAlpha.glass), shape = RoundedCornerShape(CinemaCornerRadius.medium))
                .then(
                    if (isFocused) Modifier.border(
                        TvDimensions.borderFocusedStats,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(CinemaCornerRadius.medium)
                    ) else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                // Single press closes overlay
                                onHide()
                                true
                            }
                            Key.Back -> {
                                // Close overlay (not the stream)
                                onHide()
                                true
                            }
                            Key.DirectionUp -> {
                                // Move to top
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.TOP_LEFT
                                    QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.TOP_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                // Move to bottom
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_LEFT -> QuadrantPosition.BOTTOM_LEFT
                                    QuadrantPosition.TOP_RIGHT -> QuadrantPosition.BOTTOM_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                // Move to left
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_RIGHT -> QuadrantPosition.TOP_LEFT
                                    QuadrantPosition.BOTTOM_RIGHT -> QuadrantPosition.BOTTOM_LEFT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                // Move to right
                                quadrantPosition = when (quadrantPosition) {
                                    QuadrantPosition.TOP_LEFT -> QuadrantPosition.TOP_RIGHT
                                    QuadrantPosition.BOTTOM_LEFT -> QuadrantPosition.BOTTOM_RIGHT
                                    else -> quadrantPosition
                                }
                                true
                            }
                            else -> true  // Consume all other keys when stats are visible
                        }
                    } else {
                        true  // Consume all key events
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header
                Text(
                    text = "📊 Stats for Nerds",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
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
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                        val bwEstimate = serviceBandwidth?.value ?: 0L
                        CompactStatRow("Bandwidth", if (bwEstimate > 0) formatBitrate(bwEstimate.toInt()) else "N/A")
                        CompactStatRow("Buffer", "${bufferHealth}s")
                        CompactStatRow("Buffered", formatTime(bufferedPosition))
                        val rebuffers = serviceRebufferCount?.value ?: 0
                        val rebufferTimeMs = serviceRebufferTimeMs?.value ?: 0L
                        val rebufferColor = when {
                            rebuffers == 0 -> CinemaSuccess
                            rebuffers <= 3 -> CinemaWarning
                            else -> CinemaError
                        }
                        CompactStatRowColored("Rebuffers", "$rebuffers", rebufferColor)
                        if (rebufferTimeMs > 0) {
                            CompactStatRowColored("Rebuf Time", "${rebufferTimeMs / 1000}.${(rebufferTimeMs % 1000) / 100}s", rebufferColor)
                        }
                        val qSwitches = serviceQualitySwitches?.value ?: 0
                        if (qSwitches > 0) {
                            CompactStatRow("ABR Switches", "$qSwitches")
                        }
                    }

                    // Right Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Playback stats
                        SectionHeader("PLAYBACK")
                        CompactStatRow("Pos", formatTime(position))
                        CompactStatRow("Dur", if (duration > 0) formatTime(duration) else "Live")

                        // Performance metrics with color coding
                        SectionHeader("PERFORMANCE")
                        val totalFrames = serviceTotalFrames?.value ?: 0L
                        val dropRate = if (totalFrames > 0) {
                            (droppedFrames.toFloat() / totalFrames * 100)
                        } else 0f

                        val dropColor = when {
                            dropRate < 0.5f -> CinemaSuccess // Green - Good
                            dropRate < 2.0f -> CinemaWarning // Yellow - Warning
                            else -> CinemaError // Red - Poor
                        }

                        CompactStatRowColored(
                            "Dropped",
                            "$droppedFrames / $totalFrames",
                            dropColor
                        )
                        if (totalFrames > 0) {
                            CompactStatRowColored(
                                "Drop Rate",
                                String.format("%.2f%%", dropRate),
                                dropColor
                            )
                        }

                        // Stream info
                        SectionHeader("STREAM")
                        CompactStatRow("Type", if (metadata.isLive) "Live" else "VOD")
                        CompactStatRow("Retries", "${serviceRetryCount?.value ?: 0}")
                        CompactStatRow("Uptime", streamElapsed)
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
                        color = Color.White.copy(alpha = CinemaAlpha.textLow),
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
            color = Color.White.copy(alpha = CinemaAlpha.textMedium)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White.copy(alpha = CinemaAlpha.textMedium),
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactStatRowColored(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White.copy(alpha = CinemaAlpha.textMedium),
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        ),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
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
    onSubtitle: (() -> Unit)? = null,
    onQuality: (() -> Unit)? = null,
    onStats: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
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
                        progress = { if (duration > 0) position.toFloat() / duration.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TvDimensions.progressBar),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = CinemaAlpha.overlayMedium)
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = CinemaAlpha.overlayMedium)
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
                            .size(TvDimensions.statsDotSize)
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

            // Controls - using TvLazyRow for better D-pad focus navigation
            TvLazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play/Pause button (VOD only, not for live streams)
                if (!metadata.isLive) {
                    item {
                        if (isPaused) {
                            Button(onClick = { onResume?.invoke() }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text("Resume")
                                }
                            }
                        } else {
                            Button(onClick = { onPause?.invoke() }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Pause, contentDescription = null)
                                    Text("Pause")
                                }
                            }
                        }
                    }
                }

                item {
                    Button(onClick = { onAudioTrack?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            Text("Audio")
                        }
                    }
                }

                item {
                    Button(onClick = { onSubtitle?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Subtitles, contentDescription = null)
                            Text("Subtitle")
                        }
                    }
                }

                item {
                    Button(onClick = { onQuality?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                            Text("Quality")
                        }
                    }
                }

                if (onStats != null) {
                    item {
                        Button(onClick = { onStats.invoke() }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.BarChart, contentDescription = null)
                                Text("Stats")
                            }
                        }
                    }
                }

                // Favorite toggle button
                if (onToggleFavorite != null) {
                    item {
                        Button(
                            onClick = { onToggleFavorite() },
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = if (isFavorite)
                                    MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.scrim)
                                else
                                    MaterialTheme.colorScheme.surface.copy(alpha = CinemaAlpha.textMedium)
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null
                                )
                                Text(if (isFavorite) "Favorited" else "Favorite")
                            }
                        }
                    }
                }
            }

            // Hint text
            Text(
                text = "Press OK to hide controls • Press BACK to exit",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = CinemaAlpha.textLow)
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

private fun formatEpochTime(epochSeconds: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(epochSeconds * 1000))
}

@Composable
private fun IdleContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl)
    ) {
        Text(
            text = "Ready to play",
            color = CinemaTextPrimary,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        CinemaSecondaryButton(
            onClick = onBack,
            text = "Back",
            modifier = Modifier.padding(Spacing.xs)
        )
    }
}

@Composable
private fun BufferingContent() {
    CircularProgressIndicator(
        color = CinemaAccent,
        strokeWidth = TvDimensions.progressBar,
        modifier = Modifier.size(TvDimensions.progressIndicator)
    )
}


@Composable
private fun EndedContent(onBack: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier.padding(Spacing.xl)
    ) {
        Text(
            text = "Playback ended",
            color = CinemaTextPrimary,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        CinemaSecondaryButton(
            onClick = onBack,
            text = "Back"
        )
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
            .background(CinemaBackground.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier
                .padding(Spacing.xxl)
                .width(TvDimensions.dialogWidth)
        ) {
            // Error icon/title
            Text(
                text = "⚠️ Playback Error",
                color = CinemaError,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // User-friendly error message
            Text(
                text = error.message,
                color = CinemaTextPrimary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Technical details in dev mode
            if (isDevMode && error.exception != null) {
                Spacer(modifier = Modifier.height(Spacing.xl))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CinemaSurface,
                    shape = RoundedCornerShape(CornerRadius.small)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md)
                    ) {
                        Text(
                            text = "Technical Details (Dev Mode):",
                            color = CinemaAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))

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
                            color = CinemaTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TvDimensions.statsOverlayPanelHeight)
                                .focusable(false)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxl + Spacing.xs))

            // Action buttons
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md)
            ) {
                CinemaPrimaryButton(
                    onClick = onRetry,
                    text = "Retry",
                    modifier = Modifier.width(120.dp).height(TvDimensions.trackItemHeight)
                )
                CinemaSecondaryButton(
                    onClick = onBack,
                    text = "Back",
                    modifier = Modifier.width(120.dp).height(TvDimensions.trackItemHeight)
                )
            }
        }
    }
}
