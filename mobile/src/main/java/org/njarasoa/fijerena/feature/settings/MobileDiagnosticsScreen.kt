package org.njarasoa.fijerena.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.components.text.CinemaCodeText

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
            title = { CinemaText(stringResource(R.string.settings_diagnostics_clear_confirm_title), role = TextRole.Title) },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        viewModel.clear()
                        showClearConfirm = false
                    },
                    kind = ButtonKind.Other,
                ) { CinemaText(stringResource(R.string.settings_diagnostics_clear), role = TextRole.Label, tone = TextTone.Inherit) }
            },
            dismissButton = {
                CinemaDialogActionButton(
                    onClick = { showClearConfirm = false },
                    kind = ButtonKind.Other,
                ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { CinemaText(stringResource(R.string.settings_diagnostics_title), role = TextRole.Title) },
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
                    .padding(start = CinemaSpacing.md, top = CinemaSpacing.md, end = CinemaSpacing.md),
            contentPadding = PaddingValues(bottom = CinemaSpacing.md + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            item {
                CinemaText(stringResource(R.string.settings_diagnostics_desc), tone = TextTone.Secondary)
            }
            val loaded = entries
            if (loaded != null && loaded.isEmpty()) {
                item {
                    CinemaText(stringResource(R.string.settings_diagnostics_empty))
                }
            }
            items(loaded.orEmpty()) { entry ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        CinemaText(entry.title, role = TextRole.Heading, tone = TextTone.Accent)
                        CinemaCodeText(entry.detail)
                    }
                }
            }
        }
    }
}
