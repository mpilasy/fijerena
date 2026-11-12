package org.njarasoa.fijerena.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.LiveSyncProgressBar
import org.njarasoa.fijerena.core.ui.components.QrCode
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.liveSyncConnectionLine
import org.njarasoa.fijerena.core.ui.components.liveSyncRelative
import org.njarasoa.fijerena.core.ui.components.liveSyncStatusLine
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.sync.DeviceBuildLine
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.sync.isOlderBuild
import org.njarasoa.fijerena.core.ui.sync.nowPlayingLine
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerButton
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → Live sync on TV. A TV has no camera: it joins by showing a handoff QR code for a
 * phone of the group to scan, and adds devices by showing an invite for them to scan.
 */
@Composable
fun SyncSettingsScreen() {
    val context = LocalContext.current
    val viewModel: SyncSettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val (status, ui) = viewModel.state.collectAsStateWithLifecycle().value
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val deviceBuilds by viewModel.deviceBuilds.collectAsStateWithLifecycle()
    val shareNowPlaying by viewModel.shareNowPlaying.collectAsStateWithLifecycle()
    val shareLogs by viewModel.shareLogs.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current
    var confirmRemove by remember { mutableStateOf<SyncWire.Device?>(null) }
    var renaming by remember { mutableStateOf<SyncWire.Device?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(status.linked) { if (status.linked) viewModel.loadDevices() }

    // Entry focus on Sync now (Add a device while a sync runs, as Sync now is disabled then); else the
    // screen opened with nothing focused.
    val syncNowFocus = remember { FocusRequester() }
    val addDeviceFocus = remember { FocusRequester() }
    val pairing = ui.handoffQr != null || ui.inviteQr != null
    LaunchedEffect(status.linked, pairing) {
        if (status.linked && !pairing) syncNowFocus.requestFocusWithRetry(fallback = addDeviceFocus)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        val qr = ui.handoffQr ?: ui.inviteQr
        // Add a device is an icon action in the header, as Add is on every Settings page.
        TvScreenHeader(title = stringResource(R.string.live_sync_title)) {
            if (qr == null && status.linked) {
                TvIconAction(
                    onClick = viewModel::showInvite,
                    icon = CinemaIcons.Add,
                    label = stringResource(R.string.live_sync_add_device),
                    enabled = !ui.busy,
                    modifier = Modifier.focusRequester(addDeviceFocus),
                )
            }
        }
        CinemaText(stringResource(R.string.live_sync_desc), role = TextRole.Body, tone = TextTone.Secondary)

        if (qr != null) {
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
        } else if (status.linked) {
            // T-14 order: status + Sync now → share switch → devices → danger zone; Add a device in the header.
            LinkedPanel(
                status = status,
                onSyncNow = viewModel::syncNow,
                onResyncAll = viewModel::resyncAll,
                onReconnect = viewModel::reconnectNow,
                syncNowFocus = syncNowFocus,
            )
            TvSwitchRow(
                checked = shareNowPlaying,
                onCheckedChange = viewModel::setShareNowPlaying,
                label = stringResource(R.string.live_sync_share_playing),
                description = stringResource(R.string.live_sync_share_playing_desc),
                modifier = Modifier.leftToRail(),
            )
            TvSwitchRow(
                checked = shareLogs,
                onCheckedChange = viewModel::setShareLogs,
                label = stringResource(R.string.live_sync_share_logs),
                description = stringResource(R.string.live_sync_share_logs_desc),
                modifier = Modifier.leftToRail(),
            )
            // Up from Leave goes to the last Remove: it is at the far right of its row, outside the left-aligned
            // Leave button's beam, so plain focus search went to the share switch.
            val lastActionFocus = remember { FocusRequester() }
            val hasDevices = ui.devices.orEmpty().any { !it.revoked }
            DevicesPanel(ui.devices, nowPlaying, deviceBuilds, lastActionFocus, onRename = { renaming = it }, onRemove = {
                confirmRemove =
                    it
            })
            // Rare and drastic: last, under its own heading, outlined in the error colour.
            CinemaText(
                stringResource(R.string.provider_section_danger_zone),
                role = TextRole.Heading,
                tone = TextTone.Danger,
            )
            ProviderDangerButton(
                onClick = { confirmLeave = true },
                text = stringResource(R.string.live_sync_leave),
                modifier = (if (hasDevices) Modifier.focusProperties { up = lastActionFocus } else Modifier).leftToRail(),
            )
        } else {
            SetupPanel(ui, viewModel)
        }

        ui.error?.let { CinemaText(it, role = TextRole.Body, tone = TextTone.Danger) }
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
) {
    val scale = LocalUiScale.current
    var setupSecret by remember { mutableStateOf("") }
    // Once the server checks out, Check server goes: land on Join, so the D-pad's nearest pick (Start a
    // sync group) cannot make a new group by accident.
    val joinFocus = remember { FocusRequester() }
    LaunchedEffect(ui.serverChecked) { if (ui.serverChecked) joinFocus.requestFocus() }
    // First focus before that: Check server, or the server address's edit button while Check
    // server is disabled (no address). Without it the screen opened with nothing focused.
    val serverFocus = remember { FocusRequester() }
    val checkServerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!ui.serverChecked) {
            if (ui.serverUrl.isNotBlank()) {
                checkServerFocus.requestFocusWithRetry(fallback = serverFocus)
            } else {
                serverFocus.requestFocusWithRetry()
            }
        }
    }
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            ReadOnlyFieldWithEdit(
                value = ui.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = stringResource(R.string.live_sync_server_label),
                keyboardOptions = FieldKeyboards.Url,
                modifier = Modifier.leftToRail(),
                editButtonFocusRequester = serverFocus,
            )
            if (!ui.serverChecked) {
                CinemaPrimaryButton(
                    onClick = viewModel::checkServer,
                    text = stringResource(R.string.live_sync_check_server),
                    enabled = ui.serverUrl.isNotBlank() && !ui.busy,
                    modifier = Modifier.focusRequester(checkServerFocus).leftToRail(),
                )
            } else {
                CinemaText(stringResource(R.string.live_sync_server_found), role = TextRole.Body, tone = TextTone.Success)
                if (ui.setupSecretRequired) {
                    ReadOnlyFieldWithEdit(
                        value = setupSecret,
                        onValueChange = { setupSecret = it },
                        label = stringResource(R.string.live_sync_setup_secret_label),
                        keyboardOptions = FieldKeyboards.Password,
                        visualTransformation =
                            androidx.compose.ui.text.input
                                .PasswordVisualTransformation(),
                        displayText = "•".repeat(setupSecret.length),
                        modifier = Modifier.leftToRail(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                    CinemaPrimaryButton(
                        modifier = Modifier.focusRequester(joinFocus).leftToRail(),
                        onClick = viewModel::startHandoff,
                        text = stringResource(R.string.live_sync_join_show_code),
                        enabled = !ui.busy,
                    )
                    CinemaSecondaryButton(
                        onClick = { viewModel.createAccount(setupSecret) },
                        text = stringResource(R.string.live_sync_create),
                        enabled = !ui.busy && (!ui.setupSecretRequired || setupSecret.isNotBlank()),
                    )
                }
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
    val scale = LocalUiScale.current
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Spacing.md.scaled(scale)),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // look-ok: the QR code's size is what a phone camera needs to scan it from the sofa, not a look
            QrCode(text = qr, size = 260.dp, contentDescription = stringResource(R.string.live_sync_qr_description))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                CinemaText(instructions, role = TextRole.Body, tone = TextTone.Primary)
                CinemaText(stringResource(R.string.live_sync_waiting), role = TextRole.Body, tone = TextTone.Secondary)
                // The panel's only control: Left from it goes to the navigation rail.
                CinemaSecondaryButton(onClick = onClose, text = stringResource(R.string.common_cancel), modifier = Modifier.leftToRail())
            }
        }
    }
}

