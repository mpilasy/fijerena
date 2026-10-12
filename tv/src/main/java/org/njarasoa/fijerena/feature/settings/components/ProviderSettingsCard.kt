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
import org.njarasoa.fijerena.core.ui.viewmodels.SourceSubscription
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Source: one **Manage sources ›** row (plan D6) opening the Sources list, where sources are
 * added, edited and given guide sources. Its value is the profile in use's sources
 * ([sourceNames]); there is no source in use (multi-source plan, Phase 6). Under the title, each
 * source's URL and subscription as label / value pairs, the values in one column; with several
 * sources, each block starts with the source's name.
 */
@Composable
fun ProviderSettingsCard(
    sourceNames: List<String>,
    subscriptions: List<SourceSubscription>,
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
            supporting =
                if (subscriptions.isEmpty()) {
                    null
                } else {
                    { SourceSubscriptions(subscriptions) }
                },
        )
    }
}

@Composable
private fun SourceSubscriptions(subscriptions: List<SourceSubscription>) {
    val named = subscriptions.size > 1
    Column {
        subscriptions.forEach { source ->
            if (named) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (source.url.isNotEmpty()) {
                Text(
                    text = source.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
            source.expiry?.let { expiry ->
                LabelValue(
                    label = stringResource(R.string.settings_provider_expires_label),
                    value = expiry,
                    valueColor = if (source.isExpired) CinemaError else MaterialTheme.colorScheme.onSurface,
                )
                source.maxConnections?.let { maxConnections ->
                    LabelValue(
                        label = stringResource(R.string.settings_provider_max_connections_label),
                        value = maxConnections,
                    )
                }
                if (source.isTrial) {
                    Text(
                        stringResource(R.string.settings_provider_trial_account_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaAccent,
                    )
                }
            }
        }
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
