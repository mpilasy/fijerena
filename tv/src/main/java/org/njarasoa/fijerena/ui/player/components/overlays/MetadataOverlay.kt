package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.ui.player.utils.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun MetadataOverlay(
    playbackState: PlaybackState,
    metadata: PlayerMetadata,
    isPaused: Boolean,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onAudioTrack: (() -> Unit)? = null,
    onSubtitle: (() -> Unit)? = null,
    onQuality: (() -> Unit)? = null,
    onStats: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val position = when (playbackState) {
        is PlaybackState.Playing -> playbackState.position
        is PlaybackState.Paused -> playbackState.position
        else -> 0L
    }

    val duration = when (playbackState) {
        is PlaybackState.Playing -> playbackState.duration
        is PlaybackState.Paused -> playbackState.duration
        else -> 0L
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel name and title
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = metadata.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Progress bar (for non-live streams)
            if (duration > 0 && !metadata.isLive) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (duration > 0) position.toFloat() / duration.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TvDimensions.progressBar),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = CinemaAlpha.tint)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = CinemaAlpha.overlayMedium)
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = CinemaAlpha.overlayMedium)
                        )
                    }
                }
            } else if (metadata.isLive) {
                // Live indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(TvDimensions.statsDotSize)
                            .background(Color.Red, shape = RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Controls - using TvLazyRow for better D-pad focus navigation
            TvLazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play/Pause button (VOD only, not for live streams)
                if (!metadata.isLive) {
                    item {
                        if (isPaused) {
                            Button(onClick = { onResume?.invoke() }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text("Resume")
                                }
                            }
                        } else {
                            Button(onClick = { onPause?.invoke() }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Pause, contentDescription = null)
                                    Text("Pause")
                                }
                            }
                        }
                    }
                }

                item {
                    Button(onClick = { onAudioTrack?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            Text("Audio")
                        }
                    }
                }

                item {
                    Button(onClick = { onSubtitle?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Subtitles, contentDescription = null)
                            Text("Subtitle")
                        }
                    }
                }

                item {
                    Button(onClick = { onQuality?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                            Text("Quality")
                        }
                    }
                }

                if (onStats != null) {
                    item {
                        Button(onClick = { onStats.invoke() }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.BarChart, contentDescription = null)
                                Text("Stats")
                            }
                        }
                    }
                }

                // Favorite toggle button
                if (onToggleFavorite != null) {
                    item {
                        Button(
                            onClick = { onToggleFavorite() },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isFavorite)
                                    MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.scrim)
                                else
                                    MaterialTheme.colorScheme.surface.copy(alpha = CinemaAlpha.textMedium)
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null
                                )
                                Text(if (isFavorite) "Favorited" else "Favorite")
                            }
                        }
                    }
                }
            }

            // Hint text
            Text(
                text = "Press OK to hide controls • Press BACK to exit",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = CinemaAlpha.textLow)
            )
        }
    }
}
