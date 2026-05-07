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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials

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
                label = { Text("Server URL") },
                placeholder = { Text("http://provider.example.com") },
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
                label = { Text("Username") },
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
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                    val description = if (passwordVisible) "Hide password" else "Show password"
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
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8096") },
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
                label = { Text("Username") },
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
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                    val description = if (passwordVisible) "Hide password" else "Show password"
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
                    text = "— or —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                OutlinedButton(
                    onClick = {
                        if (url.isBlank()) {
                            onErrorChange("Enter the Jellyfin server URL first")
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
                    Text("Use Quick Connect")
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
                label = { Text("Host / IP") },
                placeholder = { Text("192.168.1.100") },
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
                label = { Text("Share Name") },
                placeholder = { Text("media") },
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
                label = { Text("Username (optional)") },
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
                label = { Text("Password (optional)") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                    val description = if (passwordVisible) "Hide password" else "Show password"
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
                label = { Text("M3U Playlist URL") },
                placeholder = { Text("https://example.com/playlist.m3u") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
