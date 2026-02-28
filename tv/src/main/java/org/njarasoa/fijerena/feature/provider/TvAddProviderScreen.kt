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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.feature.provider.components.CacheManagementSection
import org.njarasoa.fijerena.feature.provider.components.CategoryFilterDialog
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.feature.provider.components.JellyfinForm
import org.njarasoa.fijerena.feature.provider.components.ProviderSettingsSection
import org.njarasoa.fijerena.feature.provider.components.ProviderTypeDropdown
import org.njarasoa.fijerena.feature.provider.components.QuickConnectDialog
import org.njarasoa.fijerena.feature.provider.components.RemoteM3uForm
import org.njarasoa.fijerena.feature.provider.components.SmbForm
import org.njarasoa.fijerena.feature.provider.components.XtreamForm
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

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
    val appSettings = remember { org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
    var error by remember { mutableStateOf<String?>(null) }
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val isBusy = saveState is SaveState.Validating || saveState is SaveState.Saving

    // Quick Connect state (Jellyfin only)
    var showQuickConnectDialog by remember { mutableStateOf(false) }

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
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    val repository = remember {
        val accountManager = org.njarasoa.fijerena.core.network.AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    LaunchedEffect(editId, cacheRefreshTrigger) {
        if (isEditMode) {
            cacheStats = providerRepo.getCacheStatsForProvider(editId)
            val ps = providerRepo.getProviderSettings(editId)
            providerSettings = ps
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

    CompositionLocalProvider(LocalUiScale provides uiScale) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val scaledDisplaySmall = remember(scale, typography) {
        typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
    }
    val scaledBodyMedium = remember(scale, typography) {
        typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
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
            GlassPanel(modifier = Modifier.width(TvDimensions.formFieldWidth.scaled(scale))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg.scaled(scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Edit Provider" else "Add Provider",
                    style = scaledDisplaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Provider type dropdown (D-pad friendly)
                ProviderTypeDropdown(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it }
                )

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Name field (all types)
                ReadOnlyFieldWithEdit(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = "Provider Name",
                    placeholder = "e.g. My IPTV"
                )

                // Type-specific fields
                when (selectedType) {
                    ProviderType.XTREAM -> {
                        XtreamForm(
                            url = url,
                            onUrlChange = { url = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            onErrorChange = { error = it }
                        )
                    }

                    ProviderType.JELLYFIN -> {
                        JellyfinForm(
                            url = url,
                            onUrlChange = { url = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isEditMode = isEditMode,
                            isBusy = isBusy,
                            onErrorChange = { error = it },
                            onQuickConnectClick = {
                                showQuickConnectDialog = true
                            }
                        )
                    }

                    ProviderType.SMB -> {
                        SmbForm(
                            host = host,
                            onHostChange = { host = it },
                            shareName = shareName,
                            onShareNameChange = { shareName = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            onErrorChange = { error = it }
                        )
                    }

                    ProviderType.LOCAL -> {
                        // LOCAL type only requires name - folder/file picker will be added later
                    }

                    ProviderType.REMOTE_M3U -> {
                        RemoteM3uForm(
                            url = url,
                            onUrlChange = { url = it },
                            onErrorChange = { error = it }
                        )
                    }
                }

                // Provider Settings (edit mode only)
                if (isEditMode) {
                    ProviderSettingsSection(
                        providerType = selectedType,
                        providerSettings = providerSettings,
                        onUpdateSettings = { newSettings ->
                            coroutineScope.launch {
                                providerRepo.updateProviderSettings(editId, newSettings)
                                providerSettings = newSettings
                                syncManager.syncProviderSettings(editId)
                            }
                        },
                        onClearFavoritesClick = { showClearFavoritesDialog = true },
                        onClearProgressClick = { showClearProgressDialog = true },
                        onManageFiltersClick = { showCategoryFilterDialog = true }
                    )
                }

                // Cache Management (edit mode only)
                if (isEditMode) {
                    CacheManagementSection(
                        cacheStats = cacheStats,
                        onClearAllClick = { showClearCacheDialog = true },
                        onClearLiveTvClick = { showClearLiveTvCacheDialog = true },
                        onClearMoviesClick = { showClearMoviesCacheDialog = true },
                        onClearTvShowsClick = { showClearTvShowsCacheDialog = true }
                    )
                }

                // Error message
                error?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    Text(
                        text = errorMsg,
                        style = scaledBodyMedium,
                        color = CinemaError
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale), Alignment.CenterHorizontally)
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
                                ProviderType.REMOTE_M3U -> when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "M3U Playlist URL is required"
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
                    ConfirmActionDialog(
                        title = "Clear All Cache?",
                        text = "This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server.",
                        confirmText = "Clear All",
                        onConfirm = {
                            coroutineScope.launch {
                                providerRepo.clearAllCacheForProvider(editId)
                                cacheRefreshTrigger++
                                showClearCacheDialog = false
                            }
                        },
                        onDismiss = { showClearCacheDialog = false }
                    )
                }

                if (showClearLiveTvCacheDialog) {
                    ConfirmActionDialog(
                        title = "Clear Live TV Cache?",
                        text = "This will remove all cached Live TV data (categories and streams).",
                        confirmText = "Clear",
                        onConfirm = {
                            coroutineScope.launch {
                                providerRepo.clearCacheForProviderContentType(editId, ContentType.LIVE_TV)
                                cacheRefreshTrigger++
                                showClearLiveTvCacheDialog = false
                            }
                        },
                        onDismiss = { showClearLiveTvCacheDialog = false }
                    )
                }

                if (showClearMoviesCacheDialog) {
                    ConfirmActionDialog(
                        title = "Clear Movies Cache?",
                        text = "This will remove all cached Movies data (categories and streams).",
                        confirmText = "Clear",
                        onConfirm = {
                            coroutineScope.launch {
                                providerRepo.clearCacheForProviderContentType(editId, ContentType.MOVIES)
                                cacheRefreshTrigger++
                                showClearMoviesCacheDialog = false
                            }
                        },
                        onDismiss = { showClearMoviesCacheDialog = false }
                    )
                }

                if (showClearTvShowsCacheDialog) {
                    ConfirmActionDialog(
                        title = "Clear TV Shows Cache?",
                        text = "This will remove all cached TV Shows data (categories and streams).",
                        confirmText = "Clear",
                        onConfirm = {
                            coroutineScope.launch {
                                providerRepo.clearCacheForProviderContentType(editId, ContentType.TV_SHOWS)
                                cacheRefreshTrigger++
                                showClearTvShowsCacheDialog = false
                            }
                        },
                        onDismiss = { showClearTvShowsCacheDialog = false }
                    )
                }

                // Clear Favorites Confirmation Dialog
                if (showClearFavoritesDialog) {
                    ConfirmActionDialog(
                        title = "Clear All Favorites?",
                        text = "This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.",
                        confirmText = "Clear All",
                        onConfirm = {
                            repository.clearFavorites()
                            showClearFavoritesDialog = false
                        },
                        onDismiss = { showClearFavoritesDialog = false }
                    )
                }

                // Clear Progress Confirmation Dialog
                if (showClearProgressDialog) {
                    ConfirmActionDialog(
                        title = "Clear All Playback Progress?",
                        text = "This will remove all saved playback positions. You will start from the beginning when playing any VOD content.",
                        confirmText = "Clear All",
                        onConfirm = {
                            repository.clearWatchHistory()
                            showClearProgressDialog = false
                        },
                        onDismiss = { showClearProgressDialog = false }
                    )
                }

                // Category Filter Dialog
                if (showCategoryFilterDialog) {
                    CategoryFilterDialog(
                        currentFilters = providerSettings.categoryFilters,
                        onSave = { newFilters ->
                            coroutineScope.launch {
                                val newSettings = providerSettings.copy(categoryFilters = newFilters)
                                providerRepo.updateProviderSettings(editId, newSettings)
                                providerSettings = newSettings
                                syncManager.syncProviderSettings(editId)
                            }
                            showCategoryFilterDialog = false
                        },
                        onDismiss = { showCategoryFilterDialog = false }
                    )
                }

                // Quick Connect dialog (Jellyfin)
                if (showQuickConnectDialog) {
                    QuickConnectDialog(
                        url = url,
                        onSuccess = { nameVal, usernameVal, token, userId ->
                            showQuickConnectDialog = false
                            viewModel.quickConnectSave(
                                name = name.ifBlank { nameVal },
                                url = url.trimEnd('/'),
                                username = username.ifBlank { usernameVal },
                                token = token,
                                userId = userId,
                                onComplete = onSuccess
                            )
                        },
                        onDismiss = { showQuickConnectDialog = false }
                    )
                }
            }
            } // GlassPanel
        }
    }
    } // CompositionLocalProvider
}
