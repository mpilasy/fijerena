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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

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
) {
    val bodySmallStyle =
        MaterialTheme.typography.bodySmall.copy(
            fontSize =
                MaterialTheme.typography.bodySmall.fontSize
                    .scaled(scale),
        )

    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = "Provider",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = providerName,
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize =
                                    MaterialTheme.typography.bodyLarge.fontSize
                                        .scaled(scale),
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currentUrl,
                        style = bodySmallStyle,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                    if (subscriptionExpiry != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        val isExpired = subscriptionStatus?.equals("Expired", ignoreCase = true) == true
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Expires", style = bodySmallStyle, color = CinemaTextSecondary)
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
                                Text("Max connections", style = bodySmallStyle, color = CinemaTextSecondary)
                                Text(subscriptionMaxCons, style = bodySmallStyle)
                            }
                        }
                        if (subscriptionIsTrial) {
                            Text("Trial account", style = bodySmallStyle, color = CinemaAccent)
                        }
                    }
                }
                CinemaSecondaryButton(
                    onClick = onManageProviders,
                    text = "Manage Providers",
                )
            }
        }
    }
}
