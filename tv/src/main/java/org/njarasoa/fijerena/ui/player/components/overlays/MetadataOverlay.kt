@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.player.model.formatTime
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

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
    val position =
        when (playbackState) {
            is PlaybackState.Playing -> playbackState.position
            is PlaybackState.Paused -> playbackState.position
            else -> 0L
        }

    val duration =
        when (playbackState) {
            is PlaybackState.Playing -> playbackState.duration
            is PlaybackState.Paused -> playbackState.duration
            else -> 0L
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Channel name and title
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = metadata.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = CinemaTextPrimary,
                )
                metadata.description?.let { description ->
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textHigh),
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.xxs)
                        )
                    }
                }
            }

            // Progress bar (for non-live streams)
            if (duration > 0 && !metadata.isLive) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LinearProgressIndicator(
                        progress = { if (duration > 0) position.toFloat() / duration.toFloat() else 0f },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(TvDimensions.progressBar),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.tint),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatTime(position),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.overlayMedium),
                        )
                        Text(
                            text = formatTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.overlayMedium),
                        )
                    }
                }
            } else if (metadata.isLive) {
                // Live indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(TvDimensions.statsDotSize)
                                .background(org.njarasoa.fijerena.ui.theme.CinemaLive, shape = RoundedCornerShape(CornerRadius.small)),
                    )
                    Text(
                        text = stringResource(R.string.player_live),
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                    )
                }
            }

            // Controls - using TvLazyRow for better D-pad focus navigation
            TvLazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Play/Pause button (VOD only, not for live streams)
                if (!metadata.isLive) {
                    item {
                        if (isPaused) {
                            CinemaButton(onClick = { onResume?.invoke() }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(CinemaIcons.PlayArrow, contentDescription = null)
                                    Text(stringResource(R.string.player_resume))
                                }
                            }
                        } else {
                            CinemaButton(onClick = { onPause?.invoke() }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(CinemaIcons.Pause, contentDescription = null)
                                    Text(stringResource(R.string.player_pause))
                                }
                            }
                        }
                    }
                }

                item {
                    CinemaButton(onClick = { onAudioTrack?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CinemaIcons.VolumeUp, contentDescription = null)
                            Text(stringResource(R.string.player_audio))
                        }
                    }
                }

                item {
                    CinemaButton(onClick = { onSubtitle?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CinemaIcons.Subtitles, contentDescription = null)
                            Text(stringResource(R.string.player_subtitles))
                        }
                    }
                }

                item {
                    CinemaButton(onClick = { onQuality?.invoke() }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CinemaIcons.Tune, contentDescription = null)
                            Text(stringResource(R.string.player_quality))
                        }
                    }
                }

                if (onStats != null) {
                    item {
                        CinemaButton(onClick = { onStats.invoke() }) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(CinemaIcons.BarChart, contentDescription = null)
                                Text(stringResource(R.string.player_stats))
                            }
                        }
                    }
                }

                // Favorite toggle button
                if (onToggleFavorite != null) {
                    item {
                        CinemaButton(
                            onClick = { onToggleFavorite() },
                            colors =
                                ButtonDefaults.colors(
                                    containerColor =
                                        if (isFavorite) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.scrim)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = CinemaAlpha.textMedium)
                                        },
                                ),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) CinemaIcons.Favorite else CinemaIcons.FavoriteBorder,
                                    contentDescription = null,
                                )
                                Text(
                                    if (isFavorite) stringResource(R.string.player_favorited)
                                    else stringResource(R.string.player_favorite)
                                )
                            }
                        }
                    }
                }
            }

            // Hint text
            Text(
                text = stringResource(R.string.player_press_back_exit),
                style = MaterialTheme.typography.bodySmall,
                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
            )
        }
    }
}