@Composable
private fun LinkedPanel(
    status: SyncManager.Status,
    onSyncNow: () -> Unit,
    onResyncAll: () -> Unit,
    onReconnect: () -> Unit,
    syncNowFocus: FocusRequester,
) {
    val scale = LocalUiScale.current
    // Reconnect now goes once the socket is back: focus on it moves to Sync now, never to nothing.
    var reconnectFocused by remember { mutableStateOf(false) }
    val reconnectShown by rememberUpdatedState(status.reconnectAt != null)
    LaunchedEffect(status.reconnectAt == null) {
        if (status.reconnectAt == null && reconnectFocused) {
            reconnectFocused = false
            syncNowFocus.requestFocusWithRetry()
        }
    }
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
            CinemaText(
                liveSyncStatusLine(status),
                role = TextRole.Heading,
                tone = if (status.lastError != null && !status.syncing) TextTone.Danger else TextTone.Primary,
            )
            // A pass that runs long fills a bar under the line (the sync progress plan).
            LiveSyncProgressBar(status)
            if (LocalNerd.current && status.lastError != null) {
                CinemaText("[dev] ${status.lastError}", role = TextRole.BodySmall, tone = TextTone.Secondary)
            }
            CinemaText(
                stringResource(R.string.live_sync_server_line, status.serverUrl.orEmpty()),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
            )
            liveSyncConnectionLine(status)?.let { line ->
                CinemaText(line, role = TextRole.BodySmall, tone = if (status.connected) TextTone.Secondary else TextTone.Warning)
            }
            Spacer(Modifier.height(Spacing.xs.scaled(scale)))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                CinemaSecondaryButton(
                    onClick = onSyncNow,
                    text = stringResource(R.string.live_sync_now),
                    enabled = !status.syncing,
                    modifier = Modifier.focusRequester(syncNowFocus).leftToRail(),
                )
                CinemaSecondaryButton(
                    onClick = onResyncAll,
                    text = stringResource(R.string.live_sync_full_resync),
                    enabled = !status.syncing,
                )
                if (status.reconnectAt != null) {
                    CinemaSecondaryButton(
                        onClick = onReconnect,
                        text = stringResource(R.string.live_sync_reconnect_now),
                        // Losing focus because it left the screen keeps the flag: the effect above moves focus.
                        modifier = Modifier.onFocusChanged { if (it.isFocused || reconnectShown) reconnectFocused = it.isFocused },
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicesPanel(
    devices: List<SyncWire.Device>?,
    nowPlaying: Map<String, SyncPayloads.NowPlaying>,
    /** Each device's reported build, by device id (the device builds plan). */
    builds: Map<String, DeviceBuild>,
    /** On the last row's last button: Up from Leave below lands there. */
    lastActionFocus: FocusRequester,
    onRename: (SyncWire.Device) -> Unit,
    onRemove: (SyncWire.Device) -> Unit,
) {
    val scale = LocalUiScale.current
    val active = devices?.filterNot { it.revoked } ?: return
    val listedBuilds = active.mapNotNull { builds[it.id] }
    val last = active.lastOrNull()
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            CinemaText(
                stringResource(R.string.live_sync_devices_title),
                role = TextRole.Heading,
                tone = TextTone.Primary,
            )
            active.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CinemaText(device.name, role = TextRole.Heading, tone = TextTone.Primary)
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
                        DeviceBuildLine(builds[device.id], isOlderBuild(builds[device.id], listedBuilds))
                        nowPlaying[device.id]?.let {
                            CinemaText(
                                nowPlayingLine(it),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.sm.scaled(scale)))
                    TvIconAction(
                        onClick = { onRename(device) },
                        icon = CinemaIcons.Edit,
                        label = stringResource(R.string.live_sync_device_rename),
                        modifier = if (device == last && device.current) Modifier.focusRequester(lastActionFocus) else Modifier,
                    )
                    if (!device.current) {
                        Spacer(Modifier.width(Spacing.sm.scaled(scale)))
                        TvIconAction(
                            onClick = { onRemove(device) },
                            icon = CinemaIcons.Delete,
                            label = stringResource(R.string.live_sync_device_remove),
                            danger = true,
                            modifier = if (device == last) Modifier.focusRequester(lastActionFocus) else Modifier,
                        )
                    }
                }
            }
        }
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
        title = stringResource(R.string.live_sync_device_rename_title, device.name),
        text = {
            // The one text field look (CinemaTextField), as every other TV field while it is edited.
            CinemaTextField(
                value = name,
                onValueChange = { name = it.take(AppSettings.MAX_DEVICE_NAME_LENGTH) },
                label = stringResource(R.string.live_sync_device_name_label),
                keyboardOptions = FieldKeyboards.Name,
                supportingText = stringResource(R.string.live_sync_device_name_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().tvDpadEscape(),
            )
        },
        main = DialogAction(stringResource(R.string.provider_save_button)) { onSave(name) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
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
        title = title,
        text = { CinemaText(message, role = TextRole.Body, tone = TextTone.Secondary) },
        danger = DialogAction(confirm, onClick = onConfirm),
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
