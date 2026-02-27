package org.njarasoa.fijerena.feature.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.feature.player.utils.formatEpochTime
import org.njarasoa.fijerena.feature.player.utils.formatTime
import androidx.compose.ui.platform.LocalContext
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
    onRewind: (() -> Unit)? = null
) {
    // Key on metadata so track counts update when a new stream is loaded
    val audioTrackCount = remember(metadata) { viewModel.getAudioTracks().size }
    val subtitleTrackCount = remember(metadata) { viewModel.getSubtitleTracks().size }
    val qualityCount = remember(metadata) { viewModel.getVideoQualities().size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.tint))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
    ) {
        // Top bar with title and clock
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(CinemaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = CinemaSpacing.xs),
                maxLines = 1
            )
            // Clock — self-ticking so only this leaf recomposes each second
            ClockDisplay()
        }

        // Center row: Rewind | Play/Pause | FastForward (VOD only shows seek buttons)
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onRewind != null) {
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier.size(MobileDimensions.iconPlayContainer)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 30s",
                            tint = Color.White,
                            modifier = Modifier.size(MobileDimensions.iconLarge)
                        )
                        Text("-30s", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(MobileDimensions.iconPlayContainer)
            ) {
                Icon(
                    imageVector = if (playbackState is PlaybackState.Paused) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.Pause
                    },
                    contentDescription = if (playbackState is PlaybackState.Paused) "Play" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(MobileDimensions.iconPlayIcon)
                )
            }
            if (onFastForward != null) {
                IconButton(
                    onClick = onFastForward,
                    modifier = Modifier.size(MobileDimensions.iconPlayContainer)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Fast Forward 1min",
                            tint = Color.White,
                            modifier = Modifier.size(MobileDimensions.iconLarge)
                        )
                        Text("+1m", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // Bottom section: progress + controls (scrollable for landscape)
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.sm)
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
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    // Remaining time and estimated end time
                    val remainingTime = duration - position
                    val estimatedEndTimeMillis = System.currentTimeMillis() + remainingTime
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remaining: ${formatTime(remainingTime)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ends at ${org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(Date(estimatedEndTimeMillis))}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // Live indicator with EPG info
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(MobileDimensions.liveDotSize)
                                .background(Color.Red, shape = MaterialTheme.shapes.small)
                        )
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                    if (currentEpgProgram != null) {
                        val epgContext = LocalContext.current
                        val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                        val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                        Text(
                            text = "Now: ${currentEpgProgram.title}  ($nowStart – $nowEnd)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = CinemaAlpha.textMedium),
                            modifier = Modifier.padding(top = 4.dp)
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
                                .padding(top = 4.dp)
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                        )
                        if (nextEpgProgram != null) {
                            Text(
                                text = "Up Next: ${nextEpgProgram.title}  (${formatEpochTime(epgContext, nextEpgProgram.startTime)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = CinemaAlpha.tint),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Control buttons row (horizontally scrollable icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio track selector (only if multiple tracks)
                if (audioTrackCount > 1) {
                    IconButton(onClick = onAudioTrack) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Audio", tint = Color.White)
                    }
                }

                // Subtitle selector (only if subtitles available)
                if (subtitleTrackCount > 0) {
                    IconButton(onClick = onSubtitle) {
                        Icon(Icons.Filled.Subtitles, "Subtitles", tint = Color.White)
                    }
                }

                // Quality selector (only if multiple qualities)
                if (qualityCount > 1) {
                    IconButton(onClick = onQuality) {
                        Icon(Icons.Filled.Tune, "Quality", tint = Color.White)
                    }
                }

                // Favorite toggle
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Stats for nerds (always visible)
                IconButton(onClick = onStats) {
                    Icon(Icons.Filled.BarChart, "Stats", tint = Color.White)
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
    @Suppress("UNUSED_VARIABLE")
    val ignored = tick
    Text(
        text = org.njarasoa.fijerena.core.ui.theme.TimeFormat.formatClockTime(Date()),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White
    )
}
