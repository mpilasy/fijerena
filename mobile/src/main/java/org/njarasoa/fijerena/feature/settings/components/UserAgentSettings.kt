package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.network.CustomUserAgent
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaSwitchRow
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A Nerd's User-Agent rows in Admin → This Device on the phone: **Use Default User-Agent** and,
 * while it is off, **Custom User-Agent**, whose tap opens a dialog to type it. Turning the switch
 * off with no custom text yet opens that dialog at once.
 */
@Composable
fun UserAgentSettingsRows(
    useDefault: Boolean,
    custom: String,
    onUseDefault: (Boolean) -> Unit,
    onCustom: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    CinemaSwitchRow(
        checked = useDefault,
        onCheckedChange = { on ->
            onUseDefault(on)
            if (!on && custom.isBlank()) editing = true
        },
        label = stringResource(R.string.settings_user_agent_default_title),
        modifier = Modifier.padding(horizontal = Spacing.md),
        supportingText =
            stringResource(
                if (useDefault) R.string.settings_user_agent_default_desc_on else R.string.settings_user_agent_default_desc_off,
            ),
    )
    if (!useDefault) {
        SettingsListRow(
            title = stringResource(R.string.settings_user_agent_custom_title),
            summary = custom.ifBlank { stringResource(R.string.settings_user_agent_custom_empty) },
            onClick = { editing = true },
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(custom) }
        CinemaAlertDialog(
            onDismissRequest = { editing = false },
            title = stringResource(R.string.settings_user_agent_custom_title),
            text = {
                CinemaTextField(
                    value = text,
                    onValueChange = { text = it.take(CustomUserAgent.MAX_LENGTH) },
                    label = stringResource(R.string.settings_user_agent_custom_title),
                    keyboardOptions = FieldKeyboards.Verbatim,
                    supportingText = stringResource(R.string.settings_user_agent_custom_hint),
                    singleLine = true,
                )
            },
            main =
                DialogAction(stringResource(R.string.provider_save_button)) {
                    onCustom(text)
                    editing = false
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { editing = false },
        )
    }
}
