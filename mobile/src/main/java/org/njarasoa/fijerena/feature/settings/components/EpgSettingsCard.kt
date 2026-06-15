package org.njarasoa.fijerena.feature.settings.components

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState

@Composable
fun EpgSettingsCard(
    context: Context,
    uiState: SettingsUiState,
    onManageEpg: () -> Unit
) {
    SettingsSection(title = "EPG Data") {
        val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
        val indexState by epgIndexer.state.collectAsStateWithLifecycle()
        var sourceCount by remember { mutableStateOf(0) }
        LaunchedEffect(uiState.epgRefreshTrigger) {
            sourceCount = epgIndexer.getSourceCount()
        }
        val summaryText =
            when (val idx = indexState) {
                is EpgIndexState.Indexed -> "${formatProgrammeCount(
                    idx.channelCount,
                )} channels, ${formatProgrammeCount(idx.programmeCount)} programmes"
                is EpgIndexState.Indexing -> "Indexing: ${idx.progressPercent}%"
                is EpgIndexState.Optimizing -> "Optimizing search index..."
                is EpgIndexState.NotIndexed ->
                    if (sourceCount >
                        0
                    ) {
                        "$sourceCount source(s) configured, not yet indexed"
                    } else {
                        "No sources configured"
                    }
                is EpgIndexState.Failed -> "Error: ${idx.reason}"
            }
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
        Button(
            onClick = onManageEpg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Manage EPG Data")
        }
    }
}
