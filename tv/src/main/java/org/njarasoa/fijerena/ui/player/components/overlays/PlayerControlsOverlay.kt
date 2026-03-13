@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.player.utils.formatEpochTime
import org.njarasoa.fijerena.ui.player.utils.formatTime
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import java.util.Date

@Composable
fun PlayerControlsOverlay(
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
    onShowAudioTrackSelector: () -> Unit,
    onShowSubtitleSelector: () -> Unit,
    onShowQualitySelector: () -> Unit,
    onShowChapterSelector: () -> Unit,
    onShowStats: () -> Unit,
    onToggleNightMode: () -> Unit = {},
    isNightModeEnabled: Boolean = false,
    dialogueBoostStrength: Float = 0f,
    onDialogueBoostStrengthChanged: (Float) -> Unit = {},
    isDialogueBoostAvailable: Boolean = false,
    isVoiceZoomAvailable: Boolean = false,
    isVoiceZoomEnabled: Boolean = false,
    onToggleVoiceZoom: () -> Unit = {},
    onOpenVoiceZoomSettings: () -> Unit = {},
    seekSpeedLabel: String? = null,
) {
    val isPaused = playbackState is PlaybackState.Paused
    val isLive = metadata.isLive
    // Memoize track counts keyed on metadata to avoid O(N) track iteration every recomposition (1 Hz clock tick)
    val audioTrackCount = remember(metadata) { viewModel.getAudioTracks().size }
    val subtitleTrackCount = remember(metadata) { viewModel.getSubtitleTracks().size }
    val qualityCount = remember(metadata) { viewModel.getVideoQualities().size }

    // Focus requester for the first focusable control
    val controlsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showFullControls) {
        if (showFullControls) {
            // Small delay to allow composition to complete
            delay(100)
            try {
                controlsFocusRequester.requestFocus()
            } catch (e: Exception) {
                android.util.Log.e("PlayerControlsOverlay", "Failed to request focus for controls", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.focusedTint))
    ) {
        // Clock in top-right corner — self-ticking so only this leaf recomposes each second
        ClockDisplay(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xl)
        )

        // Top bar with channel name and title
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xl)
        ) {
            Text(
                text = metadata.channelName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.bounceMarquee()
            )
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.bounceMarquee()
            )
        }

        // Seek speed indicator (shown when fast-forwarding/rewinding with D-pad hold)
        if (seekSpeedLabel != null && !showFullControls) {
            Text(
                text = seekSpeedLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Center)
            )
        }

        // Center row: Rewind | Play/Pause | FastForward (VOD only, hidden for live)
        if (showFullControls && !isLive) {
            Row(
                modifier = Modifier.align(Center),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
                verticalAlignment = CenterVertically
            ) {
                // Rewind -30s
                Button(
                    onClick = { viewModel.seekRelative(-30_000L) },
                    colors = ButtonDefaults.colors(
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
                    colors = ButtonDefaults.colors(
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
                    colors = ButtonDefaults.colors(
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
        TvGlassPanel(
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
                        val estimatedEndTimeMillis = remember(remainingTime) { System.currentTimeMillis() + remainingTime }
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
                                text = "Ends at ${TimeFormat.formatClockTime(Date(estimatedEndTimeMillis))}",
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
                            verticalAlignment = CenterVertically,
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
                            val epgContext = LocalContext.current
                            val nowStart = formatEpochTime(epgContext, currentEpgProgram.startTime)
                            val nowEnd = formatEpochTime(epgContext, currentEpgProgram.endTime)
                            Text(
                                text = "Now: ${currentEpgProgram.title}  ($nowStart – $nowEnd)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = CinemaAlpha.textMedium),
                                modifier = Modifier.padding(top = Spacing.xxs)
                            )
                            // Programme progress bar — keyed on livePosition to avoid untracked System.currentTimeMillis() reads
                            val nowEpoch = remember(livePosition) { System.currentTimeMillis() / 1000 }
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
                                    text = "Up Next: ${nextEpgProgram.title}  (${formatEpochTime(epgContext, nextEpgProgram.startTime)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = CinemaAlpha.tint),
                                    modifier = Modifier.padding(top = Spacing.xxs)
                                )
                            }
                        }
                    }
                }

                // Icon controls row (only when full controls are visible)
                if (showFullControls) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = CenterVertically
                    ) {
                        // Chapter selector
                        val chapters = remember(metadata) { viewModel.getChapters() }
                        if (chapters.isNotEmpty()) {
                            Button(
                                onClick = onShowChapterSelector,
                                colors = ButtonDefaults.colors(
                                    containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, "Chapters", tint = Color.White)
                            }
                        }

                        // Audio track selector
                        if (audioTrackCount > 1) {
                            Button(
                                onClick = onShowAudioTrackSelector,
                                colors = ButtonDefaults.colors(
                                    containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, "Audio", tint = Color.White)
                            }
                        }

                        // Subtitle selector
                        if (subtitleTrackCount > 0) {
                            Button(
                                onClick = onShowSubtitleSelector,
                                colors = ButtonDefaults.colors(
                                    containerColor = CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                )
                            ) {
                                Icon(Icons.Filled.Subtitles, "Subtitles", tint = Color.White)
                            }
                        }

                        // Quality selector
                        if (qualityCount > 1) {
                            Button(
                                onClick = onShowQualitySelector,
                                colors = ButtonDefaults.colors(
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
                                colors = ButtonDefaults.colors(
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

                        // Night Mode toggle
                        Button(
                            onClick = onToggleNightMode,
                            colors = ButtonDefaults.colors(
                                containerColor = if (isNightModeEnabled)
                                    CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                                else
                                    CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                            )
                        ) {
                            Icon(
                                Icons.Filled.NightsStay,
                                contentDescription = if (isNightModeEnabled) "Night Mode On" else "Night Mode Off",
                                tint = if (isNightModeEnabled) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Clear Voice (Dialogue Boost) toggle + slider
                        if (isDialogueBoostAvailable) {
                            val isActive = dialogueBoostStrength > 0f
                            var showSlider by remember { mutableStateOf(false) }

                            Button(
                                onClick = { showSlider = !showSlider },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isActive)
                                        CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                                    else
                                        CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                )
                            ) {
                                Row(
                                    verticalAlignment = CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Icon(
                                        Icons.Filled.RecordVoiceOver,
                                        contentDescription = if (isActive) "Clear Voice On" else "Clear Voice Off",
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                    if (isActive) {
                                        Text(
                                            text = "${(dialogueBoostStrength * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (showSlider) {
                                // Close other panels or just show this one
                                Box(
                                    modifier = Modifier
                                        .padding(start = Spacing.sm)
                                        .background(CinemaSurface.copy(alpha = 0.9f), RoundedCornerShape(Spacing.sm))
                                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                                ) {
                                    Row(
                                        verticalAlignment = CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                    ) {
                                        Text("Strength", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                        var sliderValue by remember(dialogueBoostStrength) { mutableFloatStateOf(if (dialogueBoostStrength > 0) dialogueBoostStrength else 0.7f) }
                                        Slider(
                                            value = sliderValue,
                                            onValueChange = { sliderValue = it },
                                            onValueChangeFinished = { onDialogueBoostStrengthChanged(sliderValue) },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.width(150.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                                            )
                                        )
                                        Button(
                                            onClick = { onDialogueBoostStrengthChanged(0f); showSlider = false },
                                            scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.1f),
                                            colors = ButtonDefaults.colors(containerColor = Color.Transparent)
                                        ) {
                                            Text("OFF", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }

                        // Voice Zoom (Sony Bravia only)
                        if (isVoiceZoomAvailable) {
                            Button(
                                onClick = onToggleVoiceZoom,
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isVoiceZoomEnabled)
                                        CinemaAccent.copy(alpha = CinemaAlpha.scrim)
                                    else
                                        CinemaSurface.copy(alpha = CinemaAlpha.textMedium)
                                ),
                                onLongClick = onOpenVoiceZoomSettings
                            ) {
                                Icon(
                                    Icons.Filled.SurroundSound,
                                    contentDescription = if (isVoiceZoomEnabled) "Voice Zoom On" else "Voice Zoom Off",
                                    tint = if (isVoiceZoomEnabled) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }

                        // Stats for nerds (always visible)
                        Button(
                            onClick = onShowStats,
                            colors = ButtonDefaults.colors(
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
        color = Color.White,
        modifier = modifier
    )
}
