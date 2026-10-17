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
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
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
        title = { CinemaText(stringResource(R.string.settings_import_select_title), role = TextRole.Title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinemaCheckbox(checked = optGlobal, onCheckedChange = { optGlobal = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    CinemaText(stringResource(R.string.settings_import_general_label))
                }
                if (parsed.hasProviders) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CinemaCheckbox(checked = optProviders, onCheckedChange = { optProviders = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        CinemaText(stringResource(R.string.settings_import_providers_count_format, parsed.settings.providers.size))
                    }
                }
                if (parsed.hasEpgSources) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CinemaCheckbox(checked = optEpg, onCheckedChange = { optEpg = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        CinemaText(stringResource(R.string.settings_import_epg_sources_count_format, parsed.settings.epgSources.size))
                    }
                }
                if (parsed.hasFavorites) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CinemaCheckbox(checked = optFavorites, onCheckedChange = { optFavorites = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        CinemaText(stringResource(R.string.settings_import_favorites_label))
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    val options =
                        SettingsExportManager.ImportOptions(
                            importProviders = optProviders,
                            importEpgSources = optEpg,
                            importGlobalSettings = optGlobal,
                            importFavorites = optFavorites,
                        )
                    onConfirm(options)
                },
                kind = ButtonKind.Main,
            ) { CinemaText(stringResource(R.string.common_import), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
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
        title = { CinemaText(stringResource(R.string.settings_import_provider_conflict_title), role = TextRole.Title) },
        text = {
            CinemaText(
                stringResource(
                    R.string.settings_import_conflict_message,
                    conflicts.joinToString("\n") { "• $it" },
                ),
                tone = TextTone.Secondary,
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
                        kind = ButtonKind.Main,
                    ) {
                        CinemaText(
                            stringResource(R.string.common_overwrite),
                            role = TextRole.Label,
                            tone = TextTone.Inherit,
                            maxLines = 1,
                        )
                    }
                    CinemaDialogActionButton(
                        onClick = onDuplicate,
                        modifier = Modifier.weight(1f),
                        kind = ButtonKind.Main,
                    ) {
                        CinemaText(
                            stringResource(R.string.common_duplicate),
                            role = TextRole.Label,
                            tone = TextTone.Inherit,
                            maxLines = 1,
                        )
                    }
                }
                CinemaDialogActionButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Main,
                ) {
                    CinemaText(
                        stringResource(R.string.settings_import_skip_duplicates_button),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}
