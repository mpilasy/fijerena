package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry
import org.njarasoa.fijerena.core.ui.viewmodels.ExtraLoginsViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaTextButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import java.time.Instant
import java.time.ZoneId

/**
 * Logins, for an Xtream source being edited: the main login and the extra ones playback shares,
 * each with Make main and Remove, and Add login (runs the same-panel check). [onMainLoginChanged]
 * runs when Make main or Remove changed the main login, so the screen reloads its own username
 * and password fields. See the shared logins plan → Phase 1.
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
    // Today, for the expiry colours: yellow at 15 days or fewer, red at 7 or fewer and once expired.
    val now = remember { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }

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
            CinemaText(stringResource(R.string.provider_logins_update_warning), role = TextRole.BodySmall, tone = TextTone.Secondary)
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CinemaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CinemaText(row.username, role = TextRole.Heading)
                        CinemaText(
                            ExtraLoginsViewModel.statusText(context, row),
                            role = TextRole.BodySmall,
                            tone =
                                when (ExtraLoginsViewModel.expiryLevel(row, now, zone)) {
                                    SubscriptionExpiry.Level.URGENT -> TextTone.Danger
                                    SubscriptionExpiry.Level.SOON -> TextTone.Warning
                                    SubscriptionExpiry.Level.NORMAL -> TextTone.Secondary
                                },
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
                            tint = CinemaTheme.colors.danger,
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
            ) { CinemaText(stringResource(R.string.provider_logins_add), role = TextRole.Label, tone = TextTone.Inherit) }
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
            title = { CinemaText(stringResource(R.string.provider_logins_remove_confirm_title), role = TextRole.Title) },
            text = {
                CinemaText(
                    if (promoted != null) {
                        stringResource(R.string.provider_logins_remove_main_confirm_format, username, promoted)
                    } else {
                        stringResource(R.string.provider_logins_remove_confirm_format, username)
                    },
                    tone = TextTone.Secondary,
                )
            },
            confirmButton = {
                CinemaDialogActionButton(
                    onClick = {
                        loginsViewModel.remove(username)
                        removeUsername = null
                    },
                    kind = ButtonKind.Danger,
                ) { CinemaText(stringResource(R.string.provider_logins_remove), role = TextRole.Label, tone = TextTone.Inherit) }
            },
            dismissButton = {
                CinemaDialogActionButton(
                    onClick = { removeUsername = null },
                    kind = ButtonKind.Other,
                ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
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
        title = { CinemaText(stringResource(R.string.provider_logins_add), role = TextRole.Title) },
        text = {
            Column {
                CinemaTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onEdit()
                    },
                    label = stringResource(R.string.provider_username_label),
                    keyboardOptions = FieldKeyboards.Username,
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                CinemaTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onEdit()
                    },
                    label = stringResource(R.string.provider_password_label),
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
                    keyboardOptions = FieldKeyboards.Password,
                    modifier = Modifier.fillMaxWidth(),
                )
                val status = if (busy) stringResource(R.string.provider_logins_checking_add) else message
                status?.let {
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    CinemaText(it, tone = if (busy) TextTone.Primary else TextTone.Danger)
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = { onAdd(username, password) },
                enabled = !busy,
                kind = ButtonKind.Main,
            ) { CinemaText(stringResource(R.string.provider_logins_add), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                enabled = !busy,
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
        },
    )
}
