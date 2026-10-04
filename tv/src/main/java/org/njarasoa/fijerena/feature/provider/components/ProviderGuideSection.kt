package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Guide: an Xtream source's **Provides a guide** switch (plan D1), showing the effective value —
 * detected, or the viewer's. Flipping it is the viewer's choice and applies at once. Rows of [pane],
 * the settings column of Edit Source.
 */
@Composable
fun ProviderGuideSection(
    providerSettings: ProviderSettings,
    onProvidesGuideChange: (Boolean) -> Unit,
    pane: PaneFocusState,
) {
    val scale = LocalUiScale.current
    ProviderSectionTitle(title = stringResource(R.string.provider_section_guide))
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
    TvSwitchRow(
        checked = providerSettings.providesGuideOn,
        onCheckedChange = onProvidesGuideChange,
        label = stringResource(R.string.provider_provides_guide_label),
        description =
            if (providerSettings.providesGuide == false && !providerSettings.providesGuideSetByUser) {
                stringResource(R.string.provider_provides_guide_detected_off_desc)
            } else {
                stringResource(R.string.provider_provides_guide_desc)
            },
        modifier = Modifier.paneItem(pane, "provides_guide"),
    )
}
