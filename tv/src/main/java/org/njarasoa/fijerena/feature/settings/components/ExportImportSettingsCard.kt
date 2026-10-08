package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R

/** Export / Import: one row per action, one under the other (no 2-D grids in Settings, rule 2). */
@Composable
fun ExportImportSettingsCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onQuickImport: () -> Unit,
    exportImportMessage: String?,
    /** Quick Import from Downloads is a developer convenience; hidden otherwise (plan Part I, A, group 6). */
    isDevMode: Boolean = false,
    /** Goes on the Export row, the group's first focusable — the pane's entry row. */
    exportRowModifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.settings_export_import_section_title),
        description =
            stringResource(R.string.settings_export_import_desc) + "\n" +
                stringResource(R.string.settings_scope_device_section),
    ) {
        SettingsRow(
            title = stringResource(R.string.settings_export_button),
            description = null,
            onClick = onExport,
            modifier = exportRowModifier,
        )
        SettingsRow(title = stringResource(R.string.settings_import_button), description = null, onClick = onImport)
        if (isDevMode) {
            SettingsRow(title = stringResource(R.string.settings_quick_import_button), description = null, onClick = onQuickImport)
        }
        if (exportImportMessage != null) SettingsNote(exportImportMessage)
    }
}
