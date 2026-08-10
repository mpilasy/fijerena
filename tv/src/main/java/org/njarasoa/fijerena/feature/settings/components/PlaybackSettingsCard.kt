package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun PlaybackSettingsCard(
    watchDelaySeconds: Int,
    onWatchDelayChanged: (Int) -> Unit,
    scale: Float,
) {
    Column {
        Text(
            text = stringResource(R.string.settings_playback_section_title),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize =
                        MaterialTheme.typography.titleMedium.fontSize
                            .scaled(scale),
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
        Text(
            text = stringResource(R.string.settings_playback_watch_delay_desc),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize =
                        MaterialTheme.typography.bodySmall.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
        ) {
            listOf(5 to "5s", 15 to "15s", 30 to "30s", 60 to "60s").chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                ) {
                    rowItems.forEach { (seconds, label) ->
                        val isSelected = watchDelaySeconds == seconds
                        if (isSelected) {
                            CinemaPrimaryButton(
                                onClick = { },
                                text = label,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            CinemaSecondaryButton(
                                onClick = { onWatchDelayChanged(seconds) },
                                text = label,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
