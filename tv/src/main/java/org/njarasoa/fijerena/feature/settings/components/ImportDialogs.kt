package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun ImportOptionsDialog(
    parsed: SettingsExportManager.ParsedImport,
    initialOptions: SettingsExportManager.ImportOptions,
    onConfirm: (SettingsExportManager.ImportOptions) -> Unit,
    onCancel: () -> Unit
) {
    var optProviders by remember { mutableStateOf(initialOptions.importProviders) }
    var optEpg by remember { mutableStateOf(initialOptions.importEpgSources) }
    var optGlobal by remember { mutableStateOf(initialOptions.importGlobalSettings) }
    var optFavorites by remember { mutableStateOf(initialOptions.importFavorites) }
    val importDialogFocusRequester = remember { FocusRequester() }

    BackHandler {
        onCancel()
    }

    LaunchedEffect(Unit) {
        try { importDialogFocusRequester.requestFocus() } catch (_: IllegalStateException) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidth)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .focusRestorer { importDialogFocusRequester },
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Select What to Import",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CinemaAccent
                )

                val checkboxColors = CheckboxDefaults.colors(
                    checkedColor = CinemaAccent,
                    uncheckedColor = CinemaTextSecondary,
                    checkmarkColor = CinemaTextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.tvFocusableNoScale()
                ) {
                    Checkbox(checked = optGlobal, onCheckedChange = { optGlobal = it }, colors = checkboxColors)
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    Text("General Settings", style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
                }
                if (parsed.hasProviders) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.tvFocusableNoScale()
                    ) {
                        Checkbox(checked = optProviders, onCheckedChange = { optProviders = it }, colors = checkboxColors)
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text("Providers (${parsed.settings.providers.size})", style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
                    }
                }
                if (parsed.hasEpgSources) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.tvFocusableNoScale()
                    ) {
                        Checkbox(checked = optEpg, onCheckedChange = { optEpg = it }, colors = checkboxColors)
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text("EPG Sources (${parsed.settings.epgSources.size})", style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
                    }
                }
                if (parsed.hasFavorites) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.tvFocusableNoScale()
                    ) {
                        Checkbox(checked = optFavorites, onCheckedChange = { optFavorites = it }, colors = checkboxColors)
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text("Favorites", style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            val options = SettingsExportManager.ImportOptions(
                                importProviders = optProviders,
                                importEpgSources = optEpg,
                                importGlobalSettings = optGlobal,
                                importFavorites = optFavorites
                            )
                            onConfirm(options)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(importDialogFocusRequester),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaAccent,
                            contentColor = CinemaTextPrimary
                        )
                    ) { Text("Import") }
                    androidx.compose.material3.Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = CinemaTextPrimary
                        )
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun ConflictResolutionDialog(
    conflicts: List<String>,
    onResolve: (SettingsExportManager.ConflictResolution) -> Unit,
    onCancel: () -> Unit
) {
    val conflictDialogFocusRequester = remember { FocusRequester() }

    BackHandler {
        onCancel()
    }

    LaunchedEffect(Unit) {
        try { conflictDialogFocusRequester.requestFocus() } catch (_: IllegalStateException) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidth)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .focusRestorer { conflictDialogFocusRequester },
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Provider Conflict",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CinemaAccent
                )
                Text(
                    text = "The following provider(s) already exist:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextPrimary
                )
                conflicts.forEach { name ->
                    Text(
                        text = "\u2022 $name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary
                    )
                }
                Text(
                    text = "What would you like to do?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextPrimary
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            onResolve(SettingsExportManager.ConflictResolution.OVERWRITE)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(conflictDialogFocusRequester),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaAccent,
                            contentColor = CinemaTextPrimary
                        )
                    ) { Text("Overwrite") }
                    androidx.compose.material3.Button(
                        onClick = {
                            onResolve(SettingsExportManager.ConflictResolution.DUPLICATE)
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = CinemaTextPrimary
                        )
                    ) { Text("Duplicate") }
                    androidx.compose.material3.Button(
                        onClick = {
                            onResolve(SettingsExportManager.ConflictResolution.SKIP)
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = CinemaTextPrimary
                        )
                    ) { Text("Skip") }
                }
            }
        }
    }
}
