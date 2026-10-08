package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaLive
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileControlsOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    viewModel: PlaybackViewModel,
    isLive: Boolean,
    isDeveloperMode: Boolean,
    isFavorite: Boolean,
    livePosition: Long,
    liveDuration: Long,
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
    onRewind: (() -> Unit)? = null,
    nextEpisode: EpisodeItem? = null,
    onPlayNextEpisode: ((EpisodeItem) -> Unit)? = null,
    // The bottom panel's height in px, for the player to lift the subtitles above it.
    onPanelHeightChanged: (Int) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

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

    // Info popover (title + synopsis) — off by default. Other mobile players (and this app's own
    // TMDB-backed detail pages) don't keep a synopsis on screen during playback; it's opt-in via
    // an info icon instead, which is also what removes any risk of it colliding with the
    // transport controls floating over the video.
    var showInfo by remember { mutableStateOf(false) }

    // Extract resolution and codec periodically
    LaunchedEffect(playbackState, metadata.streamUrl) {
        if (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering) {
            // Keep checking every second as tracks might take time to load
            while (true) {
                StreamingPlaybackService.getInstance()?.getPlayer()?.let { p ->
                    val tracks = p.currentTracks
                    for (i in 0 until tracks.groups.size) {
                        val group = tracks.groups[i]
                        if (group.isSelected && group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
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

    val typography = MaterialTheme.typography

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground.copy(alpha = CinemaAlpha.tint))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
    ) {
        // Top bar, transport controls, and bottom bar stack in one Column spanning the full
        // height, with the transport-controls region taking the leftover space via weight(1f) —
        // its bounds can never eat into the top bar's or bottom bar's, regardless of how tall the
        // bottom bar (e.g. Live TV's EPG block) gets. No synopsis competes for this space (see
        // showInfo above), so nothing here has to share it with anything else either.
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: the resolution/codec line and the clock. The title sits in the bottom
            // panel, above the seek bar. Padded clear of a display cutout; the system bars are
            // hidden while the player is up (see MobilePlayerContent).
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        .padding(CinemaSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = CinemaSpacing.xs),
                ) {
                    if (videoResolution != null || videoCodec != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = Spacing.xxxs),
                        ) {
                            if (videoResolution != null) {
                                Text(
                                    text = videoResolution!!,
                                    style = typography.labelSmall,
                                    color = CinemaAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (videoCodec != null) {
                                CinemaBadge(
                                    text = videoCodec!!,
                                    backgroundColor = CinemaSurface.copy(alpha = CinemaAlpha.tint),
                                    textColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                    style = typography.labelSmall.copy(fontSize = 10.sp),
                                )
                            }
                        }
                    }
                }
                // Clock — self-ticking so only this leaf recomposes each second
                ClockDisplay()
            }

            // Rewind | Play/Pause | FastForward, floating plainly over the video — the pattern
            // every mobile player uses: bare icons, no background pill, centred in whatever space
            // is left between the top bar and the bottom bar. Nothing else shares this space, so
            // it can never be pushed or clipped by anything growing elsewhere.
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onRewind != null) {
                        IconButton(
                            onClick = onRewind,
                            modifier = Modifier.size(MobileDimensions.iconPlayContainer),
                        ) {
                            Icon(
                                imageVector = CinemaIcons.FastRewind,
                                contentDescription = stringResource(R.string.player_rewind_1min_description),
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(MobileDimensions.iconLarge),
                            )
                        }
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(MobileDimensions.iconPlayContainer),
                    ) {
                        Icon(
                            imageVector =
                                if (playbackState is PlaybackState.Paused) {
                                    CinemaIcons.PlayArrow
                                } else {
                                    CinemaIcons.Pause
                                },
                            contentDescription =
                                stringResource(
                                    if (playbackState is PlaybackState.Paused) R.string.player_play else R.string.player_pause,
                                ),
                            tint = CinemaTextPrimary,
                            modifier = Modifier.size(MobileDimensions.iconPlayIcon),
                        )
                    }
                    if (onFastForward != null) {
                        IconButton(
                            onClick = onFastForward,
                            modifier = Modifier.size(MobileDimensions.iconPlayContainer),
                        ) {
                            Icon(
                                imageVector = CinemaIcons.FastForward,
                                contentDescription = stringResource(R.string.player_fast_forward_5min_description),
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(MobileDimensions.iconLarge),
                            )
                        }
                    }
                }
            }

            // Bottom bar: the title (with "Ends at" on VOD), then the scrubber + time row (live:
            // Now and Next), then a compact icon row — a slim strip along the bottom, not a tall
            // stack that could eat a quarter of the screen on its own. Its height goes to the
            // player, which lifts the subtitles above it.
            GlassPanel(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { onPanelHeightChanged(it.height) },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
                ) {
                    val position = livePosition
                    val duration = liveDuration
                    val hasSeekBar = !isLive && duration > 0

                    // The title, one line. VOD: the film, or "Show · S01E02 · Episode". Live:
                    // LIVE · the channel name.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLive) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(MobileDimensions.liveDotSize)
                                            .background(CinemaLive, shape = CircleShape),
                                )
                                Text(
                                    text = stringResource(R.string.player_live),
                                    style = typography.labelLarge,
                                    color = CinemaTextPrimary,
                                )
                                BadgedTitle(
                                    raw = metadata.title,
                                    style = typography.titleMedium,
                                    color = CinemaTextPrimary,
                                    modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.xxs),
                                )
                            } else {
                                // Some providers' episode titles already embed the show name and
                                // number ("A+ - Silo (2023) (US) - S03E01 - Who Are You?"): only the
                                // episode's own name is shown, none when the title is just the show
                                // and the number.
                                val episodeLine =
                                    metadata.showTitle?.let {
                                        val episodeName = playerEpisodeName(metadata.title).takeIf { name -> name.isNotBlank() }
                                        listOfNotNull(metadata.episodeLabel, episodeName).joinToString(" · ")
                                    }
                                BadgedTitle(
                                    raw = metadata.showTitle ?: metadata.title,
                                    style = typography.titleMedium,
                                    color = CinemaTextPrimary,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (!episodeLine.isNullOrBlank()) {
                                    Text(
                                        text = "· $episodeLine",
                                        style = typography.titleMedium,
                                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                        if (hasSeekBar) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.movie_ends_at_format,
                                        org.njarasoa.fijerena.core.ui.theme.TimeFormat
                                            .formatClockTime(Date(System.currentTimeMillis() + (duration - position))),
                                    ),
                                style = typography.bodySmall,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                maxLines = 1,
                            )
                        }
                    }

                    // VOD progress bar and time info
                    if (hasSeekBar) {
                        var isSeeking by remember { mutableStateOf(false) }
                        var seekPosition by remember { mutableStateOf(0f) }
                        var lastHapticSecond by remember { mutableStateOf(-1L) }

                        val scrubberColors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                        val scrubberInteractionSource = remember { MutableInteractionSource() }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTime(position),
                                style = typography.bodySmall,
                                color = CinemaTextPrimary,
                            )
                            Slider(
                                value = if (isSeeking) seekPosition else position.toFloat() / duration.toFloat(),
                                onValueChange = { newValue ->
                                    isSeeking = true
                                    seekPosition = newValue
                                    // One tick per second crossed while dragging, not per pixel —
                                    // Slider fires onValueChange continuously during a drag.
                                    val newSecond = (newValue * duration).toLong() / 1000
                                    if (newSecond != lastHapticSecond) {
                                        lastHapticSecond = newSecond
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onValueChangeFinished = {
                                    val newPositionMs = (seekPosition * duration).toLong()
                                    viewModel.seekTo(newPositionMs)
                                    isSeeking = false
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = CinemaSpacing.xs),
                                colors = scrubberColors,
                                interactionSource = scrubberInteractionSource,
                                // Default thumb is a 4dp-wide bar — round and enlarge it so it
                                // reads as a draggable knob, not a thin tick mark.
                                thumb = {
                                    SliderDefaults.Thumb(
                                        interactionSource = scrubberInteractionSource,
                                        colors = scrubberColors,
                                        thumbSize =
                                            DpSize(
                                                MobileDimensions.playerScrubberThumbSize,
                                                MobileDimensions.playerScrubberThumbSize,
                                            ),
                                    )
                                },
                            )
                            Text(
                                text = formatTime(duration),
                                style = typography.bodySmall,
                                color = CinemaTextPrimary,
                            )
                        }
                    } else if (isLive && currentEpgProgram != null) {
                        // Live: Now with its times and the programme's progress, then Next — one
                        // line each, so this can't grow past what a compact bottom bar can afford.
                        Column(modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.xs)) {
                            val epgContext = LocalContext.current
                            val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                            val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                            Text(
                                text = stringResource(R.string.player_now_playing_format, currentEpgProgram.title, nowStart, nowEnd),
                                style = typography.bodySmall,
                                color = CinemaTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Programme progress bar — keyed on livePosition to avoid untracked System.currentTimeMillis() reads
                            val nowEpoch = remember(livePosition) { System.currentTimeMillis() / 1000 }
                            val epgProgress =
                                if (currentEpgProgram.duration > 0) {
                                    ((nowEpoch - currentEpgProgram.startTime).toFloat() / currentEpgProgram.duration.toFloat())
                                        .coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            LinearProgressIndicator(
                                progress = { epgProgress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.xxs)
                                        .height(Spacing.xxxs),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                            if (nextEpgProgram != null) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.player_up_next_format,
                                            nextEpgProgram.title,
                                            formatEpochTime(epgContext, nextEpgProgram.startTime),
                                        ),
                                    style = typography.bodySmall,
                                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Control buttons row (horizontally scrollable icons)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (audioTrackCount > 1) {
                            CinemaIconButton(
                                onClick = onAudioTrack,
                                icon = {
                                    Icon(CinemaIcons.VolumeUp, stringResource(R.string.player_audio), tint = CinemaTextPrimary)
                                },
                            )
                        }

                        if (subtitleTrackCount > 0) {
                            CinemaIconButton(
                                onClick = onSubtitle,
                                icon = {
                                    Icon(CinemaIcons.Subtitles, stringResource(R.string.player_subtitles), tint = CinemaTextPrimary)
                                },
                            )
                        }

                        if (qualityCount > 1) {
                            CinemaIconButton(
                                onClick = onQuality,
                                icon = {
                                    Icon(CinemaIcons.Tune, stringResource(R.string.player_quality), tint = CinemaTextPrimary)
                                },
                            )
                        }

                        CinemaIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleFavorite()
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                    contentDescription =
                                        stringResource(
                                            if (isFavorite) R.string.player_remove_favorite else R.string.player_add_favorite,
                                        ),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                                )
                            },
                        )

                        // Stats for nerds (always visible)
                        CinemaIconButton(
                            onClick = onStats,
                            icon = {
                                Icon(CinemaIcons.BarChart, stringResource(R.string.player_stats), tint = CinemaTextPrimary)
                            },
                        )

                        // Info: title + synopsis popover (only if there's a synopsis to show)
                        if (!metadata.description.isNullOrBlank()) {
                            CinemaIconButton(
                                onClick = { showInfo = true },
                                icon = {
                                    Icon(CinemaIcons.Info, stringResource(R.string.player_info), tint = CinemaTextPrimary)
                                },
                            )
                        }

                        // Next episode button (only for TV show episodes when progress >= 80% and a next episode exists)
                        val progressRatio = if (liveDuration > 0) livePosition.toFloat() / liveDuration.toFloat() else 0f
                        val isOver80Percent = progressRatio >= 0.80f
                        if (isOver80Percent && nextEpisode != null && onPlayNextEpisode != null) {
                            CinemaIconButton(
                                onClick = { onPlayNextEpisode(nextEpisode) },
                                icon = {
                                    Icon(
                                        CinemaIcons.SkipNext,
                                        contentDescription = stringResource(R.string.player_next_episode),
                                        tint = CinemaTextPrimary,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // Info popover: title + synopsis, on demand. A full-screen scrim behind the card closes
        // it on tap — this is the LAST child of the outer Box, so it sits above everything else
        // and intercepts that tap before it reaches the play/pause-toggle handler underneath.
        if (showInfo) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(CinemaBackground.copy(alpha = CinemaAlpha.scrim))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showInfo = false },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = MobileDimensions.statsOverlayMaxWidth * 1.5f)
                            .padding(CinemaSpacing.xl)
                            .background(
                                color = CinemaSurface.copy(alpha = CinemaAlpha.scrim),
                                shape = RoundedCornerShape(Spacing.sm),
                            ).padding(CinemaSpacing.lg),
                ) {
                    Column {
                        BadgedTitle(raw = metadata.title, style = typography.titleMedium, color = CinemaTextPrimary)
                        metadata.description?.let { description ->
                            Text(
                                text = description,
                                style = typography.bodyMedium,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                                modifier = Modifier.padding(top = CinemaSpacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockDisplay() {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000L)
        }
    }
    Text(
        text =
            org.njarasoa.fijerena.core.ui.theme.TimeFormat
                .formatClockTime(Date(tick)),
        style = MaterialTheme.typography.titleMedium,
        color = CinemaTextPrimary,
    )
}
