package org.njarasoa.fijerena.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.sync.DeviceLogsEntryLines
import org.njarasoa.fijerena.core.ui.sync.DeviceLogsHeaderLines
import org.njarasoa.fijerena.core.ui.sync.deviceReportText
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceLogsViewModel
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.MobileEmptyState
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Live Sync → a device's Logs on the phone (the device diagnostics sync plan): the report that
 * device sent. Copy and Share in the top bar take the whole report as text, already masked on the
 * device that sent it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDeviceLogsScreen(
    deviceId: String,
    deviceName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: DeviceLogsViewModel =
        viewModel(factory = DeviceLogsViewModel.Factory(context.applicationContext as Application, deviceId, deviceName))
    val state by viewModel.state.collectAsStateWithLifecycle()
    MobileDeviceLogsContent(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileDeviceLogsContent(
    state: DeviceLogsViewModel.State,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nerd = LocalNerd.current
    val copiedText = stringResource(R.string.settings_diagnostics_copied)
    val report = state.report?.takeIf { it.isReported }
    Scaffold(
        topBar = {
            MobileScreenHeader(title = stringResource(R.string.device_logs_title), subtitle = state.name, onBack = onBack) {
                if (report != null) {
                    IconAction(
                        onClick = {
                            val title = context.getString(R.string.device_logs_title)
                            copyToClipboard(context, title, deviceReportText(context, state.name, state.build, report), copiedText)
                        },
                        icon = CinemaIcons.ContentCopy,
                        label = stringResource(R.string.settings_diagnostics_copy),
                    )
                    IconAction(
                        onClick = {
                            scope.launch {
                                shareLog(
                                    context,
                                    showRawError = nerd,
                                ) { deviceReportText(context, state.name, state.build, report) }
                            }
                        },
                        icon = Icons.Rounded.Share,
                        label = stringResource(R.string.settings_diagnostics_share),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding =
                PaddingValues(
                    start = CinemaSpacing.md,
                    top = CinemaSpacing.md,
                    end = CinemaSpacing.md,
                    bottom = CinemaSpacing.md + LocalDockInset.current,
                ),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        DeviceLogsHeaderLines(state.build, state.report)
                    }
                }
            }
            if (report != null && report.entries.isEmpty()) {
                item { MobileEmptyState(message = stringResource(R.string.device_logs_empty)) }
            }
            items(report?.entries.orEmpty()) { entry ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        DeviceLogsEntryLines(entry)
                    }
                }
            }
        }
    }
}
