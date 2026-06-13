package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun JellyfinForm(
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isEditMode: Boolean,
    isBusy: Boolean,
    onErrorChange: (String?) -> Unit,
    onQuickConnectClick: () -> Unit,
) {
    val scale = LocalUiScale.current
    val context = LocalContext.current
    val scaledBodySmall =
        MaterialTheme.typography.bodySmall.let { style ->
            remember(scale, style) { style.copy(fontSize = style.fontSize.scaled(scale)) }
        }

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = url,
        onValueChange = { newValue ->
            val parsed = parseUrlCredentials(newValue)
            if (parsed != null) {
                onUrlChange(parsed.baseUrl)
                parsed.username?.let { onUsernameChange(it) }
                parsed.password?.let { onPasswordChange(it) }
            } else {
                onUrlChange(newValue)
            }
            onErrorChange(null)
        },
        label = stringResource(R.string.login_server_url_label),
        placeholder = stringResource(R.string.provider_url_placeholder_jellyfin),
        keyboardType = KeyboardType.Uri,
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = username,
        onValueChange = {
            onUsernameChange(it)
            onErrorChange(null)
        },
        label = stringResource(R.string.login_username_label),
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = password,
        onValueChange = {
            onPasswordChange(it)
            onErrorChange(null)
        },
        label = stringResource(R.string.login_password_label),
        visualTransformation = PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        displayText = if (password.isNotEmpty()) "\u2022".repeat(password.length) else "",
    )

    if (!isEditMode) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
            Text(
                text = stringResource(R.string.provider_or_separator),
                style = scaledBodySmall,
                color = CinemaTextSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            CinemaSecondaryButton(
                onClick = {
                    if (url.isBlank()) {
                        onErrorChange(context.getString(R.string.provider_enter_jellyfin_url_first))
                    } else {
                        onQuickConnectClick()
                    }
                },
                text = stringResource(R.string.provider_use_quick_connect),
                enabled = !isBusy,
            )
        }
    }
}
