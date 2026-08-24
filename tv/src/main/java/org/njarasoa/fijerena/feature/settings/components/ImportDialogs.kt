package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@OptIn(ExperimentalTvMaterial3Api::class)
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

    LaunchedEffect(Unit) {
        try {
            firstOptionFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideUiScaledDensity {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(CinemaBackground.copy(alpha = LocalUiStyle.current.dialog.scrimAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(TvDimensions.dialogWidth)
                            .height(TvDimensions.dialogWidth)
                            .padding(Spacing.xxl),
                    color = CinemaSurface,
                    shape = RoundedCornerShape(CornerRadius.large),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.xxl),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_import_select_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = CinemaAccent,
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .focusRestorer { firstOptionFocusRequester },
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

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            CinemaPrimaryButton(
                                onClick = {
                                    onConfirm(
                                        SettingsExportManager.ImportOptions(
                                            importProviders = optProviders,
                                            importEpgSources = optEpg,
                                            importGlobalSettings = optGlobal,
                                            importFavorites = optFavorites,
                                        ),
                                    )
                                },
                                text = stringResource(R.string.common_import),
                                modifier = Modifier.weight(1f),
                            )
                            CinemaSecondaryButton(
                                onClick = onCancel,
                                text = stringResource(R.string.common_cancel),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    // Interactive TV Surface for D-pad focus
    androidx.tv.material3.Surface(
        onClick = {
            onToggle()
        },
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(CornerRadius.small)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = CinemaSurfaceVariant,
                contentColor = CinemaTextPrimary,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(TvDimensions.borderFocused, CinemaAccentLight),
                        shape = RoundedCornerShape(CornerRadius.small),
                    ),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.sm),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = CinemaAccent,
                        uncheckedColor = CinemaTextSecondary,
                        checkmarkColor = CinemaTextPrimary,
                        disabledCheckedColor = CinemaAccent,
                        disabledUncheckedColor = CinemaTextSecondary,
                    ),
                enabled = false,
            )
            Spacer(modifier = Modifier.width(CinemaSpacing.sm))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ConflictResolutionDialog(
    conflicts: List<String>,
    onResolve: (SettingsExportManager.ConflictResolution) -> Unit,
    onCancel: () -> Unit,
) {
    val conflictDialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            conflictDialogFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideUiScaledDensity {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(CinemaBackground.copy(alpha = LocalUiStyle.current.dialog.scrimAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .width(TvDimensions.dialogWidth)
                            .height(TvDimensions.dialogWidth)
                            .padding(Spacing.xxl),
                    color = CinemaSurface,
                    shape = RoundedCornerShape(CornerRadius.large),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .padding(Spacing.xxl)
                                .focusRestorer { conflictDialogFocusRequester }
                                .focusProperties { exit = { FocusRequester.Cancel } },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_import_provider_conflict_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = CinemaAccent,
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Text(
                            text = stringResource(R.string.settings_import_conflict_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextPrimary,
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            conflicts.forEach { name ->
                                Text(
                                    text = "\u2022 $name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaTextSecondary,
                                    modifier = Modifier.padding(start = Spacing.sm),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Text(
                            text = stringResource(R.string.settings_import_choose_action),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextPrimary,
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            CinemaPrimaryButton(
                                onClick = {
                                    onResolve(SettingsExportManager.ConflictResolution.OVERWRITE)
                                },
                                text = stringResource(R.string.common_overwrite),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(conflictDialogFocusRequester),
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    onResolve(SettingsExportManager.ConflictResolution.DUPLICATE)
                                },
                                text = stringResource(R.string.common_duplicate),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            CinemaSecondaryButton(
                                onClick = {
                                    onResolve(SettingsExportManager.ConflictResolution.SKIP)
                                },
                                text = stringResource(R.string.settings_import_skip_duplicates_button),
                                modifier = Modifier.weight(1f),
                            )
                            CinemaSecondaryButton(
                                onClick = onCancel,
                                text = stringResource(R.string.common_cancel),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
