package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Source: one **Manage sources ›** row (plan D6) opening the Sources list, where sources are
 * switched, edited and given guide sources. Its value is the source in use, with its URL and
 * subscription under it.
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
    scale: Float,
    manageRowFocusRequester: FocusRequester? = null,
    /** Goes on the Manage sources row, the card's only focusable — the pane's entry row. */
    manageRowModifier: Modifier = Modifier,
) {
    val bodySmallStyle =
        MaterialTheme.typography.bodySmall.copy(
            fontSize =
                MaterialTheme.typography.bodySmall.fontSize
                    .scaled(scale),
        )

    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_provider_section_title),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize =
                                MaterialTheme.typography.titleMedium.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaAccent,
                    modifier = Modifier.weight(1f),
                )
                SettingsScopeChip(SettingsScope.SOURCE)
            }
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            TvInputListItem(
                selected = false,
                onClick = onManageProviders,
                modifier = manageRowModifier.then(manageRowFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                trailingContent = {
                    Text(
                        text = "${providerName.ifEmpty { stringResource(R.string.provider_none_label) }} ›",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                },
                supportingContent = {
                    Column {
                        if (currentUrl.isNotEmpty()) {
                            Text(
                                text = currentUrl,
                                style = bodySmallStyle,
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            )
                        }
                        if (subscriptionExpiry != null) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            val isExpired = subscriptionStatus?.equals("Expired", ignoreCase = true) == true
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.settings_provider_expires_label),
                                    style = bodySmallStyle,
                                    color = CinemaTextSecondary,
                                )
                                Text(
                                    text = subscriptionExpiry,
                                    style = bodySmallStyle,
                                    color = if (isExpired) CinemaError else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (subscriptionMaxCons != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        stringResource(R.string.settings_provider_max_connections_label),
                                        style = bodySmallStyle,
                                        color = CinemaTextSecondary,
                                    )
                                    Text(subscriptionMaxCons, style = bodySmallStyle)
                                }
                            }
                            if (subscriptionIsTrial) {
                                Text(
                                    stringResource(R.string.settings_provider_trial_account_label),
                                    style = bodySmallStyle,
                                    color = CinemaAccent,
                                )
                            }
                        }
                    }
                },
                headlineContent = { Text(stringResource(R.string.settings_provider_manage_button)) },
            )
        }
    }
}
