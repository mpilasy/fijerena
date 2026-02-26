package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius

@Composable
fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val audioTracks = remember { viewModel.getAudioTracks() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Audio Track") },
        text = {
            if (audioTracks.isEmpty()) {
                Text("No audio tracks available")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    audioTracks.forEachIndexed { _, track ->
                        Surface(
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (track.isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (track.isSelected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "${track.channelCount}ch - ${track.sampleRate / 1000}kHz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val subtitleTracks = remember { viewModel.getSubtitleTracks() }
    val hasActiveSubtitle = subtitleTracks.any { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Subtitles") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Off" option
                Surface(
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!hasActiveSubtitle)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (!hasActiveSubtitle) FontWeight.Bold else FontWeight.Normal
                        )
                        if (!hasActiveSubtitle) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
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
                        color = if (track.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (track.isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = track.mimeType.substringAfterLast("/").uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val videoQualities = remember { viewModel.getVideoQualities() }
    val hasManualSelection = videoQualities.any { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Quality") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "Auto" option
                Surface(
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (!hasManualSelection)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CinemaCornerRadius.small)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto (Adaptive)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!hasManualSelection) FontWeight.Bold else FontWeight.Normal
                            )
                            if (!hasManualSelection) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Adjust quality based on network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (quality.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CinemaCornerRadius.small)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (quality.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (quality.isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = "${quality.width}x${quality.height} - ${quality.frameRate.toInt()}fps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
