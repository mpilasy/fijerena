package org.njarasoa.fijerena.feature.safemode

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.SafeModeViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/** Which action focus lands on when the screen (re)appears. */
private const val FOCUS_CONTINUE = 0
private const val FOCUS_CLEAR = 1
private const val FOCUS_DIAGNOSTICS = 2

/**
 * Shown instead of home when the app started in crash-loop safe mode ([SafeMode]). Focus lands
 * on Continue, and comes back to the action the user left from (the confirmation, Diagnostics).
 * Back has nothing to pop here, so it leaves the app as from home. See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-10.
 */
@Composable
fun SafeModeScreen(onShowDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel: SafeModeViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val clearState by viewModel.clearState.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current
    var confirmingClear by remember { mutableStateOf(false) }
    var focusTarget by rememberSaveable { mutableIntStateOf(FOCUS_CONTINUE) }
    val continueFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val diagnosticsFocus = remember { FocusRequester() }

    // On entry, on return from Diagnostics, and once the confirmation closes.
    LaunchedEffect(confirmingClear) {
        if (!confirmingClear) {
            val target =
                when (focusTarget) {
                    FOCUS_CLEAR -> clearFocus
                    FOCUS_DIAGNOSTICS -> diagnosticsFocus
                    else -> continueFocus
                }
            target.requestFocusWithRetry()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = TvDimensions.dialogWidthLarge.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
        ) {
            Text(
                text = stringResource(R.string.safe_mode_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.safe_mode_message),
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextPrimary,
            )
            Text(
                text = stringResource(R.string.safe_mode_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary,
            )
            clearStateText(clearState)?.let { status ->
                Text(text = status, style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
            }
            Row(
                modifier = Modifier.padding(top = Spacing.md.scaled(scale)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                CinemaPrimaryButton(
                    onClick = { activity?.let { SafeMode.leave(it) } },
                    text = stringResource(R.string.safe_mode_continue),
                    modifier = Modifier.focusRequester(continueFocus),
                )
                CinemaSecondaryButton(
                    onClick = {
                        focusTarget = FOCUS_CLEAR
                        confirmingClear = true
                    },
                    text = stringResource(R.string.safe_mode_clear_caches),
                    modifier = Modifier.focusRequester(clearFocus),
                )
                CinemaSecondaryButton(
                    onClick = {
                        focusTarget = FOCUS_DIAGNOSTICS
                        onShowDiagnostics()
                    },
                    text = stringResource(R.string.safe_mode_diagnostics),
                    modifier = Modifier.focusRequester(diagnosticsFocus),
                )
            }
        }
    }

    if (confirmingClear) {
        ConfirmActionDialog(
            title = stringResource(R.string.safe_mode_clear_confirm_title),
            text = stringResource(R.string.safe_mode_clear_confirm_message),
            confirmText = stringResource(R.string.safe_mode_clear_confirm),
            onConfirm = {
                confirmingClear = false
                viewModel.clearCaches()
            },
            onDismiss = { confirmingClear = false },
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
