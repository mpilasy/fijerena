package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.input.TvCheckRow
import org.njarasoa.fijerena.ui.theme.Spacing

/** Which parts of a settings file to import: one row per part the file has, then Import or Cancel. Focus opens on the first row. */
@Composable
fun ImportOptionsDialog(
    parsed: SettingsExportManager.ParsedImport,
    initialOptions: SettingsExportManager.ImportOptions,
    onConfirm: (SettingsExportManager.ImportOptions) -> Unit,
    onCancel: () -> Unit,
) {
    var optProviders by remember { mutableStateOf(initialOptions.importProviders) }
    var optEpg by remember { mutableStateOf(initialOptions.importEpgSources) }
    var optGlobal by remember { mutableStateOf(initialOptions.importGlobalSettings) }
    var optFavorites by remember { mutableStateOf(initialOptions.importFavorites) }

    val firstOptionFocusRequester = remember { FocusRequester() }

    CinemaAlertDialog(
        onDismissRequest = onCancel,
        initialFocus = firstOptionFocusRequester,
        main =
            DialogAction(stringResource(R.string.common_import)) {
                onConfirm(
                    SettingsExportManager.ImportOptions(
                        importProviders = optProviders,
                        importEpgSources = optEpg,
                        importGlobalSettings = optGlobal,
                        importFavorites = optFavorites,
                    ),
                )
            },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onCancel),
        title = { CinemaText(stringResource(R.string.settings_import_select_title), role = TextRole.Title, tone = TextTone.Primary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OptionRow(
                    label = stringResource(R.string.settings_import_general_label),
                    checked = optGlobal,
                    onToggle = { optGlobal = !optGlobal },
                    modifier = Modifier.focusRequester(firstOptionFocusRequester),
                )
                if (parsed.hasProviders) {
                    OptionRow(
                        label = stringResource(R.string.settings_import_providers_count_format, parsed.settings.providers.size),
                        checked = optProviders,
                        onToggle = { optProviders = !optProviders },
                    )
                }
                if (parsed.hasEpgSources) {
                    OptionRow(
                        label = stringResource(R.string.settings_import_epg_sources_count_format, parsed.settings.epgSources.size),
                        checked = optEpg,
                        onToggle = { optEpg = !optEpg },
                    )
                }
                if (parsed.hasFavorites) {
                    OptionRow(
                        label = stringResource(R.string.settings_import_favorites_label),
                        checked = optFavorites,
                        onToggle = { optFavorites = !optFavorites },
                    )
                }
            }
        },
    )
}

@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCheckRow(
        checked = checked,
        onCheckedChange = { onToggle() },
        label = label,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Sources in the file that this device has already: Overwrite them, add them as copies (Duplicate),
 * skip them, or Cancel the import. Focus opens on Overwrite.
 */
@Composable
fun ConflictResolutionDialog(
    conflicts: List<String>,
    onResolve: (SettingsExportManager.ConflictResolution) -> Unit,
    onCancel: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onCancel,
        main =
            DialogAction(stringResource(R.string.common_overwrite)) {
                onResolve(SettingsExportManager.ConflictResolution.OVERWRITE)
            },
        others =
            listOf(
                DialogAction(stringResource(R.string.common_duplicate)) {
                    onResolve(SettingsExportManager.ConflictResolution.DUPLICATE)
                },
                DialogAction(stringResource(R.string.settings_import_skip_duplicates_button)) {
                    onResolve(SettingsExportManager.ConflictResolution.SKIP)
                },
            ),
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onCancel),
        title = {
            CinemaText(stringResource(R.string.settings_import_provider_conflict_title), role = TextRole.Title, tone = TextTone.Primary)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CinemaText(stringResource(R.string.settings_import_conflict_intro), role = TextRole.Body, tone = TextTone.Primary)
                conflicts.forEach { name ->
                    CinemaText(
                        "\u2022 $name",
                        modifier = Modifier.padding(start = Spacing.sm),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                }
                CinemaText(
                    stringResource(R.string.settings_import_choose_action),
                    modifier = Modifier.padding(top = Spacing.sm),
                    role = TextRole.Body,
                    tone = TextTone.Primary,
                )
            }
        },
    )
}
