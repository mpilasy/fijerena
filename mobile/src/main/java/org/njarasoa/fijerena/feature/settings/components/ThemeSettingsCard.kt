package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel

@Composable
fun ThemeSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onThemeChanged: (String) -> Unit,
    onUiStyleChanged: (String) -> Unit,
) {
    SettingsSection(title = "Theme") {
        Text(
            text = "Select a color theme for the app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AllPalettes.chunked(2).forEach { rowPalettes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPalettes.forEach { palette ->
                        val isSelected = uiState.themeId == palette.id
                        if (isSelected) {
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(palette.displayName, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateTheme(palette.id)
                                    onThemeChanged(palette.id)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(palette.displayName, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Select a look and feel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AllUiStyles.chunked(2).forEach { rowStyles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowStyles.forEach { style ->
                        val isSelected = uiState.uiStyleId == style.id
                        if (isSelected) {
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(style.displayName, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateUiStyle(style.id)
                                    onUiStyleChanged(style.id)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(style.displayName, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
