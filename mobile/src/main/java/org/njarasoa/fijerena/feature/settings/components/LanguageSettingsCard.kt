package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel

@Composable
fun LanguageSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.settings_language)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { showLanguageDialog = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val languageLabel = when (uiState.language) {
                "en" -> stringResource(R.string.settings_language_en)
                "mg" -> stringResource(R.string.settings_language_mg)
                "fr" -> stringResource(R.string.settings_language_fr)
                else -> uiState.language
            }
            Text(text = languageLabel, style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (showLanguageDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption("en", stringResource(R.string.settings_language_en), uiState.language) {
                        viewModel.updateLanguage("en")
                        showLanguageDialog = false
                        (context as? android.app.Activity)?.recreate()
                    }
                    LanguageOption("mg", stringResource(R.string.settings_language_mg), uiState.language) {
                        viewModel.updateLanguage("mg")
                        showLanguageDialog = false
                        (context as? android.app.Activity)?.recreate()
                    }
                    LanguageOption("fr", stringResource(R.string.settings_language_fr), uiState.language) {
                        viewModel.updateLanguage("fr")
                        showLanguageDialog = false
                        (context as? android.app.Activity)?.recreate()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.player_back))
                }
            }
        )
    }
}

@Composable
private fun LanguageOption(
    code: String,
    label: String,
    selectedCode: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (code == selectedCode),
            onClick = null // handled by Row clickable
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
