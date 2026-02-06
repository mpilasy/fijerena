@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.*

@Composable
fun TvAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel = viewModel(
        factory = ProviderViewModelFactory(context)
    )
    val isEditMode = editId > 0L

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(TvDimensions.formFieldWidth)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Edit Provider" else "Add Provider",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Provider type dropdown
                var typeDropdownExpanded by remember { mutableStateOf(false) }
                @OptIn(ExperimentalMaterial3Api::class)
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CinemaTextPrimary,
                            unfocusedTextColor = CinemaTextPrimary,
                            cursorColor = CinemaAccent,
                            focusedBorderColor = CinemaAccent,
                            unfocusedBorderColor = CinemaTextSecondary,
                            focusedLabelColor = CinemaAccent,
                            unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            focusedTrailingIconColor = CinemaAccent,
                            unfocusedTrailingIconColor = CinemaTextSecondary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                        containerColor = CinemaSurface
                    ) {
                        ProviderType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = type.displayName,
                                        color = if (type == selectedType) CinemaAccent else CinemaTextPrimary
                                    )
                                },
                                onClick = {
                                    selectedType = type
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Name field (all types)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Provider Name") },
                    placeholder = { Text("e.g. My IPTV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )

                // Type-specific fields
                when (selectedType) {
                    ProviderType.XTREAM -> {
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // URL field
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
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Username field
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                error = null
                            },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Password field
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )
                    }

                    ProviderType.JELLYFIN -> {
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Server URL field
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
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Username field
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                error = null
                            },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Password field
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )
                    }

                    ProviderType.SMB -> {
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Host/IP field
                        OutlinedTextField(
                            value = host,
                            onValueChange = {
                                host = it
                                error = null
                            },
                            label = { Text("Host / IP") },
                            placeholder = { Text("192.168.1.100") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Share Name field
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = {
                                shareName = it
                                error = null
                            },
                            label = { Text("Share Name") },
                            placeholder = { Text("media") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Username field (optional)
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                error = null
                            },
                            label = { Text("Username (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Password field (optional)
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )
                    }

                    ProviderType.LOCAL -> {
                        // LOCAL type only requires name - folder/file picker will be added later
                    }
                }

                // Cache Management (edit mode only)
                if (isEditMode) {
                    Spacer(modifier = Modifier.height(Spacing.xl))

                    Text(
                        text = "Cache Management",
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = "Clear cached data to free up storage space",
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

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
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.totalSize),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = CinemaAccent
                                )
                            }
                            CinemaDangerButton(
                                onClick = { showClearCacheDialog = true },
                                text = "Clear All"
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg))
                        HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Live TV Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live TV",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.liveTv.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.liveTv.categoryCached) "1 category" else "No categories"}, ${stats.liveTv.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showClearLiveTvCacheDialog = true },
                                text = "Clear",
                                enabled = stats.liveTv.size > 0
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Movies Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Movies",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.movies.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.movies.categoryCached) "1 category" else "No categories"}, ${stats.movies.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showClearMoviesCacheDialog = true },
                                text = "Clear",
                                enabled = stats.movies.size > 0
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // TV Shows Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TV Shows",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.tvShows.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.tvShows.categoryCached) "1 category" else "No categories"}, ${stats.tvShows.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showClearTvShowsCacheDialog = true },
                                text = "Clear",
                                enabled = stats.tvShows.size > 0
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // EPG & Other
                        Text(
                            text = "EPG Data: ${stats.epgCount} channels",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Text(
                            text = "Other: ${formatBytes(stats.otherSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                    }
                }

                // Error message
                error?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaError
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally)
                ) {
                    CinemaSecondaryButton(
                        onClick = onBack,
                        enabled = !isBusy,
                        text = "Cancel"
                    )

                    CinemaPrimaryButton(
                        onClick = {
                            // Validate based on selected provider type
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
                                val saveUrl = if (selectedType == ProviderType.SMB) "" else url.trim()
                                val saveUsername = username.trim()
                                val savePassword = password.trim()
                                val saveConfig = if (selectedType == ProviderType.SMB) {
                                    """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                                } else ""

                                viewModel.validateAndSave(
                                    id = if (isEditMode) editId else null,
                                    name = name.trim(),
                                    url = saveUrl,
                                    username = saveUsername,
                                    password = savePassword,
                                    type = selectedType.name,
                                    config = saveConfig,
                                    onComplete = onSuccess
                                )
                            }
                        },
                        enabled = !isBusy,
                        text = when (saveState) {
                            is SaveState.Validating -> "Connecting..."
                            is SaveState.Saving -> "Saving..."
                            else -> if (isEditMode) "Update" else "Add"
                        }
                    )
                }

                // Validation failure dialog
                val failedState = saveState as? SaveState.ValidationFailed
                if (failedState != null) {
                    val saveUrl = if (selectedType == ProviderType.SMB) "" else url.trim()
                    val saveConfig = if (selectedType == ProviderType.SMB) {
                        """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                    } else ""

                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.resetSaveState() },
                        title = {
                            Text(
                                "Connection Failed",
                                color = CinemaTextPrimary
                            )
                        },
                        text = {
                            Text(
                                failedState.errorMessage,
                                color = CinemaTextSecondary
                            )
                        },
                        confirmButton = {
                            CinemaDangerButton(
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
                                },
                                text = "Save Anyway"
                            )
                        },
                        dismissButton = {
                            CinemaSecondaryButton(
                                onClick = { viewModel.resetSaveState() },
                                text = "Go Back"
                            )
                        },
                        containerColor = CinemaSurface
                    )
                }

                // Cache confirmation dialogs
                if (showClearCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearCacheDialog = false },
                        title = { Text("Clear All Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server.", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearAllCacheForProvider(editId)
                                    cacheRefreshTrigger++
                                    showClearCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear All") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                if (showClearLiveTvCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearLiveTvCacheDialog = false },
                        title = { Text("Clear Live TV Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached Live TV data (categories and streams).", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearCacheForProviderContentType(editId, "LIVE_TV")
                                    cacheRefreshTrigger++
                                    showClearLiveTvCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearLiveTvCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                if (showClearMoviesCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearMoviesCacheDialog = false },
                        title = { Text("Clear Movies Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached Movies data (categories and streams).", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearCacheForProviderContentType(editId, "MOVIES")
                                    cacheRefreshTrigger++
                                    showClearMoviesCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearMoviesCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                if (showClearTvShowsCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearTvShowsCacheDialog = false },
                        title = { Text("Clear TV Shows Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached TV Shows data (categories and streams).", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearCacheForProviderContentType(editId, "TV_SHOWS")
                                    cacheRefreshTrigger++
                                    showClearTvShowsCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearTvShowsCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }
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
