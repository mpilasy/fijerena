package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun ThemeSettingsCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onThemeChanged: (String) -> Unit,
    onUiStyleChanged: (String) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_theme_section_title)) {
        Text(
            text = stringResource(R.string.settings_theme_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AllPalettes.chunked(2).forEach { rowPalettes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    rowPalettes.forEach { palette ->
                        val isSelected = uiState.themeId == palette.id
                        if (isSelected) {
                            CinemaButton(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(palette.displayName, maxLines = 1)
                            }
                        } else {
                            CinemaOutlinedButton(
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

        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.settings_ui_style_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AllUiStyles.chunked(2).forEach { rowStyles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    rowStyles.forEach { style ->
                        val isSelected = uiState.uiStyleId == style.id
                        if (isSelected) {
                            CinemaButton(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(style.displayName, maxLines = 1)
                            }
                        } else {
                            CinemaOutlinedButton(
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
