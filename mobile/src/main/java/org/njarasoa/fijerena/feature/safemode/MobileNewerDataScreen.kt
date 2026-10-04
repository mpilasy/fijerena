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
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.player.diagnostics.restartProcess
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/**
 * Shown instead of home when `providers.db` was written by a newer build ([ProvidersDbGuard]).
 * Reset sources asks first, sets the file aside and restarts. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-01.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileNewerDataScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var confirmingReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.newer_data_title)) }) },
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
                text =
                    stringResource(
                        R.string.newer_data_message,
                        ProvidersDbGuard.newerFileVersion,
                        ProvidersDbGuard.supportedVersion,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.newer_data_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            CinemaButton(
                onClick = { activity?.finish() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_close)) }
            CinemaOutlinedButton(
                onClick = { confirmingReset = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.newer_data_reset)) }
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.newer_data_reset_confirm_title)) },
            text = { Text(stringResource(R.string.newer_data_reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    ProvidersDbGuard.setAside(context)
                    activity?.let { restartProcess(it) }
                }) { Text(stringResource(R.string.newer_data_reset_confirm), color = CinemaError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
