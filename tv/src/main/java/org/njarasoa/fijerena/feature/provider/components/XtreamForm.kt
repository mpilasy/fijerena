package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun XtreamForm(
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onErrorChange: (String?) -> Unit,
    onOutputFormatChange: (String) -> Unit = {}
) {
    val scale = LocalUiScale.current

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = url,
        onValueChange = { newValue ->
            val parsed = parseUrlCredentials(newValue)
            if (parsed != null) {
                onUrlChange(parsed.baseUrl)
                parsed.username?.let { onUsernameChange(it) }
                parsed.password?.let { onPasswordChange(it) }
                parsed.streamOutputFormat?.let { onOutputFormatChange(it) }
            } else {
                onUrlChange(newValue)
            }
            onErrorChange(null)
        },
        label = "Server URL",
        placeholder = "http://provider.example.com",
        keyboardType = KeyboardType.Uri
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = username,
        onValueChange = {
            onUsernameChange(it)
            onErrorChange(null)
        },
        label = "Username"
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = password,
        onValueChange = {
            onPasswordChange(it)
            onErrorChange(null)
        },
        label = "Password",
        visualTransformation = PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        displayText = if (password.isNotEmpty()) "\u2022".repeat(password.length) else ""
    )
}
