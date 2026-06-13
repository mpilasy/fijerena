@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun LanguageSettingsCard(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    scale: Float,
) {
    var showDialog by remember { mutableStateOf(false) }

    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

            val label = when (selectedLanguage) {
                "en" -> stringResource(R.string.settings_language_en)
                "mg" -> stringResource(R.string.settings_language_mg)
                "fr" -> stringResource(R.string.settings_language_fr)
                else -> selectedLanguage
            }

            CinemaSecondaryButton(
                onClick = { showDialog = true },
                text = label,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDialog) {
        val languages = listOf(
            "en" to stringResource(R.string.settings_language_en),
            "mg" to stringResource(R.string.settings_language_mg),
            "fr" to stringResource(R.string.settings_language_fr)
        )
        
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.settings_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                    languages.forEach { (code, name) ->
                        val isSelected = selectedLanguage == code
                        if (isSelected) {
                            CinemaPrimaryButton(
                                onClick = { showDialog = false },
                                text = name,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            CinemaSecondaryButton(
                                onClick = { 
                                    onLanguageSelected(code)
                                    showDialog = false
                                },
                                text = name,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
