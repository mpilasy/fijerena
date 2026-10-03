package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

// Includes the 10 s default (AppSettings.DEFAULT_WATCH_DELAY_SECONDS) so a fresh install shows
// its current value selected.
private val WATCH_DELAY_OPTIONS = listOf(5, 10, 15, 30, 60, 120)

private fun secondsLabel(seconds: Int): String = "$seconds s"

/** The stored value as shown on the row: a preset as is, anything else (mobile allows 5–120) marked custom. */
@Composable
private fun watchDelayLabel(seconds: Int): String =
    if (seconds in WATCH_DELAY_OPTIONS) {
        secondsLabel(seconds)
    } else {
        stringResource(R.string.settings_custom_value_format, secondsLabel(seconds))
    }

@Composable
fun PlaybackSettingsCard(
    watchDelaySeconds: Int,
    onOpenWatchDelayPicker: () -> Unit,
    autoplayNextEpisode: Boolean,
    onAutoplayNextEpisodeChanged: (Boolean) -> Unit,
    scale: Float,
    watchDelayRowFocusRequester: FocusRequester? = null,
    /** Goes on the watch-delay row, the card's first focusable — the pane's entry row. */
    watchDelayRowModifier: Modifier = Modifier,
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
                text = stringResource(R.string.settings_per_source_playback_hint),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize =
                            MaterialTheme.typography.bodySmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            SettingsRow(
                title = stringResource(R.string.settings_watch_delay_row_title),
                description = stringResource(R.string.settings_playback_watch_delay_desc),
                value = watchDelayLabel(watchDelaySeconds),
                scope = SettingsScope.DEVICE,
                onClick = onOpenWatchDelayPicker,
                focusRequester = watchDelayRowFocusRequester,
                modifier = watchDelayRowModifier,
            )
            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_autoplay_next_episode_title),
                description = stringResource(R.string.settings_autoplay_next_episode_desc),
                scope = SettingsScope.PROFILE,
                checked = autoplayNextEpisode,
                onCheckedChange = onAutoplayNextEpisodeChanged,
            )
        }
    }
}

@Composable
fun WatchDelayPickerPane(
    watchDelaySeconds: Int,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val presets = WATCH_DELAY_OPTIONS.map { PickerOption(secondsLabel(it), it) }
    // A stored value outside the presets is kept as its own checked option, so nothing is lost.
    val options =
        if (watchDelaySeconds in WATCH_DELAY_OPTIONS) {
            presets
        } else {
            (presets + PickerOption(watchDelayLabel(watchDelaySeconds), watchDelaySeconds)).sortedBy { it.value }
        }
    SettingsPickerPane(
        title = stringResource(R.string.settings_watch_delay_row_title),
        options = options,
        selectedValue = watchDelaySeconds,
        onPick = onPick,
        onBack = onBack,
    )
}
