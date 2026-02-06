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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
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
    val saveState by viewModel.saveState.collectAsState()
    val isBusy = saveState is SaveState.Validating || saveState is SaveState.Saving

    // Cache management state (edit mode only)
    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var cacheRefreshTrigger by remember { mutableIntStateOf(0) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearLiveTvCacheDialog by remember { mutableStateOf(false) }
    var showClearMoviesCacheDialog by remember { mutableStateOf(false) }
    var showClearTvShowsCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editId, cacheRefreshTrigger) {
        if (isEditMode) {
            cacheStats = providerRepo.getCacheStatsForProvider(editId)
        }
    }

    // Load existing provider data in edit mode directly from the repository
    LaunchedEffect(editId) {
        if (isEditMode) {
            val provider = providerRepo.getProviderById(editId)
            if (provider != null) {
                name = provider.name
                url = provider.url
                username = provider.username
                password = providerRepo.getPassword(editId) ?: ""
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
                .padding(CinemaSpacing.md)
                .verticalScroll(rememberScrollState())
        ) {
            // Provider type selector
            Text(
                text = "Provider Type",
                style = MaterialTheme.typography.labelLarge,
                color = CinemaTextSecondary
            )

            Spacer(modifier = Modifier.height(CinemaSpacing.sm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)
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

            Spacer(modifier = Modifier.height(CinemaSpacing.md))

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
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { newValue ->
                            val parsed = parseUrlCredentials(newValue)
                            if (parsed != null) {
                                url = parsed.baseUrl
                                parsed.username?.let { username = it }
                                parsed.password?.let { password = it }
                            } else {
                                url = newValue
                            }
                            error = null
                        },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://provider.example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { newValue ->
                            val parsed = parseUrlCredentials(newValue)
                            if (parsed != null) {
                                url = parsed.baseUrl
                                parsed.username?.let { username = it }
                                parsed.password?.let { password = it }
                            } else {
                                url = newValue
                            }
                            error = null
                        },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.100:8096") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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

            // Cache Management (edit mode only)
            if (isEditMode) {
                Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                Text(
                    text = "Cache Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                Text(
                    text = "Clear cached data to free up storage space",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                cacheStats?.let { stats ->
                    // Total cache size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Cache Size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = formatBytes(stats.totalSize),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = { showClearCacheDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) {
                            Text("Clear All")
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.divider)
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // Live TV
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Live TV", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = formatBytes(stats.liveTv.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${if (stats.liveTv.categoryCached) "1 category" else "No categories"}, ${stats.liveTv.streamListsCount} stream lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearLiveTvCacheDialog = true },
                            enabled = stats.liveTv.size > 0
                        ) { Text("Clear") }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // Movies
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Movies", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = formatBytes(stats.movies.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${if (stats.movies.categoryCached) "1 category" else "No categories"}, ${stats.movies.streamListsCount} stream lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearMoviesCacheDialog = true },
                            enabled = stats.movies.size > 0
                        ) { Text("Clear") }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // TV Shows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "TV Shows", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = formatBytes(stats.tvShows.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${if (stats.tvShows.categoryCached) "1 category" else "No categories"}, ${stats.tvShows.streamListsCount} stream lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearTvShowsCacheDialog = true },
                            enabled = stats.tvShows.size > 0
                        ) { Text("Clear") }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // EPG & Other
                    Text(
                        text = "EPG Data: ${stats.epgCount} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Text(
                        text = "Other: ${formatBytes(stats.otherSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                }
                } // Column
                } // GlassPanel
            }

            error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaError
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.lg))

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
                        val saveUrl = when (selectedType) {
                            ProviderType.SMB -> "smb://${host.trim()}/${shareName.trim()}"
                            else -> url.trim()
                        }
                        val saveConfig = when (selectedType) {
                            ProviderType.SMB -> """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                            else -> ""
                        }

                        viewModel.validateAndSave(
                            id = if (isEditMode) editId else null,
                            name = name.trim(),
                            url = saveUrl,
                            username = username.trim(),
                            password = password.trim(),
                            type = selectedType.name,
                            config = saveConfig,
                            onComplete = onSuccess
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (saveState) {
                        is SaveState.Validating -> "Connecting..."
                        is SaveState.Saving -> "Saving..."
                        else -> if (isEditMode) "Update Provider" else "Add Provider"
                    }
                )
            }

            // Validation failure dialog
            val failedState = saveState as? SaveState.ValidationFailed
            if (failedState != null) {
                val saveUrl = when (selectedType) {
                    ProviderType.SMB -> "smb://${host.trim()}/${shareName.trim()}"
                    else -> url.trim()
                }
                val saveConfig = when (selectedType) {
                    ProviderType.SMB -> """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                    else -> ""
                }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { viewModel.resetSaveState() },
                    title = { Text("Connection Failed") },
                    text = { Text(failedState.errorMessage) },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.forceSave(
                                    id = if (isEditMode) editId else null,
                                    name = name.trim(),
                                    url = saveUrl,
                                    username = username.trim(),
                                    password = password.trim(),
                                    type = selectedType.name,
                                    config = saveConfig,
                                    onComplete = onSuccess
                                )
                            }
                        ) {
                            Text("Save Anyway")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.resetSaveState() }
                        ) {
                            Text("Go Back")
                        }
                    }
                )
            }

            // Cache confirmation dialogs
            if (showClearCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearCacheDialog = false },
                    title = { Text("Clear All Cache?") },
                    text = { Text("This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                providerRepo.clearAllCacheForProvider(editId)
                                cacheRefreshTrigger++
                                showClearCacheDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Clear All") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showClearLiveTvCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearLiveTvCacheDialog = false },
                    title = { Text("Clear Live TV Cache?") },
                    text = { Text("This will remove all cached Live TV data (categories and streams).") },
                    confirmButton = {
                        Button(
                            onClick = {
                                providerRepo.clearCacheForProviderContentType(editId, "LIVE_TV")
                                cacheRefreshTrigger++
                                showClearLiveTvCacheDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearLiveTvCacheDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showClearMoviesCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearMoviesCacheDialog = false },
                    title = { Text("Clear Movies Cache?") },
                    text = { Text("This will remove all cached Movies data (categories and streams).") },
                    confirmButton = {
                        Button(
                            onClick = {
                                providerRepo.clearCacheForProviderContentType(editId, "MOVIES")
                                cacheRefreshTrigger++
                                showClearMoviesCacheDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearMoviesCacheDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showClearTvShowsCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearTvShowsCacheDialog = false },
                    title = { Text("Clear TV Shows Cache?") },
                    text = { Text("This will remove all cached TV Shows data (categories and streams).") },
                    confirmButton = {
                        Button(
                            onClick = {
                                providerRepo.clearCacheForProviderContentType(editId, "TV_SHOWS")
                                cacheRefreshTrigger++
                                showClearTvShowsCacheDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearTvShowsCacheDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
