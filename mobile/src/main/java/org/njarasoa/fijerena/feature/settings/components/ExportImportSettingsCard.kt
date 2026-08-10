package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.ManagedActivityResultLauncher
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
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

@Composable
fun ExportImportSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    exportManager: SettingsExportManager,
    exportLauncher: ManagedActivityResultLauncher<String, android.net.Uri?>,
    importLauncher: ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
    onPendingImportPathChange: (String) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_export_import_section_title)) {
        Text(
            text = stringResource(R.string.settings_export_import_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CinemaButton(
                onClick = { exportLauncher.launch("fijerena_settings.json") },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.common_export))
            }
            CinemaOutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.common_import))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val fileNotFoundText = stringResource(R.string.settings_quick_import_not_found)
        CinemaOutlinedButton(
            onClick = {
                val path = exportManager.getQuickImportPath()
                if (path != null) {
                    onPendingImportPathChange(path)
                } else {
                    viewModel.setExportImportMessage(fileNotFoundText)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_quick_import_button))
        }
        if (uiState.exportImportMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.exportImportMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
        }
    }
}
