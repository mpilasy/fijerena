package org.njarasoa.fijerena.feature.settings.components

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

@Composable
fun EpgSettingsCard(
    context: Context,
    uiState: SettingsUiState,
    onManageEpg: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_epg_section_title)) {
        val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
        val indexState by epgIndexer.state.collectAsStateWithLifecycle()
        var sourceCount by remember { mutableStateOf(0) }
        LaunchedEffect(uiState.epgRefreshTrigger) {
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
                is EpgIndexState.Optimizing -> stringResource(R.string.epg_database_optimizing)
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
        CinemaButton(
            onClick = onManageEpg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.epg_data_manage_button))
        }
    }
}
