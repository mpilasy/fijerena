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
import org.njarasoa.fijerena.ui.components.CinemaChoiceRow
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
        title = {
            CinemaText(
                stringResource(R.string.player_select_audio),
                role = TextRole.Title,
                tone = TextTone.Inherit,
            )
        },
        text = {
            if (audioTracks.isEmpty()) {
                CinemaText(
                    stringResource(R.string.player_no_audio),
                    role = TextRole.Body,
                    tone = TextTone.Inherit,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    audioTracks.forEachIndexed { _, track ->
                        CinemaChoiceRow(
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            selected = track.isSelected,
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CinemaText(
                                        text = track.label,
                                        role = TextRole.Body,
                                        tone = TextTone.Inherit,
                                        emphasis = track.isSelected,
                                    )
                                    if (track.isSelected) {
                                        CinemaText(
                                            text = stringResource(R.string.player_active),
                                            role = TextRole.BodySmall,
                                            tone = TextTone.Inherit,
                                        )
                                    }
                                }
                                CinemaText(
                                    text = stringResource(R.string.player_audio_format_hint, track.channelCount, track.sampleRate / 1000),
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                )
                            }
                        }
                    }
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_close), onClick = onDismiss),
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
        title = {
            CinemaText(
                stringResource(R.string.player_select_subtitles),
                role = TextRole.Title,
                tone = TextTone.Inherit,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                CinemaChoiceRow(
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    selected = !hasActiveSubtitle,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CinemaText(
                            text = stringResource(R.string.common_off),
                            role = TextRole.Body,
                            tone = TextTone.Inherit,
                            emphasis = !hasActiveSubtitle,
                        )
                        if (!hasActiveSubtitle) {
                            CinemaText(
                                text = stringResource(R.string.player_active),
                                role = TextRole.BodySmall,
                                tone = TextTone.Inherit,
                            )
                        }
                    }
                }

                subtitleTracks.forEachIndexed { _, track ->
                    CinemaChoiceRow(
                        onClick = {
                            viewModel.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        selected = track.isSelected,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CinemaText(
                                    text = track.label,
                                    role = TextRole.Body,
                                    tone = TextTone.Inherit,
                                    emphasis = track.isSelected,
                                )
                                if (track.isSelected) {
                                    CinemaText(
                                        text = stringResource(R.string.player_active),
                                        role = TextRole.BodySmall,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                            CinemaText(
                                text = track.mimeType.substringAfterLast("/").uppercase(),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                    }
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_close), onClick = onDismiss),
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
        title = {
            CinemaText(
                stringResource(R.string.player_select_quality),
                role = TextRole.Title,
                tone = TextTone.Inherit,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                CinemaChoiceRow(
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    selected = !hasManualSelection,
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CinemaText(
                                text = stringResource(R.string.player_quality_auto),
                                role = TextRole.Body,
                                tone = TextTone.Inherit,
                                emphasis = !hasManualSelection,
                            )
                            if (!hasManualSelection) {
                                CinemaText(
                                    text = stringResource(R.string.player_active),
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Inherit,
                                )
                            }
                        }
                        CinemaText(
                            text = stringResource(R.string.player_quality_auto_subtitle),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                    }
                }

                videoQualities.forEachIndexed { _, quality ->
                    CinemaChoiceRow(
                        onClick = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        selected = quality.isSelected,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CinemaText(
                                    text = quality.label,
                                    role = TextRole.Body,
                                    tone = TextTone.Inherit,
                                    emphasis = quality.isSelected,
                                )
                                if (quality.isSelected) {
                                    CinemaText(
                                        text = stringResource(R.string.player_active),
                                        role = TextRole.BodySmall,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            }
                            CinemaText(
                                text =
                                    stringResource(
                                        R.string.player_quality_dimensions_format,
                                        quality.width,
                                        quality.height,
                                        quality.frameRate.toInt(),
                                    ),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                    }
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_close), onClick = onDismiss),
    )
}
