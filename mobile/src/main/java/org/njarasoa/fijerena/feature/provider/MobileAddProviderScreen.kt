package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
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
    var streamOutputFormat by remember { mutableStateOf("m3u8") }
    var passwordVisible by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isBusy = saveState is SaveState.Validating || saveState is SaveState.Saving || syncState is SyncState.Syncing

    // Quick Connect state (Jellyfin only)
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var qcCode by remember { mutableStateOf("") }
    var qcSecret by remember { mutableStateOf("") }
    var qcError by remember { mutableStateOf<String?>(null) }

    // Cache management state (edit mode only)
    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val syncManager = remember { DriveSettingsSyncManager(context.applicationContext, providerRepo) }
    val coroutineScope = rememberCoroutineScope()
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var cacheRefreshTrigger by remember { mutableIntStateOf(0) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearLiveTvCacheDialog by remember { mutableStateOf(false) }
    var showClearMoviesCacheDialog by remember { mutableStateOf(false) }
    var showClearTvShowsCacheDialog by remember { mutableStateOf(false) }

    // Provider settings state (edit mode only)
    var providerSettings by remember { mutableStateOf(ProviderSettings.DEFAULT) }
    var autoResumeEnabled by remember { mutableStateOf(true) }
    var watchHistorySize by remember { mutableStateOf("25") }
    var newWatchHistorySize by remember { mutableStateOf("") }
    var isEditingQueueSize by remember { mutableStateOf(false) }
    var favoritesMaxSize by remember { mutableStateOf("100") }
    var newFavoritesMaxSize by remember { mutableStateOf("") }
    var isEditingFavoritesSize by remember { mutableStateOf(false) }
    var cachingEnabled by remember { mutableStateOf(true) }
    var categoryFilters by remember { mutableStateOf(CategoryFilters()) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    LaunchedEffect(editId, cacheRefreshTrigger) {
        if (isEditMode) {
            cacheStats = providerRepo.getCacheStatsForProvider(editId)
            val ps = providerRepo.getProviderSettings(editId)
            providerSettings = ps
            autoResumeEnabled = ps.autoResumeEnabled
            watchHistorySize = ps.watchHistorySize.toString()
            favoritesMaxSize = ps.favoritesMaxSize.toString()
            cachingEnabled = ps.cachingEnabled
            categoryFilters = ps.categoryFilters
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
                    } catch (e: Exception) {
                        android.util.Log.e("MobileAddProviderScreen", "Failed to parse SMB provider config", e)
                    }
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
            // Provider type dropdown
            var typeDropdownExpanded by remember { mutableStateOf(false) }
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
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    ProviderType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                selectedType = type
                                error = null
                                typeDropdownExpanded = false
                            }
                        )
                    }
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
                    ProviderType.REMOTE_M3U -> "e.g. My M3U Playlist"
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
                                parsed.streamOutputFormat?.let { streamOutputFormat = it }
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
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (passwordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
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
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (passwordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isEditMode) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        Text(
                            text = "— or —",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        OutlinedButton(
                            onClick = {
                                if (url.isBlank()) {
                                    error = "Enter the Jellyfin server URL first"
                                } else {
                                    qcCode = ""
                                    qcSecret = ""
                                    qcError = null
                                    showQuickConnectDialog = true
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
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
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (passwordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
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
                            url = it
                            error = null
                        },
                        label = { Text("M3U Playlist URL") },
                        placeholder = { Text("https://example.com/playlist.m3u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Provider Settings (edit mode only)
            if (isEditMode) {
                Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                    Text(
                        text = "Provider Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                    // Auto-Resume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Resume",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Resume VOD content from where you left off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        Spacer(modifier = Modifier.width(CinemaSpacing.md))
                        Switch(
                            checked = autoResumeEnabled,
                            onCheckedChange = { enabled ->
                                autoResumeEnabled = enabled
                                coroutineScope.launch {
                                    val newSettings = providerSettings.copy(autoResumeEnabled = enabled)
                                    providerRepo.updateProviderSettings(editId, newSettings)
                                    providerSettings = newSettings
                                    syncManager.syncProviderSettings(editId)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Watch History Size
                    Text(text = "Last Watched Queue Size", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Items to keep in Last Watched category (1-100)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))

                    if (!isEditingQueueSize) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = watchHistorySize, style = MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick = {
                                isEditingQueueSize = true
                                newWatchHistorySize = watchHistorySize
                            }) { Text("Edit") }
                        }
                    } else {
                        OutlinedTextField(
                            value = newWatchHistorySize,
                            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) newWatchHistorySize = it },
                            label = { Text("Queue Size") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs, Alignment.End)
                        ) {
                            OutlinedButton(onClick = { isEditingQueueSize = false; newWatchHistorySize = "" }) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val size = newWatchHistorySize.toIntOrNull()
                                    if (size != null && size in 1..100) {
                                        watchHistorySize = size.toString()
                                        isEditingQueueSize = false
                                        newWatchHistorySize = ""
                                        coroutineScope.launch {
                                            val newSettings = providerSettings.copy(watchHistorySize = size)
                                            providerRepo.updateProviderSettings(editId, newSettings)
                                            providerSettings = newSettings
                                            syncManager.syncProviderSettings(editId)
                                        }
                                    }
                                },
                                enabled = newWatchHistorySize.toIntOrNull()?.let { it in 1..100 } == true
                            ) { Text("Save") }
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Favorites Max Size
                    Text(text = "Favorites Max Size", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Maximum number of favorites to store (10-500)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))

                    if (!isEditingFavoritesSize) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = favoritesMaxSize, style = MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick = {
                                isEditingFavoritesSize = true
                                newFavoritesMaxSize = favoritesMaxSize
                            }) { Text("Edit") }
                        }
                    } else {
                        OutlinedTextField(
                            value = newFavoritesMaxSize,
                            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) newFavoritesMaxSize = it },
                            label = { Text("Max Size") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs, Alignment.End)
                        ) {
                            OutlinedButton(onClick = { isEditingFavoritesSize = false; newFavoritesMaxSize = "" }) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val size = newFavoritesMaxSize.toIntOrNull()
                                    if (size != null && size in 10..500) {
                                        favoritesMaxSize = size.toString()
                                        isEditingFavoritesSize = false
                                        newFavoritesMaxSize = ""
                                        coroutineScope.launch {
                                            val newSettings = providerSettings.copy(favoritesMaxSize = size)
                                            providerRepo.updateProviderSettings(editId, newSettings)
                                            providerSettings = newSettings
                                            syncManager.syncProviderSettings(editId)
                                        }
                                    }
                                },
                                enabled = newFavoritesMaxSize.toIntOrNull()?.let { it in 10..500 } == true
                            ) { Text("Save") }
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Clear Favorites
                    Text(text = "Clear All Favorites", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Remove all favorited streams from all content types",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Button(
                        onClick = { showClearFavoritesDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear All Favorites") }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Clear Progress
                    Text(text = "Clear Playback Progress", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Remove all saved positions (Continue Watching will be empty)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Button(
                        onClick = { showClearProgressDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear All Progress") }

                    // Category Filters (Xtream only)
                    if (selectedType == ProviderType.XTREAM) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))

                        Text(text = "Category Filters", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Hide categories by prefix (e.g., 'Adult', 'XXX')",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Mode: ${categoryFilters.mode.name}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (categoryFilters.prefixes.isEmpty()) "No filters configured"
                                           else "${categoryFilters.prefixes.size} prefix(es): ${categoryFilters.prefixes.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                )
                                Text(
                                    text = "Scripts: ${if (categoryFilters.allowedScripts.isEmpty()) "All" else categoryFilters.allowedScripts.joinToString(", ") { it.displayName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                )
                            }
                            OutlinedButton(onClick = { showCategoryFilterDialog = true }) { Text("Edit") }
                        }

                        Spacer(modifier = Modifier.height(CinemaSpacing.md))

                        // Enable Caching
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Enable Caching", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Enable caching for faster loading",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                )
                            }
                            Spacer(modifier = Modifier.width(CinemaSpacing.md))
                            Switch(
                                checked = cachingEnabled,
                                onCheckedChange = { enabled ->
                                    cachingEnabled = enabled
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(cachingEnabled = enabled)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        providerSettings = newSettings
                                        syncManager.syncProviderSettings(editId)
                                    }
                                }
                            )
                        }
                    }
                } // Column
                } // GlassPanel
            }

            // Cache Management (edit mode only)
            if (isEditMode) {
                Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
                Text(
                    text = "Manage local database and cached data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                cacheStats?.let { stats ->
                    // Sync Data Button (Xtream only)
                    if (selectedType == ProviderType.XTREAM) {
                        Button(
                            onClick = { viewModel.syncProvider(editId) },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (syncState is SyncState.Syncing) "Syncing..." else "Sync Data Now")
                        }

                        if (syncState is SyncState.Error) {
                            Text(
                                text = (syncState as SyncState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaError
                            )
                        }
                        if (syncState is SyncState.Success) {
                            Text(
                                text = "Sync completed successfully",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    }

                    // Total Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Database Items",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val totalItems = stats.liveTv.streamListsCount + stats.movies.streamListsCount + stats.tvShows.streamListsCount
                            Text(
                                text = "$totalItems Items",
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
                                text = "${stats.liveTv.streamListsCount} channels",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (stats.liveTv.categoryCached) "Categories cached" else "No categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearLiveTvCacheDialog = true },
                            enabled = stats.liveTv.streamListsCount > 0
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
                                text = "${stats.movies.streamListsCount} movies",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (stats.movies.categoryCached) "Categories cached" else "No categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearMoviesCacheDialog = true },
                            enabled = stats.movies.streamListsCount > 0
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
                                text = "${stats.tvShows.streamListsCount} series",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (stats.tvShows.categoryCached) "Categories cached" else "No categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(
                            onClick = { showClearTvShowsCacheDialog = true },
                            enabled = stats.tvShows.streamListsCount > 0
                        ) { Text("Clear") }
                    }
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
                        ProviderType.REMOTE_M3U -> when {
                            name.isBlank() -> "Provider name is required"
                            url.isBlank() -> "M3U Playlist URL is required"
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
                            onComplete = onSuccess,
                            initialSettings = ProviderSettings(streamOutputFormat = streamOutputFormat)
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
                                    onComplete = onSuccess,
                                    initialSettings = ProviderSettings(streamOutputFormat = streamOutputFormat)
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
                                coroutineScope.launch {
                                    providerRepo.clearAllCacheForProvider(editId)
                                    cacheRefreshTrigger++
                                    showClearCacheDialog = false
                                }
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
                                coroutineScope.launch {
                                    providerRepo.clearCacheForProviderContentType(editId, ContentType.LIVE_TV)
                                    cacheRefreshTrigger++
                                    showClearLiveTvCacheDialog = false
                                }
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
                                coroutineScope.launch {
                                    providerRepo.clearCacheForProviderContentType(editId, ContentType.MOVIES)
                                    cacheRefreshTrigger++
                                    showClearMoviesCacheDialog = false
                                }
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
                                coroutineScope.launch {
                                    providerRepo.clearCacheForProviderContentType(editId, ContentType.TV_SHOWS)
                                    cacheRefreshTrigger++
                                    showClearTvShowsCacheDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearTvShowsCacheDialog = false }) { Text("Cancel") }
                    }
                )
            }
            // Clear Favorites Confirmation Dialog
            if (showClearFavoritesDialog) {
                AlertDialog(
                    onDismissRequest = { showClearFavoritesDialog = false },
                    title = { Text("Clear All Favorites?") },
                    text = { Text("This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                repository.clearFavorites()
                                showClearFavoritesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearFavoritesDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // Clear Progress Confirmation Dialog
            if (showClearProgressDialog) {
                AlertDialog(
                    onDismissRequest = { showClearProgressDialog = false },
                    title = { Text("Clear All Playback Progress?") },
                    text = { Text("This will remove all saved playback positions. You will start from the beginning when playing any VOD content.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                repository.clearWatchHistory()
                                showClearProgressDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError)
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearProgressDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // Category Filter Dialog
            if (showCategoryFilterDialog) {
                var filterMode by remember { mutableStateOf(categoryFilters.mode) }
                var prefixesText by remember { mutableStateOf(categoryFilters.prefixes.joinToString(", ")) }
                var selectedScripts by remember { mutableStateOf(categoryFilters.allowedScripts) }

                AlertDialog(
                    onDismissRequest = { showCategoryFilterDialog = false },
                    title = { Text("Category Filters") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
                        ) {
                            Text(text = "Filter mode:", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                                FilterChip(
                                    selected = filterMode == FilterMode.EXCLUDE,
                                    onClick = { filterMode = FilterMode.EXCLUDE },
                                    label = { Text("Exclude") }
                                )
                                FilterChip(
                                    selected = filterMode == FilterMode.INCLUDE,
                                    onClick = { filterMode = FilterMode.INCLUDE },
                                    label = { Text("Include Only") }
                                )
                            }
                            Text(
                                text = if (filterMode == FilterMode.EXCLUDE) "Categories starting with these prefixes will be hidden"
                                       else "Only categories starting with these prefixes will be shown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                            OutlinedTextField(
                                value = prefixesText,
                                onValueChange = { prefixesText = it },
                                label = { Text("Prefixes (comma-separated)") },
                                placeholder = { Text("Adult, XXX, 18+") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                minLines = 2
                            )
                            Text(text = "Language Script Filter:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Show only categories in selected scripts (none = show all)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                            ScriptType.entries.forEach { script ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = script in selectedScripts,
                                        onCheckedChange = { checked ->
                                            selectedScripts = if (checked) selectedScripts + script else selectedScripts - script
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(CinemaSpacing.xxs))
                                    Text(text = script.displayName, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val prefixes = prefixesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val newFilters = CategoryFilters(mode = filterMode, prefixes = prefixes, allowedScripts = selectedScripts)
                                categoryFilters = newFilters
                                coroutineScope.launch {
                                    val newSettings = providerSettings.copy(categoryFilters = newFilters)
                                    providerRepo.updateProviderSettings(editId, newSettings)
                                    providerSettings = newSettings
                                    syncManager.syncProviderSettings(editId)
                                }
                                showCategoryFilterDialog = false
                            }
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showCategoryFilterDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // Quick Connect dialog (Jellyfin)
            if (showQuickConnectDialog) {
                LaunchedEffect(Unit) {
                    qcCode = ""
                    qcSecret = ""
                    qcError = null
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "fijerena"
                    val api = JellyfinApiService(url.trimEnd('/'), deviceId)
                    val initResult = api.initiateQuickConnect()
                    if (initResult.isFailure) {
                        qcError = initResult.exceptionOrNull()?.message ?: "Failed to start Quick Connect"
                        return@LaunchedEffect
                    }
                    val init = initResult.getOrThrow()
                    qcCode = init.code
                    qcSecret = init.secret
                    // Poll every 3 s for up to 2 minutes
                    repeat(40) {
                        delay(3_000)
                        val poll = api.pollQuickConnect(qcSecret)
                        if (poll.isFailure) {
                            qcError = "Polling failed: ${poll.exceptionOrNull()?.message}"
                            return@LaunchedEffect
                        }
                        if (poll.getOrThrow().authenticated) {
                            val authResult = api.authenticateWithQuickConnect(qcSecret)
                            if (authResult.isFailure) {
                                qcError = "Authentication failed: ${authResult.exceptionOrNull()?.message}"
                                return@LaunchedEffect
                            }
                            val auth = authResult.getOrThrow()
                            showQuickConnectDialog = false
                            viewModel.quickConnectSave(
                                name = name.ifBlank { auth.user.name },
                                url = url.trimEnd('/'),
                                username = username.ifBlank { auth.user.name },
                                token = auth.accessToken,
                                userId = auth.user.id,
                                onComplete = onSuccess
                            )
                            return@LaunchedEffect
                        }
                    }
                    qcError = "Timed out waiting for approval. Please try again."
                }

                AlertDialog(
                    onDismissRequest = { showQuickConnectDialog = false },
                    title = { Text("Quick Connect") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when {
                                qcError != null -> {
                                    Text(
                                        text = qcError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                qcCode.isEmpty() -> {
                                    androidx.compose.material3.CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                    Text(
                                        text = "Connecting to server...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Enter this code in Jellyfin:",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                    Text(
                                        text = qcCode,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.displayMedium
                                    )
                                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                                    Text(
                                        text = "Open Jellyfin → Dashboard → Quick Connect, then enter the code above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                                    androidx.compose.material3.CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                                    Text(
                                        text = "Waiting for approval...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        OutlinedButton(onClick = { showQuickConnectDialog = false }) {
                            Text("Cancel")
                        }
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
