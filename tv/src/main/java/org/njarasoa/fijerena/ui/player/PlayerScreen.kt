@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.components.EmbeddedPlayerSurface
import org.njarasoa.fijerena.core.ui.components.awaitStarted
import org.njarasoa.fijerena.core.ui.components.rememberSlowConnectionText
import org.njarasoa.fijerena.core.ui.components.showUpNext
import org.njarasoa.fijerena.core.ui.components.upNextOnEnd
import org.njarasoa.fijerena.core.ui.components.upNextSecondsLeft
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.player.components.BufferingContent
import org.njarasoa.fijerena.ui.player.components.EndedContent
import org.njarasoa.fijerena.ui.player.components.ErrorContent
import org.njarasoa.fijerena.ui.player.components.dialogs.AudioTrackSelectorDialog
import org.njarasoa.fijerena.ui.player.components.dialogs.ChapterSelectorDialog
import org.njarasoa.fijerena.ui.player.components.dialogs.QualitySelectorDialog
import org.njarasoa.fijerena.ui.player.components.dialogs.SubtitleSelectorDialog
import org.njarasoa.fijerena.ui.player.components.overlays.TvPlayerControlsOverlay
import org.njarasoa.fijerena.ui.player.components.overlays.TvSlowConnectionBanner
import org.njarasoa.fijerena.ui.player.components.overlays.TvStatsOverlay
import org.njarasoa.fijerena.ui.player.components.overlays.TvTuningOverlay
import org.njarasoa.fijerena.ui.player.components.overlays.TvUpNextOverlay
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
    /**
     * Live TV's channel panel (LT3): the preview's own panel, drawn over the video from the right
     * while open. Left or Right opens it when neither it nor the OSD is showing; Back closes it,
     * and focus returns to the player. The panel calls `close` once it has tuned a row. Null (the
     * standalone route, VOD only on TV) leaves Left/Right to the scrub cursor.
     */
    channelPanel: (@Composable (close: () -> Unit) -> Unit)? = null,
    /**
     * Live TV zap feedback (LT5): the channel being tuned, from the zap until it plays — the
     * caller clears it on Playing or Error. Shown as [TvTuningOverlay] in place of the loading
     * spinner. Null = nothing being tuned.
     */
    tuningChannelName: String? = null,
    /**
     * Live TV's Guide button on the OSD (GD5): opens the TV Guide on the playing channel. Null (no
     * guide for this source, VOD) leaves the button out.
     */
    onShowGuide: (() -> Unit)? = null,
    nextEpisode: EpisodeItem? = null,
    onPlayNextEpisode: ((EpisodeItem) -> Unit)? = null,
    // Whether the provider lets episodes roll on to [nextEpisode] — Xtream, not Jellyfin.
    autoplayNextSupported: Boolean = false,
    upNextState: UpNextState = remember { UpNextState() },
    // Catch-up: called when the stream ends; true when it carries on (a programme still on air was
    // asked for again), so the player stays. Null leaves the player at the end as for a film.
    continueOnEnd: (() -> Boolean)? = null,
    // Catch-up: the OSD's Watch live, to the channel live. Null leaves the button out.
    onWatchLive: (() -> Unit)? = null,
    // Live TV: the OSD's Start over, the programme on air from its start. Null leaves it out.
    onStartOver: (() -> Unit)? = null,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentMetadata by viewModel.currentMetadata.collectAsStateWithLifecycle()
    val controller by viewModel.controller.collectAsStateWithLifecycle()

    // Capture delegated properties into local variables for stable smart casting
    val currentPs = playbackState
    val currentMeta = currentMetadata

    val context = LocalContext.current

    val state = rememberPlayerScreenState(context, currentMetadata)

    // Autoplay next episode — see UpNextState. The "Up next" card shows over the playing episode
    // once little enough is left; its countdown is the playback time left, from the polled
    // position, so it stops with the video.
    val autoplayNext = remember { AppSettings(context.applicationContext).autoplayNextEpisode }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val upNextDismissed = upNextState.dismissedFor != null && upNextState.dismissedFor == currentStreamId
    val upNext =
        nextEpisode?.takeIf {
            onPlayNextEpisode != null &&
                upNextState.startingFrom == null &&
                (currentPs is PlaybackState.Playing || currentPs is PlaybackState.Paused) &&
                showUpNext(autoplayNext, autoplayNextSupported, it, state.livePosition, state.liveDuration, upNextDismissed)
        }
    val upNextVisible = upNext != null
    val upNextFocus = remember { FocusRequester() }
    var upNextFocused by remember { mutableStateOf(false) }
    // Same path as the Next button (TvPlayerScreen's onPlayNextEpisode: awaited finalise, then load).
    val playUpNext: (EpisodeItem) -> Unit = { next ->
        upNextState.dismissedFor = currentStreamId
        upNextState.startingFrom = currentStreamId
        onPlayNextEpisode?.invoke(next)
    }
    // Cancel hides the card for this episode; it plays on and ends as it always has. Focus leaves
    // with the card, so the OSD closes too and focus returns to the player.
    val cancelUpNext: () -> Unit = {
        upNextState.dismissedFor = currentStreamId
        if (upNextFocused) {
            state.showControls = false
            state.showStreamInfo = false
        }
    }

    // Proper BackHandler (not the onKeyEvent below) so this composes correctly whether
    // PlayerScreen is reached via nav (Screen.Player) or embedded full-screen inside
    // LiveTvSplitLayout's own BackHandler. A raw onKeyEvent consuming KEYCODE_BACK does not
    // stop the OnBackPressedDispatcher chain, so an outer BackHandler still fires for the same
    // press and can race a state read here into a double pop — same class of bug fixed earlier
    // for the CategoryList/ContentTypeSelection transition. BackHandler's registration is
    // properly stacked instead (innermost/most-recently-composed wins), so only one handler
    // ever runs per press.
    BackHandler {
        when {
            upNextVisible -> {
                cancelUpNext()
            }

            state.scrubPositionMs != null -> {
                state.scrubPositionMs = null
            }

            // Inert fallback: Back is taken in onPreviewKeyEvent below while the panel is open.
            state.showChannelPanel -> {
                state.showChannelPanel = false
            }

            state.showStats || state.showControls || state.showStreamInfo -> {
                state.showStats = false
                state.showControls = false
                state.showStreamInfo = false
            }

            // Whether "back" should stop playback is the caller's call, not this shared
            // composable's — the standalone route's onBack stops (TvPlayerScreen.kt), while
            // LiveTvSplitLayout's promoted view just wants to demote back to the dock without
            // interrupting playback.
            else -> {
                onBack()
            }
        }
    }

    PlayerEffects(
        state = state,
        playbackState = playbackState,
        currentMetadata = currentMetadata,
        viewModel = viewModel,
        onNextChannel = onNextChannel,
        onPreviousChannel = onPreviousChannel,
    )

    // Ensure focus is requested when no overlays are visible — on the "Up next" card while it is up.
    // Keyed on isModalOpen too: a track picker keeps focus while the OSD auto-hides behind it, so
    // focus comes back to the player when it closes. Closing the channel panel lands here too.
    LaunchedEffect(state.showControls, state.showChannelPanel, upNextVisible, state.isModalOpen) {
        if (!upNextVisible) upNextFocused = false
        val noOverlays = !state.showControls && !state.showChannelPanel
        if (noOverlays && upNextVisible) {
            withFrameMillis {}
            upNextFocus.requestFocus()
        } else if (noOverlays) {
            android.util.Log.i("PlayerScreen", "Requesting focus for main Box")
            state.focusRequester.requestFocus()
        }
    }
    // The card appearing while the OSD is up takes focus too ("Play now"), unless a picker is open.
    LaunchedEffect(upNextVisible) {
        if (upNextVisible && state.showControls && !state.isModalOpen) {
            withFrameMillis {}
            upNextFocus.requestFocus()
        }
    }

    var screenHeightPx by remember { mutableIntStateOf(0) }
    var osdPanelHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground)
                .then(
                    // Only make the player box focusable when controls and menus are NOT visible.
                    // This allows focus to pass to the active overlay (e.g. the channel panel).
                    if (!state.showControls && !state.showChannelPanel) {
                        Modifier
                            .focusRequester(state.focusRequester)
                            .focusable()
                    } else {
                        Modifier
                    },
                )
                // onPreviewKeyEvent (top-down, before any focused descendant) rather than
                // onKeyEvent (bubble-up, after) — mirrors the BackHandler/onPreviewKeyEvent
                // fix below for the same reason: a bubble-phase handler only gets an event
                // once the currently focused child has passed on it. The Center/Enter reveal
                // tap moves focus onto the play/pause button asynchronously (LaunchedEffect in
                // TvPlayerControlsOverlay); if that lands before this same press's KeyUp is
                // dispatched, the button's own click-on-KeyUp handling fired first and
                // suppressNextCenterKeyUp never got a chance to run, so one press both opened
                // the OSD and toggled play/pause. Preview phase always wins that race.
                .onPreviewKeyEvent { keyEvent ->
                    android.util.Log.i(
                        "PlayerScreen",
                        "onPreviewKeyEvent: action=${keyEvent.nativeKeyEvent.action}, code=${keyEvent.nativeKeyEvent.keyCode}",
                    )
                    // Any key while the OSD is up restarts its auto-hide (LT4).
                    if (state.showControls && keyEvent.type == KeyEventType.KeyDown) state.controlsKeyTick++
                    when {
                        // Channel panel open: Back closes it (on KeyUp, so the release does not
                        // land on the player). Taken here, top-down, because a focused row
                        // swallows the first Back before BackHandler sees it (AGENTS.md → Back on
                        // TV). Every other key goes on to the panel: the handler below leaves the
                        // D-pad and OK alone while isModalOpen.
                        state.showChannelPanel && keyEvent.key == Key.Back -> {
                            if (keyEvent.type == KeyEventType.KeyUp) state.showChannelPanel = false
                            true
                        }

                        // "Up next" card up: Back hides it and playback carries on. Taken here,
                        // top-down, because a focused TV Button swallows the first Back before
                        // BackHandler sees it (AGENTS.md → Back on TV).
                        upNextVisible && keyEvent.key == Key.Back -> {
                            if (keyEvent.type == KeyEventType.KeyUp) cancelUpNext()
                            true
                        }

                        // Focus is on the card: its buttons get the D-pad and OK.
                        upNextVisible && upNextFocused && keyEvent.key in UP_NEXT_CARD_KEYS -> {
                            false
                        }

                        // Error up, OSD hidden: its Retry and Back get the D-pad and OK.
                        playbackState is PlaybackState.Error && !state.showControls && !state.isModalOpen &&
                            keyEvent.key in UP_NEXT_CARD_KEYS -> {
                            false
                        }

                        // Card up, OSD hidden, focus on the player: Down moves onto the card.
                        upNextVisible && !state.showControls && !state.isModalOpen && keyEvent.key == Key.DirectionDown -> {
                            if (keyEvent.type == KeyEventType.KeyDown) upNextFocus.requestFocus()
                            true
                        }

                        else -> {
                            handlePlayerKeyEvent(
                                keyEvent = keyEvent,
                                state = state,
                                viewModel = viewModel,
                                playbackState = playbackState,
                                currentMetadata = currentMetadata,
                                onNextChannel = onNextChannel,
                                onPreviousChannel = onPreviousChannel,
                                hasChannelPanel = channelPanel != null,
                            )
                        }
                    }
                },
    ) {
        // SurfaceView (EmbeddedPlayerSurface's default), always — full-screen playback is
        // composited independently of the UI thread by SurfaceFlinger, so OSD/flyout
        // recomposition here never steals frames from the video. See LiveTvSplitLayout's
        // videoSurface comment for why the preview pane needs the opposite (TextureView).
        // While the controls are up, the subtitles sit above their bottom panel instead of on
        // the title and timeline. A share of the screen's height: the subtitles' own share is of
        // the video's height, never taller than the screen, so a letterboxed video only lifts them
        // a little further.
        val osdShown = state.showControls || state.showStreamInfo
        EmbeddedPlayerSurface(
            modifier = Modifier.fillMaxSize().onSizeChanged { screenHeightPx = it.height },
            subtitleBottomPaddingFraction =
                if (osdShown && osdPanelHeightPx > 0 && screenHeightPx > 0) {
                    osdPanelHeightPx.toFloat() / screenHeightPx + SUBTITLE_GAP_FRACTION
                } else {
                    null
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

            // Failsafe Truth: High-frequency poll of player status to clear stuck UI.
            // `currentPosition` doesn't reset when buffering mid-stream, so comparing it against
            // a threshold (e.g. > 0L) is always true the instant any playback has occurred —
            // track actual progression against the previous sample instead.
            var isActuallyMoving by remember { mutableStateOf(false) }
            LaunchedEffect(currentPs, isRecycling) {
                if (currentPs is PlaybackState.Buffering && !isRecycling) {
                    var lastPos = StreamingPlaybackService.getInstance()?.getPlayer()?.currentPosition ?: 0L
                    while (true) {
                        delay(500)
                        val player = StreamingPlaybackService.getInstance()?.getPlayer()
                        val pos = player?.currentPosition ?: 0L
                        val playing = player?.isPlaying == true
                        if (playing || pos > lastPos) {
                            isActuallyMoving = true
                            break
                        }
                        lastPos = pos
                    }
                } else {
                    isActuallyMoving = false
                }
            }

            // Natural end of a movie/episode (never fires for live TV — handleStreamEndedOrError
            // only emits Ended for !metadata.isLive) — leave the player instead of waiting on a
            // manual Back press, unless the profile plays the next episode automatically, there
            // is one and its card wasn't cancelled: then it plays at once (once the screen is in
            // the foreground). EndedContent below still renders for the brief window before this
            // fires. While a next episode is starting, the player still reads Ended (and this
            // screen re-mounts) — nothing to do until it plays.
            LaunchedEffect(currentPs, currentStreamId) {
                val startingFrom = upNextState.startingFrom
                if (startingFrom != null) {
                    if (currentPs !is PlaybackState.Ended && currentStreamId != startingFrom) upNextState.startingFrom = null
                } else if (currentPs is PlaybackState.Ended && continueOnEnd?.invoke() != true) {
                    val next =
                        upNextOnEnd(autoplayNext, autoplayNextSupported, nextEpisode, upNextDismissed)
                            ?.takeIf { onPlayNextEpisode != null }
                    if (next != null) {
                        lifecycle.awaitStarted()
                        playUpNext(next)
                    } else {
                        onBack()
                    }
                }
            }

            when (val ps = currentPs) {
                PlaybackState.Idle -> { /* Silent */ }

                PlaybackState.Buffering -> {
                    // While tuning, the overlay below carries its own spinner.
                    if (!isActuallyMoving && tuningChannelName == null) {
                        BufferingContent()
                    }
                }

                is PlaybackState.Ended -> {
                    if (upNextState.startingFrom != null) BufferingContent() else EndedContent(onBack)
                }

                is PlaybackState.Error -> {
                    ErrorContent(
                        error = ps,
                        onRetry = { viewModel.playStream(currentMeta) },
                        onBack = onBack,
                    )
                }

                else -> { /* Show controls overlay below */ }
            }
        }

        // Zap feedback (LT5): under the banner and the panel, over the picture.
        tuningChannelName?.let { TvTuningOverlay(channelName = it) }

        // P4: the connection can't keep up with the stream.
        rememberSlowConnectionText()?.let { TvSlowConnectionBanner(text = it) }

        // Stats overlay (double-click to show)
        // Visible whenever showStats is true, regardless of playbackState (survives channel switches)
        AnimatedVisibility(
            visible = state.showStats,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TvStatsOverlay(
                playbackState = currentPs,
                metadata = currentMeta,
                onHide = {
                    // Just close stats, leave controls as they are
                    state.showStats = false
                },
            )
        }

        if (state.showAudioTrackSelector) {
            AudioTrackSelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showAudioTrackSelector = false },
            )
        }

        if (state.showSubtitleSelector) {
            SubtitleSelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showSubtitleSelector = false },
            )
        }

        if (state.showQualitySelector) {
            QualitySelectorDialog(
                viewModel = viewModel,
                onDismiss = { state.showQualitySelector = false },
            )
        }

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
            val screenHeight = Dp(LocalConfiguration.current.screenHeightDp.toFloat())
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

        // Controls overlay. Declared before the channel panel below
        // so that on the rare overlap (a channel-zap's showStreamInfo hasn't auto-hidden yet when
        // the panel opens) it renders underneath it, never on top — opening the panel is never
        // itself a reason to show this. hideTopBars still exists for that overlap case, so the
        // compact top bars don't double up with the panel's tab row.
        AnimatedVisibility(
            visible = state.showControls || state.showStreamInfo,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TvPlayerControlsOverlay(
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
                hideTopBars = state.showChannelPanel,
                isDeveloperMode = state.isDeveloperMode,
                // Channels (LT4): the same panel Left/Right open; the OSD makes way for it.
                onShowChannels =
                    channelPanel?.let {
                        {
                            state.showControls = false
                            state.showStreamInfo = false
                            state.showChannelPanel = true
                        }
                    },
                onShowGuide = onShowGuide,
                onWatchLive = onWatchLive,
                onStartOver = onStartOver,
                onShowAudioTrackSelector = { state.showAudioTrackSelector = true },
                onShowSubtitleSelector = { state.showSubtitleSelector = true },
                onShowQualitySelector = { state.showQualitySelector = true },
                onShowChapterSelector = { state.showChapterSelector = true },
                onShowStats = { state.showStats = !state.showStats },
                pickerOpen = state.isModalOpen,
                scrubPositionMs = state.scrubPositionMs,
                onScrubStep = { nativeEvent, forward -> stepScrubCursor(state, currentPs, nativeEvent, forward) },
                onCommitScrub = { commitScrub(state, viewModel) },
                nextEpisode = nextEpisode,
                onPlayNextEpisode = onPlayNextEpisode,
                onPanelHeightChanged = { osdPanelHeightPx = it },
            )
        }

        // Autoplay next episode: the "Up next" card, above the controls.
        upNext?.let { next ->
            TvUpNextOverlay(
                episode = next,
                secondsLeft = upNextSecondsLeft(state.livePosition, state.liveDuration),
                belowClock = state.showControls && !state.showChannelPanel,
                playNowFocus = upNextFocus,
                onFocusChanged = { upNextFocused = it },
                onPlayNow = { playUpNext(next) },
                onCancel = cancelUpNext,
            )
        }

        // Live TV's channel panel (LT3) — slides in from the right, where the preview docks it.
        channelPanel?.let { panel ->
            AnimatedVisibility(
                visible = state.showChannelPanel,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(CinemaBackground.copy(alpha = CinemaAlpha.tint))
                            .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
                ) {
                    TvGlassPanel(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(CHANNEL_PANEL_WIDTH_FRACTION)
                                .fillMaxHeight()
                                // Keep focus inside while open: this is an overlay in the
                                // player's own tree, so a D-pad press past the tab row or the
                                // last row would otherwise move on to whatever lies behind
                                // (AGENTS.md → In-tree overlays trap focus). Only while open: it
                                // animates out focused, and a cancelled exit would also block the
                                // player taking focus back on close. focusProperties directly
                                // before focusGroup, so the exit belongs to the group.
                                .focusProperties { onExit = { if (state.showChannelPanel) cancelFocusChange() } }
                                .focusGroup(),
                        backgroundAlpha = CHANNEL_PANEL_BACKGROUND_ALPHA,
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
                            panel { state.showChannelPanel = false }
                        }
                    }
                }
            }
        }
    }
}

