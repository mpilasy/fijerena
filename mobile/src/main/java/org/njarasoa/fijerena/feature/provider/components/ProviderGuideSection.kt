package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * Guide: an Xtream source's **Provides a guide** switch (plan D1), showing the effective value —
 * detected, or the viewer's. Flipping it is the viewer's choice and applies at once.
 */
@Composable
fun ColumnScope.ProviderGuideSection(
    providerSettings: ProviderSettings,
    onProvidesGuideChange: (Boolean) -> Unit,
) {
    Spacer(modifier = Modifier.height(CinemaSpacing.lg))

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            ProviderSectionTitle(title = stringResource(R.string.provider_section_guide))
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.provider_provides_guide_label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text =
                            if (providerSettings.providesGuide == false && !providerSettings.providesGuideSetByUser) {
                                stringResource(R.string.provider_provides_guide_detected_off_desc)
                            } else {
                                stringResource(R.string.provider_provides_guide_desc)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                }
                Spacer(modifier = Modifier.width(CinemaSpacing.md))
                Switch(
                    checked = providerSettings.providesGuideOn,
                    onCheckedChange = onProvidesGuideChange,
                )
            }
        }
    }
}
