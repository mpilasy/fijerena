package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.MatchType
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.provider.withAddedRules
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
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
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

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
    // A background data sync must never block saving or leaving this screen — it runs
    // independently of the form (ProviderSyncManager's own scope), so it isn't disturbed by
    // either.
    val isBusy = saveState is SaveState.Validating || saveState is SaveState.Saving

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

    // A sync started here outlives this screen, so pick it back up when we come back to it
    LaunchedEffect(editId) {
        if (isEditMode) viewModel.observeRunningSync(editId)
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
                    Text(if (isEditMode) stringResource(R.string.provider_edit_title) else stringResource(R.string.provider_add_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
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
                    label = { Text(stringResource(R.string.provider_type_label)) },
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
                label = { Text(stringResource(R.string.provider_name_label)) },
                placeholder = {
                    Text(
                        when (selectedType) {
                            ProviderType.XTREAM -> stringResource(R.string.provider_name_placeholder_xtream)
                            ProviderType.JELLYFIN -> stringResource(R.string.provider_name_placeholder_jellyfin)
                            ProviderType.SMB -> stringResource(R.string.provider_name_placeholder_smb)
                            ProviderType.LOCAL -> stringResource(R.string.provider_name_placeholder_local)
                            ProviderType.REMOTE_M3U -> stringResource(R.string.provider_name_placeholder_remote_m3u)
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

            CinemaButton(
                onClick = {
                    // Validation based on selected type
                    val validationError =
                        when (selectedType) {
                            ProviderType.XTREAM ->
                                when {
                                    name.isBlank() -> context.getString(R.string.provider_error_name_required)
                                    url.isBlank() -> context.getString(R.string.provider_error_url_required)
                                    username.isBlank() -> context.getString(R.string.provider_error_username_required)
                                    password.isBlank() -> context.getString(R.string.provider_error_password_required)
                                    else -> null
                                }
                            ProviderType.JELLYFIN ->
                                when {
                                    name.isBlank() -> context.getString(R.string.provider_error_name_required)
                                    url.isBlank() -> context.getString(R.string.provider_error_url_required)
                                    username.isBlank() -> context.getString(R.string.provider_error_username_required)
                                    password.isBlank() -> context.getString(R.string.provider_error_password_required)
                                    else -> null
                                }
                            ProviderType.SMB ->
                                when {
                                    name.isBlank() -> context.getString(R.string.provider_error_name_required)
                                    host.isBlank() -> context.getString(R.string.provider_error_host_required)
                                    shareName.isBlank() -> context.getString(R.string.provider_error_share_required)
                                    else -> null
                                }
                            ProviderType.LOCAL ->
                                when {
                                    name.isBlank() -> context.getString(R.string.provider_error_name_required)
                                    else -> null
                                }
                            ProviderType.REMOTE_M3U ->
                                when {
                                    name.isBlank() -> context.getString(R.string.provider_error_name_required)
                                    url.isBlank() -> context.getString(R.string.provider_error_m3u_url_required)
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
                        is SaveState.Validating -> stringResource(R.string.provider_connecting)
                        is SaveState.Saving -> stringResource(R.string.provider_saving)
                        else -> if (isEditMode) stringResource(R.string.provider_update_button) else stringResource(R.string.provider_add_title)
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

                CinemaAlertDialog(
                    onDismissRequest = { viewModel.resetSaveState() },
                    title = { Text(stringResource(R.string.provider_connection_failed)) },
                    text = { Text(failedState.errorMessage) },
                    confirmButton = {
                        CinemaDialogActionButton(
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
                            Text(stringResource(R.string.provider_save_anyway))
                        }
                    },
                    dismissButton = {
                        CinemaDialogTextButton(
                            onClick = { viewModel.resetSaveState() },
                        ) {
                            Text(stringResource(R.string.provider_go_back))
                        }
                    },
                )
            }

            // Cache confirmation dialogs
            if (showClearCacheDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showClearCacheDialog = false },
                    title = { Text(stringResource(R.string.provider_clear_cache_all_title)) },
                    text = {
                        Text(stringResource(R.string.provider_clear_cache_all_message))
                    },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    providerRepo.clearAllCacheForProvider(editId)
                                    cacheRefreshTrigger++
                                    showClearCacheDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.provider_clear_all_button)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }

            if (showClearLiveTvCacheDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showClearLiveTvCacheDialog = false },
                    title = { Text(stringResource(R.string.provider_clear_cache_live_title)) },
                    text = { Text(stringResource(R.string.provider_clear_cache_live_message)) },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    providerRepo.clearCacheForProviderContentType(editId, ContentType.LIVE_TV)
                                    cacheRefreshTrigger++
                                    showClearLiveTvCacheDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.provider_clear_button)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showClearLiveTvCacheDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }

            if (showClearMoviesCacheDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showClearMoviesCacheDialog = false },
                    title = { Text(stringResource(R.string.provider_clear_cache_movies_title)) },
                    text = { Text(stringResource(R.string.provider_clear_cache_movies_message)) },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    providerRepo.clearCacheForProviderContentType(editId, ContentType.MOVIES)
                                    cacheRefreshTrigger++
                                    showClearMoviesCacheDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.provider_clear_button)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showClearMoviesCacheDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }

            if (showClearTvShowsCacheDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showClearTvShowsCacheDialog = false },
                    title = { Text(stringResource(R.string.provider_clear_cache_tvshows_title)) },
                    text = { Text(stringResource(R.string.provider_clear_cache_tvshows_message)) },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    providerRepo.clearCacheForProviderContentType(editId, ContentType.TV_SHOWS)
                                    cacheRefreshTrigger++
                                    showClearTvShowsCacheDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.provider_clear_button)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showClearTvShowsCacheDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }
            // Clear Favorites Confirmation Dialog
            if (showClearFavoritesDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showClearFavoritesDialog = false },
                    title = { Text(stringResource(R.string.provider_clear_favorites_title)) },
                    text = {
                        Text(stringResource(R.string.provider_clear_favorites_message))
                    },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                repository.clearFavorites()
                                showClearFavoritesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showClearFavoritesDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }

            // Clear Progress Confirmation Dialog
            if (showClearProgressDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showClearProgressDialog = false },
                    title = { Text(stringResource(R.string.provider_clear_progress_title)) },
                    text = {
                        Text(stringResource(R.string.provider_clear_progress_message))
                    },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                repository.clearWatchHistory()
                                showClearProgressDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showClearProgressDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }

            // Category Filter Dialog
            if (showCategoryFilterDialog) {
                var filterMode by remember { mutableStateOf(categoryFilters.mode) }
                var rules by remember { mutableStateOf(categoryFilters.rules) }
                var editingIndex by remember { mutableStateOf<Int?>(null) }
                var editingValue by remember { mutableStateOf("") }
                var editingMatchType by remember { mutableStateOf(MatchType.STARTS_WITH) }
                var addRulesText by remember { mutableStateOf("") }
                var pendingAddValues by remember { mutableStateOf<List<String>?>(null) }
                var pendingAddMatchType by remember { mutableStateOf(MatchType.STARTS_WITH) }
                var selectedScripts by remember { mutableStateOf(categoryFilters.allowedScripts) }

                @Composable
                fun matchTypeLabel(type: MatchType): String =
                    when (type) {
                        MatchType.STARTS_WITH -> stringResource(R.string.provider_filter_match_starts)
                        MatchType.ENDS_WITH -> stringResource(R.string.provider_filter_match_ends)
                        MatchType.CONTAINS -> stringResource(R.string.provider_filter_match_contains)
                        MatchType.EXACT -> stringResource(R.string.provider_filter_match_exact)
                    }

                CinemaAlertDialog(
                    onDismissRequest = { showCategoryFilterDialog = false },
                    title = { Text(stringResource(R.string.provider_category_filters_title)) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                        ) {
                            Text(text = stringResource(R.string.provider_filter_mode_label), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                                CinemaFilterChip(
                                    selected = filterMode == FilterMode.EXCLUDE,
                                    onClick = { filterMode = FilterMode.EXCLUDE },
                                    label = { Text(stringResource(R.string.provider_filter_exclude)) },
                                )
                                CinemaFilterChip(
                                    selected = filterMode == FilterMode.INCLUDE,
                                    onClick = { filterMode = FilterMode.INCLUDE },
                                    label = { Text(stringResource(R.string.provider_filter_include)) },
                                )
                            }
                            Text(
                                text =
                                    if (filterMode == FilterMode.EXCLUDE) {
                                        stringResource(R.string.provider_filter_exclude_desc)
                                    } else {
                                        stringResource(R.string.provider_filter_include_desc)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                            )
                            if (filterMode == FilterMode.INCLUDE && rules.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.provider_filter_include_empty_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            // Add section first — the thing you open this dialog to do most often.
                            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                OutlinedTextField(
                                    value = addRulesText,
                                    onValueChange = { addRulesText = it },
                                    label = { Text(stringResource(R.string.provider_filter_add_rules_label)) },
                                    placeholder = { Text(stringResource(R.string.provider_filter_prefixes_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    minLines = 2,
                                )
                                CinemaOutlinedButton(
                                    onClick = {
                                        val values = addRulesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (values.isNotEmpty()) {
                                            pendingAddValues = values
                                            pendingAddMatchType = MatchType.STARTS_WITH
                                        }
                                    },
                                    enabled = addRulesText.isNotBlank(),
                                ) { Text(stringResource(R.string.common_add)) }

                                pendingAddValues?.let { values ->
                                    Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                        Text(
                                            text = stringResource(R.string.provider_filter_choose_match_type),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        // Manual 2-per-row wrap, not FlowRow — see MatchTypeChipRow note in
                                        // tv/ProviderDialogs.kt for why FlowRow is avoided here right now.
                                        Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                            MatchType.entries.chunked(2).forEach { rowTypes ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                                    rowTypes.forEach { type ->
                                                        CinemaFilterChip(
                                                            selected = pendingAddMatchType == type,
                                                            onClick = { pendingAddMatchType = type },
                                                            label = { Text(matchTypeLabel(type)) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                            CinemaButton(onClick = {
                                                rules = rules.withAddedRules(values, pendingAddMatchType)
                                                addRulesText = ""
                                                pendingAddValues = null
                                            }) { Text(stringResource(R.string.common_ok)) }
                                            CinemaOutlinedButton(onClick = { pendingAddValues = null }) {
                                                Text(stringResource(R.string.common_cancel))
                                            }
                                        }
                                    }
                                }
                            }

                            if (rules.isNotEmpty()) {
                                HorizontalDivider()
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs)) {
                                rules.forEachIndexed { index, rule ->
                                    if (editingIndex == index) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(CinemaSpacing.sm),
                                                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.provider_filter_edit_rule),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                                OutlinedTextField(
                                                    value = editingValue,
                                                    onValueChange = { editingValue = it },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                )
                                                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                                    MatchType.entries.chunked(2).forEach { rowTypes ->
                                                        Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                                            rowTypes.forEach { type ->
                                                                CinemaFilterChip(
                                                                    selected = editingMatchType == type,
                                                                    onClick = { editingMatchType = type },
                                                                    label = { Text(matchTypeLabel(type)) },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                                                    CinemaButton(onClick = {
                                                        val trimmed = editingValue.trim()
                                                        if (trimmed.isNotEmpty()) {
                                                            rules =
                                                                rules.toMutableList().also {
                                                                    it[index] = CategoryMatcher(trimmed, editingMatchType)
                                                                }
                                                        }
                                                        editingIndex = null
                                                    }) { Text(stringResource(R.string.common_ok)) }
                                                    CinemaOutlinedButton(onClick = { editingIndex = null }) {
                                                        Text(stringResource(R.string.common_cancel))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = CinemaSpacing.xl + CinemaSpacing.xxs),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = matchTypeLabel(rule.matchType),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                                modifier = Modifier.width(MobileDimensions.buttonHeight),
                                            )
                                            Text(
                                                text = rule.value,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = {
                                                    editingIndex = index
                                                    editingValue = rule.value
                                                    editingMatchType = rule.matchType
                                                },
                                                modifier = Modifier.size(MobileDimensions.iconLarge),
                                            ) {
                                                Icon(
                                                    CinemaIcons.Edit,
                                                    contentDescription = stringResource(R.string.provider_filter_edit_rule),
                                                    modifier = Modifier.size(MobileDimensions.iconSmall - Spacing.xxxs),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(CinemaSpacing.xxs))
                                            IconButton(
                                                onClick = {
                                                    rules = rules.toMutableList().also { it.removeAt(index) }
                                                },
                                                modifier = Modifier.size(MobileDimensions.iconLarge),
                                            ) {
                                                Icon(
                                                    CinemaIcons.Delete,
                                                    contentDescription = stringResource(R.string.provider_filter_delete_rule),
                                                    modifier = Modifier.size(MobileDimensions.iconSmall - Spacing.xxxs),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(text = stringResource(R.string.provider_filter_script_title), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(R.string.provider_filter_script_desc),
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
                        CinemaDialogActionButton(
                            onClick = {
                                val newFilters = CategoryFilters(mode = filterMode, rules = rules, allowedScripts = selectedScripts)
                                categoryFilters = newFilters
                                coroutineScope.launch {
                                    val newSettings = providerSettings.copy(categoryFilters = newFilters)
                                    providerRepo.updateProviderSettings(editId, newSettings)
                                    providerSettings = newSettings
                                    syncManager.syncProviderSettings(editId)
                                }
                                showCategoryFilterDialog = false
                            },
                        ) { Text(stringResource(R.string.provider_save_button)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(onClick = { showCategoryFilterDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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
