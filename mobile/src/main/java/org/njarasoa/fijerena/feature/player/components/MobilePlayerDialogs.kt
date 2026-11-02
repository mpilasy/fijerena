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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaRadioRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not composed once: a dialog opened before ExoPlayer resolved
    // tracks would freeze on an empty list for its whole lifetime.
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycle()
    val audioTracks = remember(tracksVersion) { viewModel.getAudioTracks() }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_select_audio),
        text = {
            if (audioTracks.isEmpty()) {
                CinemaText(
                    stringResource(R.string.player_no_audio),
                    role = TextRole.Body,
                    tone = TextTone.Inherit,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    audioTracks.forEach { track ->
                        CinemaRadioRow(
                            selected = track.isSelected,
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            label = track.label,
                            supportingText = stringResource(R.string.player_audio_format_hint, track.channelCount, track.sampleRate / 1000),
                        )
                    }
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}

@Composable
fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not composed once: a dialog opened before ExoPlayer resolved
    // tracks would freeze on an empty list for its whole lifetime.
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycle()
    val subtitleTracks = remember(tracksVersion) { viewModel.getSubtitleTracks() }
    val hasActiveSubtitle = subtitleTracks.any { it.isSelected }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_select_subtitles),
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CinemaRadioRow(
                    selected = !hasActiveSubtitle,
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    label = stringResource(R.string.common_off),
                )
                subtitleTracks.forEach { track ->
                    CinemaRadioRow(
                        selected = track.isSelected,
                        onClick = {
                            viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        label = track.label,
                        supportingText = track.mimeType.substringAfterLast("/").uppercase(),
                    )
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}

@Composable
fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not composed once: a dialog opened before ExoPlayer resolved
    // tracks would freeze on an empty list for its whole lifetime.
    val tracksVersion by viewModel.tracksVersion.collectAsStateWithLifecycle()
    val videoQualities = remember(tracksVersion) { viewModel.getVideoQualities() }
    val hasManualSelection = videoQualities.any { it.isSelected }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_select_quality),
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CinemaRadioRow(
                    selected = !hasManualSelection,
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    label = stringResource(R.string.player_quality_auto),
                    supportingText = stringResource(R.string.player_quality_auto_subtitle),
                )
                videoQualities.forEach { quality ->
                    CinemaRadioRow(
                        selected = quality.isSelected,
                        onClick = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                        label = quality.label,
                        supportingText =
                            stringResource(
                                R.string.player_quality_dimensions_format,
                                quality.width,
                                quality.height,
                                quality.frameRate.toInt(),
                            ),
                    )
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
