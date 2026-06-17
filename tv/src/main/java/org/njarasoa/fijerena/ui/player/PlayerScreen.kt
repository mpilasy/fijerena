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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
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
import org.njarasoa.fijerena.ui.theme.Spacing
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
    currentStreamId: String? = null,
    categoryStreams: ImmutableMediaList = ImmutableMediaList(),
    lastWatchedStreams: ImmutableMediaList = ImmutableMediaList(),
    onStreamSelected: ((MediaItem) -> Unit)? = null,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentMetadata by viewModel.currentMetadata.collectAsStateWithLifecycle()
    val controller by viewModel.controller.collectAsStateWithLifecycle()

    // Capture delegated properties into local variables for stable smart casting
    val currentPs = playbackState
    val currentMeta = currentMetadata

    val context = LocalContext.current

    val state = rememberPlayerScreenState(context, currentMetadata)

    PlayerEffects(
        state = state,
        playbackState = playbackState,
        currentMetadata = currentMetadata,
        viewModel = viewModel,
    )

    // Ensure focus is requested when no overlays are visible
    LaunchedEffect(state.showControls, state.showCategoryOverlay, state.showLastWatchedOverlay) {
        val noOverlays = !state.showControls && !state.showCategoryOverlay && !state.showLastWatchedOverlay
        if (noOverlays) {
            android.util.Log.i("PlayerScreen", "Requesting focus for main Box")
            state.focusRequester.requestFocus()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground)
                .then(
                    // Only make the player box focusable when controls and menus are NOT visible.
                    // This allows focus to pass to the active overlay (e.g. Category List).
                    if (!state.showControls && !state.showCategoryOverlay && !state.showLastWatchedOverlay) {
                        Modifier
                            .focusRequester(state.focusRequester)
                            .focusable()
                    } else {
                        Modifier
                    }
                )
                .onKeyEvent { keyEvent ->
                    android.util.Log.i("PlayerScreen", "onKeyEvent: action=${keyEvent.nativeKeyEvent.action}, code=${keyEvent.nativeKeyEvent.keyCode}")
                    handlePlayerKeyEvent(
                        keyEvent = keyEvent,
                        state = state,
                        viewModel = viewModel,
                        playbackState = playbackState,
                        currentMetadata = currentMetadata,
                        onBack = onBack,
                        onNextChannel = onNextChannel,
                        onPreviousChannel = onPreviousChannel,
                    )
                },
    ) {
        // Use metadata title as key to identify stream changes without recreating view
        val streamKey = currentMetadata.title + currentMetadata.streamUrl

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // CRITICAL: Block all native focus
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                // Bind player directly from service for zero-lag visibility
                val service = StreamingPlaybackService.getInstance()
                val player = service?.getPlayer()
                if (view.player != player) {
                    view.player = player
                }
            },
        )

        // Loading/Error overlays (always show, except Idle which is handled silently)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Center,
        ) {
            val service = StreamingPlaybackService.getInstance()
            val isRecycling by (service?.isRecyclingFlow ?: kotlinx.coroutines.flow.MutableStateFlow(false))
                .collectAsStateWithLifecycle()

            // Failsafe Truth: High-frequency poll of player status to clear stuck UI
            var isActuallyMoving by remember { mutableStateOf(false) }
            LaunchedEffect(currentPs, isRecycling) {
                if (currentPs is PlaybackState.Buffering && !isRecycling) {
                    while (true) {
                        val player = StreamingPlaybackService.getInstance()?.getPlayer()
                        val pos = player?.currentPosition ?: 0L
                        val playing = player?.isPlaying == true
                        if (playing || pos > 0L) {
                            isActuallyMoving = true
                            break
                        }
                        delay(1000)
                    }
                } else {
                    isActuallyMoving = false
                }
            }

            when (val ps = currentPs) {
                PlaybackState.Idle -> { /* Silent */ }
                PlaybackState.Buffering -> {
                    if (!isRecycling && !isActuallyMoving) {
                        BufferingContent()
                    }
                }
                is PlaybackState.Ended -> EndedContent(onBack)
                is PlaybackState.Error ->
                    ErrorContent(
                        error = ps,
                        onRetry = { viewModel.playStream(currentMeta) },
                        onBack = onBack,
                    )
                else -> { /* Show controls overlay below */ }
            }
        }

        // Stats overlay (double-click to show)
        // Visible whenever showStats is true, regardless of playbackState (survives channel switches)
        AnimatedVisibility(
            visible = state.showStats,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            StatsOverlay(
                playbackState = currentPs,
                metadata = currentMeta,
                onHide = {
                    // Just close stats, leave controls as they are
                    state.showStats = false
                },
            )
        }

        // Modern unified controls overlay (mobile-style)
        AnimatedVisibility(
            visible = state.showControls || state.showStreamInfo,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerControlsOverlay(
                playbackState = currentPs,
                metadata = state.displayedMetadata,
                viewModel = viewModel,
                livePosition = state.livePosition,
                liveDuration = state.liveDuration,
                currentEpgProgram = currentEpgProgram,
                nextEpgProgram = nextEpgProgram,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                showFullControls = state.showControls,
                onShowAudioTrackSelector = { state.showAudioTrackSelector = true },
                onShowSubtitleSelector = { state.showSubtitleSelector = true },
                onShowQualitySelector = { state.showQualitySelector = true },
                onShowChapterSelector = { state.showChapterSelector = true },
                onShowStats = { state.showStats = !state.showStats },
                seekSpeedLabel = state.seekSpeedLabel,
                scrubPositionMs = state.scrubPositionMs,
                onCommitScrub = { target ->
                    viewModel.seekTo(target)
                    state.scrubPositionMs = null
                },
            )
        }

        // Audio track selector dialog
        if (state.showAudioTrackSelector) {
            AudioTrackSelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showAudioTrackSelector = false },
            )
        }

        // Subtitle selector dialog
        if (state.showSubtitleSelector) {
            SubtitleSelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showSubtitleSelector = false },
            )
        }

        // Quality selector dialog
        if (state.showQualitySelector) {
            QualitySelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showQualitySelector = false },
            )
        }

        // Chapter selector dialog
        if (state.showChapterSelector) {
            ChapterSelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showChapterSelector = false },
            )
        }

        // Autonomous top-of-hour clock
        AnimatedVisibility(
            visible = state.showTopOfHourClock && !state.showControls && !state.showStreamInfo && !state.showStats,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Self-ticking: only this composable recomposes each second
            var tick by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    tick = System.currentTimeMillis()
                    delay(1000L)
                }
            }
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = TimeFormat.formatClockTime(Date(tick)),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White.copy(alpha = CinemaAlpha.textDisabled),
                    modifier = Modifier.height(screenHeight * 0.1f),
                )
            }
        }

        // Control hints for first-time users
        if (state.showControlHints && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused)) {
            ControlHintsOverlay(
                onDismiss = {
                    state.dismissControlHints()
                },
                onDontShowAgain = {
                    state.markHintsDismissed()
                },
            )
        }

        // Category streams overlay — slides in from the left
        AnimatedVisibility(
            visible = state.showCategoryOverlay,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            ChannelListOverlay(
                title = stringResource(R.string.player_category_channels),
                streams = categoryStreams,
                panelAlignment = Alignment.CenterStart,
                currentStreamId = currentStreamId,
                onSelect = { item ->
                    state.showCategoryOverlay = false
                    onStreamSelected?.invoke(item)
                },
                onDismiss = { state.showCategoryOverlay = false },
            )
        }

        // Last watched overlay — slides in from the right
        AnimatedVisibility(
            visible = state.showLastWatchedOverlay,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            ChannelListOverlay(
                title = stringResource(R.string.player_last_watched),
                streams = lastWatchedStreams,
                panelAlignment = Alignment.CenterEnd,
                emptyMessage = stringResource(R.string.player_no_last_watched),
                onSelect = { item ->
                    state.showLastWatchedOverlay = false
                    onStreamSelected?.invoke(item)
                },
                onDismiss = { state.showLastWatchedOverlay = false },
            )
        }
    }
}
