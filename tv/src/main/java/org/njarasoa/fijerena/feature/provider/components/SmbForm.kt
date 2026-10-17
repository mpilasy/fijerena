package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun SmbForm(
    host: String,
    onHostChange: (String) -> Unit,
    shareName: String,
    onShareNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onErrorChange: (String?) -> Unit,
) {
    val scale = LocalUiScale.current

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = host,
        onValueChange = {
            onHostChange(it)
            onErrorChange(null)
        },
        label = stringResource(R.string.provider_host_label),
        keyboardOptions = FieldKeyboards.Url,
        placeholder = stringResource(R.string.provider_host_placeholder),
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = shareName,
        onValueChange = {
            onShareNameChange(it)
            onErrorChange(null)
        },
        label = stringResource(R.string.provider_share_label),
        keyboardOptions = FieldKeyboards.Url,
        placeholder = stringResource(R.string.provider_share_placeholder),
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = username,
        onValueChange = {
            onUsernameChange(it)
            onErrorChange(null)
        },
        label = stringResource(R.string.provider_username_optional_label),
        keyboardOptions = FieldKeyboards.Username,
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = password,
        onValueChange = {
            onPasswordChange(it)
            onErrorChange(null)
        },
        label = stringResource(R.string.provider_password_optional_label),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = FieldKeyboards.Password,
        displayText = if (password.isNotEmpty()) "\u2022".repeat(password.length) else "",
    )
}
