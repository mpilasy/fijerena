package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Guide, for a source with live channels: an Xtream source's **Provides a guide** switch (plan
 * D1), showing the effective value — detected, or the viewer's; flipping it is the viewer's choice
 * and applies at once. Then **Guide sources ›** (D3), its value the count and last refresh, opening
 * this source's guide sources; [guideSourcesModifier] carries its Back focus target. Rows of
 * [pane], the settings column of Edit Source.
 */
@Composable
fun ProviderGuideSection(
    providerId: Long,
    showProvidesGuide: Boolean,
    providerSettings: ProviderSettings,
    onProvidesGuideChange: (Boolean) -> Unit,
    onGuideSourcesClick: () -> Unit,
    pane: PaneFocusState,
    guideSourcesModifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val context = LocalContext.current
    val sources by remember(providerId) {
        ProviderRepository(context.applicationContext).getGuideSources(providerId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    ProviderSectionTitle(title = stringResource(R.string.provider_section_guide))
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
    if (showProvidesGuide) {
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
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
    }
    TvInputListItem(
        selected = false,
        onClick = onGuideSourcesClick,
        modifier = guideSourcesModifier.paneItem(pane, "guide_sources"),
        supportingContent = {
            Text(
                text = guideSourcesValue(sources.size, sources.maxOfOrNull { it.lastIngestedAtMs } ?: 0L),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize =
                            MaterialTheme.typography.bodySmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        },
        trailingContent = { Text(text = "›", style = MaterialTheme.typography.bodyMedium) },
        headlineContent = { Text(stringResource(R.string.epg_sources_header)) },
    )
}

/** "3 · last refresh <date, time>", "3 · never refreshed", or none configured. */
@Composable
private fun guideSourcesValue(
    count: Int,
    lastRefreshMs: Long,
): String =
    when {
        count == 0 -> {
            stringResource(R.string.epg_summary_no_sources)
        }

        lastRefreshMs > 0 -> {
            val context = LocalContext.current
            val formatted = remember(lastRefreshMs) { NumberUtils.formatTimestamp(context, lastRefreshMs) }
            stringResource(R.string.provider_guide_sources_value_format, count, formatted)
        }

        else -> {
            stringResource(R.string.provider_guide_sources_value_never_format, count)
        }
    }
