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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.player.viewmodel.PlaybackViewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun AudioTrackSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not just composed-once: opening this dialog before ExoPlayer
    // resolved tracks used to freeze on an empty list for the dialog's whole lifetime.
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
                        Surface(
                            onClick = {
                                viewModel.selectAudioTrack(track.groupIndex, track.trackIndex)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color =
                                if (track.isSelected) {
                                    CinemaTheme.colors.accentStrong
                                } else {
                                    CinemaTheme.colors.surfaceRaised
                                },
                            shape = CinemaTheme.shapes.chip,
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
                                            tone = TextTone.Accent,
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
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(
                    stringResource(R.string.common_close),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
    )
}

@Composable
fun SubtitleSelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not just composed-once: opening this dialog before ExoPlayer
    // resolved tracks used to freeze on an empty list for the dialog's whole lifetime.
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
                // "Off" option
                Surface(
                    onClick = {
                        viewModel.disableSubtitles()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color =
                        if (!hasActiveSubtitle) {
                            CinemaTheme.colors.accentStrong
                        } else {
                            CinemaTheme.colors.surfaceRaised
                        },
                    shape = CinemaTheme.shapes.chip,
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
                                tone = TextTone.Accent,
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
                                CinemaTheme.colors.accentStrong
                            } else {
                                CinemaTheme.colors.surfaceRaised
                            },
                        shape = CinemaTheme.shapes.chip,
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
                                        tone = TextTone.Accent,
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
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(
                    stringResource(R.string.common_close),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
    )
}

@Composable
fun QualitySelectorDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    // Keyed on tracksVersion, not just composed-once: opening this dialog before ExoPlayer
    // resolved tracks used to freeze on an empty list for the dialog's whole lifetime.
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
                // "Auto" option
                Surface(
                    onClick = {
                        viewModel.enableAutoQuality()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color =
                        if (!hasManualSelection) {
                            CinemaTheme.colors.accentStrong
                        } else {
                            CinemaTheme.colors.surfaceRaised
                        },
                    shape = CinemaTheme.shapes.chip,
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
                                    tone = TextTone.Accent,
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
                    Surface(
                        onClick = {
                            viewModel.selectVideoQuality(quality.groupIndex, quality.trackIndex)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color =
                            if (quality.isSelected) {
                                CinemaTheme.colors.accentStrong
                            } else {
                                CinemaTheme.colors.surfaceRaised
                            },
                        shape = CinemaTheme.shapes.chip,
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
                                        tone = TextTone.Accent,
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
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(
                    stringResource(R.string.common_close),
                    role = TextRole.Label,
                    tone = TextTone.Inherit,
                )
            }
        },
    )
}
