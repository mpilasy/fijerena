@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.feature.settings.components.AboutSettingsCard
import org.njarasoa.fijerena.feature.settings.components.CloudSyncSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ConflictResolutionDialog
import org.njarasoa.fijerena.feature.settings.components.DeveloperSettingsCard
import org.njarasoa.fijerena.feature.settings.components.EpgSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ExportImportSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ImportOptionsDialog
import org.njarasoa.fijerena.feature.settings.components.PlaybackSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ProviderSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ThemeSettingsCard
import org.njarasoa.fijerena.feature.settings.components.UiScaleSettingsCard
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings screen for app configuration.
 *
 * Features:
 * - Change provider URL
 * - Adjust watch history size
 * - Toggle developer mode
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onUiScaleChanged: (Float) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onManageEpg: () -> Unit = {},
    onProviderChanged: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }
    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val appSettings = remember { AppSettings(context.applicationContext) }
    val syncManager = remember { DriveSettingsSyncManager(context.applicationContext, providerRepo) }
    val exportManager = remember { SettingsExportManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Get active provider info from ProviderEntity (not legacy AppSettings)
    var providerName by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var currentUsername by remember { mutableStateOf("") }
    var activeProviderId by remember { mutableStateOf<Long?>(null) }

    // Track whether we had a provider at initial load
    var hadProviderOnLoad by remember { mutableStateOf<Boolean?>(null) }

    // Global settings
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    var uiScale by remember { mutableStateOf(appSettings.uiScale) }
    var selectedThemeId by remember { mutableStateOf(appSettings.themeId) }
    var watchDelaySeconds by remember { mutableStateOf(appSettings.watchDelaySeconds) }

    // Export/Import state
    var exportImportMessage by remember { mutableStateOf<String?>(null) }
    var epgRefreshTrigger by remember { mutableStateOf(0) }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }
    var pendingParsedImport by remember { mutableStateOf<SettingsExportManager.ParsedImport?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportOptions by remember { mutableStateOf(SettingsExportManager.ImportOptions()) }

    // SAF launcher for export (create file)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> pendingExportUri = uri }

    // SAF launcher for import (open file)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImportUri = uri }

    // Process export in LaunchedEffect (survives recomposition)
    LaunchedEffect(pendingExportUri) {
        val uri = pendingExportUri ?: return@LaunchedEffect
        val success = exportManager.exportToUri(uri)
        exportImportMessage = if (success) "Settings exported successfully" else "Export failed"
        pendingExportUri = null
    }

    // Parse import file (URI) and show options dialog
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportUri(uri)
        parseResult.onSuccess { parsed ->
            pendingParsedImport = parsed
            pendingImportOptions = SettingsExportManager.ImportOptions()
            showImportOptionsDialog = true
        }.onFailure { e ->
            exportImportMessage = "Import failed: ${e.message}"
        }
        pendingImportUri = null
    }

    // Parse import file (Path) and show options dialog
    LaunchedEffect(pendingImportPath) {
        val path = pendingImportPath ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportPath(path)
        parseResult.onSuccess { parsed ->
            pendingParsedImport = parsed
            pendingImportOptions = SettingsExportManager.ImportOptions()
            showImportOptionsDialog = true
        }.onFailure { e ->
            exportImportMessage = "Import failed: ${e.message}"
        }
        pendingImportPath = null
    }

    // Helper to perform import and refresh UI state
    fun doImport(parsed: SettingsExportManager.ParsedImport, resolution: SettingsExportManager.ConflictResolution, options: SettingsExportManager.ImportOptions) {
        coroutineScope.launch {
            val result = exportManager.importFromParsed(parsed, resolution, options)
            exportImportMessage = result.toSummary()
            if (result.isSuccess) {
                if (options.importGlobalSettings) {
                    selectedThemeId = appSettings.themeId
                    onThemeChanged(appSettings.themeId)
                    uiScale = appSettings.uiScale
                    isDevMode = appSettings.isDevMode
                }
                val activeProvider = providerRepo.getActiveProvider()
                providerName = activeProvider?.name ?: "No provider"
                currentUrl = activeProvider?.url ?: ""
                currentUsername = activeProvider?.username ?: ""
                activeProviderId = activeProvider?.id
                epgRefreshTrigger++
            }
        }
    }

    // Drive sync state
    val syncStatus by syncManager.syncStatus.collectAsStateWithLifecycle()
    val signedInEmail by syncManager.signedInEmail.collectAsStateWithLifecycle()

    // Sign-in error state
    var signInError by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            val success = syncManager.handleSignInResult(result.data)
            if (!success) {
                signInError = "Sign-in failed. Check Google Play Services."
            } else {
                signInError = null
            }
        }
    }

    // Initialize sync on startup
    LaunchedEffect(Unit) {
        syncManager.initialize()
    }

    LaunchedEffect(Unit) {
        val activeProvider = providerRepo.getActiveProvider()
        providerName = activeProvider?.name ?: "No provider"
        currentUrl = activeProvider?.url ?: ""
        currentUsername = activeProvider?.username ?: ""
        activeProviderId = activeProvider?.id
        hadProviderOnLoad = activeProvider != null
    }

    // Re-check provider when returning from provider management screens
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            val activeProvider = providerRepo.getActiveProvider()
            val hadProvider = hadProviderOnLoad
            providerName = activeProvider?.name ?: "No provider"
            currentUrl = activeProvider?.url ?: ""
            currentUsername = activeProvider?.username ?: ""
            activeProviderId = activeProvider?.id
            // Navigate to content selection if we just got our first provider
            if (hadProvider == false && activeProvider != null) {
                hadProviderOnLoad = true
                onProviderChanged()
            }
        }
    }

    val scale = LocalUiScale.current
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            // Settings List
            TvLazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer { initialFocusRequester }
            ) {
                // Provider Details
                item {
                    ProviderSettingsCard(
                        providerName = providerName,
                        currentUrl = currentUrl,
                        onManageProviders = onManageProviders,
                        initialFocusRequester = initialFocusRequester,
                        scale = scale
                    )
                }

                // Playback
                item {
                    PlaybackSettingsCard(
                        watchDelaySeconds = watchDelaySeconds,
                        onWatchDelayChanged = { seconds ->
                            watchDelaySeconds = seconds
                            appSettings.watchDelaySeconds = seconds
                        },
                        scale = scale
                    )
                }

                // Theme Selection
                item {
                    ThemeSettingsCard(
                        selectedThemeId = selectedThemeId,
                        onThemeSelected = { newThemeId ->
                            selectedThemeId = newThemeId
                            appSettings.themeId = newThemeId
                            onThemeChanged(newThemeId)
                        },
                        scale = scale
                    )
                }

                // EPG Data
                item {
                    EpgSettingsCard(
                        context = context,
                        epgRefreshTrigger = epgRefreshTrigger,
                        onManageEpg = onManageEpg,
                        scale = scale
                    )
                }

                // UI Scale
                item {
                    UiScaleSettingsCard(
                        uiScale = uiScale,
                        onScaleSelected = { newScale ->
                            uiScale = newScale
                            appSettings.uiScale = newScale
                            onUiScaleChanged(newScale)
                        },
                        scale = scale
                    )
                }

                // Developer Mode
                item {
                    DeveloperSettingsCard(
                        isDevMode = isDevMode,
                        onDevModeChanged = { enabled ->
                            isDevMode = enabled
                            appSettings.isDevMode = enabled
                        },
                        scale = scale
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
                        scale = scale
                    )
                }

                // Export / Import Settings
                item {
                    ExportImportSettingsCard(
                        onExport = { exportLauncher.launch("fijerena_settings.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        onQuickImport = {
                            val downloadPath = "/sdcard/Download/fijerena_settings.json"
                            val privatePath = context.getExternalFilesDir(null)?.absolutePath + "/fijerena_settings.json"
                            
                            coroutineScope.launch {
                                // Try public download folder first
                                val file = java.io.File(downloadPath)
                                if (file.exists() && file.canRead()) {
                                    pendingImportPath = downloadPath
                                } else {
                                    // Fallback to app private folder (no permission needed)
                                    pendingImportPath = privatePath
                                }
                            }
                        },
                        exportImportMessage = exportImportMessage,
                        scale = scale
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
                        doImport(p, SettingsExportManager.ConflictResolution.SKIP, options)
                    }
                },
                onCancel = {
                    showImportOptionsDialog = false
                    if (!showConflictDialog) pendingParsedImport = null
                }
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
                    doImport(parsed, resolution, options)
                },
                onCancel = {
                    showConflictDialog = false
                    pendingParsedImport = null
                }
            )
        }
    }
}
