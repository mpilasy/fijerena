package org.njarasoa.fijerena.feature.settings.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun EpgSettingsCard(
    context: Context,
    epgRefreshTrigger: Int,
    scale: Float,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_epg_section_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))

            val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
            val indexState by epgIndexer.state.collectAsStateWithLifecycle()
            var sourceCount by remember { mutableStateOf(0) }
            LaunchedEffect(epgRefreshTrigger) {
                sourceCount = epgIndexer.getSourceCount()
            }
            val summaryText =
                when (val idx = indexState) {
                    is EpgIndexState.Indexed -> stringResource(
                        R.string.epg_summary_channels_programmes,
                        formatProgrammeCount(idx.channelCount),
                        formatProgrammeCount(idx.programmeCount),
                    )
                    is EpgIndexState.Indexing -> stringResource(R.string.epg_summary_indexing, idx.progressPercent)
                    is EpgIndexState.Optimizing -> stringResource(R.string.epg_browser_optimizing_index_label)
                    is EpgIndexState.NotIndexed ->
                        if (sourceCount >
                            0
                        ) {
                            stringResource(R.string.epg_summary_not_indexed, sourceCount)
                        } else {
                            stringResource(R.string.epg_summary_no_sources)
                        }
                    is EpgIndexState.Failed -> stringResource(R.string.epg_database_error, idx.reason)
                }
            Text(
                text = summaryText,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize =
                            MaterialTheme.typography.bodySmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            // EPG sources belong to a provider - they're managed per provider, from the provider list.
        }
    }
}
