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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.domain.EpisodeItem
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.model.formatEpochTime
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.AdaptiveLogoImage
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
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

    // State for resolution and codec
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
                // If we found both, we can stop polling for this stream state
                if (videoCodec != null && videoResolution != null) break
                delay(1000)
            }
        } else {
            videoCodec = null
            videoResolution = null
        }
    }

    // Focus requester for the first focusable control.
    //
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
        // Clock in top-right corner — self-ticking so only this leaf recomposes each second.
        // Hidden while a side panel is open since it would collide with the last-watched
        // panel's own top-right-ish title.
        if (!hideTopBars) {
            ClockDisplay(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
            )
        }

        // Top bar: VOD's title, and the developer-mode resolution/codec line. Hidden while a side
        // panel is open since it would collide with the panel's own tab row.
        if (!hideTopBars) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
            ) {
                // Big title treatment for VOD (movies get the show's own title big; episodes get
                // the series title big with the episode demoted to a subtitle line). Live TV's one
                // title is the channel name in the banner at the bottom (LT4) — for live, title and
                // channel name are the same string, which used to show here twice.
                if (!isLive) {
                    val bigTitle = metadata.showTitle ?: metadata.title
                    val logoUrl = metadata.logoUrl
                    if (logoUrl != null) {
                        AdaptiveLogoImage(
                            logoUrl = logoUrl,
                            contentDescription = bigTitle,
                            modifier = Modifier.height(TvDimensions.osdLogoHeight),
                        )
                    } else {
                        // No TMDB logo art for this title — fall back to a stylized gradient
                        // rendering of the title text instead.
                        Text(
                            text = bigTitle,
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    brush = Brush.linearGradient(listOf(CinemaAccent, CinemaTextPrimary)),
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.bounceMarquee(),
                        )
                    }
                    if (metadata.showTitle != null) {
                        // Some providers' episode titles already embed the show name — the real
                        // name is consistently the last " - "-separated segment, so take that; a
                        // clean title (no " - " in it, the common case) passes through unchanged.
                        val episodeName = metadata.title.substringAfterLast(" - ").takeIf { it.isNotBlank() }
                        val episodeSubtitle = listOfNotNull(metadata.episodeLabel, episodeName).joinToString(" - ")
                        if (episodeSubtitle.isNotBlank()) {
                            Text(
                                text = episodeSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.bounceMarquee(),
                            )
                        }
                    }
                }

                // Resolution and codec — developer mode only (the polling above is gated too).
                if (isDeveloperMode && (videoResolution != null || videoCodec != null)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.xs),
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
        // ErrorContent(), ...). An earlier version of this fix excluded only Buffering and missed
        // Error the same way; allowlisting Playing/Paused closes all of them at once instead of
        // one at a time. Mobile's equivalent overlay uses the same allowlist for the same reason.
        // The rest of this panel (title, description, audio/subtitle/quality selectors) stays
        // visible regardless — only this button collides with another state's centered content.
        if (showFullControls && !isLive && (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Paused)) {
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
                        .focusRequester(controlsFocusRequester),
            ) {
                Icon(
                    imageVector = if (isPaused) CinemaIcons.PlayArrow else CinemaIcons.Pause,
                    contentDescription = if (isPaused) stringResource(R.string.player_resume) else stringResource(R.string.player_pause),
                    modifier = Modifier.size(TvDimensions.iconXLarge),
                )
            }
        }

        // Bottom section: progress/EPG info + icon controls. Spans full width, so when a side
        // panel is open this needs to be opaque enough to fully mask its channel list rather
        // than letting it ghost through at the usual, lighter glass alpha.
        TvGlassPanel(
            modifier =
                Modifier
                    .align(BottomCenter)
                    .fillMaxWidth(),
            backgroundAlpha = if (hideTopBars) 0.92f else 0.6f,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            ) {
                // Stream description (above the progress / EPG section). Shown for both VOD and Live
                // whenever the OSD is visible — TV parity with mobile.
                val description = metadata.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.sm),
                    )
                }

                // VOD progress bar and time info
                if (!isLive) {
                    val position = livePosition
                    val duration = liveDuration

                    if (duration > 0) {
                        val isScrubbing = scrubPositionMs != null
                        val displayPosition = scrubPositionMs ?: position
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
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

                                                else -> {
                                                    false
                                                }
                                            }
                                        } else {
                                            false
                                        }
                                    }.then(
                                        if (isProgressBarFocused || isScrubbing) {
                                            Modifier.border(
                                                width = TvDimensions.borderFocused,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(CornerRadius.small),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ).padding(vertical = if (isProgressBarFocused || isScrubbing) Spacing.xs else Spacing.none),
                        ) {
                            LinearProgressIndicator(
                                progress = { displayPosition.toFloat() / duration.toFloat() },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(if (isProgressBarFocused || isScrubbing) Spacing.xs else TvDimensions.progressBar),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(displayPosition),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isScrubbing) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                                fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextPrimary,
                            )
                        }

                        // Remaining time + estimated end time, grouped together at the right.
                        val remainingTime = duration - displayPosition
                        val estimatedEndTimeMillis = remember(remainingTime) { System.currentTimeMillis() + remainingTime }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.player_remaining_ends_at_format,
                                        formatTime(remainingTime),
                                        TimeFormat.formatClockTime(Date(estimatedEndTimeMillis)),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaAccent,
                            )
                        }

                        if (isScrubbing) {
                            Text(
                                text = stringResource(R.string.player_seek_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = Spacing.xxs),
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                } else {
                    // The live banner (LT4): LIVE · channel name — the one title — then Now with
                    // the programme's progress and Next, when the guide has them. It lives here,
                    // not in the top bar, so it is still there when hideTopBars is set — this
                    // bottom section is the only thing shown while a side panel is open.
                    // TODO: the channel number goes before the name once one reaches the player.
                    Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                        Row(
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(TvDimensions.statsDotSize)
                                        .background(
                                            org.njarasoa.fijerena.ui.theme.CinemaLive,
                                            shape =
                                                RoundedCornerShape(
                                                    TvDimensions.statsDotSize / 2,
                                                ),
                                        ),
                            )
                            Text(
                                text = stringResource(R.string.player_live),
                                style = MaterialTheme.typography.labelLarge,
                                color = CinemaTextPrimary,
                            )
                            Text(
                                text = metadata.channelName,
                                style = MaterialTheme.typography.titleLarge,
                                color = CinemaTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = Spacing.xs).bounceMarquee(),
                            )
                        }
                        if (currentEpgProgram != null) {
                            val epgContext = LocalContext.current
                            val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                            val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                            Text(
                                text =
                                    stringResource(
                                        R.string.player_now_playing_format,
                                        currentEpgProgram.title,
                                        nowStart,
                                        nowEnd,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                modifier = Modifier.padding(top = Spacing.xxs),
                            )
                            // Programme progress bar — keyed on livePosition to avoid untracked System.currentTimeMillis() reads
                            val nowEpoch = remember(livePosition) { System.currentTimeMillis() / 1000 }
                            val epgProgress =
                                if (currentEpgProgram.duration > 0) {
                                    ((nowEpoch - currentEpgProgram.startTime).toFloat() / currentEpgProgram.duration.toFloat()).coerceIn(
                                        0f,
                                        1f,
                                    )
                                } else {
                                    0f
                                }
                            LinearProgressIndicator(
                                progress = { epgProgress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = Spacing.xxs)
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                            }
                        }
                    }
                }

                // Button row (only when full controls are visible). Every button carries its label
                // (LT4). Live: Channels, ★, Subtitles, Audio, Quality, ⋮ More (Stats); VOD the same
                // without Channels, with Chapters first and Next episode before More. Left/Right
                // move along it and stop at its ends; Up/Down zap on live (PlayerKeyHandler).
                if (showFullControls) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
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
                        if (onShowChannels != null) {
                            OsdButton(
                                icon = CinemaIcons.LiveTv,
                                label = stringResource(R.string.player_osd_channels),
                                onClick = onShowChannels,
                                modifier = Modifier.focusRequester(channelsFocusRequester),
                            )
                        }

                        // TODO(GD5): the Guide button goes here, after Channels and only when the
                        // channel has guide data, once a callback to open the guide reaches the player.

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
                                icon = if (isFavorite) CinemaIcons.Favorite else CinemaIcons.FavoriteBorder,
                                label = stringResource(if (isFavorite) R.string.player_favorited else R.string.player_favorite),
                                onClick = onToggleFavorite,
                                active = isFavorite,
                                iconTint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Unspecified,
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
                            active = moreOpen,
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

/** One OSD button: icon and its label, always shown (LT4). */
@Composable
private fun OsdButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    iconTint: Color = Color.Unspecified,
) {
    CinemaButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.colors(
                containerColor =
                    if (active) {
                        CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                    } else {
                        CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                    },
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaTextPrimary,
                focusedContentColor = CinemaBackground,
            ),
    ) {
        // The label says what the button is; the icon needs no description of its own.
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
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
