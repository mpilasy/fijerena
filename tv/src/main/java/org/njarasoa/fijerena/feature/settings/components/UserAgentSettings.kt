package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.network.CustomUserAgent
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape

/**
 * A Nerd's User-Agent rows in Admin → This Device on TV: **Use Default User-Agent** and, while it
 * is off, **Custom User-Agent**, whose OK opens a dialog to type it. Turning the switch off with
 * no custom text yet opens that dialog at once. Focus comes back to the row that opened it.
 */
@Composable
fun UserAgentSettingsRows(
    useDefault: Boolean,
    custom: String,
    onUseDefault: (Boolean) -> Unit,
    onCustom: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val switchFocus = remember { FocusRequester() }
    val customFocus = remember { FocusRequester() }
    LaunchedEffect(closed) {
        if (closed) (if (useDefault) switchFocus else customFocus).requestFocusWithRetry()
        closed = false
    }
    TvSwitchRow(
        checked = useDefault,
        onCheckedChange = { on ->
            onUseDefault(on)
            if (!on && custom.isBlank()) editing = true
        },
        label = stringResource(R.string.settings_user_agent_default_title),
        modifier = Modifier.focusRequester(switchFocus),
        description =
            if (useDefault) {
                stringResource(R.string.settings_user_agent_default_desc_on, CustomUserAgent.STREAM, CustomUserAgent.app)
            } else {
                stringResource(R.string.settings_user_agent_default_desc_off)
            },
    )
    if (!useDefault) {
        SettingsRow(
            title = stringResource(R.string.settings_user_agent_custom_title),
            description = custom.ifBlank { stringResource(R.string.settings_user_agent_custom_empty) },
            onClick = { editing = true },
            focusRequester = customFocus,
        )
    }
    if (editing) {
        UserAgentDialog(
            initial = custom,
            onSave = {
                onCustom(it)
                editing = false
                closed = true
            },
            onDismiss = {
                editing = false
                closed = true
            },
        )
    }
}

@Composable
private fun UserAgentDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_user_agent_custom_title),
        text = {
            // The one text field look (CinemaTextField), as every other TV field while it is edited.
            CinemaTextField(
                value = text,
                onValueChange = { text = it.take(CustomUserAgent.MAX_LENGTH) },
                label = stringResource(R.string.settings_user_agent_custom_title),
                keyboardOptions = FieldKeyboards.Verbatim,
                supportingText = stringResource(R.string.settings_user_agent_custom_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().tvDpadEscape(),
            )
        },
        main = DialogAction(stringResource(R.string.provider_save_button)) { onSave(text) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
