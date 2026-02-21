@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun PlayerControlsOverlay(
    isVisible: Boolean,
    isLive: Boolean,
    isPaused: Boolean,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onFastForward: (() -> Unit)?,
    onRewind: (() -> Unit)?,
    hasMultipleAudioTracks: Boolean,
    onAudioTrack: (() -> Unit)?,
    hasSubtitles: Boolean,
    onSubtitle: (() -> Unit)?,
    hasMultipleQualities: Boolean,
    onQuality: (() -> Unit)?,
    onStats: (() -> Unit)?,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scale = LocalUiScale.current

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl.scaled(scale), vertical = Spacing.xl.scaled(scale))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Controls Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind (VOD)
                    if (onRewind != null) {
                        CinemaIconButton(
                            onClick = onRewind,
                            icon = { Icon(Icons.Default.FastRewind, "Rewind") },
                            size = 56.dp
                        )
                    }

                    // Play/Pause (VOD)
                    if (!isLive) {
                        if (isPaused) {
                            CinemaIconButton(
                                onClick = { onResume?.invoke() },
                                icon = { Icon(Icons.Default.PlayArrow, "Resume") },
                                size = 64.dp
                            )
                        } else {
                            CinemaIconButton(
                                onClick = { onPause?.invoke() },
                                icon = { Icon(Icons.Default.Pause, "Pause") },
                                size = 64.dp
                            )
                        }
                    }

                    // Fast Forward (VOD)
                    if (onFastForward != null) {
                        CinemaIconButton(
                            onClick = onFastForward,
                            icon = { Icon(Icons.Default.FastForward, "Fast Forward") },
                            size = 56.dp
                        )
                    }
                }

                // Secondary Controls Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasMultipleAudioTracks) {
                        CinemaIconButton(
                            onClick = { onAudioTrack?.invoke() },
                            icon = { Icon(Icons.Default.VolumeUp, "Audio") }
                        )
                    }

                    if (hasSubtitles) {
                        CinemaIconButton(
                            onClick = { onSubtitle?.invoke() },
                            icon = { Icon(Icons.Default.Subtitles, "Subtitles") }
                        )
                    }

                    if (hasMultipleQualities) {
                        CinemaIconButton(
                            onClick = { onQuality?.invoke() },
                            icon = { Icon(Icons.Default.Tune, "Quality") }
                        )
                    }

                    if (onStats != null) {
                        CinemaIconButton(
                            onClick = { onStats.invoke() },
                            icon = { Icon(Icons.Default.BarChart, "Stats") }
                        )
                    }

                    if (onToggleFavorite != null) {
                        CinemaIconButton(
                            onClick = { onToggleFavorite() },
                            icon = {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) CinemaAccent else CinemaTextPrimary
                                )
                            }
                        )
                    }
                }

                Text(
                    text = "Press OK to hide controls",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary
                )
            }
        }
    }
}
