package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun PlaybackSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    SettingsSection(title = stringResource(R.string.settings_playback_section_title)) {
        var watchDelayText by remember(uiState.watchDelaySeconds) {
            mutableStateOf(uiState.watchDelaySeconds.toString())
        }
        Text(
            text = stringResource(R.string.settings_playback_watch_delay_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = watchDelayText,
            onValueChange = { newValue ->
                watchDelayText = newValue
                newValue.toIntOrNull()?.let { seconds ->
                    viewModel.updateWatchDelay(seconds)
                }
            },
            label = { Text(stringResource(R.string.settings_watch_delay_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    stringResource(
                        R.string.settings_watch_delay_range_format,
                        AppSettings.MIN_WATCH_DELAY_SECONDS,
                        AppSettings.MAX_WATCH_DELAY_SECONDS,
                    ),
                )
            },
        )
    }
}
