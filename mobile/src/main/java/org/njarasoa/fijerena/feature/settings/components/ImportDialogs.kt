package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

@Composable
fun ImportOptionsDialog(
    parsed: SettingsExportManager.ParsedImport,
    initialOptions: SettingsExportManager.ImportOptions,
    onDismiss: () -> Unit,
    onConfirm: (SettingsExportManager.ImportOptions) -> Unit
) {
    var optProviders by remember { mutableStateOf(initialOptions.importProviders) }
    var optEpg by remember { mutableStateOf(initialOptions.importEpgSources) }
    var optGlobal by remember { mutableStateOf(initialOptions.importGlobalSettings) }
    var optFavorites by remember { mutableStateOf(initialOptions.importFavorites) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_select_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = optGlobal, onCheckedChange = { optGlobal = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    Text(stringResource(R.string.settings_import_general_label), style = MaterialTheme.typography.bodyMedium)
                }
                if (parsed.hasProviders) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optProviders, onCheckedChange = { optProviders = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text(
                            stringResource(R.string.settings_import_providers_count_format, parsed.settings.providers.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (parsed.hasEpgSources) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optEpg, onCheckedChange = { optEpg = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text(
                            stringResource(R.string.settings_import_epg_sources_count_format, parsed.settings.epgSources.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (parsed.hasFavorites) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optFavorites, onCheckedChange = { optFavorites = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text(stringResource(R.string.settings_import_favorites_label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = {
                val options =
                    SettingsExportManager.ImportOptions(
                        importProviders = optProviders,
                        importEpgSources = optEpg,
                        importGlobalSettings = optGlobal,
                        importFavorites = optFavorites,
                    )
                onConfirm(options)
            }) { Text(stringResource(R.string.common_import)) }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
fun ImportConflictDialog(
    conflicts: List<String>,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onDuplicate: () -> Unit,
    onSkip: () -> Unit
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_provider_conflict_title)) },
        text = {
            Text(
                stringResource(
                    R.string.settings_import_conflict_message,
                    conflicts.joinToString("\n") { "• $it" },
                ),
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                ) {
                    CinemaDialogActionButton(
                        onClick = onOverwrite,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_overwrite), maxLines = 1) }
                    CinemaDialogActionButton(
                        onClick = onDuplicate,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_duplicate), maxLines = 1) }
                }
                CinemaDialogActionButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_import_skip_duplicates_button)) }
            }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
