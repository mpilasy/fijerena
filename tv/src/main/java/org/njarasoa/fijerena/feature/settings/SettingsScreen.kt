@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.*
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings screen for app configuration.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
    onUiScaleChanged: (Float) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val syncManager = remember { DriveSettingsSyncManager(context.applicationContext, providerRepo) }
    val exportManager = remember { SettingsExportManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Track whether we had a provider at initial load
    var hadProviderOnLoad by remember { mutableStateOf<Boolean?>(null) }

    // Export/Import transient state (not in ViewModel yet as it involves SAF Launchers)
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }
    var pendingParsedImport by remember { mutableStateOf<SettingsExportManager.ParsedImport?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportOptions by remember { mutableStateOf(SettingsExportManager.ImportOptions()) }

    // SAF launcher for export (create file)
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> pendingExportUri = uri }

    // SAF launcher for import (open file)
    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> pendingImportUri = uri }

    // Process export in LaunchedEffect (survives recomposition)
    LaunchedEffect(pendingExportUri) {
        val uri = pendingExportUri ?: return@LaunchedEffect
        val success = exportManager.exportToUri(uri)
        viewModel.setExportImportMessage(
            if (success) context.getString(R.string.settings_export_success) else context.getString(R.string.settings_export_failed),
        )
        pendingExportUri = null
    }

    // Parse import file (URI) and show options dialog
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportUri(uri)
        parseResult
            .onSuccess { parsed ->
                pendingParsedImport = parsed
                pendingImportOptions = SettingsExportManager.ImportOptions()
                showImportOptionsDialog = true
            }.onFailure { e ->
                viewModel.setExportImportMessage(context.getString(R.string.settings_import_failed, e.message))
            }
        pendingImportUri = null
    }

    // Parse import file (Path) and show options dialog
    LaunchedEffect(pendingImportPath) {
        val path = pendingImportPath ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportPath(path)
        parseResult
            .onSuccess { parsed ->
                pendingParsedImport = parsed
                pendingImportOptions = SettingsExportManager.ImportOptions()
                showImportOptionsDialog = true
            }.onFailure { e ->
                viewModel.setExportImportMessage(context.getString(R.string.settings_import_failed, e.message))
            }
        pendingImportPath = null
    }

    // Drive sync state
    val syncStatus by syncManager.syncStatus.collectAsStateWithLifecycle()
    val signedInEmail by syncManager.signedInEmail.collectAsStateWithLifecycle()

    // Sign-in error state
    var signInError by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val signInLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            coroutineScope.launch {
                val success = syncManager.handleSignInResult(result.data)
                if (!success) {
                    signInError = context.getString(R.string.settings_google_signin_failed)
                } else {
                    signInError = null
                }
            }
        }

    // Initialize sync on startup
    LaunchedEffect(Unit) {
        syncManager.initialize()
    }

    // Re-check provider when returning from provider management screens
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            val activeProvider = providerRepo.getActiveProvider()
            val hadOnLoad = hadProviderOnLoad
            viewModel.refreshProviderInfo()

            if (hadOnLoad == null) {
                hadProviderOnLoad = activeProvider != null
            } else if (hadOnLoad == false && activeProvider != null) {
                hadProviderOnLoad = true
                onProviderChanged()
            }
        }
    }

    val scale = LocalUiScale.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            // Settings List
            TvLazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                // Scrolling a focused card out of view and back otherwise loses it: the item is
                // disposed, focus falls to the root, and the next D-pad press restarts at the top
                // of the list. focusRestorer remembers the last focused child and hands it back.
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                // Provider Details
                item {
                    ProviderSettingsCard(
                        providerName = uiState.providerName,
                        currentUrl = uiState.currentUrl,
                        subscriptionExpiry = uiState.subscriptionExpiry,
                        subscriptionMaxCons = uiState.subscriptionMaxCons,
                        subscriptionIsTrial = uiState.subscriptionIsTrial,
                        subscriptionStatus = uiState.subscriptionStatus,
                        onManageProviders = onManageProviders,
                        scale = scale,
                    )
                }

                // Playback
                item {
                    PlaybackSettingsCard(
                        watchDelaySeconds = uiState.watchDelaySeconds,
                        onWatchDelayChanged = { seconds ->
                            viewModel.updateWatchDelay(seconds)
                        },
                        scale = scale,
                    )
                }

                // Theme Selection
                item {
                    ThemeSettingsCard(
                        selectedThemeId = uiState.themeId,
                        onThemeSelected = { newThemeId ->
                            viewModel.updateTheme(newThemeId)
                            onThemeChanged(newThemeId)
                        },
                        selectedUiStyleId = uiState.uiStyleId,
                        onUiStyleSelected = { newStyleId ->
                            viewModel.updateUiStyle(newStyleId)
                            onUiStyleChanged(newStyleId)
                        },
                        scale = scale,
                    )
                }

                // Language Selection
                item {
                    LanguageSettingsCard(
                        selectedLanguage = uiState.language,
                        onLanguageSelected = { newLang ->
                            viewModel.updateLanguage(newLang)
                            (context as? android.app.Activity)?.recreate()
                        },
                        scale = scale,
                    )
                }

                // EPG Data
                item {
                    EpgSettingsCard(
                        context = context,
                        epgRefreshTrigger = uiState.epgRefreshTrigger,
                        scale = scale,
                    )
                }

                // UI Scale
                item {
                    UiScaleSettingsCard(
                        uiScale = uiState.uiScale,
                        onScaleSelected = { newScale ->
                            viewModel.updateUiScale(newScale)
                            onUiScaleChanged(newScale)
                        },
                        scale = scale,
                    )
                }

                // Developer Mode
                item {
                    DeveloperSettingsCard(
                        isDevMode = uiState.isDevMode,
                        onDevModeChanged = { enabled ->
                            viewModel.updateDevMode(enabled)
                        },
                        scale = scale,
                    )
                }

                // Cloud Sync (Google Drive)
                item {
                    CloudSyncSettingsCard(
                        syncStatus = syncStatus,
                        signedInEmail = signedInEmail,
                        signInError = signInError,
                        onSyncNow = { coroutineScope.launch { syncManager.syncNow() } },
                        onSignOut = { coroutineScope.launch { syncManager.signOut() } },
                        onSignIn = {
                            signInError = null
                            signInLauncher.launch(syncManager.getSignInIntent())
                        },
                        scale = scale,
                    )
                }

                // Export / Import Settings
                item {
                    ExportImportSettingsCard(
                        onExport = { exportLauncher.launch("fijerena_settings.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        onQuickImport = {
                            val path = exportManager.getQuickImportPath()
                            if (path != null) {
                                pendingImportPath = path
                            } else {
                                viewModel.setExportImportMessage(context.getString(R.string.settings_quick_import_not_found))
                            }
                        },
                        exportImportMessage = uiState.exportImportMessage,
                        scale = scale,
                    )
                }

                // About this app
                item {
                    AboutSettingsCard(scale = scale)
                }
            }
        }

        // Import options dialog
        if (showImportOptionsDialog && pendingParsedImport != null) {
            ImportOptionsDialog(
                parsed = pendingParsedImport!!,
                initialOptions = pendingImportOptions,
                onConfirm = { options ->
                    pendingImportOptions = options
                    val p = pendingParsedImport!!
                    if (options.importProviders && p.hasConflicts) {
                        showConflictDialog = true
                        showImportOptionsDialog = false
                    } else {
                        pendingParsedImport = null
                        showImportOptionsDialog = false
                        viewModel.doImport(p, SettingsExportManager.ConflictResolution.SKIP, options)
                    }
                },
                onCancel = {
                    showImportOptionsDialog = false
                    if (!showConflictDialog) pendingParsedImport = null
                },
            )
        }

        // Conflict resolution dialog
        if (showConflictDialog && pendingParsedImport != null) {
            val conflicts = pendingParsedImport!!.conflictingProviders
            ConflictResolutionDialog(
                conflicts = conflicts,
                onResolve = { resolution ->
                    showConflictDialog = false
                    val parsed = pendingParsedImport!!
                    val options = pendingImportOptions
                    pendingParsedImport = null
                    viewModel.doImport(parsed, resolution, options)
                },
                onCancel = {
                    showConflictDialog = false
                    pendingParsedImport = null
                },
            )
        }
    }
}
