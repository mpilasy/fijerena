package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R

/**
 * Source: one **Manage sources ›** row (plan D6) opening the Sources list, where sources are
 * added, edited and given guide sources. Its value is the profile in use's sources
 * ([sourceNames]); there is no source in use (multi-source plan, Phase 6).
 */
@Composable
fun ProviderSettingsCard(
    sourceNames: List<String>,
    onManageProviders: () -> Unit,
    manageRowFocusRequester: FocusRequester? = null,
    /** Goes on the Manage sources row, the card's only focusable — the pane's entry row. */
    manageRowModifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_provider_section_title)) {
        SettingsRow(
            title = stringResource(R.string.settings_provider_manage_button),
            description = null,
            value = sourceNames.joinToString().ifEmpty { stringResource(R.string.provider_none_label) },
            onClick = onManageProviders,
            modifier = manageRowModifier,
            focusRequester = manageRowFocusRequester,
        )
    }
}
