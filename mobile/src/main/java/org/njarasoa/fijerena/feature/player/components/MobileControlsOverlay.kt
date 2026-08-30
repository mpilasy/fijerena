package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaBadge
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.player.model.formatEpochTime
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaLive
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing
import java.util.Date
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

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
) {
    // Key on metadata so track counts update when a new stream is loaded
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
                // If we found both, we can stop polling for this stream state
                if (videoCodec != null && videoResolution != null) break
                delay(1000)
            }
        } else {
            videoCodec = null
            videoResolution = null
        }
    }

    val typography = MaterialTheme.typography
    val labelStyle = typography.labelSmall

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
        // Top bar, middle (description + transport controls), and bottom panel used to be three
        // independently Alignment.align()-ed overlays sized only by their own content, with
        // nothing making them aware of each other's bounds. That already caused one overlap
        // (description colliding with the transport pill, fixed by stacking those two into one
        // centred Column — see below) and it recurred immediately after: a long description
        // grows that Column, and since it was centred in the *entire* screen, growing it pushed
        // its bottom edge down into the bottom panel's fixed territory, burying the transport
        // controls under the scrubber. A single Column spanning the full height, with the middle
        // region taking the leftover space via weight(1f), makes every region's bounds mutually
        // exclusive by construction — nothing sized here can ever encroach on the top bar or the
        // bottom panel, independent of how long the description or how tall the bottom panel
        // (e.g. Live TV's EPG block) gets.
        Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with title and clock
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(CinemaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Title
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = CinemaSpacing.xs),
            ) {
                Text(
                    text = metadata.title,
                    style = typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Resolution and Codec Info
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

        // Description, centred in whatever space is left between the top bar and the bottom
        // panel (never the whole screen — see the comment above). Transport controls used to
        // live here too, in a pill sharing this Column with the description; a long (4-line)
        // description could grow past the Box's actual height and, since nothing clipped it,
        // shove the pill down behind the bottom panel — it just disappeared. Rewind/play/pause/
        // forward now live in the bottom panel below, alongside the scrubber, which is sized to
        // its own content and can't be squeezed by anything above it.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            metadata.description?.let { description ->
                if (description.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = CinemaSpacing.xl)
                            .background(
                                color = CinemaSurface.copy(alpha = CinemaAlpha.scrim),
                                shape = RoundedCornerShape(Spacing.xs)
                            )
                            .padding(CinemaSpacing.md)
                    ) {
                        Text(
                            text = description,
                            style = typography.bodyMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.8f) // Limit width for better readability
                        )
                    }
                }
            }
        }
        // Bottom section: progress + controls (scrollable for landscape)
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm),
            ) {
                // VOD progress bar and time info
                if (!isLive) {
                    val position = livePosition
                    val duration = liveDuration

                    if (duration > 0) {
                        // Seek position state for dragging
                        var isSeeking by remember { mutableStateOf(false) }
                        var seekPosition by remember { mutableStateOf(0f) }

                        val scrubberColors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                        val scrubberInteractionSource = remember { MutableInteractionSource() }
                        Slider(
                            value = if (isSeeking) seekPosition else position.toFloat() / duration.toFloat(),
                            onValueChange = { newValue ->
                                isSeeking = true
                                seekPosition = newValue
                            },
                            onValueChangeFinished = {
                                val newPositionMs = (seekPosition * duration).toLong()
                                viewModel.seekTo(newPositionMs)
                                isSeeking = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = scrubberColors,
                            interactionSource = scrubberInteractionSource,
                            // Default thumb is a 4dp-wide bar — round and enlarge it so it reads
                            // as a draggable knob, not a thin tick mark.
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = scrubberInteractionSource,
                                    colors = scrubberColors,
                                    thumbSize = DpSize(MobileDimensions.playerScrubberThumbSize, MobileDimensions.playerScrubberThumbSize),
                                )
                            },
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(position),
                                style = typography.bodySmall,
                                color = CinemaTextPrimary,
                            )
                            Text(
                                text = formatTime(duration),
                                style = typography.bodySmall,
                                color = CinemaTextPrimary,
                            )
                        }

                        // Remaining time + estimated end time, grouped together at the right.
                        val remainingTime = duration - position
                        val estimatedEndTimeMillis = remember(remainingTime) { System.currentTimeMillis() + remainingTime }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xxxs),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.player_remaining_ends_at_format,
                                    formatTime(remainingTime),
                                    org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(
                                        Date(estimatedEndTimeMillis),
                                    ),
                                ),
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                } else {
                    // Live indicator with EPG info
                    Column(modifier = Modifier.padding(bottom = Spacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(MobileDimensions.liveDotSize)
                                        .background(CinemaLive, shape = MaterialTheme.shapes.small),
                            )
                            Text(
                                text = stringResource(R.string.player_live),
                                style = typography.labelLarge,
                                color = CinemaTextPrimary,
                            )
                        }
                        if (currentEpgProgram != null) {
                            val epgContext = LocalContext.current
                            val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                            val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                            Text(
                                text = stringResource(R.string.player_now_playing_format, currentEpgProgram.title, nowStart, nowEnd),
                                style = typography.bodySmall,
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
                                        .height(Spacing.xxxs),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                            if (nextEpgProgram != null) {
                                Text(
                                    text = stringResource(
                                        R.string.player_up_next_format,
                                        nextEpgProgram.title,
                                        formatEpochTime(epgContext, nextEpgProgram.startTime),
                                    ),
                                    style = labelStyle,
                                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                                    modifier = Modifier.padding(top = Spacing.xxxs),
                                )
                            }
                        }
                    }
                }

                // Rewind | Play/Pause | FastForward (VOD only shows seek buttons), grouped into a
                // pill-shaped glass surface and centred in this panel — same container as the
                // scrubber, so it's sized to content and can never be pushed off by the
                // description above.
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs, bottom = Spacing.xs),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    GlassPanel(panelShape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(horizontal = CinemaSpacing.lg, vertical = CinemaSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xl),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onRewind != null) {
                                IconButton(
                                    onClick = onRewind,
                                    modifier = Modifier.size(MobileDimensions.iconPlayContainer),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = CinemaIcons.FastRewind,
                                            contentDescription = stringResource(R.string.player_rewind_1min_description),
                                            tint = CinemaTextPrimary,
                                            modifier = Modifier.size(MobileDimensions.iconLarge),
                                        )
                                        Text(stringResource(R.string.player_rewind_label), style = typography.labelSmall, color = CinemaTextPrimary)
                                    }
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
                                    contentDescription = stringResource(if (playbackState is PlaybackState.Paused) R.string.player_play else R.string.player_pause),
                                    tint = CinemaTextPrimary,
                                    modifier = Modifier.size(MobileDimensions.iconPlayIcon),
                                )
                            }
                            if (onFastForward != null) {
                                IconButton(
                                    onClick = onFastForward,
                                    modifier = Modifier.size(MobileDimensions.iconPlayContainer),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = CinemaIcons.FastForward,
                                            contentDescription = stringResource(R.string.player_fast_forward_5min_description),
                                            tint = CinemaTextPrimary,
                                            modifier = Modifier.size(MobileDimensions.iconLarge),
                                        )
                                        Text(stringResource(R.string.player_forward_label), style = typography.labelSmall, color = CinemaTextPrimary)
                                    }
                                }
                            }
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
                    // Audio track selector (only if multiple tracks)
                    if (audioTrackCount > 1) {
                        CinemaIconButton(onClick = onAudioTrack,
                            icon = {
                                Icon(CinemaIcons.VolumeUp, stringResource(R.string.player_audio), tint = CinemaTextPrimary)
                            }
                        )
                    }

                    // Subtitle selector (only if subtitles available)
                    if (subtitleTrackCount > 0) {
                        CinemaIconButton(onClick = onSubtitle,
                            icon = {
                                Icon(CinemaIcons.Subtitles, stringResource(R.string.player_subtitles), tint = CinemaTextPrimary)
                            }
                        )
                    }

                    // Quality selector (only if multiple qualities)
                    if (qualityCount > 1) {
                        CinemaIconButton(onClick = onQuality,
                            icon = {
                                Icon(CinemaIcons.Tune, stringResource(R.string.player_quality), tint = CinemaTextPrimary)
                            }
                        )
                    }

                    // Favorite toggle
                    CinemaIconButton(onClick = onToggleFavorite,
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) CinemaIcons.Favorite else CinemaIcons.FavoriteBorder,
                                contentDescription = stringResource(if (isFavorite) R.string.player_remove_favorite else R.string.player_add_favorite),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                            )
                        }
                    )

                    // Stats for nerds (always visible)
                    CinemaIconButton(onClick = onStats,
                        icon = {
                            Icon(CinemaIcons.BarChart, stringResource(R.string.player_stats), tint = CinemaTextPrimary)
                        }
                    )
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
