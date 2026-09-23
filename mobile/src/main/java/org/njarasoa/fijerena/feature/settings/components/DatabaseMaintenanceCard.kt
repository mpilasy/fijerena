package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * "Shrink Database" — manually sweeps `xtream_v2.db` for rows left behind by a deleted provider
 * and reclaims the freed disk space. See [org.njarasoa.fijerena.core.network.provider.ProviderRepository.pruneOrphanedCatalogData].
 */
@Composable
fun DatabaseMaintenanceCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SettingsSection(title = stringResource(R.string.settings_shrink_database_title)) {
        Text(
            text = stringResource(R.string.settings_shrink_database_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        CinemaOutlinedButton(
            onClick = { viewModel.pruneDatabase() },
            enabled = !uiState.isPruningDatabase,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (uiState.isPruningDatabase) R.string.settings_shrink_database_button_running else R.string.settings_shrink_database_button,
                ),
            )
        }
        if (uiState.databaseMaintenanceMessage != null) {
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = uiState.databaseMaintenanceMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
        }
    }
}
