package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@Composable
fun ColumnScope.ProviderFormSection(
    selectedType: ProviderType,
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    shareName: String,
    onShareNameChange: (String) -> Unit,
    isEditMode: Boolean,
    isBusy: Boolean,
    onErrorChange: (String?) -> Unit,
    onShowQuickConnectDialogChange: (Boolean) -> Unit,
    onStreamOutputFormatChange: (String) -> Unit,
    onPlaylistTypeChange: (String) -> Unit,
    onQcCodeChange: (String) -> Unit,
    onQcSecretChange: (String) -> Unit,
    onQcErrorChange: (String?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Type-specific fields
    when (selectedType) {
        ProviderType.XTREAM -> {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = url,
                onValueChange = { newValue ->
                    val parsed = parseUrlCredentials(newValue)
                    if (parsed != null) {
                        onUrlChange(parsed.baseUrl)
                        parsed.username?.let { onUsernameChange(it) }
                        parsed.password?.let { onPasswordChange(it) }
                        parsed.streamOutputFormat?.let { onStreamOutputFormatChange(it) }
                        parsed.playlistType?.let { onPlaylistTypeChange(it) }
                    } else {
                        onUrlChange(newValue)
                    }
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_url_label)) },
                placeholder = { Text(stringResource(R.string.provider_url_placeholder_xtream)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    onUsernameChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_username_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    onPasswordChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_password_label)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) CinemaIcons.VisibilityOff else CinemaIcons.Visibility
                    val description = if (passwordVisible) stringResource(R.string.provider_hide_password) else stringResource(R.string.provider_show_password)
                    IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ProviderType.JELLYFIN -> {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
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
                label = { Text(stringResource(R.string.provider_url_label)) },
                placeholder = { Text(stringResource(R.string.provider_url_placeholder_jellyfin)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    onUsernameChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_username_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    onPasswordChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_password_label)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) CinemaIcons.VisibilityOff else CinemaIcons.Visibility
                    val description = if (passwordVisible) stringResource(R.string.provider_hide_password) else stringResource(R.string.provider_show_password)
                    IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isEditMode) {
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Text(
                    text = stringResource(R.string.provider_or_separator),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                OutlinedButton(
                    onClick = {
                        if (url.isBlank()) {
                            onErrorChange(context.getString(R.string.provider_enter_jellyfin_url_first))
                        } else {
                            onQcCodeChange("")
                            onQcSecretChange("")
                            onQcErrorChange(null)
                            onShowQuickConnectDialogChange(true)
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.provider_use_quick_connect))
                }
            }
        }

        ProviderType.SMB -> {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = host,
                onValueChange = {
                    onHostChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_host_label)) },
                placeholder = { Text(stringResource(R.string.provider_host_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = shareName,
                onValueChange = {
                    onShareNameChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_share_label)) },
                placeholder = { Text(stringResource(R.string.provider_share_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    onUsernameChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_username_optional_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    onPasswordChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_password_optional_label)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) CinemaIcons.VisibilityOff else CinemaIcons.Visibility
                    val description = if (passwordVisible) stringResource(R.string.provider_hide_password) else stringResource(R.string.provider_show_password)
                    IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ProviderType.LOCAL -> {
            // Only the name field is needed; folder/file picker will be added later
        }

        ProviderType.REMOTE_M3U -> {
            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            OutlinedTextField(
                value = url,
                onValueChange = {
                    onUrlChange(it)
                    onErrorChange(null)
                },
                label = { Text(stringResource(R.string.provider_m3u_url_label)) },
                placeholder = { Text(stringResource(R.string.provider_m3u_url_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
