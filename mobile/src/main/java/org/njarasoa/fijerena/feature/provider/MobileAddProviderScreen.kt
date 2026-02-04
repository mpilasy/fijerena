package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel = viewModel(
        factory = ProviderViewModelFactory(context)
    )
    val isEditMode = editId > 0L

    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing provider data in edit mode
    LaunchedEffect(editId) {
        if (isEditMode) {
            val uiState = viewModel.uiState.value
            val providers = when (uiState) {
                is ProviderUiState.SingleProvider -> listOf(uiState.provider)
                is ProviderUiState.MultipleProviders -> uiState.providers
                else -> emptyList()
            }
            val provider = providers.find { it.id == editId }
            if (provider != null) {
                name = provider.name
                url = provider.url
                username = provider.username
                password = viewModel.getPassword(editId) ?: ""
                selectedType = try { ProviderType.valueOf(provider.type) } catch (_: Exception) { ProviderType.XTREAM }
                if (provider.type == "SMB" && provider.config.isNotBlank()) {
                    try {
                        val json = org.json.JSONObject(provider.config)
                        host = json.optString("host", "")
                        shareName = json.optString("share", "")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditMode) "Edit Provider" else "Add Provider")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Provider type selector
            Text(
                text = "Provider Type",
                style = MaterialTheme.typography.labelLarge,
                color = CinemaTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            error = null
                        },
                        label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CinemaAccent,
                            selectedLabelColor = CinemaTextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name field (common to all types)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Provider Name") },
                placeholder = { Text(when (selectedType) {
                    ProviderType.XTREAM -> "e.g. My IPTV"
                    ProviderType.JELLYFIN -> "e.g. My Jellyfin Server"
                    ProviderType.SMB -> "e.g. NAS Media"
                    ProviderType.LOCAL -> "e.g. Local Videos"
                }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Type-specific fields
            when (selectedType) {
                ProviderType.XTREAM -> {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = null
                        },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://provider.example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            error = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ProviderType.JELLYFIN -> {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = null
                        },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.100:8096") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            error = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ProviderType.SMB -> {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = host,
                        onValueChange = {
                            host = it
                            error = null
                        },
                        label = { Text("Host / IP") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = shareName,
                        onValueChange = {
                            shareName = it
                            error = null
                        },
                        label = { Text("Share Name") },
                        placeholder = { Text("media") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            error = null
                        },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ProviderType.LOCAL -> {
                    // Only the name field is needed; folder/file picker will be added later
                }
            }

            error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaError
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // Validation based on selected type
                    val validationError = when (selectedType) {
                        ProviderType.XTREAM -> when {
                            name.isBlank() -> "Provider name is required"
                            url.isBlank() -> "Server URL is required"
                            username.isBlank() -> "Username is required"
                            password.isBlank() -> "Password is required"
                            else -> null
                        }
                        ProviderType.JELLYFIN -> when {
                            name.isBlank() -> "Provider name is required"
                            url.isBlank() -> "Server URL is required"
                            username.isBlank() -> "Username is required"
                            password.isBlank() -> "Password is required"
                            else -> null
                        }
                        ProviderType.SMB -> when {
                            name.isBlank() -> "Provider name is required"
                            host.isBlank() -> "Host / IP is required"
                            shareName.isBlank() -> "Share name is required"
                            else -> null
                        }
                        ProviderType.LOCAL -> when {
                            name.isBlank() -> "Provider name is required"
                            else -> null
                        }
                    }

                    if (validationError != null) {
                        error = validationError
                    } else {
                        isSaving = true

                        val saveUrl = when (selectedType) {
                            ProviderType.SMB -> "smb://${host.trim()}/${shareName.trim()}"
                            else -> url.trim()
                        }
                        val saveConfig = when (selectedType) {
                            ProviderType.SMB -> """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                            else -> ""
                        }

                        if (isEditMode) {
                            viewModel.updateProvider(
                                id = editId,
                                name = name.trim(),
                                url = saveUrl,
                                username = username.trim(),
                                password = password.trim(),
                                type = selectedType.name,
                                config = saveConfig
                            )
                        } else {
                            viewModel.addProvider(
                                name = name.trim(),
                                url = saveUrl,
                                username = username.trim(),
                                password = password.trim(),
                                type = selectedType.name,
                                config = saveConfig
                            )
                        }
                        onSuccess()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSaving) "Saving..."
                    else if (isEditMode) "Update Provider"
                    else "Add Provider"
                )
            }
        }
    }
}
