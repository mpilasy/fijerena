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
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Source: one **Manage sources ›** row (plan D6) opening the Sources list, where sources are
 * switched, edited and given guide sources. Its value is the source in use; under the title, its
 * URL and subscription as label / value pairs, the values in one column.
 */
@Composable
fun ProviderSettingsCard(
    providerName: String,
    currentUrl: String,
    subscriptionExpiry: String? = null,
    subscriptionMaxCons: String? = null,
    subscriptionIsTrial: Boolean = false,
    subscriptionStatus: String? = null,
    onManageProviders: () -> Unit,
    manageRowFocusRequester: FocusRequester? = null,
    /** Goes on the Manage sources row, the card's only focusable — the pane's entry row. */
    manageRowModifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(R.string.settings_provider_section_title)) {
        SettingsRow(
            title = stringResource(R.string.settings_provider_manage_button),
            description = null,
            value = providerName.ifEmpty { stringResource(R.string.provider_none_label) },
            onClick = onManageProviders,
            modifier = manageRowModifier,
            focusRequester = manageRowFocusRequester,
            supporting = {
                Column {
                    if (currentUrl.isNotEmpty()) {
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                    if (subscriptionExpiry != null) {
                        val isExpired = subscriptionStatus?.equals("Expired", ignoreCase = true) == true
                        LabelValue(
                            label = stringResource(R.string.settings_provider_expires_label),
                            value = subscriptionExpiry,
                            valueColor = if (isExpired) CinemaError else MaterialTheme.colorScheme.onSurface,
                        )
                        if (subscriptionMaxCons != null) {
                            LabelValue(
                                label = stringResource(R.string.settings_provider_max_connections_label),
                                value = subscriptionMaxCons,
                            )
                        }
                        if (subscriptionIsTrial) {
                            Text(
                                stringResource(R.string.settings_provider_trial_account_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaAccent,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** One label / value pair: the label in a fixed-width column, so the values line up under each other. */
@Composable
private fun LabelValue(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary,
            modifier = Modifier.width(TvDimensions.settingsInputWidth),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
