package org.njarasoa.fijerena.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Settings → Diagnostics on mobile (developer mode): recorded crashes and process exit reasons,
 * shareable as plain text. See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-30.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.settings_diagnostics_share)
    // A top-bar icon is easy to hit by accident: clearing the log asks first.
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        CinemaAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_diagnostics_clear_confirm_title)) },
            confirmButton = {
                CinemaDialogTextButton(onClick = {
                    viewModel.clear()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.settings_diagnostics_clear)) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconAction(onClick = viewModel::reload, icon = CinemaIcons.Refresh, label = stringResource(R.string.common_refresh))
                    IconAction(
                        onClick = { showClearConfirm = true },
                        icon = CinemaIcons.Delete,
                        label = stringResource(R.string.settings_diagnostics_clear),
                    )
                    IconAction(
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, viewModel.asText(entries.orEmpty()))
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        icon = Icons.Rounded.Share,
                        label = shareTitle,
                        enabled = !entries.isNullOrEmpty(),
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_diagnostics_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                )
            }
            val loaded = entries
            if (loaded != null && loaded.isEmpty()) {
                item {
                    Text(stringResource(R.string.settings_diagnostics_empty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(loaded.orEmpty()) { entry ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = entry.detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                }
            }
        }
    }
}
