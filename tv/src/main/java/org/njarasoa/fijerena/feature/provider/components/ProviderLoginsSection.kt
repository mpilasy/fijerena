package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ExtraLoginsViewModel
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Logins, for an Xtream source being edited (docs/plans/20261005_shared-logins-plan.md, Phase 1):
 * the main login first (marked as current), then the extra ones playback shares, each a row with
 * what the panel says about it and its actions as icons on the right (docs/plans/20261005_icon-buttons-plan.md):
 * Make main (an extra login with a password) and Remove (when the source has more than one login,
 * after a confirmation); a lone login is a focusable row of its own. Then Add login, which opens a dialog and runs the same-panel check. Every change
 * applies at once. [onMainLoginChanged] gets the main username whenever it is known or changes, so
 * the screen's own login fields can follow it. Rows of [pane], the settings column of Edit Source.
 */
@Composable
fun ProviderLoginsSection(
    viewModel: ExtraLoginsViewModel,
    pane: PaneFocusState,
    onMainLoginChanged: (String) -> Unit,
) {
    val scale = LocalUiScale.current
    val context = LocalContext.current
    val typography = MaterialTheme.typography
    val bodySmall = remember(scale, typography) { typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale)) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // The last result, shown under the rows (and in the add dialog) until the next action.
    var shownMessage by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    // The username being added: the dialog closes once it is on the list (the check passed).
    var pendingAdd by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    // After Make main / Remove the focused button goes away: focus the main login's row once the
    // list shows [expectMain] first and [expectGone] gone.
    var expectMain by remember { mutableStateOf<String?>(null) }
    var expectGone by remember { mutableStateOf<String?>(null) }
    // Focus back on the opener: Add login after its dialog, a row's Remove after Cancel.
    var refocusAdd by remember { mutableStateOf(false) }
    var refocusRemoveOf by remember { mutableStateOf<String?>(null) }
    val addRequester = remember { FocusRequester() }
    val mainRowRequester = remember { FocusRequester() }

    val mainUsername = rows.firstOrNull()?.username
    LaunchedEffect(mainUsername) { mainUsername?.let(onMainLoginChanged) }

    LaunchedEffect(message) {
        message?.let {
            shownMessage = it
            viewModel.clearMessage()
            expectMain = null
            expectGone = null
        }
    }

    LaunchedEffect(rows) {
        val adding = pendingAdd
        if (adding != null && rows.any { it.username == adding }) {
            pendingAdd = null
            showAddDialog = false
            refocusAdd = true
        }
        val main = expectMain
        val gone = expectGone
        if (main != null && rows.firstOrNull()?.username == main && rows.none { it.username == gone }) {
            expectMain = null
            expectGone = null
            mainRowRequester.requestFocusWithRetry(fallback = addRequester)
        }
    }

    LaunchedEffect(refocusAdd) {
        if (refocusAdd) {
            addRequester.requestFocusWithRetry()
            refocusAdd = false
        }
    }

    ProviderSectionTitle(
        title = stringResource(R.string.provider_logins_title),
        subtitle = stringResource(R.string.provider_logins_desc),
    )
    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
    Text(
        text = stringResource(R.string.provider_logins_update_warning),
        style = bodySmall,
        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
    )

    rows.forEachIndexed { index, row ->
        key(row.username) {
            val removeRequester = remember { FocusRequester() }
            LaunchedEffect(refocusRemoveOf) {
                if (refocusRemoveOf == row.username) {
                    removeRequester.requestFocusWithRetry()
                    refocusRemoveOf = null
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            val showMakeMain = !row.isMain && row.hasPassword
            val showRemove = rows.size > 1
            val makeMainRequester = remember { FocusRequester() }
            val loginText: @Composable () -> Unit = {
                Column {
                    Text(
                        text = row.username,
                        color = if (row.isMain) TvFocusTokens.currentText else CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ExtraLoginsViewModel.statusText(context, row),
                        style = bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!showMakeMain && !showRemove) {
                // The only login: no buttons, so the row itself is the focus stop (Up/Down never
                // skip content).
                TvInputListItem(
                    selected = row.isMain,
                    onClick = {},
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(mainRowRequester) else Modifier)
                            .paneItem(pane, "login:${row.username}"),
                    headlineContent = { Text(row.username) },
                    supportingContent = {
                        Text(
                            text = ExtraLoginsViewModel.statusText(context, row),
                            style = bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    },
                )
            } else {
                // The login on the left, its actions as icons on the right; the icons are the
                // focus stops, the label shows on the focused one.
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .currentIndicator(active = row.isMain)
                            .padding(start = Spacing.md.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) { loginText() }
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    if (showMakeMain) {
                        TvIconAction(
                            onClick = {
                                shownMessage = null
                                expectMain = row.username
                                expectGone = null
                                viewModel.makeMain(row.username)
                            },
                            icon = Icons.Outlined.StarOutline,
                            label = stringResource(R.string.provider_logins_make_main),
                            enabled = !busy,
                            modifier =
                                Modifier
                                    .focusRequester(makeMainRequester)
                                    .paneItem(pane, "login_make_main:${row.username}")
                                    .rowNeighbours(right = if (showRemove) removeRequester else null),
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    }
                    if (showRemove) {
                        TvIconAction(
                            onClick = {
                                shownMessage = null
                                removeTarget = row.username
                            },
                            icon = Icons.Outlined.Delete,
                            label = stringResource(R.string.provider_logins_remove),
                            danger = true,
                            modifier =
                                Modifier
                                    .focusRequester(removeRequester)
                                    .then(if (index == 0) Modifier.focusRequester(mainRowRequester) else Modifier)
                                    .paneItem(pane, "login_remove:${row.username}")
                                    .rowNeighbours(left = if (showMakeMain) makeMainRequester else null),
                        )
                    }
                }
            }
        }
    }

    shownMessage?.takeIf { !showAddDialog }?.let {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
        Text(text = it, style = bodySmall, color = CinemaAccent)
    }

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
    CinemaSecondaryButton(
        onClick = {
            shownMessage = null
            showAddDialog = true
        },
        enabled = !busy,
        text = stringResource(R.string.provider_logins_add),
        modifier = Modifier.focusRequester(addRequester).paneItem(pane, "login_add"),
    )

    if (showAddDialog) {
        AddLoginDialog(
            busy = busy,
            message = shownMessage,
            onAdd = { username, password ->
                shownMessage = null
                // A username already on the list is refused (the message says so); don't wait for it.
                pendingAdd = username.trim().takeIf { name -> rows.none { it.username == name } }
                viewModel.add(username, password)
            },
            onDismiss = {
                pendingAdd = null
                showAddDialog = false
                refocusAdd = true
            },
        )
    }

    removeTarget?.let { username ->
        val promoted = viewModel.promotedOnRemove(username)
        ConfirmActionDialog(
            title = stringResource(R.string.provider_logins_remove_confirm_title),
            text =
                if (promoted != null) {
                    stringResource(R.string.provider_logins_remove_main_confirm_format, username, promoted)
                } else {
                    stringResource(R.string.provider_logins_remove_confirm_format, username)
                },
            confirmText = stringResource(R.string.provider_logins_remove),
            onConfirm = {
                removeTarget = null
                expectMain = promoted ?: mainUsername
                expectGone = username
                viewModel.remove(username)
            },
            onDismiss = {
                removeTarget = null
                refocusRemoveOf = username
            },
        )
    }
}

/**
 * Username and password of a login to add, the same fields as the source's own login. Add runs
 * the same-panel check, which takes a few seconds ([busy]); a refusal shows in [message] and the
 * dialog stays open. Back or Cancel closes it.
 */
@Composable
private fun AddLoginDialog(
    busy: Boolean,
    message: String?,
    onAdd: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scale = LocalUiScale.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val usernameRequester = remember { FocusRequester() }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_logins_add), color = CinemaTextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ReadOnlyFieldWithEdit(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.provider_username_label),
                    editButtonFocusRequester = usernameRequester,
                )
                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                ReadOnlyFieldWithEdit(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.provider_password_label),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    displayText = if (password.isNotEmpty()) "•".repeat(password.length) else "",
                )
                val status = if (busy) stringResource(R.string.provider_logins_checking_add) else message
                status?.let {
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = CinemaTextSecondary)
                }
            }
        },
        initialFocus = usernameRequester,
        confirmButton = {
            CinemaDialogActionButton(
                onClick = { onAdd(username, password.trim()) },
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaAccent,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = {
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary,
                    ),
            ) { Text(stringResource(R.string.common_cancel)) }
        },
        containerColor = CinemaSurface,
    )
}
