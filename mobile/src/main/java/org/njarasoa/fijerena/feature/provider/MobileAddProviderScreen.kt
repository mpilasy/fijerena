package org.njarasoa.fijerena.feature.provider

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.smb.smbSourceConfig
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.model.addSourceTypes
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.feature.provider.components.DataManagementSection
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerZoneSection
import org.njarasoa.fijerena.feature.provider.components.ProviderFormSection
import org.njarasoa.fijerena.feature.provider.components.ProviderGuideSection
import org.njarasoa.fijerena.feature.provider.components.ProviderLoginsSection
import org.njarasoa.fijerena.feature.provider.components.ProviderSectionTitle
import org.njarasoa.fijerena.feature.provider.components.ProviderSettingsSection
import org.njarasoa.fijerena.feature.provider.components.QuickConnectDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onGuideSources: (providerId: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: ProviderViewModel =
        viewModel(
            factory = ProviderViewModelFactory(context),
        )
    val isEditMode = editId > 0L

    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
    // The type of the source being edited: kept on the type list even when it's dev-mode only (R-22).
    var editedType by remember { mutableStateOf<ProviderType?>(null) }
    val isDevMode = remember { AppSettings(context.applicationContext).isDevMode }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var streamOutputFormat by remember { mutableStateOf("m3u8") }
    var playlistType by remember { mutableStateOf("m3u_plus") }
    var passwordVisible by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    // The connection as loaded (edit mode): Back with anything different asks before dropping it.
    // Behaviour settings save as they change, so they never count as unsaved.
    var loadedConnection by remember { mutableStateOf(listOf("", "", "", "", "", "")) }
    val hasUnsavedConnectionEdits = isEditMode && listOf(name, url, username, password, host, shareName) != loadedConnection
    var showDiscardDialog by remember { mutableStateOf(false) }
    // Leaving for the guide sources with unsaved connection edits asks first, as Back does.
    var discardThen by remember { mutableStateOf<(() -> Unit)?>(null) }
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var currentProvider by remember { mutableStateOf<org.njarasoa.fijerena.core.network.provider.ProviderEntity?>(null) }

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
    var cachingEnabled by remember { mutableStateOf(true) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }

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
            cachingEnabled = ps.cachingEnabled
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
                // This profile's login: its own for Jellyfin, the shared one otherwise.
                val login = withContext(Dispatchers.IO) { providerRepo.getLogin(provider) }
                username = login.username
                password = login.password
                selectedType =
                    try {
                        ProviderType.valueOf(provider.type)
                    } catch (_: Exception) {
                        // cancellation-ok: no suspension point in the try
                        ProviderType.XTREAM
                    }
                editedType = selectedType
                if (provider.type == "SMB" && provider.config.isNotBlank()) {
                    try {
                        val json = org.json.JSONObject(provider.config)
                        host = json.optString("host", "")
                        shareName = json.optString("share", "")
                    } catch (e: Exception) {
                        // cancellation-ok: no suspension point in the try
                        android.util.Log.e("MobileAddProviderScreen", "Failed to parse SMB provider config", e)
                    }
                }
                loadedConnection = listOf(name, url, username, password, host, shareName)
            }
        }
    }

    // Make main / Remove in Logins changed the main login: reload the fields so Save doesn't put
    // the old one back. Other unsaved edits (name, URL) stay.
    val reloadMainLogin: () -> Unit = {
        coroutineScope.launch {
            providerRepo.getProviderById(editId)?.let { provider ->
                val login = withContext(Dispatchers.IO) { providerRepo.getLogin(provider) }
                username = login.username
                password = login.password
                loadedConnection =
                    loadedConnection.toMutableList().also {
                        it[2] = login.username
                        it[3] = login.password
                    }
            }
        }
    }

    val requestBack: () -> Unit = {
        if (hasUnsavedConnectionEdits) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = hasUnsavedConnectionEdits) { showDiscardDialog = true }

    // Validate the connection fields for the selected type, then save through the ViewModel.
    val submitConnection: () -> Unit = {
        val validationError =
            when (selectedType) {
                ProviderType.XTREAM -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        url.isBlank() -> resources.getString(R.string.provider_error_url_required)
                        username.isBlank() -> resources.getString(R.string.provider_error_username_required)
                        password.isBlank() -> resources.getString(R.string.provider_error_password_required)
                        else -> null
                    }
                }

                ProviderType.JELLYFIN -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        url.isBlank() -> resources.getString(R.string.provider_error_url_required)
                        username.isBlank() -> resources.getString(R.string.provider_error_username_required)
                        password.isBlank() -> resources.getString(R.string.provider_error_password_required)
                        else -> null
                    }
                }

                ProviderType.SMB -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        host.isBlank() -> resources.getString(R.string.provider_error_host_required)
                        shareName.isBlank() -> resources.getString(R.string.provider_error_share_required)
                        else -> null
                    }
                }

                ProviderType.LOCAL -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        else -> null
                    }
                }

                ProviderType.REMOTE_M3U -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        url.isBlank() -> resources.getString(R.string.provider_error_m3u_url_required)
                        else -> null
                    }
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
                    ProviderType.SMB -> smbSourceConfig(host.trim(), shareName.trim())
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
    }
    val submitLabel =
        when (saveState) {
            is SaveState.Validating -> {
                stringResource(R.string.provider_connecting)
            }

            is SaveState.Saving -> {
                stringResource(R.string.provider_saving)
            }

            else -> {
                if (isEditMode) {
                    stringResource(R.string.provider_save_connection_button)
                } else {
                    stringResource(R.string.provider_add_title)
                }
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditMode) stringResource(R.string.provider_edit_title) else stringResource(R.string.provider_add_title))
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
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
            // Provider type: a dropdown when adding, a disabled field when editing (the type of
            // an existing source cannot change).
            if (isEditMode) {
                ProviderSectionTitle(title = stringResource(R.string.provider_section_connection))
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.provider_type_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
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
                        addSourceTypes(isDevMode, editedType).forEach { type ->
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
                onQcErrorChange = { qcError = it },
            )

            if (isEditMode) {
                // Save sits right under the login it saves; the rest of the screen applies on its own.
                FormErrorText(error)
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                ) {
                    CinemaOutlinedButton(
                        onClick = onBack,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_cancel)) }
                    CinemaButton(
                        onClick = submitConnection,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) { Text(submitLabel) }
                }
            }

            if (isEditMode && editedType == ProviderType.XTREAM) {
                ProviderLoginsSection(
                    providerId = editId,
                    snackbarHostState = snackbarHostState,
                    onMainLoginChanged = reloadMainLogin,
                )
            }

            ProviderSettingsSection(
                isEditMode = isEditMode,
                editId = editId,
                selectedType = selectedType,
                providerSettings = providerSettings,
                autoResumeEnabled = autoResumeEnabled,
                watchHistorySize = watchHistorySize,
                cachingEnabled = cachingEnabled,
                streamOutputFormat = streamOutputFormat,
                playlistType = playlistType,
                coroutineScope = coroutineScope,
                providerRepo = providerRepo,
                onProviderSettingsChange = { providerSettings = it },
                onAutoResumeEnabledChange = { autoResumeEnabled = it },
                onWatchHistorySizeChange = { watchHistorySize = it },
                onCachingEnabledChange = { cachingEnabled = it },
                onStreamOutputFormatChange = { streamOutputFormat = it },
                onPlaylistTypeChange = { playlistType = it },
            )
            if (isEditMode && currentProvider?.let { MediaProviderFactory.hasLiveTv(it) } == true) {
                ProviderGuideSection(
                    providerId = editId,
                    showProvidesGuide = selectedType == ProviderType.XTREAM,
                    providerSettings = providerSettings,
                    onProvidesGuideChange = { enabled ->
                        providerSettings = providerSettings.copy(providesGuide = enabled, providesGuideSetByUser = true)
                        viewModel.setProvidesGuide(editId, enabled)
                    },
                    onGuideSourcesClick = {
                        val open = { onGuideSources(editId) }
                        if (hasUnsavedConnectionEdits) discardThen = open else open()
                    },
                )
            }
            DataManagementSection(
                isEditMode = isEditMode,
                editId = editId,
                cacheStats = cacheStats,
                selectedType = selectedType,
                viewModel = viewModel,
                isBusy = isBusy,
                syncState = syncState,
                currentProvider = currentProvider,
            )
            ProviderDangerZoneSection(
                isEditMode = isEditMode,
                cacheStats = cacheStats,
                onClearFavorites = { showClearFavoritesDialog = true },
                onClearProgress = { showClearProgressDialog = true },
                onClearAllCache = { showClearCacheDialog = true },
                onClearLiveTvCache = { showClearLiveTvCacheDialog = true },
                onClearMoviesCache = { showClearMoviesCacheDialog = true },
                onClearTvShowsCache = { showClearTvShowsCacheDialog = true },
            )

            if (!isEditMode) {
                FormErrorText(error)

                Spacer(modifier = Modifier.height(CinemaSpacing.lg))

                CinemaButton(
                    onClick = submitConnection,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(submitLabel) }
            }

            if (showDiscardDialog || discardThen != null) {
                CinemaAlertDialog(
                    onDismissRequest = {
                        showDiscardDialog = false
                        discardThen = null
                    },
                    title = { Text(stringResource(R.string.provider_discard_changes_title)) },
                    text = { Text(stringResource(R.string.provider_discard_changes_message)) },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                val then = discardThen
                                showDiscardDialog = false
                                discardThen = null
                                if (then != null) then() else onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.provider_discard_button)) }
                    },
                    dismissButton = {
                        CinemaDialogTextButton(
                            onClick = {
                                showDiscardDialog = false
                                discardThen = null
                            },
                        ) { Text(stringResource(R.string.provider_keep_editing_button)) }
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
                        ProviderType.SMB -> smbSourceConfig(host.trim(), shareName.trim())
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
                        CinemaOutlinedButton(
                            onClick = { showClearLiveTvCacheDialog = false },
                        ) { Text(stringResource(R.string.common_cancel)) }
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
                        CinemaOutlinedButton(
                            onClick = { showClearMoviesCacheDialog = false },
                        ) { Text(stringResource(R.string.common_cancel)) }
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
                        CinemaOutlinedButton(
                            onClick = { showClearTvShowsCacheDialog = false },
                        ) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }
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
                                coroutineScope.launch {
                                    // The live per-provider store, not a fresh XtreamRepository on provider 0:
                                    // going through AppContainer keeps the cached instance's in-memory
                                    // favorites view consistent with what was just removed from disk.
                                    AppContainer.getInstance(context).getMediaRepository(editId).clearFavorites()
                                }
                                showClearFavoritesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        CinemaOutlinedButton(
                            onClick = { showClearFavoritesDialog = false },
                        ) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }

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
                                coroutineScope.launch {
                                    AppContainer.getInstance(context).getMediaRepository(editId).clearWatchHistory()
                                }
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

            QuickConnectDialog(
                showQuickConnectDialog = showQuickConnectDialog,
                editId = if (isEditMode) editId else null,
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
                onSuccess = onSuccess,
            )
        }
    }
}

@Composable
private fun FormErrorText(message: String?) {
    message?.let {
        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaError,
        )
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
