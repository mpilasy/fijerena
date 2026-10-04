package org.njarasoa.fijerena.feature.safemode

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SafeModeViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.safe_mode_title)) }) },
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
            Text(
                text = stringResource(R.string.safe_mode_message),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.safe_mode_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            clearStateText(clearState)?.let { status ->
                Text(text = status, style = MaterialTheme.typography.bodyMedium)
            }
            CinemaButton(
                onClick = { activity?.let { SafeMode.leave(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.safe_mode_continue)) }
            CinemaOutlinedButton(
                onClick = { confirmingClear = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = clearState != SafeModeViewModel.ClearState.CLEARING,
            ) { Text(stringResource(R.string.safe_mode_clear_caches)) }
            CinemaOutlinedButton(
                onClick = onShowDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.safe_mode_diagnostics)) }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.safe_mode_clear_confirm_title)) },
            text = { Text(stringResource(R.string.safe_mode_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingClear = false
                    viewModel.clearCaches()
                }) { Text(stringResource(R.string.safe_mode_clear_confirm), color = CinemaError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.common_cancel)) }
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
