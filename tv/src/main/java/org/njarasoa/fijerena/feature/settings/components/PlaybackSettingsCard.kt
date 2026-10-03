package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

// Includes the 10 s default (AppSettings.DEFAULT_WATCH_DELAY_SECONDS) so a fresh install shows
// its current value selected.
private val WATCH_DELAY_OPTIONS = listOf(5, 10, 15, 30, 60, 120).chunked(3)

@Composable
fun PlaybackSettingsCard(
    watchDelaySeconds: Int,
    onWatchDelayChanged: (Int) -> Unit,
    autoplayNextEpisode: Boolean,
    onAutoplayNextEpisodeChanged: (Boolean) -> Unit,
    scale: Float,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_playback_section_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
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
                WATCH_DELAY_OPTIONS.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                    ) {
                        rowItems.forEach { seconds ->
                            TvSelectableButton(
                                selected = watchDelaySeconds == seconds,
                                onSelect = { onWatchDelayChanged(seconds) },
                                text = "${seconds}s",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            TvSwitchRow(
                checked = autoplayNextEpisode,
                onCheckedChange = onAutoplayNextEpisodeChanged,
                label = stringResource(R.string.settings_autoplay_next_episode_title),
                description = stringResource(R.string.settings_autoplay_next_episode_desc),
            )
        }
    }
}
