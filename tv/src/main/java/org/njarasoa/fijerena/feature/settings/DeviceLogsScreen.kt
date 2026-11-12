package org.njarasoa.fijerena.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.DeviceLogsEntryLines
import org.njarasoa.fijerena.core.ui.sync.DeviceLogsHeaderLines
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceLogsViewModel
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Live Sync → a device's Logs on TV (the device diagnostics sync plan): the report that device
 * sent. The first block (when it was sent, the build, the counts) and each entry are focusable
 * cards, so the D-pad scrolls through them, as on Logs; focus opens on the first block. Back returns
 * to the device's Logs action on Live Sync.
 */
@Composable
fun DeviceLogsScreen(
    deviceId: String,
    deviceName: String,
) {
    val context = LocalContext.current
    val viewModel: DeviceLogsViewModel =
        viewModel(factory = DeviceLogsViewModel.Factory(context.applicationContext as Application, deviceId, deviceName))
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceLogsContent(state)
}

@Composable
internal fun DeviceLogsContent(state: DeviceLogsViewModel.State) {
    val scale = LocalUiScale.current
    val headerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { headerFocus.requestFocusWithRetry() }
    val report = state.report
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        item {
            TvScreenHeader(title = stringResource(R.string.device_logs_title), subtitle = state.name)
        }
        item {
            // A card with nothing to do on OK: it takes focus so that Up/Down never skip it.
            TvCard(
                onClick = {},
                look = TvCardLooks.infoBlock(),
                modifier = Modifier.fillMaxWidth().focusRequester(headerFocus).leftToRail(),
            ) {
                Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    DeviceLogsHeaderLines(state.build, report)
                }
            }
        }
        if (report != null && report.isReported && report.entries.isEmpty()) {
            item { TvEmptyState(message = stringResource(R.string.device_logs_empty), takeFocus = false) }
        }
        items(report?.entries.orEmpty()) { entry ->
            TvCard(
                onClick = {},
                look = TvCardLooks.infoBlock(),
                modifier = Modifier.fillMaxWidth().leftToRail(),
            ) {
                Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    DeviceLogsEntryLines(entry)
                }
            }
        }
    }
}
