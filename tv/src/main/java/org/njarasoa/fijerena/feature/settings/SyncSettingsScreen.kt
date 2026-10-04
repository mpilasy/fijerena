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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncWire
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.QrCode
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.sync.nowPlayingLine
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerButton
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
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
    val shareNowPlaying by viewModel.shareNowPlaying.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current
    var confirmRemove by remember { mutableStateOf<SyncWire.Device?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(status.linked) { if (status.linked) viewModel.loadDevices() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        Text(
            text = stringResource(R.string.live_sync_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.live_sync_desc), style = MaterialTheme.typography.bodyMedium, color = CinemaTextSecondary)

        val qr = ui.handoffQr ?: ui.inviteQr
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
            // T-14 order: status + Sync now → Add a device → share switch → devices → danger zone.
            LinkedPanel(
                status = status,
                devMode = viewModel.devMode,
                onSyncNow = viewModel::syncNow,
            )
            CinemaPrimaryButton(onClick = viewModel::showInvite, text = stringResource(R.string.live_sync_add_device), enabled = !ui.busy)
            TvSwitchRow(
                checked = shareNowPlaying,
                onCheckedChange = viewModel::setShareNowPlaying,
                label = stringResource(R.string.live_sync_share_playing),
                description = stringResource(R.string.live_sync_share_playing_desc),
            )
            // Up from Leave goes to the last Remove: it sits at the far right of its row, outside the
            // left-aligned Leave button's beam, so plain focus search skipped it for the share switch.
            val lastRemoveFocus = remember { FocusRequester() }
            val hasRemovable = ui.devices.orEmpty().any { !it.revoked && !it.current }
            DevicesPanel(ui.devices, nowPlaying, lastRemoveFocus, onRemove = { confirmRemove = it })
            // Rare and drastic: last, under its own heading, outlined in the error colour.
            Text(
                stringResource(R.string.provider_section_danger_zone),
                style = MaterialTheme.typography.titleMedium,
                color = CinemaError,
            )
            ProviderDangerButton(
                onClick = { confirmLeave = true },
                text = stringResource(R.string.live_sync_leave),
                modifier = if (hasRemovable) Modifier.focusProperties { up = lastRemoveFocus } else Modifier,
            )
        } else {
            SetupPanel(ui, viewModel)
        }

        ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CinemaError) }
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
    // Once the server checks out, Check server is gone: land on Join, so the D-pad's nearest pick
    // (Start a sync group, under the edit button) can't make a new group by accident.
    val joinFocus = remember { FocusRequester() }
    LaunchedEffect(ui.serverChecked) { if (ui.serverChecked) joinFocus.requestFocus() }
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            ReadOnlyFieldWithEdit(
                value = ui.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = stringResource(R.string.live_sync_server_label),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
            if (!ui.serverChecked) {
                CinemaPrimaryButton(
                    onClick = viewModel::checkServer,
                    text = stringResource(R.string.live_sync_check_server),
                    enabled = ui.serverUrl.isNotBlank() && !ui.busy,
                )
            } else {
                Text(stringResource(R.string.live_sync_server_found), color = CinemaAccent)
                if (ui.setupSecretRequired) {
                    ReadOnlyFieldWithEdit(
                        value = setupSecret,
                        onValueChange = { setupSecret = it },
                        label = stringResource(R.string.live_sync_setup_secret_label),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        visualTransformation =
                            androidx.compose.ui.text.input
                                .PasswordVisualTransformation(),
                        displayText = "•".repeat(setupSecret.length),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                    CinemaPrimaryButton(
                        modifier = Modifier.focusRequester(joinFocus),
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
            QrCode(text = qr, size = 260.dp, contentDescription = stringResource(R.string.live_sync_qr_description))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                Text(instructions, style = MaterialTheme.typography.bodyLarge, color = CinemaTextPrimary)
                Text(stringResource(R.string.live_sync_waiting), style = MaterialTheme.typography.bodyMedium, color = CinemaTextSecondary)
                CinemaSecondaryButton(onClick = onClose, text = stringResource(R.string.common_cancel))
            }
        }
    }
}

@Composable
private fun LinkedPanel(
    status: SyncManager.Status,
    devMode: Boolean,
    onSyncNow: () -> Unit,
) {
    val scale = LocalUiScale.current
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
            Text(
                statusLine(status),
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (status.lastError != null &&
                        !status.syncing
                    ) {
                        CinemaError
                    } else {
                        CinemaAccent
                    },
            )
            if (devMode && status.lastError != null) {
                Text("[dev] ${status.lastError}", style = MaterialTheme.typography.bodySmall, color = CinemaTextSecondary)
            }
            Text(
                stringResource(R.string.live_sync_server_line, status.serverUrl.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = CinemaTextSecondary,
            )
            Spacer(Modifier.height(Spacing.xs.scaled(scale)))
            CinemaSecondaryButton(onClick = onSyncNow, text = stringResource(R.string.live_sync_now), enabled = !status.syncing)
        }
    }
}

@Composable
private fun DevicesPanel(
    devices: List<SyncWire.Device>?,
    nowPlaying: Map<String, SyncPayloads.NowPlaying>,
    lastRemoveFocus: FocusRequester,
    onRemove: (SyncWire.Device) -> Unit,
) {
    val scale = LocalUiScale.current
    val active = devices?.filterNot { it.revoked } ?: return
    val lastRemovable = active.lastOrNull { !it.current }
    TvGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            Text(stringResource(R.string.live_sync_devices_title), style = MaterialTheme.typography.titleMedium, color = CinemaAccent)
            active.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge, color = CinemaTextPrimary)
                        Text(
                            if (device.current) {
                                stringResource(
                                    R.string.live_sync_device_this,
                                )
                            } else {
                                stringResource(R.string.live_sync_device_last_seen, relative(device.lastSeen))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary,
                        )
                        nowPlaying[device.id]?.let {
                            Text(
                                nowPlayingLine(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaAccent,
                            )
                        }
                    }
                    if (!device.current) {
                        Spacer(Modifier.width(Spacing.sm.scaled(scale)))
                        CinemaSecondaryButton(
                            onClick = { onRemove(device) },
                            text = stringResource(R.string.live_sync_device_remove),
                            modifier = if (device == lastRemovable) Modifier.focusRequester(lastRemoveFocus) else Modifier,
                        )
                    }
                }
            }
        }
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
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = { androidx.compose.material3.Text(message) },
        confirmButton = { CinemaDangerButton(onClick = onConfirm, text = confirm) },
        dismissButton = { CinemaSecondaryButton(onClick = onDismiss, text = stringResource(R.string.common_cancel)) },
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
