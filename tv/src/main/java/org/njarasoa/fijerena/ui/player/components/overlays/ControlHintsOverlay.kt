@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun ControlHintsOverlay(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    org.njarasoa.fijerena.core.ui.theme.CinemaBackground
                        .copy(alpha = CinemaAlpha.glass),
                ),
        contentAlignment = Center,
    ) {
        TvGlassPanel(
            modifier =
                Modifier
                    .width(TvDimensions.dialogWidthLarge)
                    .padding(Spacing.xxl),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Header
                Text(
                    text = stringResource(R.string.player_controls_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Control hints
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ControlHint(stringResource(R.string.player_hint_ok_button), stringResource(R.string.player_hint_ok_description))
                    ControlHint(stringResource(R.string.player_hint_double_ok_button), stringResource(R.string.player_hint_double_ok_description))
                    ControlHint(stringResource(R.string.player_hint_back_button), stringResource(R.string.player_hint_back_description))
                    ControlHint(stringResource(R.string.player_hint_dpad_button), stringResource(R.string.player_hint_dpad_description))
                    ControlHint(stringResource(R.string.player_hint_pause_resume_button), stringResource(R.string.player_hint_pause_resume_description))
                    ControlHint(stringResource(R.string.player_hint_audio_button), stringResource(R.string.player_hint_audio_description))
                    ControlHint(stringResource(R.string.player_hint_subtitle_button), stringResource(R.string.player_hint_subtitle_description))
                    ControlHint(stringResource(R.string.player_hint_quality_button), stringResource(R.string.player_hint_quality_description))
                    ControlHint(stringResource(R.string.player_hint_favorite_button), stringResource(R.string.player_hint_favorite_description))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_got_it))
                    }
                    Button(
                        onClick = onDontShowAgain,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.colors(
                                containerColor = CinemaSurfaceVariant,
                            ),
                    ) {
                        Text(stringResource(R.string.common_dont_show_again))
                    }
                }

                // Auto-dismiss info
                Text(
                    text = stringResource(R.string.player_hint_dismiss_format, 7),
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textDisabled),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ControlHint(
    control: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = control,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(TvDimensions.audioTrackSelectorWidth),
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyLarge,
            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textDisabled),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = CinemaTextPrimary,
        )
    }
}
