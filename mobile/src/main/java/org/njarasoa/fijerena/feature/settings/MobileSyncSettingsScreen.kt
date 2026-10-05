package org.njarasoa.fijerena.feature.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.QrCode
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.sync.nowPlayingLine
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel
import org.njarasoa.fijerena.feature.settings.components.QrScanner
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Settings → Live sync on a phone. A phone joins by scanning an invite, adds a phone by showing
 * one, and adds a TV by scanning the handoff code the TV shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSyncSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: SyncSettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val (status, ui) = viewModel.state.collectAsStateWithLifecycle().value
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val shareNowPlaying by viewModel.shareNowPlaying.collectAsStateWithLifecycle()
    val stopStates by viewModel.stopStates.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<SyncWire.Device?>(null) }
    var confirmStop by remember { mutableStateOf<SyncWire.Device?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(status.linked) { if (status.linked) viewModel.loadDevices() }
    LaunchedEffect(ui.scanDone) {
        val done = ui.scanDone ?: return@LaunchedEffect
        val message = if (done == SyncSettingsViewModel.ScanResult.JOINED) R.string.live_sync_joined else R.string.live_sync_handed_over
        Toast.makeText(context, resources.getString(message), Toast.LENGTH_SHORT).show()
        viewModel.consumeScanDone()
    }

    if (scanning) {
        BackHandler { scanning = false }
        QrScanner(
            onScanned = {
                scanning = false
                viewModel.onScanned(it)
            },
            onClose = { scanning = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            Text(
                stringResource(R.string.live_sync_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )

            val qr = ui.handoffQr ?: ui.inviteQr
            when {
                qr != null -> {
                    PairingPanel(
                        qr = qr,
                        instructions =
                            stringResource(
                                if (ui.handoffQr !=
                                    null
                                ) {
                                    R.string.live_sync_handoff_instructions
                                } else {
                                    R.string.live_sync_invite_instructions
                                },
                            ),
                        onClose = { if (ui.handoffQr != null) viewModel.cancelHandoff() else viewModel.hideInvite() },
                    )
                }

                status.linked -> {
                    LinkedPanel(
                        status = status,
                        devMode = viewModel.devMode,
                        busy = ui.busy,
                        onSyncNow = viewModel::syncNow,
                        onAddDevice = viewModel::showInvite,
                        onScan = { scanning = true },
                    )
                    ShareNowPlayingRow(shareNowPlaying, viewModel::setShareNowPlaying)
                    DevicesPanel(ui.devices, nowPlaying, stopStates, onStop = { confirmStop = it }, onRemove = { confirmRemove = it })
                    CinemaOutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.live_sync_leave), color = CinemaError)
                    }
                }

                else -> {
                    SetupPanel(ui, viewModel, onScan = { scanning = true })
                }
            }

            ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CinemaError) }
        }
    }

    confirmRemove?.let { device ->
        ConfirmDialog(
            title = stringResource(R.string.live_sync_device_remove_title, device.name),
            message = stringResource(R.string.live_sync_device_remove_message),
            confirm = stringResource(R.string.live_sync_device_remove),
            onConfirm = {
                viewModel.revoke(device.id)
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }
    confirmStop?.let { device ->
        ConfirmDialog(
            title = stringResource(R.string.live_sync_stop_title, device.name),
            message = stringResource(R.string.live_sync_stop_message),
            confirm = stringResource(R.string.live_sync_stop),
            onConfirm = {
                viewModel.requestStop(device.id)
                confirmStop = null
            },
            onDismiss = { confirmStop = null },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.live_sync_leave_title),
            message = stringResource(R.string.live_sync_leave_message),
            confirm = stringResource(R.string.live_sync_leave),
            onConfirm = {
                viewModel.leave()
                confirmLeave = false
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun SetupPanel(
    ui: SyncSettingsViewModel.Ui,
    viewModel: SyncSettingsViewModel,
    onScan: () -> Unit,
) {
    var setupSecret by remember { mutableStateOf("") }
    // Joining needs no address: the invite carries it.
    CinemaButton(onClick = onScan, modifier = Modifier.fillMaxWidth(), enabled = !ui.busy) {
        Text(stringResource(R.string.live_sync_join_scan))
    }
    HorizontalDivider()
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
            OutlinedTextField(
                value = ui.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = { Text(stringResource(R.string.live_sync_server_label)) },
                placeholder = { Text(stringResource(R.string.live_sync_server_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!ui.serverChecked) {
                CinemaOutlinedButton(onClick = viewModel::checkServer, enabled = ui.serverUrl.isNotBlank() && !ui.busy) {
                    Text(stringResource(R.string.live_sync_check_server))
                }
            } else {
                Text(stringResource(R.string.live_sync_server_found), color = CinemaAccent)
                if (ui.setupSecretRequired) {
                    OutlinedTextField(
                        value = setupSecret,
                        onValueChange = { setupSecret = it },
                        label = { Text(stringResource(R.string.live_sync_setup_secret_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CinemaButton(
                    onClick = { viewModel.createAccount(setupSecret) },
                    enabled = !ui.busy && (!ui.setupSecretRequired || setupSecret.isNotBlank()),
                ) { Text(stringResource(R.string.live_sync_create)) }
            }
        }
    }
}

@Composable
private fun PairingPanel(
    qr: String,
    instructions: String,
    onClose: () -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(CinemaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            QrCode(text = qr, size = 280.dp, contentDescription = stringResource(R.string.live_sync_qr_description))
            Text(instructions, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.live_sync_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            CinemaOutlinedButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}

@Composable
private fun LinkedPanel(
    status: SyncManager.Status,
    devMode: Boolean,
    busy: Boolean,
    onSyncNow: () -> Unit,
    onAddDevice: () -> Unit,
    onScan: () -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
            Text(
                statusLine(status),
                style = MaterialTheme.typography.titleSmall,
                color = if (status.lastError != null && !status.syncing) CinemaError else CinemaAccent,
            )
            if (devMode && status.lastError != null) {
                Text("[dev] ${status.lastError}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.live_sync_server_line, status.serverUrl.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm), modifier = Modifier.padding(top = CinemaSpacing.xs)) {
                CinemaButton(onClick = onAddDevice, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.live_sync_add_device))
                }
                CinemaOutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.live_sync_scan_code))
                }
            }
            // Same weight as Scan a code: one primary (Add a device) per card (M-11).
            CinemaOutlinedButton(onClick = onSyncNow, enabled = !status.syncing, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.live_sync_now))
            }
        }
    }
}

@Composable
private fun DevicesPanel(
    devices: List<SyncWire.Device>?,
    nowPlaying: Map<String, SyncPayloads.NowPlaying>,
    stopStates: Map<String, SyncSettingsViewModel.StopState>,
    onStop: (SyncWire.Device) -> Unit,
    onRemove: (SyncWire.Device) -> Unit,
) {
    val active = devices?.filterNot { it.revoked } ?: return
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
            Text(stringResource(R.string.live_sync_devices_title), style = MaterialTheme.typography.titleSmall, color = CinemaAccent)
            active.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (device.current) {
                                stringResource(
                                    R.string.live_sync_device_this,
                                )
                            } else {
                                stringResource(R.string.live_sync_device_last_seen, relative(device.lastSeen))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        )
                        nowPlaying[device.id]?.let {
                            Text(
                                nowPlayingLine(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaAccent,
                            )
                        }
                        stopStates[device.id]?.let { state ->
                            Text(
                                if (state == SyncSettingsViewModel.StopState.STOPPING) {
                                    stringResource(R.string.live_sync_stopping)
                                } else {
                                    stringResource(R.string.live_sync_stop_unreachable, device.name)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (state ==
                                        SyncSettingsViewModel.StopState.STOPPING
                                    ) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                                    } else {
                                        CinemaError
                                    },
                            )
                        }
                    }
                    // Remote Stop — phone app only (never on TV): another device's current
                    // playback, once it says which session it is. Hidden while a Stop is on its way.
                    val canStop = !device.current && nowPlaying[device.id]?.sessionId != null
                    if (canStop && stopStates[device.id] != SyncSettingsViewModel.StopState.STOPPING) {
                        IconAction(
                            onClick = { onStop(device) },
                            icon = Icons.Filled.Stop,
                            label = stringResource(R.string.live_sync_stop),
                            tint = CinemaError,
                        )
                    }
                    if (!device.current) {
                        IconAction(
                            onClick = { onRemove(device) },
                            icon = CinemaIcons.Delete,
                            label = stringResource(R.string.live_sync_device_remove),
                            tint = CinemaError,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareNowPlayingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.live_sync_share_playing), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.live_sync_share_playing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = CinemaError) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun statusLine(status: SyncManager.Status): String =
    when {
        status.syncing -> stringResource(R.string.live_sync_status_syncing)
        status.lastError != null -> stringResource(R.string.live_sync_status_failed)
        status.lastSyncAt == 0L -> stringResource(R.string.live_sync_status_never)
        else -> stringResource(R.string.live_sync_status_last, relative(status.lastSyncAt))
    }

@Composable
private fun relative(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.live_sync_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
