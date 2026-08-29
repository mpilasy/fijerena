@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.tv.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.C
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.core.player.model.formatEpochTime
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import java.util.Date
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

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
    onShowAudioTrackSelector: () -> Unit,
    onShowSubtitleSelector: () -> Unit,
    onShowQualitySelector: () -> Unit,
    onShowChapterSelector: () -> Unit,
    onShowStats: () -> Unit,
    seekSpeedLabel: String? = null,
    scrubPositionMs: Long? = null,
    onCommitScrub: (Long) -> Unit = {},
) {
    val isPaused = playbackState is PlaybackState.Paused
    val isLive = metadata.isLive
    // Memoize track counts keyed on metadata to avoid O(N) track iteration every recomposition (1 Hz clock tick)
    val audioTrackCount = remember(metadata) { viewModel.getAudioTracks().size }
    val subtitleTrackCount = remember(metadata) { viewModel.getSubtitleTracks().size }
    val qualityCount = remember(metadata) { viewModel.getVideoQualities().size }

    // State for resolution and codec
    var videoCodec by remember { mutableStateOf<String?>(null) }
    var videoResolution by remember { mutableStateOf<String?>(null) }

    // Extract resolution and codec periodically
    LaunchedEffect(playbackState, metadata.streamUrl) {
        if (playbackState is PlaybackState.Playing || playbackState is PlaybackState.Buffering) {
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
    // live (there is nothing to pause). Aiming at it on a live stream targets a node that was
    // never composed, so the request failed into a log line and the icon row below — subtitles,
    // favourite, stats — could not be reached by D-pad at all. Live lands on that row instead;
    // it is a focus group, so focus falls through to whichever of its buttons is first for this
    // stream (the row's contents vary with the track counts).
    val controlsFocusRequester = remember { FocusRequester() }
    val iconRowFocusRequester = remember { FocusRequester() }
    var isProgressBarFocused by remember { mutableStateOf(false) }

    LaunchedEffect(showFullControls, isLive) {
        if (showFullControls) {
            // Small delay to allow composition to complete
            androidx.compose.runtime.withFrameMillis {}
            try {
                if (isLive) iconRowFocusRequester.requestFocus() else controlsFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Retry after another frame
                try {
                    androidx.compose.runtime.withFrameMillis {}
                    if (isLive) iconRowFocusRequester.requestFocus() else controlsFocusRequester.requestFocus()
                } catch (e2: Exception) {
                    android.util.Log.e("TvPlayerControlsOverlay", "Failed to request focus for controls", e2)
                }
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

        // Top bar with channel name and title. Hidden while a side panel is open since it
        // would collide with the category panel's own title — the bottom program-info bar
        // already covers channel/program context in that case.
        if (!hideTopBars) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
            ) {
                Text(
                    text = metadata.channelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.bounceMarquee(),
                )
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.bounceMarquee(),
                )

                // Resolution and Codec Info
                if (videoResolution != null || videoCodec != null) {
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

        // Seek speed indicator (shown when fast-forwarding/rewinding with D-pad hold)
        if (seekSpeedLabel != null && !showFullControls) {
            Text(
                text = seekSpeedLabel,
                style = MaterialTheme.typography.headlineMedium,
                color = CinemaTextPrimary,
                modifier = Modifier.align(Center),
            )
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
                        modifier = Modifier
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
                                                Key.DirectionLeft -> {
                                                    val origin = scrubPositionMs ?: position
                                                    viewModel.seekTo((origin - 10_000L).coerceAtLeast(0L))
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    val origin = scrubPositionMs ?: position
                                                    viewModel.seekTo((origin + 10_000L).coerceAtMost(duration))
                                                    true
                                                }
                                                Key.DirectionCenter, Key.Enter -> {
                                                    if (scrubPositionMs != null) {
                                                        onCommitScrub(scrubPositionMs)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                                else -> false
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
                                text = stringResource(
                                    R.string.player_remaining_ends_at_format,
                                    formatTime(remainingTime),
                                    TimeFormat.formatClockTime(Date(estimatedEndTimeMillis))
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xxs),
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                } else {
                    // Live indicator with channel + EPG info. Channel name lives here (rather
                    // than only in the top bar) so it's still visible when hideTopBars is set —
                    // this bottom section is the only thing shown while a side panel is open.
                    Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                        Text(
                            text = metadata.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.bounceMarquee(),
                        )
                        Row(
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier.padding(top = Spacing.xxs),
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
                        }
                        if (currentEpgProgram != null) {
                            val epgContext = LocalContext.current
                            val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                            val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                            Text(
                                text = stringResource(
                                    R.string.player_now_playing_format,
                                    currentEpgProgram.title,
                                    nowStart,
                                    nowEnd
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
                                    text = stringResource(
                                        R.string.player_up_next_format,
                                        nextEpgProgram.title,
                                        formatEpochTime(epgContext, nextEpgProgram.startTime)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                            }
                        }
                    }
                }

                // Icon controls row (only when full controls are visible)
                if (showFullControls) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(iconRowFocusRequester)
                                .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = CenterVertically,
                    ) {
                        // Chapter selector
                        val chapters = remember(metadata) { viewModel.getChapters() }
                        if (chapters.isNotEmpty()) {
                            CinemaButton(
                                onClick = onShowChapterSelector,
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium),
                                        contentColor = CinemaTextPrimary,
                                        focusedContainerColor = CinemaTextPrimary,
                                        focusedContentColor = CinemaBackground,
                                    ),
                            ) {
                                Icon(CinemaIcons.List, stringResource(R.string.player_chapters))
                            }
                        }

                        // Audio track selector
                        if (audioTrackCount > 1) {
                            CinemaButton(
                                onClick = onShowAudioTrackSelector,
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium),
                                        contentColor = CinemaTextPrimary,
                                        focusedContainerColor = CinemaTextPrimary,
                                        focusedContentColor = CinemaBackground,
                                    ),
                            ) {
                                Icon(CinemaIcons.VolumeUp, stringResource(R.string.player_audio))
                            }
                        }

                        // Subtitle selector
                        if (subtitleTrackCount > 0) {
                            CinemaButton(
                                onClick = onShowSubtitleSelector,
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium),
                                        contentColor = CinemaTextPrimary,
                                        focusedContainerColor = CinemaTextPrimary,
                                        focusedContentColor = CinemaBackground,
                                    ),
                            ) {
                                Icon(CinemaIcons.Subtitles, stringResource(R.string.player_subtitles))
                            }
                        }

                        // Quality selector
                        if (qualityCount > 1) {
                            CinemaButton(
                                onClick = onShowQualitySelector,
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium),
                                        contentColor = CinemaTextPrimary,
                                        focusedContainerColor = CinemaTextPrimary,
                                        focusedContentColor = CinemaBackground,
                                    ),
                            ) {
                                Icon(CinemaIcons.Tune, stringResource(R.string.player_quality))
                            }
                        }

                        // Favorite toggle
                        if (onToggleFavorite != null) {
                            CinemaButton(
                                onClick = { onToggleFavorite() },
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor =
                                            if (isFavorite) {
                                                CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                                            } else {
                                                CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                            },
                                        contentColor = CinemaTextPrimary,
                                        focusedContainerColor = CinemaTextPrimary,
                                        focusedContentColor = CinemaBackground,
                                    ),
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) CinemaIcons.Favorite else CinemaIcons.FavoriteBorder,
                                    contentDescription = if (isFavorite) stringResource(R.string.player_remove_favorite) else stringResource(R.string.player_add_favorite),
                                    tint =
                                        if (isFavorite &&
                                            !isProgressBarFocused
                                        ) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Unspecified
                                        },
                                )
                            }
                        }

                        // Stats for nerds (always visible)
                        CinemaButton(
                            onClick = onShowStats,
                            colors =
                                ButtonDefaults.colors(
                                    containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium),
                                    contentColor = CinemaTextPrimary,
                                    focusedContainerColor = CinemaTextPrimary,
                                    focusedContentColor = CinemaBackground,
                                ),
                        ) {
                            Icon(CinemaIcons.BarChart, stringResource(R.string.player_stats))
                        }
                    }
                }
            }
        }
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
