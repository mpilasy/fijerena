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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.SafeModeViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.network.R as NetworkR

/** Which action focus lands on when the screen (re)appears. */
private const val FOCUS_CONTINUE = 0
private const val FOCUS_CLEAR = 1
private const val FOCUS_DIAGNOSTICS = 2
private const val FOCUS_SEND = 3

/**
 * Shown instead of home when the app started in crash-loop safe mode ([SafeMode]). Focus lands on
 * Continue, and comes back to the action the user left from (the confirmation, Diagnostics). Back
 * has nothing to pop here, so it leaves the app as from home. See the next level rock solid
 * resilience plan, R-10. On a linked device that shares its logs, an icon action after Show logs
 * sends the device report to the sync group (the device diagnostics sync plan).
 */
@Composable
fun SafeModeScreen(onShowDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel: SafeModeViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val clearState by viewModel.clearState.collectAsStateWithLifecycle()
    val canSendLogs by viewModel.canSendLogs.collectAsStateWithLifecycle()
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()
    val storageLow by viewModel.storageLow.collectAsStateWithLifecycle()
    val sendFocus = remember { FocusRequester() }
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
                    FOCUS_SEND -> sendFocus
                    else -> continueFocus
                }
            target.requestFocusWithRetry(fallback = continueFocus)
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
            CinemaText(
                stringResource(R.string.safe_mode_title),
                role = TextRole.Display,
                tone = TextTone.Primary,
            )
            CinemaText(
                stringResource(R.string.safe_mode_message),
                role = TextRole.Body,
                tone = TextTone.Primary,
            )
            CinemaText(
                stringResource(R.string.safe_mode_hint),
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
            clearStateText(clearState)?.let { status ->
                CinemaText(status, role = TextRole.Body, tone = TextTone.Primary)
            }
            sendStateText(sendState)?.let { status ->
                CinemaText(
                    status,
                    role = TextRole.Body,
                    tone =
                        if (sendState ==
                            SafeModeViewModel.SendState.FAILED
                        ) {
                            TextTone.Danger
                        } else {
                            TextTone.Primary
                        },
                )
            }
            // Raw error text for a Nerd only, masked.
            if (LocalNerd.current && sendState == SafeModeViewModel.SendState.FAILED) {
                sendError?.let { CinemaText("[dev] $it", role = TextRole.BodySmall, tone = TextTone.Secondary) }
            }
            // A full disk is a likely cause of the crashes: say what to clear, above Clear caches.
            if (storageLow) {
                CinemaText(stringResource(NetworkR.string.error_storage_low), role = TextRole.Body, tone = TextTone.Danger)
            }
            SafeModeActions(
                canSendLogs = canSendLogs,
                onContinue = { activity?.let { SafeMode.leave(it) } },
                onClearCaches = {
                    focusTarget = FOCUS_CLEAR
                    confirmingClear = true
                },
                onShowLogs = {
                    focusTarget = FOCUS_DIAGNOSTICS
                    onShowDiagnostics()
                },
                onSendLogs = {
                    focusTarget = FOCUS_SEND
                    viewModel.sendLogs()
                },
                modifier = Modifier.padding(top = Spacing.md.scaled(scale)),
                continueFocus = continueFocus,
                clearFocus = clearFocus,
                diagnosticsFocus = diagnosticsFocus,
                sendFocus = sendFocus,
            )
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

/**
 * Continue, Clear caches, Show logs and, when [canSendLogs], the send icon action. The icon action
 * is taller than the text buttons, so the row centres them all on one line.
 */
@Composable
internal fun SafeModeActions(
    canSendLogs: Boolean,
    onContinue: () -> Unit,
    onClearCaches: () -> Unit,
    onShowLogs: () -> Unit,
    onSendLogs: () -> Unit,
    modifier: Modifier = Modifier,
    continueFocus: FocusRequester = remember { FocusRequester() },
    clearFocus: FocusRequester = remember { FocusRequester() },
    diagnosticsFocus: FocusRequester = remember { FocusRequester() },
    sendFocus: FocusRequester = remember { FocusRequester() },
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(LocalUiScale.current)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaPrimaryButton(
            onClick = onContinue,
            text = stringResource(R.string.safe_mode_continue),
            modifier = Modifier.focusRequester(continueFocus),
        )
        CinemaSecondaryButton(
            onClick = onClearCaches,
            text = stringResource(R.string.safe_mode_clear_caches),
            modifier = Modifier.focusRequester(clearFocus),
        )
        CinemaSecondaryButton(
            onClick = onShowLogs,
            text = stringResource(R.string.safe_mode_diagnostics),
            modifier = Modifier.focusRequester(diagnosticsFocus),
        )
        if (canSendLogs) {
            TvIconAction(
                onClick = onSendLogs,
                icon = CinemaIcons.Send,
                label = stringResource(R.string.safe_mode_send_logs),
                modifier = Modifier.focusRequester(sendFocus),
            )
        }
    }
}

@Composable
private fun sendStateText(state: SafeModeViewModel.SendState): String? =
    when (state) {
        SafeModeViewModel.SendState.IDLE -> null
        SafeModeViewModel.SendState.SENDING -> stringResource(R.string.safe_mode_logs_sending)
        SafeModeViewModel.SendState.SENT -> stringResource(R.string.safe_mode_logs_sent)
        SafeModeViewModel.SendState.FAILED -> stringResource(R.string.safe_mode_logs_send_failed)
    }

@Composable
private fun clearStateText(state: SafeModeViewModel.ClearState): String? =
    when (state) {
        SafeModeViewModel.ClearState.IDLE -> null
        SafeModeViewModel.ClearState.CLEARING -> stringResource(R.string.safe_mode_clearing)
        SafeModeViewModel.ClearState.DONE -> stringResource(R.string.safe_mode_cleared)
        SafeModeViewModel.ClearState.FAILED -> stringResource(R.string.safe_mode_clear_failed)
    }