/** Space between the lifted subtitles and the controls' panel, as a share of the screen's height. */
private const val SUBTITLE_GAP_FRACTION = 0.02f

/** The full-screen channel panel's share of the width, as the preview docks it. */
private const val CHANNEL_PANEL_WIDTH_FRACTION = 0.34f

/**
 * The panel's surface over video: near-opaque, so its rows read over any picture (at 0.5 a bright
 * frame showed through and the rows' text was lost in it).
 */
private const val CHANNEL_PANEL_BACKGROUND_ALPHA = 0.9f

/** Keys the "Up next" card's buttons, and the error's Retry and Back, get while they hold focus. */
private val UP_NEXT_CARD_KEYS =
    setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter)

/**
 * Autoplay next episode, per playing stream: the one whose "Up next" card was cancelled or used
 * ([dismissedFor] — it stays hidden for that episode), and the one a next episode is starting
 * from ([startingFrom]) until the new one plays. The player keeps reading the old episode, or
 * Ended, until then, which must neither show the card again nor run the end-of-episode logic.
 * Held by the route (TvPlayerScreen) because PlayerScreen is re-mounted while the next episode
 * loads.
 */
@Stable
class UpNextState {
    var dismissedFor by mutableStateOf<String?>(null)
    var startingFrom by mutableStateOf<String?>(null)
}
