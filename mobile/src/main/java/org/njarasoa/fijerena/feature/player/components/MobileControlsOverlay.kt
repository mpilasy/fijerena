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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.feature.player.utils.formatEpochTime
import org.njarasoa.fijerena.feature.player.utils.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaLive
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import java.util.Date

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
        // Top bar with title and clock
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
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
                        modifier = Modifier.padding(top = 2.dp),
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
                            Text(
                                text = videoCodec!!,
                                style = typography.labelSmall.copy(fontSize = 10.sp),
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                modifier =
                                    Modifier
                                        .background(CinemaSurface.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
            // Clock — self-ticking so only this leaf recomposes each second
            ClockDisplay()
        }

        // Metadata Description (Middle area, above play buttons)
        metadata.description?.let { description ->
            if (description.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 120.dp) // Push above play buttons
                        .padding(horizontal = CinemaSpacing.xl)
                        .background(
                            color = CinemaSurface.copy(alpha = CinemaAlpha.scrim),
                            shape = RoundedCornerShape(8.dp)
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

        // Center row: Rewind | Play/Pause | FastForward (VOD only shows seek buttons)
        Row(
            modifier = Modifier.align(Alignment.Center),
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
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = "Rewind 1min",
                            tint = CinemaTextPrimary,
                            modifier = Modifier.size(MobileDimensions.iconLarge),
                        )
                        Text("-1m", style = typography.labelSmall, color = CinemaTextPrimary)
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
                            Icons.Rounded.PlayArrow
                        } else {
                            Icons.Rounded.Pause
                        },
                    contentDescription = if (playbackState is PlaybackState.Paused) "Play" else "Pause",
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
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Fast Forward 5min",
                            tint = CinemaTextPrimary,
                            modifier = Modifier.size(MobileDimensions.iconLarge),
                        )
                        Text("+5m", style = typography.labelSmall, color = CinemaTextPrimary)
                    }
                }
            }
        }

        // Bottom section: progress + controls (scrollable for landscape)
        GlassPanel(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
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
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                                ),
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
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
                                    .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = "Remaining: ${formatTime(remainingTime)}  •  Ends at ${
                                    org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(
                                        Date(estimatedEndTimeMillis),
                                    )
                                }",
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    // Live indicator with EPG info
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(MobileDimensions.liveDotSize)
                                        .background(CinemaLive, shape = MaterialTheme.shapes.small),
                            )
                            Text(
                                text = "LIVE",
                                style = typography.labelLarge,
                                color = CinemaTextPrimary,
                            )
                        }
                        if (currentEpgProgram != null) {
                            val epgContext = LocalContext.current
                            val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                            val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                            Text(
                                text = "Now: ${currentEpgProgram.title}  ($nowStart – $nowEnd)",
                                style = typography.bodySmall,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                                modifier = Modifier.padding(top = 4.dp),
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
                                        .padding(top = 4.dp)
                                        .height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                            )
                            if (nextEpgProgram != null) {
                                Text(
                                    text = "Up Next: ${nextEpgProgram.title}  (${formatEpochTime(epgContext, nextEpgProgram.startTime)})",
                                    style = labelStyle,
                                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
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
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Audio", tint = CinemaTextPrimary)
                            }
                        )
                    }

                    // Subtitle selector (only if subtitles available)
                    if (subtitleTrackCount > 0) {
                        CinemaIconButton(onClick = onSubtitle,
                            icon = {
                                Icon(Icons.Rounded.Subtitles, "Subtitles", tint = CinemaTextPrimary)
                            }
                        )
                    }

                    // Quality selector (only if multiple qualities)
                    if (qualityCount > 1) {
                        CinemaIconButton(onClick = onQuality,
                            icon = {
                                Icon(Icons.Rounded.Tune, "Quality", tint = CinemaTextPrimary)
                            }
                        )
                    }

                    // Favorite toggle
                    CinemaIconButton(onClick = onToggleFavorite,
                        icon = {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else CinemaTextPrimary,
                            )
                        }
                    )

                    // Stats for nerds (always visible)
                    CinemaIconButton(onClick = onStats,
                        icon = {
                            Icon(Icons.Rounded.BarChart, "Stats", tint = CinemaTextPrimary)
                        }
                    )
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
