package org.njarasoa.fijerena.feature.safemode

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SafeModeViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/**
 * Shown instead of home when the app started in crash-loop safe mode ([SafeMode]). Back has
 * nothing to pop here, so it leaves the app as from home. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-10.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSafeModeScreen(onShowDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel: SafeModeViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val clearState by viewModel.clearState.collectAsStateWithLifecycle()
    var confirmingClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { CinemaText(stringResource(R.string.safe_mode_title), role = TextRole.Title) }) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            CinemaText(stringResource(R.string.safe_mode_message))
            CinemaText(stringResource(R.string.safe_mode_hint), tone = TextTone.Secondary)
            clearStateText(clearState)?.let { status ->
                CinemaText(status)
            }
            CinemaButton(
                onClick = { activity?.let { SafeMode.leave(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) { CinemaText(stringResource(R.string.safe_mode_continue), role = TextRole.Label, tone = TextTone.Inherit) }
            CinemaOutlinedButton(
                onClick = { confirmingClear = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = clearState != SafeModeViewModel.ClearState.CLEARING,
            ) { CinemaText(stringResource(R.string.safe_mode_clear_caches), role = TextRole.Label, tone = TextTone.Inherit) }
            CinemaOutlinedButton(
                onClick = onShowDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) { CinemaText(stringResource(R.string.safe_mode_diagnostics), role = TextRole.Label, tone = TextTone.Inherit) }
        }
    }

    if (confirmingClear) {
        CinemaAlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { CinemaText(stringResource(R.string.safe_mode_clear_confirm_title), role = TextRole.Title) },
            text = { CinemaText(stringResource(R.string.safe_mode_clear_confirm_message), tone = TextTone.Secondary) },
            confirmButton = {
                CinemaDialogActionButton(onClick = {
                    confirmingClear = false
                    viewModel.clearCaches()
                }, kind = ButtonKind.Danger) {
                    CinemaText(stringResource(R.string.safe_mode_clear_confirm), role = TextRole.Label, tone = TextTone.Inherit)
                }
            },
            dismissButton = {
                CinemaDialogActionButton(
                    onClick = { confirmingClear = false },
                    kind = ButtonKind.Other,
                ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
            },
        )
    }
}

@Composable
private fun clearStateText(state: SafeModeViewModel.ClearState): String? =
    when (state) {
        SafeModeViewModel.ClearState.IDLE -> null
        SafeModeViewModel.ClearState.CLEARING -> stringResource(R.string.safe_mode_clearing)
        SafeModeViewModel.ClearState.DONE -> stringResource(R.string.safe_mode_cleared)
        SafeModeViewModel.ClearState.FAILED -> stringResource(R.string.safe_mode_clear_failed)
    }
