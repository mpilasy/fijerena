package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.NumberUtils

/**
 * Guide, for a source with live channels: an Xtream source's **Provides a guide** switch (plan
 * D1), showing the effective value — detected, or the viewer's; flipping it is the viewer's choice
 * and applies at once. Then **Guide sources ›** (D3), its value the count and last refresh,
 * opening this source's guide sources.
 */
@Composable
fun ColumnScope.ProviderGuideSection(
    providerId: Long,
    showProvidesGuide: Boolean,
    providerSettings: ProviderSettings,
    onProvidesGuideChange: (Boolean) -> Unit,
    onGuideSourcesClick: () -> Unit,
) {
    val context = LocalContext.current
    val sources by remember(providerId) {
        ProviderRepository(context.applicationContext).getGuideSources(providerId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Spacer(modifier = Modifier.height(CinemaSpacing.lg))

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            ProviderSectionTitle(title = stringResource(R.string.provider_section_guide))
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            if (showProvidesGuide) {
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
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onGuideSourcesClick).padding(vertical = CinemaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.epg_sources_header), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = guideSourcesValue(sources.size, sources.maxOfOrNull { it.lastIngestedAtMs } ?: 0L),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                }
                Text(text = "›", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
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
