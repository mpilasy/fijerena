@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.*
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
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
    onLiveSync: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onGuideSources: (providerId: Long) -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val profilesMessage by profilesViewModel.message.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val providerRepo = remember { ProviderRepository(context.applicationContext) }
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
            if (success) resources.getString(R.string.settings_export_success) else resources.getString(R.string.settings_export_failed),
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
                viewModel.setExportImportMessage(resources.getString(R.string.settings_import_failed, e.message))
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
                viewModel.setExportImportMessage(resources.getString(R.string.settings_import_failed, e.message))
            }
        pendingImportPath = null
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

    // Back from Manage Sources, Live Sync or Diagnostics lands on the button that opened it, at
    // the scroll position the list had — not on the first card at the top.
    val listState = rememberLazyListState()
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus, listState = listState)

    // Choice settings drill into a picker pane that replaces the list (plan Part I, B). The row
    // that opened it is recorded like a navigation away, and gets focus back when the pane closes
    // — that close is not a lifecycle resume, so the hand-back runs here rather than in
    // NavReturnFocusEffect (which still covers Language's Activity recreate).
    var picker by rememberSaveable { mutableStateOf<SettingPicker?>(null) }
    val openPicker: (SettingPicker) -> Unit = { target ->
        returnFocus.leaveFrom(target.returnKey, listState)
        picker = target
    }
    val closePicker: () -> Unit = {
        picker = null
        coroutineScope.launch {
            returnFocus.restoreScroll(listState)
            returnFocus.requester.requestFocusWithRetry()
            returnFocus.clear()
        }
    }

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

            when (picker) {
                SettingPicker.THEME -> {
                    ThemePickerPane(
                        selectedThemeId = uiState.themeId,
                        onPick = { newThemeId ->
                            viewModel.updateTheme(newThemeId)
                            onThemeChanged(newThemeId)
                        },
                        onBack = closePicker,
                    )
                }

                SettingPicker.LOOK -> {
                    UiStylePickerPane(
                        selectedUiStyleId = uiState.uiStyleId,
                        onPick = { newStyleId ->
                            viewModel.updateUiStyle(newStyleId)
                            onUiStyleChanged(newStyleId)
                        },
                        onBack = closePicker,
                    )
                }

                SettingPicker.TEXT_SIZE -> {
                    UiScalePickerPane(
                        uiScale = uiState.uiScale,
                        onPick = { newScale ->
                            viewModel.updateUiScale(newScale)
                            onUiScaleChanged(newScale)
                        },
                        onBack = closePicker,
                    )
                }

                SettingPicker.WATCH_DELAY -> {
                    WatchDelayPickerPane(
                        watchDelaySeconds = uiState.watchDelaySeconds,
                        onPick = { seconds -> viewModel.updateWatchDelay(seconds) },
                        onBack = closePicker,
                    )
                }

                SettingPicker.LANGUAGE -> {
                    LanguagePickerPane(
                        selectedLanguage = uiState.language,
                        onPick = { newLang ->
                            viewModel.updateLanguage(newLang)
                            (context as? android.app.Activity)?.recreate()
                        },
                        onBack = closePicker,
                    )
                }

                null -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        // Scrolling a focused card out of view and back otherwise loses it: the item is
                        // disposed, focus falls to the root, and the next D-pad press restarts at the top
                        // of the list. focusRestorer remembers the last focused child and hands it back.
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                    ) {
                        // Profiles — first, since it decides whose favourites and history everything below
                        // the provider line is about.
                        item {
                            ProfilesSettingsCard(
                                profiles = profiles,
                                message = profilesMessage,
                                newProfileColorIndex = profilesViewModel::nextFreeColorIndex,
                                onAdd = profilesViewModel::addProfile,
                                onUpdate = profilesViewModel::updateProfile,
                                onDelete = profilesViewModel::deleteProfile,
                                onDismissMessage = profilesViewModel::clearMessage,
                                scale = scale,
                            )
                        }

                        // Provider & Playback
                        item {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_provider_playback), scale = scale)
                        }
                        item {
                            ProviderSettingsCard(
                                providerName = uiState.providerName,
                                currentUrl = uiState.currentUrl,
                                subscriptionExpiry = uiState.subscriptionExpiry,
                                subscriptionMaxCons = uiState.subscriptionMaxCons,
                                subscriptionIsTrial = uiState.subscriptionIsTrial,
                                subscriptionStatus = uiState.subscriptionStatus,
                                onManageProviders = {
                                    returnFocus.leaveFrom(RETURN_PROVIDERS, listState)
                                    onManageProviders()
                                },
                                scale = scale,
                                manageButtonFocusRequester = returnFocus.requesterFor(RETURN_PROVIDERS),
                            )
                        }

                        // Playback
                        item {
                            PlaybackSettingsCard(
                                watchDelaySeconds = uiState.watchDelaySeconds,
                                onOpenWatchDelayPicker = { openPicker(SettingPicker.WATCH_DELAY) },
                                autoplayNextEpisode = uiState.autoplayNextEpisode,
                                onAutoplayNextEpisodeChanged = { enabled ->
                                    viewModel.updateAutoplayNextEpisode(enabled)
                                },
                                scale = scale,
                                watchDelayRowFocusRequester = returnFocus.requesterFor(SettingPicker.WATCH_DELAY.returnKey),
                            )
                        }

                        // Appearance
                        item {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_appearance), scale = scale)
                        }
                        item {
                            ThemeSettingsCard(
                                selectedThemeId = uiState.themeId,
                                onOpenThemePicker = { openPicker(SettingPicker.THEME) },
                                selectedUiStyleId = uiState.uiStyleId,
                                onOpenUiStylePicker = { openPicker(SettingPicker.LOOK) },
                                scale = scale,
                                themeRowFocusRequester = returnFocus.requesterFor(SettingPicker.THEME.returnKey),
                                uiStyleRowFocusRequester = returnFocus.requesterFor(SettingPicker.LOOK.returnKey),
                            )
                        }

                        // Language Selection
                        item {
                            LanguageSettingsCard(
                                selectedLanguage = uiState.language,
                                onOpenPicker = { openPicker(SettingPicker.LANGUAGE) },
                                scale = scale,
                                rowFocusRequester = returnFocus.requesterFor(SettingPicker.LANGUAGE.returnKey),
                            )
                        }

                        // UI Scale
                        item {
                            UiScaleSettingsCard(
                                uiScale = uiState.uiScale,
                                onOpenPicker = { openPicker(SettingPicker.TEXT_SIZE) },
                                scale = scale,
                                rowFocusRequester = returnFocus.requesterFor(SettingPicker.TEXT_SIZE.returnKey),
                            )
                        }

                        // Data & Sync
                        item {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_data_sync), scale = scale)
                        }
                        item {
                            EpgSettingsCard(
                                context = context,
                                epgRefreshTrigger = uiState.epgRefreshTrigger,
                                activeProviderId = uiState.activeProviderId,
                                onGuideSources = { id ->
                                    returnFocus.leaveFrom(RETURN_EPG, listState)
                                    onGuideSources(id)
                                },
                                scale = scale,
                                rowFocusRequester = returnFocus.requesterFor(RETURN_EPG),
                            )
                        }

                        item {
                            LiveSyncSettingsCard(
                                onOpen = {
                                    returnFocus.leaveFrom(RETURN_LIVE_SYNC, listState)
                                    onLiveSync()
                                },
                                scale = scale,
                                openButtonFocusRequester = returnFocus.requesterFor(RETURN_LIVE_SYNC),
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
                                        viewModel.setExportImportMessage(resources.getString(R.string.settings_quick_import_not_found))
                                    }
                                },
                                exportImportMessage = uiState.exportImportMessage,
                                scale = scale,
                            )
                        }

                        // Database Maintenance
                        item {
                            DatabaseMaintenanceCard(
                                isPruning = uiState.isPruningDatabase,
                                resultMessage = uiState.databaseMaintenanceMessage,
                                onShrinkClick = { viewModel.pruneDatabase() },
                                scale = scale,
                                isDevMode = uiState.isDevMode,
                                lastShrinkAtMs = uiState.lastShrinkAtMs,
                                lastShrinkDurationMs = uiState.lastShrinkDurationMs,
                                lastShrinkRowsRemoved = uiState.lastShrinkRowsRemoved,
                                lastShrinkBytesReclaimed = uiState.lastShrinkBytesReclaimed,
                            )
                        }

                        // Advanced
                        item {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_advanced), scale = scale)
                        }
                        item {
                            DeveloperSettingsCard(
                                isDevMode = uiState.isDevMode,
                                onDevModeChanged = { enabled ->
                                    viewModel.updateDevMode(enabled)
                                },
                                onDiagnostics = {
                                    returnFocus.leaveFrom(RETURN_DIAGNOSTICS, listState)
                                    onDiagnostics()
                                },
                                scale = scale,
                                diagnosticsButtonFocusRequester = returnFocus.requesterFor(RETURN_DIAGNOSTICS),
                            )
                        }

                        // About this app
                        item {
                            AboutSettingsCard(scale = scale)
                        }
                    }
                }
            }
        }

        // Import options dialog. The dialog shows what was parsed when it composed; its buttons read
        // the live state, so a second click (OK auto-repeat) after the first cleared it does
        // nothing rather than crash or import twice (R-20).
        val parsedImport = pendingParsedImport
        if (showImportOptionsDialog && parsedImport != null) {
            ImportOptionsDialog(
                parsed = parsedImport,
                initialOptions = pendingImportOptions,
                onConfirm = { options ->
                    pendingParsedImport?.let { p ->
                        pendingImportOptions = options
                        if (options.importProviders && p.hasConflicts) {
                            showConflictDialog = true
                            showImportOptionsDialog = false
                        } else {
                            pendingParsedImport = null
                            showImportOptionsDialog = false
                            viewModel.doImport(p, SettingsExportManager.ConflictResolution.SKIP, options)
                        }
                    }
                },
                onCancel = {
                    showImportOptionsDialog = false
                    if (!showConflictDialog) pendingParsedImport = null
                },
            )
        }

        // Conflict resolution dialog
        if (showConflictDialog && parsedImport != null) {
            ConflictResolutionDialog(
                conflicts = parsedImport.conflictingProviders,
                onResolve = { resolution ->
                    showConflictDialog = false
                    pendingParsedImport?.let { parsed ->
                        pendingParsedImport = null
                        viewModel.doImport(parsed, resolution, pendingImportOptions)
                    }
                },
                onCancel = {
                    showConflictDialog = false
                    pendingParsedImport = null
                },
            )
        }
    }
}

// Keys for the buttons that navigate away from Settings — see rememberNavReturnFocus.
private const val RETURN_PROVIDERS = "providers"
private const val RETURN_LIVE_SYNC = "liveSync"
private const val RETURN_DIAGNOSTICS = "diagnostics"
private const val RETURN_EPG = "epg"

/** The choice settings that drill into a [SettingsPickerPane]; [returnKey] names the row for rememberNavReturnFocus. */
enum class SettingPicker(
    val returnKey: String,
) {
    THEME("picker:theme"),
    LOOK("picker:look"),
    TEXT_SIZE("picker:textSize"),
    WATCH_DELAY("picker:watchDelay"),
    LANGUAGE("picker:language"),
}

/**
 * Groups the settings list into visual waypoints (docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md,
 * Phase 5, 8d) without touching any card's internals — plain non-focusable label, same as a list
 * section header anywhere else in the app.
 */
@Composable
private fun SettingsSectionHeader(
    text: String,
    scale: Float,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize =
                    MaterialTheme.typography.labelLarge.fontSize
                        .scaled(scale),
            ),
        color = CinemaAccent,
        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
    )
}
