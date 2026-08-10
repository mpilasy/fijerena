package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

@Composable
fun ProviderSettingsCard(
    uiState: SettingsUiState,
    onManageProviders: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_provider_section_title)) {
        Text(
            text = uiState.providerName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = uiState.currentUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        if (uiState.subscriptionExpiry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val isExpired = uiState.subscriptionStatus?.equals("Expired", ignoreCase = true) == true
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.settings_provider_expires_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Text(
                    text = uiState.subscriptionExpiry!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (uiState.subscriptionMaxCons != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.settings_provider_max_connections_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                    Text(uiState.subscriptionMaxCons!!, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (uiState.subscriptionIsTrial) {
                Text(stringResource(R.string.settings_provider_trial_account_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CinemaButton(
            onClick = onManageProviders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_provider_manage_button))
        }
    }
}
