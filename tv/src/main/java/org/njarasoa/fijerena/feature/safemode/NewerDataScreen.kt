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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.player.diagnostics.restartProcess
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Shown instead of home when `providers.db` was written by a newer build ([ProvidersDbGuard]).
 * Focus lands on Close, the safe choice; Reset sources asks first, sets the file aside and
 * restarts. See docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-01.
 */
@Composable
fun NewerDataScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scale = LocalUiScale.current
    var confirmingReset by remember { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    val resetFocus = remember { FocusRequester() }
    var resetChosen by remember { mutableStateOf(false) }

    // On entry, and back on Reset sources once its confirmation closes.
    LaunchedEffect(confirmingReset) {
        if (!confirmingReset) (if (resetChosen) resetFocus else closeFocus).requestFocusWithRetry()
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
                text = stringResource(R.string.newer_data_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    stringResource(
                        R.string.newer_data_message,
                        ProvidersDbGuard.newerFileVersion,
                        ProvidersDbGuard.supportedVersion,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextPrimary,
            )
            Text(
                text = stringResource(R.string.newer_data_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.md.scaled(scale)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                CinemaPrimaryButton(
                    onClick = { activity?.finish() },
                    text = stringResource(R.string.common_close),
                    modifier = Modifier.focusRequester(closeFocus),
                )
                CinemaSecondaryButton(
                    onClick = {
                        resetChosen = true
                        confirmingReset = true
                    },
                    text = stringResource(R.string.newer_data_reset),
                    modifier = Modifier.focusRequester(resetFocus),
                )
            }
        }
    }

    if (confirmingReset) {
        ConfirmActionDialog(
            title = stringResource(R.string.newer_data_reset_confirm_title),
            text = stringResource(R.string.newer_data_reset_confirm_message),
            confirmText = stringResource(R.string.newer_data_reset_confirm),
            onConfirm = {
                confirmingReset = false
                ProvidersDbGuard.setAside(context)
                activity?.let { restartProcess(it) }
            },
            onDismiss = { confirmingReset = false },
        )
    }
}
