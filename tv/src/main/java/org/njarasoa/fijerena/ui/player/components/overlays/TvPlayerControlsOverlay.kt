@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.domain.playerEpisodeName
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.formatEpochTime
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BadgedTitle
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaLive
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import java.util.Date

// Frame-count budget for the OSD's initial-focus retry loop — see its LaunchedEffect below.
private const val FOCUS_REQUEST_MAX_ATTEMPTS = 5

@Composable
fun TvPlayerControlsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    viewModel: PlaybackViewModel,
    livePosition: Long,
    liveDuration: Long,
    currentEpgProgram: EpgProgram?,
    nextEpgProgram: EpgProgram?,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    showFullControls: Boolean,
    hideTopBars: Boolean = false,
    // The resolution/codec line is for developers only (AppSettings.isDevMode, via PlayerScreenState).
    isDeveloperMode: Boolean = false,
    // Live TV in full screen: opens the channel panel (LT4). Null (VOD, the standalone route)
    // leaves the Channels button out.
    onShowChannels: (() -> Unit)? = null,
    // Live TV with a guide: opens the TV Guide on the playing channel (GD5). Null leaves Guide out.
    onShowGuide: (() -> Unit)? = null,
    // Catch-up: Watch live, first in the row. Null leaves it out.
    onWatchLive: (() -> Unit)? = null,
    onShowAudioTrackSelector: () -> Unit,
    onShowSubtitleSelector: () -> Unit,
    onShowQualitySelector: () -> Unit,
    onShowChapterSelector: () -> Unit,
    onShowStats: () -> Unit,
    pickerOpen: Boolean = false,
    scrubPositionMs: Long? = null,
    onScrubStep: (nativeEvent: android.view.KeyEvent, forward: Boolean) -> Unit = { _, _ -> },
    onCommitScrub: () -> Unit = {},
    nextEpisode: EpisodeItem? = null,
    onPlayNextEpisode: ((EpisodeItem) -> Unit)? = null,
    // The bottom panel's height in px, for the player to lift the subtitles above it.
    onPanelHeightChanged: (Int) -> Unit = {},
) {
    val isPaused = playbackState is PlaybackState.Paused
    val isLive = metadata.isLive
    // Memoize track counts keyed on metadata to avoid O(N) track iteration every recomposition (1 Hz clock tick)
    // Keyed on tracksVersion, not just metadata: metadata is set once at playStream() time,
    // before ExoPlayer typically resolves tracks, and (for Live TV especially) may never change
    // again for the rest of the session — leaving these counts permanently 0 and hiding the
    // audio/subtitle/quality buttons even once tracks actually loaded.
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycle()
    val audioTrackCount = remember(metadata, tracksVersion) { viewModel.getAudioTracks().size }
    val subtitleTrackCount = remember(metadata, tracksVersion) { viewModel.getSubtitleTracks().size }
    val qualityCount = remember(metadata, tracksVersion) { viewModel.getVideoQualities().size }

    var videoCodec by remember { mutableStateOf<String?>(null) }
    var videoResolution by remember { mutableStateOf<String?>(null) }

    // Extract resolution and codec periodically — developer mode only, the only time it is shown.
    LaunchedEffect(playbackState, metadata.streamUrl, isDeveloperMode) {
        if (isDeveloperMode && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering)) {
            // Keep checking every second as tracks might take time to load
            while (true) {
                StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                    val tracks = p.currentTracks
                    for (i in 0 until tracks.groups.size) {
                        val group = tracks.groups[i]
                        if (group.isSelected && group.type == C.TRACK_TYPE_VIDEO) {
                            for (j in 0 until group.length) {
                                if (group.isTrackSelected(j)) {
                                    val format = group.getTrackFormat(j)
                                    videoCodec = format.sampleMimeType?.substringAfter("/")?.uppercase()
                                    videoResolution =
                                        if (format.width > 0 && format.height > 0) "${format.width}x${format.height}" else null
                                    break
                                }
                            }
                        }
                    }
                }
                if (videoCodec != null && videoResolution != null) break
                delay(1000)
            }
        } else {
            videoCodec = null
            videoResolution = null
        }
    }

    // [controlsFocusRequester] is attached to the centre play/pause button, which is hidden for
    // live (there is nothing to pause) AND for VOD while playbackState is anything but
    // Playing/Paused (see the button's own composition guard below). Aiming at it before that
    // button exists targets a node that was never composed, so the request fails into a log
    // line and the button row below could not be reached by D-pad at all. [canFocusPlayPause]
    // tracks whether that button currently exists; while it doesn't, focus falls to
    // [safeIconFocusRequester] instead (see below). Keying the effect
    // on [canFocusPlayPause] means that once playback moves into Playing/Paused — e.g.
    // buffering finishes right as OSD is opened — focus is re-requested onto the play/pause
    // button, so a subsequent centre press pauses instead of activating an icon row button.
    val controlsFocusRequester = remember { FocusRequester() }
    val iconRowFocusRequester = remember { FocusRequester() }
    var isProgressBarFocused by remember { mutableStateOf(false) }
    val canFocusPlayPause = !isLive && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused)
    val showsPlayPause = showFullControls && canFocusPlayPause
    // VOD's D-pad column: Play/Pause → (Down) the seek bar → (Down) the first button of the row,
    // Up back the same way. Set explicitly: geometric search from the full-width seek bar picked
    // the button nearest the screen's centre (More), not the first.
    val seekBarFocusRequester = remember { FocusRequester() }
    val hasSeekBar = !isLive && liveDuration > 0

    // Default focus when landing on the button row must not go to [iconRowFocusRequester]'s
    // implicit first child — whichever button happens to be first for this stream. Live opens on
    // Channels (LT4): a stray second OK opens the channel panel, never favourites the channel.
    // Otherwise (VOD before canFocusPlayPause, live without the panel) it opens on More, which
    // only shows Stats — never on Favourite or a track picker.
    val channelsFocusRequester = remember { FocusRequester() }
    val moreFocusRequester = remember { FocusRequester() }
    val statsFocusRequester = remember { FocusRequester() }
    val safeIconFocusRequester = if (onShowChannels != null) channelsFocusRequester else moreFocusRequester
    // ⋮ More: Stats (rarely used) sits behind it, shown in the row after More while open.
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moreOpen) {
        if (moreOpen) statsFocusRequester.requestFocusWithRetry()
    }

    // When a track picker closes (Back or a choice), focus goes back to the button that opened it,
    // instead of being left on nothing once the picker's rows leave composition.
    val chapterFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    var pickerOpener by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(pickerOpen) {
        val opener = pickerOpener
        if (!pickerOpen && opener != null) {
            pickerOpener = null
            opener.requestFocusWithRetry(fallback = safeIconFocusRequester)
        }
    }

    // One-shot per OSD session: once focus has landed on the play/pause button, later
    // Playing<->Paused/Buffering flicker must not keep yanking focus away from wherever the
    // user has since navigated.
    var reachedPlayPauseFocus by remember { mutableStateOf(false) }
    LaunchedEffect(showFullControls) {
        if (showFullControls) {
            reachedPlayPauseFocus = false
            moreOpen = false
        }
    }

    // Live: re-keyed on the stream too — Up/Down zap with the OSD up (LT4), and the new channel's
    // track buttons can come and go under focus, so focus goes back to Channels on each zap.
    LaunchedEffect(showFullControls, isLive, canFocusPlayPause, metadata.streamUrl.takeIf { isLive }) {
        if (showFullControls && !reachedPlayPauseFocus) {
            val requester = if (isLive || !canFocusPlayPause) safeIconFocusRequester else controlsFocusRequester
            // requestFocus() fails (returns false) if its target isn't composed/laid out yet — the
            // play/pause button only composes in this same frame canFocusPlayPause flips true
            // (see its guard below), so on a slower recompose one frame's delay wasn't always
            // enough. A single fixed retry silently gave up when it still wasn't ready, leaving
            // focus wherever it was before the OSD opened — a subsequent OK press then hit
            // whatever that was instead of pausing, which is exactly the "center button sometimes
            // pauses, sometimes doesn't" symptom. Retry across frames instead of one fixed
            // retry; the frame cap just guards against a target that never composes at all.
            androidx.compose.runtime.withFrameMillis {}
            if (requester.requestFocusWithRetry(maxFrames = FOCUS_REQUEST_MAX_ATTEMPTS)) {
                if (canFocusPlayPause) reachedPlayPauseFocus = true
            } else {
                android.util.Log.e("TvPlayerControlsOverlay", "Failed to focus the controls after $FOCUS_REQUEST_MAX_ATTEMPTS frames")
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground.copy(alpha = CinemaAlpha.focusedTint)),
    ) {
        // Top: the clock, and the developer-mode resolution/codec line, on a soft gradient rather
        // than a band. Hidden while a side panel is open since they would collide with the panel's
        // own tab row.
        if (!hideTopBars) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(CinemaBackground.copy(alpha = CinemaAlpha.textMedium), Color.Transparent)),
                        ).padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
            ) {
                // Self-ticking so only this leaf recomposes each second.
                ClockDisplay(modifier = Modifier.align(Alignment.TopEnd))

                // Resolution and codec — developer mode only (the polling above is gated too).
                if (isDeveloperMode && (videoResolution != null || videoCodec != null)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        if (videoResolution != null) {
                            Text(
                                text = videoResolution!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = CinemaAccent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (videoCodec != null) {
                            CinemaBadge(
                                text = videoCodec!!,
                                backgroundColor = CinemaSurface.copy(alpha = CinemaAlpha.tint),
                                textColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        // Center: Play/Pause (VOD only, hidden for live). Also gated to Playing/Paused —
        // showFullControls is driven purely by the OK-key toggle, with no gate on playback state,
        // so pressing OK during Buffering/Error/Ended/Idle used to land this button directly on
        // top of PlayerScreen's own centered content for that state (BufferingContent(),
        // ErrorContent(), ...). An allowlist closes all of them at once. Mobile's equivalent overlay uses the same allowlist for the same reason.
        // The rest of this panel (title, description, audio/subtitle/quality selectors) stays
        // visible regardless — only this button collides with another state's centered content.
        if (showsPlayPause) {
            CinemaButton(
                onClick = {
                    if (isPaused) viewModel.resume() else viewModel.pause()
                },
                colors =
                    ButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = CinemaTextPrimary,
                        focusedContainerColor = CinemaTextPrimary,
                        focusedContentColor = CinemaBackground,
                    ),
                modifier =
                    Modifier
                        .align(Center)
                        .size(TvDimensions.iconButtonSizeLarge)
                        .focusRequester(controlsFocusRequester)
                        .moveFocusOn(Key.DirectionDown, if (hasSeekBar) seekBarFocusRequester else iconRowFocusRequester),
            ) {
                Icon(
                    imageVector = if (isPaused) CinemaIcons.PlayArrow else CinemaIcons.Pause,
                    contentDescription = if (isPaused) stringResource(R.string.player_resume) else stringResource(R.string.player_pause),
                    modifier = Modifier.size(TvDimensions.iconXLarge),
                )
            }
        }

        // Bottom section: title, progress/EPG info + icon controls. Spans full width, so when a
        // side panel is open this needs to be opaque enough to fully mask its channel list rather
        // than letting it ghost through at the usual, lighter glass alpha. Its height goes to the
        // player, which lifts the subtitles above it.
        TvGlassPanel(
            modifier =
                Modifier
                    .align(BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { onPanelHeightChanged(it.height) },
            backgroundAlpha = if (hideTopBars) 0.92f else 0.6f,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            ) {
                // The title, the same size on VOD and live. VOD: the film, or "Show · S01E02 ·
                // Episode". Live (LT4): LIVE · the channel name, the one title — this banner, not a
                // top bar, so it is still there when hideTopBars is set.
                // TODO: the channel number goes before the name once one reaches the player.
                Row(
                    verticalAlignment = CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (isLive) {
                        Box(
                            modifier =
                                Modifier
                                    .size(TvDimensions.statsDotSize)
                                    .background(CinemaLive, shape = CircleShape),
                        )
                        Text(
                            text = stringResource(R.string.player_live),
                            style = MaterialTheme.typography.labelLarge,
                            color = CinemaTextPrimary,
                        )
                        BadgedTitle(
                            raw = metadata.channelName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = CinemaTextPrimary,
                            modifier = Modifier.padding(start = Spacing.xs),
                            textModifier = Modifier.bounceMarquee(),
                        )
                    } else if (metadata.catchupLabel != null) {
                        // Catch-up: CATCH-UP · the programme · its channel and day, time.
                        Icon(
                            imageVector = CinemaIcons.Replay,
                            contentDescription = null,
                            tint = CinemaAccentLight,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                        )
                        Text(
                            text = stringResource(R.string.player_catchup),
                            style = MaterialTheme.typography.labelLarge,
                            color = CinemaTextPrimary,
                        )
                        BadgedTitle(
                            raw = metadata.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = CinemaTextPrimary,
                            modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.xs),
                            textModifier = Modifier.bounceMarquee(),
                        )
                        Text(
                            text = "· ${metadata.catchupLabel}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else {
                        // Some providers' episode titles already embed the show name and number
                        // ("EN - Show - S01E22 - Pilot"): only the episode's own name is shown, and
                        // none when the title is just the show and the number.
                        val episodeLine =
                            metadata.showTitle?.let {
                                val episodeName = playerEpisodeName(metadata.title).takeIf { name -> name.isNotBlank() }
                                listOfNotNull(metadata.episodeLabel, episodeName).joinToString(" · ")
                            }
                        BadgedTitle(
                            raw = metadata.showTitle ?: metadata.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = CinemaTextPrimary,
                            modifier = Modifier.weight(1f, fill = false),
                            textModifier = Modifier.bounceMarquee(),
                        )
                        if (!episodeLine.isNullOrBlank()) {
                            Text(
                                text = "· $episodeLine",
                                style = MaterialTheme.typography.headlineSmall,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }

                // The plot (or the channel's description), secondary and short: two lines at most.
                val description = metadata.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xxs),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))

                if (!isLive) {
                    val position = livePosition
                    val duration = liveDuration

                    if (duration > 0) {
                        val isScrubbing = scrubPositionMs != null
                        val displayPosition = scrubPositionMs ?: position
                        val seekBarLit = isProgressBarFocused || isScrubbing
                        // The seek bar: a focus stop between Play/Pause and the buttons. Focused,
                        // it is outlined and thicker, like a focused row; the box keeps one height
                        // so the rows below never shift.
                        Box(
                            contentAlignment = Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(seekBarFocusRequester)
                                    .focusable(enabled = showFullControls)
                                    .onFocusChanged { isProgressBarFocused = it.isFocused }
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                // Same cursor as the hidden-OSD D-pad scrub: Left/Right
                                                // move it, OK commits, Back cancels.
                                                Key.DirectionLeft -> {
                                                    onScrubStep(event.nativeKeyEvent, false)
                                                    true
                                                }

                                                Key.DirectionRight -> {
                                                    onScrubStep(event.nativeKeyEvent, true)
                                                    true
                                                }

                                                Key.DirectionCenter, Key.Enter -> {
                                                    if (scrubPositionMs != null) {
                                                        onCommitScrub()
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }

                                                Key.DirectionUp -> {
                                                    showsPlayPause && controlsFocusRequester.requestFocus()
                                                }

                                                Key.DirectionDown -> {
                                                    iconRowFocusRequester.requestFocus()
                                                }

                                                else -> {
                                                    false
                                                }
                                            }
                                        } else {
                                            false
                                        }
                                    }.border(
                                        width = TvFocusTokens.focusBorderWidth,
                                        color = if (seekBarLit) TvFocusTokens.focusedRowOutline else Color.Transparent,
                                        shape = RoundedCornerShape(CornerRadius.small),
                                    ).padding(Spacing.xs),
                        ) {
                            LinearProgressIndicator(
                                progress = { displayPosition.toFloat() / duration.toFloat() },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(if (seekBarLit) Spacing.xs else TvDimensions.progressBar),
                                color = if (seekBarLit) CinemaAccentLight else MaterialTheme.colorScheme.primary,
                                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                        }

                        // Position · remaining and end time (the seek hint while scrubbing) · duration.
                        val remainingTime = duration - displayPosition
                        val estimatedEndTimeMillis = remember(remainingTime) { System.currentTimeMillis() + remainingTime }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = CenterVertically,
                        ) {
                            Text(
                                text = formatTime(displayPosition),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isScrubbing) CinemaAccentLight else CinemaTextPrimary,
                                fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text =
                                    if (isScrubbing) {
                                        stringResource(R.string.player_seek_hint)
                                    } else {
                                        stringResource(
                                            R.string.player_remaining_ends_at_format,
                                            formatTime(remainingTime),
                                            TimeFormat.formatClockTime(Date(estimatedEndTimeMillis)),
                                        )
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextPrimary,
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                } else if (currentEpgProgram != null) {
                    // Live: Now with its times and the programme's progress, then Next.
                    val epgContext = LocalContext.current
                    Text(
                        text =
                            stringResource(
                                R.string.player_now_playing_format,
                                currentEpgProgram.title,
                                formatEpochTime(epgContext, currentEpgProgram.startTime),
                                formatEpochTime(epgContext, currentEpgProgram.endTime),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Programme progress bar — keyed on livePosition to avoid untracked System.currentTimeMillis() reads
                    val nowEpoch = remember(livePosition) { System.currentTimeMillis() / 1000 }
                    val epgProgress =
                        if (currentEpgProgram.duration > 0) {
                            ((nowEpoch - currentEpgProgram.startTime).toFloat() / currentEpgProgram.duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    LinearProgressIndicator(
                        progress = { epgProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs)
                                .height(TvDimensions.progressBar),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                    )
                    if (nextEpgProgram != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.player_osd_next_format,
                                    nextEpgProgram.title,
                                    formatEpochTime(epgContext, nextEpgProgram.startTime),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }

                // Button row (only when full controls are visible). Icon buttons, each naming itself
                // while focused. Live: Channels, Guide, ★, Subtitles, Audio, Quality, ⋮ More (Stats);
                // VOD the same without Channels, with Chapters first and Next episode before More.
                // Left/Right move along it and stop at its ends; Up/Down zap on live
                // (PlayerKeyHandler); on VOD Up goes back to the seek bar (or Play/Pause).
                if (showFullControls) {
                    val rowUp =
                        when {
                            isLive -> null
                            hasSeekBar -> seekBarFocusRequester
                            showsPlayPause -> controlsFocusRequester
                            else -> null
                        }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .moveFocusOn(Key.DirectionUp, rowUp)
                                .focusRequester(iconRowFocusRequester)
                                // Left/Right past either end stay on the row instead of dropping
                                // focus (L-6). Directly before focusGroup, so the exit is the group's.
                                .focusProperties {
                                    onExit = {
                                        if (requestedFocusDirection == FocusDirection.Left ||
                                            requestedFocusDirection == FocusDirection.Right
                                        ) {
                                            cancelFocusChange()
                                        }
                                    }
                                }.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = CenterVertically,
                    ) {
                        if (onWatchLive != null) {
                            OsdButton(
                                icon = CinemaIcons.LiveTv,
                                label = stringResource(R.string.player_osd_watch_live),
                                onClick = onWatchLive,
                            )
                        }

                        if (onShowChannels != null) {
                            OsdButton(
                                icon = CinemaIcons.LiveTv,
                                label = stringResource(R.string.player_osd_channels),
                                onClick = onShowChannels,
                                modifier = Modifier.focusRequester(channelsFocusRequester),
                            )
                        }

                        // Guide (GD5): after Channels, only when the source has a guide.
                        if (onShowGuide != null) {
                            OsdButton(
                                icon = CinemaIcons.DateRange,
                                label = stringResource(R.string.player_osd_guide),
                                onClick = onShowGuide,
                            )
                        }

                        val chapters = remember(metadata) { viewModel.getChapters() }
                        if (chapters.isNotEmpty()) {
                            OsdButton(
                                icon = CinemaIcons.List,
                                label = stringResource(R.string.player_chapters),
                                onClick = {
                                    pickerOpener = chapterFocusRequester
                                    onShowChapterSelector()
                                },
                                modifier = Modifier.focusRequester(chapterFocusRequester),
                            )
                        }

                        if (onToggleFavorite != null) {
                            OsdButton(
                                icon = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                label = stringResource(if (isFavorite) R.string.player_favorited else R.string.player_favorite),
                                onClick = onToggleFavorite,
                                iconTint = if (isFavorite) CinemaAccent else null,
                            )
                        }

                        if (subtitleTrackCount > 0) {
                            OsdButton(
                                icon = CinemaIcons.Subtitles,
                                label = stringResource(R.string.player_subtitles),
                                onClick = {
                                    pickerOpener = subtitleFocusRequester
                                    onShowSubtitleSelector()
                                },
                                modifier = Modifier.focusRequester(subtitleFocusRequester),
                            )
                        }

                        if (audioTrackCount > 1) {
                            OsdButton(
                                icon = CinemaIcons.VolumeUp,
                                label = stringResource(R.string.player_audio),
                                onClick = {
                                    pickerOpener = audioFocusRequester
                                    onShowAudioTrackSelector()
                                },
                                modifier = Modifier.focusRequester(audioFocusRequester),
                            )
                        }

                        if (qualityCount > 1) {
                            OsdButton(
                                icon = CinemaIcons.Tune,
                                label = stringResource(R.string.player_quality),
                                onClick = {
                                    pickerOpener = qualityFocusRequester
                                    onShowQualitySelector()
                                },
                                modifier = Modifier.focusRequester(qualityFocusRequester),
                            )
                        }

                        // Next episode button (only for TV show episodes when progress >= 80% and a next episode exists)
                        val progressRatio = if (liveDuration > 0) livePosition.toFloat() / liveDuration.toFloat() else 0f
                        val isOver80Percent = progressRatio >= 0.80f
                        if (isOver80Percent && nextEpisode != null && onPlayNextEpisode != null) {
                            OsdButton(
                                icon = CinemaIcons.SkipNext,
                                label = stringResource(R.string.player_osd_next_episode),
                                onClick = { onPlayNextEpisode(nextEpisode) },
                            )
                        }

                        OsdButton(
                            icon = CinemaIcons.MoreVert,
                            label = stringResource(R.string.player_osd_more),
                            onClick = { moreOpen = !moreOpen },
                            modifier = Modifier.focusRequester(moreFocusRequester),
                            iconTint = if (moreOpen) CinemaAccentLight else null,
                        )

                        if (moreOpen) {
                            OsdButton(
                                icon = CinemaIcons.BarChart,
                                label = stringResource(R.string.player_stats),
                                onClick = onShowStats,
                                modifier = Modifier.focusRequester(statsFocusRequester),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One OSD button: [TvIconAction], the icon button used on details and across the app — its label
 * shows beside the icon while focused. [iconTint] marks a state (a favourite, More open).
 */
@Composable
private fun OsdButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
) {
    TvIconAction(onClick = onClick, icon = icon, label = label, modifier = modifier, iconTint = iconTint)
}

/** [key] (on KeyDown) moves focus to [target]; no-op when [target] is null or can't take focus. */
private fun Modifier.moveFocusOn(
    key: Key,
    target: FocusRequester?,
): Modifier =
    if (target == null) {
        this
    } else {
        onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && event.key == key && target.requestFocus() }
    }

@Composable
private fun ClockDisplay(modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000L)
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val ignored = tick // Read to trigger recomposition
    Text(
        text = TimeFormat.formatClockTime(Date(tick)),
        style = MaterialTheme.typography.titleMedium,
        color = CinemaTextPrimary,
        modifier = modifier,
    )
}
