package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
        label = "Host / IP",
        placeholder = "192.168.1.100",
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = shareName,
        onValueChange = {
            onShareNameChange(it)
            onErrorChange(null)
        },
        label = "Share Name",
        placeholder = "media",
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = username,
        onValueChange = {
            onUsernameChange(it)
            onErrorChange(null)
        },
        label = "Username (optional)",
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = password,
        onValueChange = {
            onPasswordChange(it)
            onErrorChange(null)
        },
        label = "Password (optional)",
        visualTransformation = PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        displayText = if (password.isNotEmpty()) "\u2022".repeat(password.length) else "",
    )
}
