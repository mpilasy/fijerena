package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyCheck
import org.njarasoa.fijerena.core.network.tmdb.TmdbKeyState
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbKeyViewModel
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.TvDimensions

/*
 * The user's own TMDB key (D4, docs/plans/20261011_public-release-fdroid-readiness-plan.md): a row
 * in Source & guide showing whether TMDB is on, the key's sub-pane, and TMDB's attribution in About.
 */

/** "Film and series details (TMDB) · On / Off / Key refused ›"; OK opens [TmdbKeyPane]. */
@Composable
fun TmdbKeySettingsRow(
    viewModel: TmdbKeyViewModel,
    onOpen: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsRow(
        title = stringResource(R.string.tmdb_settings_title),
        description = stringResource(R.string.tmdb_settings_desc),
        value = tmdbStatusText(uiState.state),
        onClick = onOpen,
        focusRequester = focusRequester,
    )
}

/**
 * The key's page: what TMDB adds, where to get a key, the key (masked; OK edits it), Test, Save,
 * then Remove key — never the first focus, and behind a confirm. A key TMDB refused is said here
 * and nowhere else.
 */
@Composable
fun TmdbKeyPane(
    viewModel: TmdbKeyViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var draft by rememberSaveable(uiState.savedKey) { mutableStateOf(uiState.savedKey) }
    var confirmRemove by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusWithRetry() }

    SettingsSubPane(title = stringResource(R.string.tmdb_settings_title), onBack = onBack) {
        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = stringResource(R.string.tmdb_logo_description),
            modifier = Modifier.height(TvDimensions.iconSmall),
        )
        SettingsNote(stringResource(R.string.tmdb_settings_desc))
        // The banner is about the saved key: gone once another key is typed or tested.
        if (uiState.state == TmdbKeyState.Rejected && draft == uiState.savedKey) {
            Text(
                text = stringResource(R.string.tmdb_rejected_message),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaError,
            )
        }
        ReadOnlyFieldWithEdit(
            value = draft,
            onValueChange = {
                draft = it.trim()
                viewModel.clearCheck()
            },
            label = stringResource(R.string.tmdb_key_label),
            visualTransformation = PasswordVisualTransformation(),
            displayText = maskedKey(draft),
            editButtonFocusRequester = fieldFocus,
        )
        SettingsNote(stringResource(R.string.tmdb_key_hint))
        tmdbCheckText(uiState.testing, uiState.lastCheck)?.let { SettingsNote(it) }
        CinemaSecondaryButton(
            onClick = { viewModel.test(draft) },
            text = stringResource(R.string.tmdb_test_button),
            enabled = draft.isNotBlank() && !uiState.testing,
            modifier = Modifier.fillMaxWidth(),
        )
        CinemaPrimaryButton(
            onClick = {
                viewModel.save(draft)
                onBack()
            },
            text = stringResource(R.string.provider_save_button),
            enabled = draft.isNotBlank() && draft != uiState.savedKey,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.savedKey.isNotBlank()) {
            CinemaSecondaryButton(
                onClick = { confirmRemove = true },
                text = stringResource(R.string.tmdb_remove_button),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmRemove) {
        ConfirmActionDialog(
            title = stringResource(R.string.tmdb_remove_confirm_title),
            text = stringResource(R.string.tmdb_remove_confirm_text),
            confirmText = stringResource(R.string.tmdb_remove_button),
            onConfirm = {
                confirmRemove = false
                viewModel.save("")
                onBack()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

/**
 * TMDB's required attribution (About): its logo and notice, as one focusable row so Up/Down
 * never skip it (focus contract rule 3); OK does nothing.
 */
@Composable
fun TmdbAttributionRow() {
    SettingsRow(
        title = stringResource(R.string.tmdb_attribution_title),
        description = null,
        onClick = {},
        chevron = false,
        supporting = {
            Column {
                Image(
                    painter = painterResource(R.drawable.tmdb_logo),
                    contentDescription = stringResource(R.string.tmdb_logo_description),
                    modifier = Modifier.height(TvDimensions.iconSmall),
                )
                SettingsNote(stringResource(R.string.tmdb_attribution))
            }
        },
    )
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

/** The key as the row shows it: its last four characters only. */
private fun maskedKey(key: String): String = if (key.length <= KEY_VISIBLE_TAIL) key else "••••" + key.takeLast(KEY_VISIBLE_TAIL)

private const val KEY_VISIBLE_TAIL = 4
