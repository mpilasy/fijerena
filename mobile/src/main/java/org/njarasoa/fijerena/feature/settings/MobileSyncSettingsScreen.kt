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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.QrCode
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.liveSyncRelative
import org.njarasoa.fijerena.core.ui.components.liveSyncStatusLine
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.sync.nowPlayingLine
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel
import org.njarasoa.fijerena.feature.settings.components.QrScanner
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.MobileDimensions

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
    var renaming by remember { mutableStateOf<SyncWire.Device?>(null) }
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
                title = { CinemaText(stringResource(R.string.live_sync_title), role = TextRole.Title) },
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
                    .padding(
                        start = CinemaSpacing.md,
                        top = CinemaSpacing.md,
                        end = CinemaSpacing.md,
                        bottom =
                            CinemaSpacing.md + LocalDockInset.current,
                    ),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            CinemaText(stringResource(R.string.live_sync_desc), tone = TextTone.Secondary)

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
                    DevicesPanel(ui.devices, nowPlaying, stopStates, onStop = {
                        confirmStop = it
                    }, onRename = { renaming = it }, onRemove = {
                        confirmRemove =
                            it
                    })
                    CinemaButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Danger) {
                        CinemaText(stringResource(R.string.live_sync_leave), role = TextRole.Label, tone = TextTone.Inherit)
                    }
                }

                else -> {
                    SetupPanel(ui, viewModel, onScan = { scanning = true })
                }
            }

            ui.error?.let { CinemaText(it, tone = TextTone.Danger) }
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
    renaming?.let { device ->
        RenameDialog(
            device = device,
            onSave = { name ->
                viewModel.rename(device.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
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
        CinemaText(stringResource(R.string.live_sync_join_scan), role = TextRole.Label, tone = TextTone.Inherit)
    }
    CinemaDivider()
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
            CinemaTextField(
                value = ui.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = stringResource(R.string.live_sync_server_label),
                placeholder = stringResource(R.string.live_sync_server_hint),
                singleLine = true,
                keyboardOptions = FieldKeyboards.Url,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!ui.serverChecked) {
                CinemaOutlinedButton(onClick = viewModel::checkServer, enabled = ui.serverUrl.isNotBlank() && !ui.busy) {
                    CinemaText(stringResource(R.string.live_sync_check_server), role = TextRole.Label, tone = TextTone.Inherit)
                }
            } else {
                CinemaText(stringResource(R.string.live_sync_server_found), tone = TextTone.Accent)
                if (ui.setupSecretRequired) {
                    CinemaTextField(
                        value = setupSecret,
                        onValueChange = { setupSecret = it },
                        label = stringResource(R.string.live_sync_setup_secret_label),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = FieldKeyboards.Password,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CinemaButton(
                    onClick = { viewModel.createAccount(setupSecret) },
                    enabled = !ui.busy && (!ui.setupSecretRequired || setupSecret.isNotBlank()),
                ) { CinemaText(stringResource(R.string.live_sync_create), role = TextRole.Label, tone = TextTone.Inherit) }
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
            QrCode(text = qr, size = MobileDimensions.qrCodeSize, contentDescription = stringResource(R.string.live_sync_qr_description))
            CinemaText(instructions)
            CinemaText(stringResource(R.string.live_sync_waiting), role = TextRole.BodySmall, tone = TextTone.Secondary)
            CinemaOutlinedButton(onClick = onClose) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
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
            CinemaText(
                liveSyncStatusLine(status),
                role = TextRole.Heading,
                tone = if (status.lastError != null && !status.syncing) TextTone.Danger else TextTone.Accent,
            )
            if (devMode && status.lastError != null) {
                CinemaText("[dev] ${status.lastError}", role = TextRole.BodySmall)
            }
            CinemaText(
                stringResource(R.string.live_sync_server_line, status.serverUrl.orEmpty()),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm), modifier = Modifier.padding(top = CinemaSpacing.xs)) {
                CinemaButton(onClick = onAddDevice, enabled = !busy, modifier = Modifier.weight(1f)) {
                    CinemaText(stringResource(R.string.live_sync_add_device), role = TextRole.Label, tone = TextTone.Inherit)
                }
                CinemaOutlinedButton(onClick = onScan, enabled = !busy, modifier = Modifier.weight(1f)) {
                    CinemaText(stringResource(R.string.live_sync_scan_code), role = TextRole.Label, tone = TextTone.Inherit)
                }
            }
            // Same weight as Scan a code: one primary (Add a device) per card (M-11).
            CinemaOutlinedButton(onClick = onSyncNow, enabled = !status.syncing, modifier = Modifier.fillMaxWidth()) {
                CinemaText(stringResource(R.string.live_sync_now), role = TextRole.Label, tone = TextTone.Inherit)
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
    onRename: (SyncWire.Device) -> Unit,
    onRemove: (SyncWire.Device) -> Unit,
) {
    val active = devices?.filterNot { it.revoked } ?: return
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
            CinemaText(stringResource(R.string.live_sync_devices_title), role = TextRole.Heading, tone = TextTone.Accent)
            active.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CinemaText(device.name)
                        // A playing device's status line says it is there; last seen only when idle.
                        if (device.current) {
                            CinemaText(
                                stringResource(R.string.live_sync_device_this),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        } else if (device.id !in nowPlaying) {
                            CinemaText(
                                stringResource(R.string.live_sync_device_last_seen, liveSyncRelative(device.lastSeen)),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                        nowPlaying[device.id]?.let {
                            CinemaText(nowPlayingLine(it), role = TextRole.BodySmall, tone = TextTone.Accent)
                        }
                        stopStates[device.id]?.let { state ->
                            CinemaText(
                                if (state == SyncSettingsViewModel.StopState.STOPPING) {
                                    stringResource(R.string.live_sync_stopping)
                                } else {
                                    stringResource(R.string.live_sync_stop_unreachable, device.name)
                                },
                                role = TextRole.BodySmall,
                                tone =
                                    if (state ==
                                        SyncSettingsViewModel.StopState.STOPPING
                                    ) {
                                        TextTone.Secondary
                                    } else {
                                        TextTone.Danger
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
                            tint = CinemaTheme.colors.danger,
                        )
                    }
                    IconAction(
                        onClick = { onRename(device) },
                        icon = CinemaIcons.Edit,
                        label = stringResource(R.string.live_sync_device_rename),
                    )
                    if (!device.current) {
                        IconAction(
                            onClick = { onRemove(device) },
                            icon = CinemaIcons.Delete,
                            label = stringResource(R.string.live_sync_device_remove),
                            tint = CinemaTheme.colors.danger,
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
            CinemaText(stringResource(R.string.live_sync_share_playing))
            CinemaText(stringResource(R.string.live_sync_share_playing_desc), role = TextRole.BodySmall, tone = TextTone.Secondary)
        }
        CinemaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Names a device for the whole group; empty goes back to its model's name. */
@Composable
private fun RenameDialog(
    device: SyncWire.Device,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(device.id) { mutableStateOf(device.name) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.live_sync_device_rename_title, device.name), role = TextRole.Title) },
        text = {
            CinemaTextField(
                value = name,
                onValueChange = { name = it.take(AppSettings.MAX_DEVICE_NAME_LENGTH) },
                label = stringResource(R.string.live_sync_device_name_label),
                keyboardOptions = FieldKeyboards.Name,
                supportingText = stringResource(R.string.live_sync_device_name_hint),
                singleLine = true,
            )
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = {
                onSave(name)
            }) { CinemaText(stringResource(R.string.provider_save_button), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(title, role = TextRole.Title) },
        text = { CinemaText(message, tone = TextTone.Secondary) },
        confirmButton = {
            CinemaDialogActionButton(onClick = onConfirm, kind = ButtonKind.Danger) {
                CinemaText(confirm, role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
    )
}
