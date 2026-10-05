package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ExtraLoginsViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.CinemaError

/**
 * Logins, for an Xtream source being edited: the main login and the extra ones playback shares,
 * each with Make main and Remove, and Add login (runs the same-panel check). [onMainLoginChanged]
 * runs when Make main or Remove changed the main login, so the screen reloads its own username
 * and password fields. See docs/plans/20261005_shared-logins-plan.md → Phase 1.
 */
@Composable
fun ColumnScope.ProviderLoginsSection(
    providerId: Long,
    snackbarHostState: SnackbarHostState,
    onMainLoginChanged: () -> Unit,
) {
    val context = LocalContext.current
    val loginsViewModel: ExtraLoginsViewModel =
        viewModel(key = "extraLogins-$providerId", factory = ExtraLoginsViewModel.Factory(context, providerId))
    val rows by loginsViewModel.rows.collectAsStateWithLifecycle()
    val busy by loginsViewModel.busy.collectAsStateWithLifecycle()
    val message by loginsViewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    // The username being added, while the dialog waits for it to appear in the list.
    var pendingAdd by remember { mutableStateOf<String?>(null) }
    var addDialogMessage by remember { mutableStateOf<String?>(null) }
    var removeUsername by remember { mutableStateOf<String?>(null) }

    // Messages show in the Add dialog while it is open, otherwise as a snackbar.
    LaunchedEffect(message) {
        message?.let { text ->
            if (showAddDialog) {
                addDialogMessage = text
            } else {
                scope.launch { snackbarHostState.showSnackbar(text) }
            }
            loginsViewModel.clearMessage()
        }
    }

    // The added login is in the list: the add went through, close the dialog and say so.
    LaunchedEffect(rows) {
        val added = pendingAdd
        if (added != null && rows.any { it.username == added }) {
            showAddDialog = false
            pendingAdd = null
            addDialogMessage?.let { text -> scope.launch { snackbarHostState.showSnackbar(text) } }
            addDialogMessage = null
        }
    }

    // Make main / Remove changed the main login: the screen's fields must follow it.
    val mainUsername = rows.firstOrNull()?.username
    var knownMainUsername by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mainUsername) {
        if (mainUsername != null) {
            if (knownMainUsername != null && knownMainUsername != mainUsername) onMainLoginChanged()
            knownMainUsername = mainUsername
        }
    }

    Spacer(modifier = Modifier.height(CinemaSpacing.lg))

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CinemaSpacing.md)) {
            ProviderSectionTitle(
                title = stringResource(R.string.provider_logins_title),
                subtitle = stringResource(R.string.provider_logins_desc),
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
            Text(
                text = stringResource(R.string.provider_logins_update_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CinemaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = row.username, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = ExtraLoginsViewModel.statusText(context, row),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        )
                    }
                    if (!row.isMain && row.hasPassword) {
                        IconAction(
                            onClick = { loginsViewModel.makeMain(row.username) },
                            icon = Icons.Outlined.StarOutline,
                            label = stringResource(R.string.provider_logins_make_main),
                            enabled = !busy,
                        )
                    }
                    if (rows.size > 1) {
                        IconAction(
                            onClick = { removeUsername = row.username },
                            icon = Icons.Outlined.Delete,
                            label = stringResource(R.string.provider_logins_remove),
                            enabled = !busy,
                            tint = CinemaError,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            CinemaOutlinedButton(
                onClick = {
                    addDialogMessage = null
                    showAddDialog = true
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.provider_logins_add)) }
        }
    }

    if (showAddDialog) {
        AddLoginDialog(
            busy = busy,
            message = addDialogMessage,
            onEdit = { addDialogMessage = null },
            onAdd = { username, password ->
                val name = username.trim()
                // Only a username not yet on the source can close the dialog by appearing.
                pendingAdd = name.takeIf { n -> rows.none { it.username == n } }
                loginsViewModel.add(name, password)
            },
            onDismiss = {
                showAddDialog = false
                pendingAdd = null
                addDialogMessage = null
            },
        )
    }

    removeUsername?.let { username ->
        val promoted = loginsViewModel.promotedOnRemove(username)
        CinemaAlertDialog(
            onDismissRequest = { removeUsername = null },
            title = { Text(stringResource(R.string.provider_logins_remove_confirm_title)) },
            text = {
                Text(
                    if (promoted != null) {
                        stringResource(R.string.provider_logins_remove_main_confirm_format, username, promoted)
                    } else {
                        stringResource(R.string.provider_logins_remove_confirm_format, username)
                    },
                )
            },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        loginsViewModel.remove(username)
                        removeUsername = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                ) { Text(stringResource(R.string.provider_logins_remove)) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { removeUsername = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** Username + password for a new login; [message] is the last result (refused, other server, …). */
@Composable
private fun AddLoginDialog(
    busy: Boolean,
    message: String?,
    onEdit: () -> Unit,
    onAdd: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    CinemaAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.provider_logins_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onEdit()
                    },
                    label = { Text(stringResource(R.string.provider_username_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onEdit()
                    },
                    label = { Text(stringResource(R.string.provider_password_label)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) CinemaIcons.VisibilityOff else CinemaIcons.Visibility
                        val description =
                            if (passwordVisible) {
                                stringResource(R.string.provider_hide_password)
                            } else {
                                stringResource(R.string.provider_show_password)
                            }
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                val status = if (busy) stringResource(R.string.provider_logins_checking_add) else message
                status?.let {
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (busy) MaterialTheme.colorScheme.onSurface else CinemaError,
                    )
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = { onAdd(username, password) },
                enabled = !busy,
            ) { Text(stringResource(R.string.provider_logins_add)) }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
