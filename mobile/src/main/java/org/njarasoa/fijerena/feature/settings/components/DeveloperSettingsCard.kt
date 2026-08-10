package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

@Composable
fun DeveloperSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onCellularBuffers: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_developer_mode_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_developer_mode_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = uiState.isDevMode,
                onCheckedChange = { enabled ->
                    viewModel.updateDevMode(enabled)
                },
            )
        }

        if (uiState.isDevMode) {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaButton(
                onClick = onCellularBuffers,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_configure_cellular_buffers_button))
            }
        }
    }
}
