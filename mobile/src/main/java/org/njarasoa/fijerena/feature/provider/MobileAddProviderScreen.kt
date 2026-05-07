package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
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
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.feature.provider.components.ProviderFormSection
import org.njarasoa.fijerena.feature.provider.components.ProviderSettingsSection
import org.njarasoa.fijerena.feature.provider.components.DataManagementSection
import org.njarasoa.fijerena.feature.provider.components.QuickConnectDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel =
        viewModel(
            factory = ProviderViewModelFactory(context),
        )
    val isEditMode = editId > 0L

    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var streamOutputFormat by remember { mutableStateOf("m3u8") }
    var playlistType by remember { mutableStateOf("m3u_plus") }
    var passwordVisible by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
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
    var currentProvider by remember { mutableStateOf<org.njarasoa.fijerena.core.network.provider.ProviderEntity?>(null) }
    
    // Update currentProvider when providers list changes
    LaunchedEffect(providers, editId) {
        if (isEditMode) {
            currentProvider = providers.find { it.id == editId }
        }
    }

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

    val repository =
        remember {
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
            streamOutputFormat = ps.streamOutputFormat
            playlistType = ps.playlistType
        }
    }

    // Refresh UI data when sync completes
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success || syncState is SyncState.Error) {
            cacheRefreshTrigger++
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
                selectedType =
                    try {
                        ProviderType.valueOf(provider.type)
                    } catch (_: Exception) {
                        ProviderType.XTREAM
                    }
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(CinemaSpacing.md)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Provider type dropdown
            var typeDropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider Type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false },
                ) {
                    ProviderType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                selectedType = type
                                error = null
                                typeDropdownExpanded = false
                            },
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
                placeholder = {
                    Text(
                        when (selectedType) {
                            ProviderType.XTREAM -> "e.g. My IPTV"
                            ProviderType.JELLYFIN -> "e.g. My Jellyfin Server"
                            ProviderType.SMB -> "e.g. NAS Media"
                            ProviderType.LOCAL -> "e.g. Local Videos"
                            ProviderType.REMOTE_M3U -> "e.g. My M3U Playlist"
                        },
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ProviderFormSection(
                selectedType = selectedType,
                url = url,
                onUrlChange = { url = it },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                passwordVisible = passwordVisible,
                onPasswordVisibleChange = { passwordVisible = it },
                host = host,
                onHostChange = { host = it },
                shareName = shareName,
                onShareNameChange = { shareName = it },
                isEditMode = isEditMode,
                isBusy = isBusy,
                onErrorChange = { error = it },
                onShowQuickConnectDialogChange = { showQuickConnectDialog = it },
                onStreamOutputFormatChange = { streamOutputFormat = it },
                onPlaylistTypeChange = { playlistType = it },
                onQcCodeChange = { qcCode = it },
                onQcSecretChange = { qcSecret = it },
                onQcErrorChange = { qcError = it }
            )
            ProviderSettingsSection(
                isEditMode = isEditMode,
                editId = editId,
                selectedType = selectedType,
                providerSettings = providerSettings,
                autoResumeEnabled = autoResumeEnabled,
                watchHistorySize = watchHistorySize,
                newWatchHistorySize = newWatchHistorySize,
                isEditingQueueSize = isEditingQueueSize,
                favoritesMaxSize = favoritesMaxSize,
                newFavoritesMaxSize = newFavoritesMaxSize,
                isEditingFavoritesSize = isEditingFavoritesSize,
                cachingEnabled = cachingEnabled,
                categoryFilters = categoryFilters,
                streamOutputFormat = streamOutputFormat,
                playlistType = playlistType,
                coroutineScope = coroutineScope,
                providerRepo = providerRepo,
                syncManager = syncManager,
                onProviderSettingsChange = { providerSettings = it },
                onAutoResumeEnabledChange = { autoResumeEnabled = it },
                onWatchHistorySizeChange = { watchHistorySize = it },
                onNewWatchHistorySizeChange = { newWatchHistorySize = it },
                onIsEditingQueueSizeChange = { isEditingQueueSize = it },
                onFavoritesMaxSizeChange = { favoritesMaxSize = it },
                onNewFavoritesMaxSizeChange = { newFavoritesMaxSize = it },
                onIsEditingFavoritesSizeChange = { isEditingFavoritesSize = it },
                onCachingEnabledChange = { cachingEnabled = it },
                onStreamOutputFormatChange = { streamOutputFormat = it },
                onPlaylistTypeChange = { playlistType = it },
                onShowClearFavoritesDialogChange = { showClearFavoritesDialog = it },
                onShowClearProgressDialogChange = { showClearProgressDialog = it },
                onShowCategoryFilterDialogChange = { showCategoryFilterDialog = it }
            )
            DataManagementSection(
                isEditMode = isEditMode,
                editId = editId,
                cacheStats = cacheStats,
                selectedType = selectedType,
                viewModel = viewModel,
                isBusy = isBusy,
                syncState = syncState,
                currentProvider = currentProvider,
                onShowClearCacheDialogChange = { showClearCacheDialog = it },
                onShowClearLiveTvCacheDialogChange = { showClearLiveTvCacheDialog = it },
                onShowClearMoviesCacheDialogChange = { showClearMoviesCacheDialog = it },
                onShowClearTvShowsCacheDialogChange = { showClearTvShowsCacheDialog = it }
            )

            error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaError,
                )
            }

            Spacer(modifier = Modifier.height(CinemaSpacing.lg))

            Button(
                onClick = {
                    // Validation based on selected type
                    val validationError =
                        when (selectedType) {
                            ProviderType.XTREAM ->
                                when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "Server URL is required"
                                    username.isBlank() -> "Username is required"
                                    password.isBlank() -> "Password is required"
                                    else -> null
                                }
                            ProviderType.JELLYFIN ->
                                when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "Server URL is required"
                                    username.isBlank() -> "Username is required"
                                    password.isBlank() -> "Password is required"
                                    else -> null
                                }
                            ProviderType.SMB ->
                                when {
                                    name.isBlank() -> "Provider name is required"
                                    host.isBlank() -> "Host / IP is required"
                                    shareName.isBlank() -> "Share name is required"
                                    else -> null
                                }
                            ProviderType.LOCAL ->
                                when {
                                    name.isBlank() -> "Provider name is required"
                                    else -> null
                                }
                            ProviderType.REMOTE_M3U ->
                                when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "M3U Playlist URL is required"
                                    else -> null
                                }
                        }

                    if (validationError != null) {
                        error = validationError
                    } else {
                        val saveUrl =
                            when (selectedType) {
                                ProviderType.SMB -> "smb://${host.trim()}/${shareName.trim()}"
                                else -> url.trim()
                            }
                        val saveConfig =
                            when (selectedType) {
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
                            initialSettings = ProviderSettings(streamOutputFormat = streamOutputFormat, playlistType = playlistType),
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (saveState) {
                        is SaveState.Validating -> "Connecting..."
                        is SaveState.Saving -> "Saving..."
                        else -> if (isEditMode) "Update Provider" else "Add Provider"
                    },
                )
            }

            // Validation failure dialog
            val failedState = saveState as? SaveState.ValidationFailed
            if (failedState != null) {
                val saveUrl =
                    when (selectedType) {
                        ProviderType.SMB -> "smb://${host.trim()}/${shareName.trim()}"
                        else -> url.trim()
                    }
                val saveConfig =
                    when (selectedType) {
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
                                    initialSettings =
                                        ProviderSettings(
                                            streamOutputFormat = streamOutputFormat,
                                            playlistType = playlistType,
                                        ),
                                )
                            },
                        ) {
                            Text("Save Anyway")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.resetSaveState() },
                        ) {
                            Text("Go Back")
                        }
                    },
                )
            }

            // Cache confirmation dialogs
            if (showClearCacheDialog) {
                AlertDialog(
                    onDismissRequest = { showClearCacheDialog = false },
                    title = { Text("Clear All Cache?") },
                    text = {
                        Text(
                            "This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server.",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    providerRepo.clearAllCacheForProvider(editId)
                                    cacheRefreshTrigger++
                                    showClearCacheDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text("Clear All") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
                    },
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
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearLiveTvCacheDialog = false }) { Text("Cancel") }
                    },
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
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearMoviesCacheDialog = false }) { Text("Cancel") }
                    },
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
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text("Clear") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearTvShowsCacheDialog = false }) { Text("Cancel") }
                    },
                )
            }
            // Clear Favorites Confirmation Dialog
            if (showClearFavoritesDialog) {
                AlertDialog(
                    onDismissRequest = { showClearFavoritesDialog = false },
                    title = { Text("Clear All Favorites?") },
                    text = {
                        Text(
                            "This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                repository.clearFavorites()
                                showClearFavoritesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearFavoritesDialog = false }) { Text("Cancel") }
                    },
                )
            }

            // Clear Progress Confirmation Dialog
            if (showClearProgressDialog) {
                AlertDialog(
                    onDismissRequest = { showClearProgressDialog = false },
                    title = { Text("Clear All Playback Progress?") },
                    text = {
                        Text(
                            "This will remove all saved playback positions. You will start from the beginning when playing any VOD content.",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                repository.clearWatchHistory()
                                showClearProgressDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showClearProgressDialog = false }) { Text("Cancel") }
                    },
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
                            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                        ) {
                            Text(text = "Filter mode:", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                                FilterChip(
                                    selected = filterMode == FilterMode.EXCLUDE,
                                    onClick = { filterMode = FilterMode.EXCLUDE },
                                    label = { Text("Exclude") },
                                )
                                FilterChip(
                                    selected = filterMode == FilterMode.INCLUDE,
                                    onClick = { filterMode = FilterMode.INCLUDE },
                                    label = { Text("Include Only") },
                                )
                            }
                            Text(
                                text =
                                    if (filterMode == FilterMode.EXCLUDE) {
                                        "Categories starting with these prefixes will be hidden"
                                    } else {
                                        "Only categories starting with these prefixes will be shown"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                            )
                            OutlinedTextField(
                                value = prefixesText,
                                onValueChange = { prefixesText = it },
                                label = { Text("Prefixes (comma-separated)") },
                                placeholder = { Text("Adult, XXX, 18+") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                minLines = 2,
                            )
                            Text(text = "Language Script Filter:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Show only categories in selected scripts (none = show all)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                            )
                            ScriptType.entries.forEach { script ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = script in selectedScripts,
                                        onCheckedChange = { checked ->
                                            selectedScripts = if (checked) selectedScripts + script else selectedScripts - script
                                        },
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
                            },
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showCategoryFilterDialog = false }) { Text("Cancel") }
                    },
                )
            }

            QuickConnectDialog(
                showQuickConnectDialog = showQuickConnectDialog,
                qcCode = qcCode,
                qcSecret = qcSecret,
                qcError = qcError,
                url = url,
                name = name,
                username = username,
                context = context,
                viewModel = viewModel,
                onQcCodeChange = { qcCode = it },
                onQcSecretChange = { qcSecret = it },
                onQcErrorChange = { qcError = it },
                onShowQuickConnectDialogChange = { showQuickConnectDialog = it },
                onSuccess = onSuccess
            )
        }
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
