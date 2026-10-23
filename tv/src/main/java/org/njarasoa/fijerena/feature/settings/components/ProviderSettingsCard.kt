package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Source: one **Manage sources ›** row (plan D6) opening the Sources list, where sources are
 * added, edited and given guide sources, and each one's kind and subscription are shown. Its value
 * is the profile in use's sources ([sourceNames]).
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
