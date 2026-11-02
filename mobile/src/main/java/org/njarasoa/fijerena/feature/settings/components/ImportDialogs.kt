package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaCheckRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

@Composable
fun ImportOptionsDialog(
    parsed: SettingsExportManager.ParsedImport,
    initialOptions: SettingsExportManager.ImportOptions,
    onDismiss: () -> Unit,
    onConfirm: (SettingsExportManager.ImportOptions) -> Unit,
) {
    var optProviders by remember { mutableStateOf(initialOptions.importProviders) }
    var optEpg by remember { mutableStateOf(initialOptions.importEpgSources) }
    var optGlobal by remember { mutableStateOf(initialOptions.importGlobalSettings) }
    var optFavorites by remember { mutableStateOf(initialOptions.importFavorites) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_import_select_title),
        text = {
            Column {
                CinemaCheckRow(
                    checked = optGlobal,
                    onCheckedChange = { optGlobal = it },
                    label = stringResource(R.string.settings_import_general_label),
                )
                if (parsed.hasProviders) {
                    CinemaCheckRow(
                        checked = optProviders,
                        onCheckedChange = { optProviders = it },
                        label = stringResource(R.string.settings_import_providers_count_format, parsed.settings.providers.size),
                    )
                }
                if (parsed.hasEpgSources) {
                    CinemaCheckRow(
                        checked = optEpg,
                        onCheckedChange = { optEpg = it },
                        label = stringResource(R.string.settings_import_epg_sources_count_format, parsed.settings.epgSources.size),
                    )
                }
                if (parsed.hasFavorites) {
                    CinemaCheckRow(
                        checked = optFavorites,
                        onCheckedChange = { optFavorites = it },
                        label = stringResource(R.string.settings_import_favorites_label),
                    )
                }
            }
        },
        main =
            DialogAction(stringResource(R.string.common_import)) {
                val options =
                    SettingsExportManager.ImportOptions(
                        importProviders = optProviders,
                        importEpgSources = optEpg,
                        importGlobalSettings = optGlobal,
                        importFavorites = optFavorites,
                    )
                onConfirm(options)
            },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}

@Composable
fun ImportConflictDialog(
    conflicts: List<String>,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onDuplicate: () -> Unit,
    onSkip: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_import_provider_conflict_title),
        text = {
            CinemaText(
                stringResource(
                    R.string.settings_import_conflict_message,
                    conflicts.joinToString("\n") { "• $it" },
                ),
                tone = TextTone.Secondary,
            )
        },
        // Overwrite is the main action, as on TV.
        main = DialogAction(stringResource(R.string.common_overwrite), onClick = onOverwrite),
        others =
            listOf(
                DialogAction(stringResource(R.string.common_duplicate), onClick = onDuplicate),
                DialogAction(stringResource(R.string.settings_import_skip_duplicates_button), onClick = onSkip),
            ),
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
