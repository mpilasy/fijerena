package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyCheck
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbKeyViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/*
 * The user's own TMDB key (D4, docs/plans/20261011_public-release-fdroid-readiness-plan.md): a row
 * in Source & guide showing whether TMDB is on, its dialog, and TMDB's attribution in About.
 */

/** "Film and series details (TMDB)" with On / Off / Key refused as its value; opens [TmdbKeyDialog]. */
@Composable
fun TmdbKeySettingsRow(
    viewModel: TmdbKeyViewModel,
    onOpen: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsListRow(
        title = stringResource(R.string.tmdb_settings_title),
        summary = tmdbStatusText(uiState.state),
        onClick = onOpen,
    )
}

/**
 * What TMDB adds, where to get a key, the key (hidden), Test and Remove key; Save keeps it. A key
 * TMDB refused is said here and nowhere else.
 */
@Composable
fun TmdbKeyDialog(
    viewModel: TmdbKeyViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by rememberSaveable(uiState.savedKey) { mutableStateOf(uiState.savedKey) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tmdb_settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Image(
                    painter = painterResource(R.drawable.tmdb_logo),
                    contentDescription = stringResource(R.string.tmdb_logo_description),
                    modifier = Modifier.height(MobileDimensions.iconSmall),
                )
                Text(stringResource(R.string.tmdb_settings_desc), style = MaterialTheme.typography.bodyMedium)
                // The banner is about the saved key: gone once another key is typed or tested.
                if (uiState.state == TmdbKeyState.Rejected && draft == uiState.savedKey) {
                    Text(
                        text = stringResource(R.string.tmdb_rejected_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.trim()
                        viewModel.clearCheck()
                    },
                    label = { Text(stringResource(R.string.tmdb_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.tmdb_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                tmdbCheckText(uiState.testing, uiState.lastCheck)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CinemaDialogActionButton(
                        onClick = { viewModel.test(draft) },
                        enabled = draft.isNotBlank() && !uiState.testing,
                        kind = ButtonKind.Other,
                    ) { Text(stringResource(R.string.tmdb_test_button)) }
                    if (uiState.savedKey.isNotBlank()) {
                        CinemaDialogActionButton(
                            onClick = {
                                viewModel.save("")
                                onDismiss()
                            },
                            kind = ButtonKind.Other,
                        ) { Text(stringResource(R.string.tmdb_remove_button)) }
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    viewModel.save(draft)
                    onDismiss()
                },
                enabled = draft.isNotBlank() && draft != uiState.savedKey,
                kind = ButtonKind.Main,
            ) { Text(stringResource(R.string.provider_save_button)) }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** TMDB's required attribution, in About: its logo and notice. */
@Composable
fun TmdbAttribution() {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = stringResource(R.string.tmdb_logo_description),
            modifier = Modifier.height(MobileDimensions.iconSmall),
        )
        Text(
            text = stringResource(R.string.tmdb_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
    }
}

@Composable
private fun tmdbStatusText(state: TmdbKeyState): String =
    when (state) {
        TmdbKeyState.None -> stringResource(R.string.common_off)
        is TmdbKeyState.Active -> stringResource(R.string.tmdb_status_on)
        TmdbKeyState.Rejected -> stringResource(R.string.tmdb_status_rejected)
    }

@Composable
private fun tmdbCheckText(
    testing: Boolean,
    check: TmdbKeyCheck?,
): String? =
    when {
        testing -> stringResource(R.string.tmdb_testing)
        check == TmdbKeyCheck.VALID -> stringResource(R.string.tmdb_check_valid)
        check == TmdbKeyCheck.REJECTED -> stringResource(R.string.tmdb_check_rejected)
        check == TmdbKeyCheck.UNREACHABLE -> stringResource(R.string.tmdb_check_unreachable)
        else -> null
    }
