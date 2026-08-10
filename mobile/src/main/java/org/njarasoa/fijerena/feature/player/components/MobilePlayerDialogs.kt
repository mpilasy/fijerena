package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius

@Composable
fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val audioTracks = remember { viewModel.getAudioTracks() }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_select_audio)) },
        text = {
            if (audioTracks.isEmpty()) {
                Text(stringResource(R.string.player_no_audio))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    audioTracks.forEachIndexed { _, track ->
                        Surface(
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color =
                                if (track.isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (track.isSelected) {
                                        Text(
                                            text = stringResource(R.string.player_active),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.player_audio_format_hint, track.channelCount, track.sampleRate / 1000),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    val hasActiveSubtitle = subtitleTracks.any { it.isSelected }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_select_subtitles)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // "Off" option
                Surface(
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color =
                        if (!hasActiveSubtitle) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.common_off),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (!hasActiveSubtitle) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (!hasActiveSubtitle) {
                            Text(
                                text = stringResource(R.string.player_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                subtitleTracks.forEachIndexed { _, track ->
                    Surface(
                        onClick = {
                            viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color =
                            if (track.isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        shape = RoundedCornerShape(CinemaCornerRadius.small),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (track.isSelected) {
                                    Text(
                                        text = stringResource(R.string.player_active),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = track.mimeType.substringAfterLast("/").uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    val hasManualSelection = videoQualities.any { it.isSelected }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_select_quality)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // "Auto" option
                Surface(
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color =
                        if (!hasManualSelection) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.player_quality_auto),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!hasManualSelection) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (!hasManualSelection) {
                                Text(
                                    text = stringResource(R.string.player_active),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.player_quality_auto_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                videoQualities.forEachIndexed { _, quality ->
                    Surface(
                        onClick = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color =
                            if (quality.isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        shape = RoundedCornerShape(CinemaCornerRadius.small),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (quality.isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (quality.isSelected) {
                                    Text(
                                        text = stringResource(R.string.player_active),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(
                                    R.string.player_quality_dimensions_format,
                                    quality.width,
                                    quality.height,
                                    quality.frameRate.toInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}
