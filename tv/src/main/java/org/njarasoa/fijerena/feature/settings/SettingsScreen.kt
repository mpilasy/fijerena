@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.*
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.input.tvPane
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

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
    onEditSource: (providerId: Long) -> Unit = {},
    onEditSourceFilters: (providerId: Long) -> Unit = {},
    onProfileSwitched: () -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val profilesMessage by profilesViewModel.message.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Guide auto-refresh and maintenance are device-wide, so no provider id (A-9, T6).
    val epgViewModel: EpgManagementViewModel = viewModel(factory = SettingsViewModelFactory(context))

    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val exportManager = remember { SettingsExportManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var hadProviderOnLoad by remember { mutableStateOf<Boolean?>(null) }

    // Export/Import transient state (not in ViewModel yet as it involves SAF Launchers)
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }
    var pendingParsedImport by remember { mutableStateOf<SettingsExportManager.ParsedImport?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportOptions by remember { mutableStateOf(SettingsExportManager.ImportOptions()) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> pendingExportUri = uri }

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
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            epgViewModel.toastMessage.collect { message ->
                android.widget.Toast
                    .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            val activeProvider = providerRepo.getActiveProvider()
            val hadOnLoad = hadProviderOnLoad
            viewModel.refreshProviderInfo()
            viewModel.refreshDevMode()

            if (hadOnLoad == null) {
                hadProviderOnLoad = activeProvider != null
            } else if (hadOnLoad == false && activeProvider != null) {
                hadProviderOnLoad = true
                onProviderChanged()
            }
        }
    }

    val scale = LocalUiScale.current

    // Two panes (plan Part I, B): a rail of the seven groups on the left, the selected group's
    // rows on the right. The rail row is saveable so a language change (Activity recreate) and the
    // Back round trip from a child screen come back to the same group.
    var selectedGroup by rememberSaveable { mutableStateOf(SettingsGroup.PROFILES) }
    val railPane = rememberPaneFocus()
    val contentPane = rememberPaneFocus()
    // One list state per group: a group change starts its pane at the top, and the Back
    // round trip restores the scroll of the group it left.
    val listState = rememberSaveable(selectedGroup, saver = LazyListState.Saver) { LazyListState() }
    railPane.bind(
        selectedKey = selectedGroup.name,
        firstKey = SettingsGroup.entries.first().name,
        listState = null,
        indexOf = { -1 },
    )
    // The pane's entry row is the group's first focusable (each card puts `paneItem` on it); the
    // remembered row of a previous group is not in this one, so entry falls through to it.
    val contentEntryKey = selectedGroup.entryKey
    contentPane.bind(
        selectedKey = null,
        firstKey = contentEntryKey,
        listState = listState,
        indexOf = { key -> if (key == contentEntryKey) 0 else -1 },
    )

    // Back from Manage Sources, Live sync, Diagnostics, EPG Management or Edit Source lands on
    // the control that opened it, in its group, at the scroll position the pane had.
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus, listState = listState)

    // First open: the rail row of the selected group. A pending hand-back (Back from a child
    // screen, the Language recreate) or an open picker has the last word instead.
    var picker by rememberSaveable { mutableStateOf<SettingPicker?>(null) }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && picker == null) railPane.focusEntry()
    }

    // Choice settings drill into a picker that replaces the group's rows inside the pane. The row
    // that opened it is recorded like a navigation away, and gets focus back when the picker
    // closes — that close is not a lifecycle resume, so the hand-back runs here rather than in
    // NavReturnFocusEffect (which still covers Language's Activity recreate).
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

    // Back while in the pane returns to the rail row (Left does the same, via tvPane); Back on
    // the rail leaves Settings as before. Taken in onPreviewKeyEvent on the root — a BackHandler
    // misses the first press while a button holds focus (docs/NAVIGATION_GUIDE.md, "TV Back on
    // Detail Screens"); the BackHandler stays as the fallback. A picker takes its own Back.
    var paneHasFocus by remember { mutableStateOf(false) }
    val backToRail = { coroutineScope.launch { railPane.focusEntry() } }
    BackHandler(enabled = paneHasFocus && picker == null) { backToRail() }

    Box(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.key == Key.Back && paneHasFocus && picker == null) {
                    if (event.type == KeyEventType.KeyUp) backToRail()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            SettingsHeader(
                activeProfile = profiles.firstOrNull { it.isActive },
                providerName = uiState.providerName,
                scale = scale,
            )

            Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

            Row(modifier = Modifier.fillMaxSize()) {
                // Rail: Up/Down swap the pane live, Right (or OK) enters it on its first row.
                Column(
                    modifier =
                        Modifier
                            .weight(RAIL_WEIGHT)
                            .fillMaxHeight()
                            .tvPane(railPane, exitRight = contentPane, exitUp = false),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                ) {
                    SettingsGroup.entries.forEach { group ->
                        TvInputListItem(
                            selected = group == selectedGroup,
                            onClick = {
                                selectedGroup = group
                                coroutineScope.launch { contentPane.focusEntry() }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .paneItem(railPane, group.name)
                                    .onFocusChanged { if (it.isFocused) selectedGroup = group },
                            headlineContent = { Text(stringResource(group.titleRes)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.lg.scaled(scale)))

                // Pane: the group's rows, or the open picker in their place. Left (and Back) go
                // back to the rail row; Up at the top stays (the header has nothing to focus).
                Box(
                    modifier =
                        Modifier
                            .weight(PANE_WEIGHT)
                            .fillMaxHeight()
                            .onFocusChanged { paneHasFocus = it.hasFocus }
                            .tvPane(contentPane, exitLeft = railPane, exitUp = false),
                ) {
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

                        SettingPicker.GUIDE_AUTO_REFRESH -> {
                            GuideAutoRefreshPane(viewModel = epgViewModel, onBack = closePicker)
                        }

                        SettingPicker.GUIDE_MAINTENANCE -> {
                            GuideMaintenancePane(viewModel = epgViewModel, onBack = closePicker)
                        }

                        null -> {
                            val entryModifier = Modifier.paneItem(contentPane, contentEntryKey)
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                when (selectedGroup) {
                                    SettingsGroup.PROFILES -> {
                                        item {
                                            ProfilesSettingsCard(
                                                profiles = profiles,
                                                message = profilesMessage,
                                                newProfileColorIndex = profilesViewModel::nextFreeColorIndex,
                                                onAdd = profilesViewModel::addProfile,
                                                onUpdate = { id, name, color, settings ->
                                                    profilesViewModel.updateProfile(id, name, color, settings)
                                                    viewModel.refreshDevMode()
                                                },
                                                settingsOf = profilesViewModel::settingsOf,
                                                onDelete = profilesViewModel::deleteProfile,
                                                onSwitchTo = { id -> profilesViewModel.switchTo(id, onProfileSwitched) },
                                                onDismissMessage = profilesViewModel::clearMessage,
                                                scale = scale,
                                                firstRowModifier = entryModifier,
                                            )
                                        }
                                        // Content filters live on the source: the row opens Edit Source
                                        // with focus on its filters.
                                        item {
                                            FiltersHintRow(
                                                profileName = profiles.firstOrNull { it.isActive }?.name.orEmpty(),
                                                enabled = uiState.activeProviderId != null,
                                                onClick = {
                                                    uiState.activeProviderId?.let { id ->
                                                        returnFocus.leaveFrom(RETURN_FILTERS, listState)
                                                        onEditSourceFilters(id)
                                                    }
                                                },
                                                scale = scale,
                                                focusRequester = returnFocus.requesterFor(RETURN_FILTERS),
                                            )
                                        }
                                    }

                                    SettingsGroup.SOURCE_GUIDE -> {
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
                                                onEditSource = {
                                                    uiState.activeProviderId?.let { id ->
                                                        returnFocus.leaveFrom(RETURN_EDIT_SOURCE, listState)
                                                        onEditSource(id)
                                                    }
                                                },
                                                scale = scale,
                                                editEnabled = uiState.activeProviderId != null,
                                                manageButtonFocusRequester = returnFocus.requesterFor(RETURN_PROVIDERS),
                                                editRowFocusRequester = returnFocus.requesterFor(RETURN_EDIT_SOURCE),
                                                manageButtonModifier = entryModifier,
                                            )
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
                                                extraRows = {
                                                    GuideAutoRefreshRow(
                                                        viewModel = epgViewModel,
                                                        onOpen = { openPicker(SettingPicker.GUIDE_AUTO_REFRESH) },
                                                        focusRequester =
                                                            returnFocus.requesterFor(SettingPicker.GUIDE_AUTO_REFRESH.returnKey),
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    SettingsGroup.PLAYBACK -> {
                                        item {
                                            PlaybackSettingsCard(
                                                watchDelaySeconds = uiState.watchDelaySeconds,
                                                onOpenWatchDelayPicker = { openPicker(SettingPicker.WATCH_DELAY) },
                                                scale = scale,
                                                watchDelayRowFocusRequester = returnFocus.requesterFor(SettingPicker.WATCH_DELAY.returnKey),
                                                watchDelayRowModifier = entryModifier,
                                            )
                                        }
                                    }

                                    SettingsGroup.DISPLAY -> {
                                        item {
                                            ThemeSettingsCard(
                                                selectedThemeId = uiState.themeId,
                                                onOpenThemePicker = { openPicker(SettingPicker.THEME) },
                                                selectedUiStyleId = uiState.uiStyleId,
                                                onOpenUiStylePicker = { openPicker(SettingPicker.LOOK) },
                                                scale = scale,
                                                themeRowFocusRequester = returnFocus.requesterFor(SettingPicker.THEME.returnKey),
                                                uiStyleRowFocusRequester = returnFocus.requesterFor(SettingPicker.LOOK.returnKey),
                                                themeRowModifier = entryModifier,
                                            )
                                        }
                                        item {
                                            UiScaleSettingsCard(
                                                uiScale = uiState.uiScale,
                                                onOpenPicker = { openPicker(SettingPicker.TEXT_SIZE) },
                                                scale = scale,
                                                rowFocusRequester = returnFocus.requesterFor(SettingPicker.TEXT_SIZE.returnKey),
                                            )
                                        }
                                        item {
                                            LanguageSettingsCard(
                                                selectedLanguage = uiState.language,
                                                onOpenPicker = { openPicker(SettingPicker.LANGUAGE) },
                                                scale = scale,
                                                rowFocusRequester = returnFocus.requesterFor(SettingPicker.LANGUAGE.returnKey),
                                            )
                                        }
                                    }

                                    SettingsGroup.LIVE_SYNC -> {
                                        item {
                                            LiveSyncSettingsCard(
                                                onOpen = {
                                                    returnFocus.leaveFrom(RETURN_LIVE_SYNC, listState)
                                                    onLiveSync()
                                                },
                                                scale = scale,
                                                openButtonFocusRequester = returnFocus.requesterFor(RETURN_LIVE_SYNC),
                                                openButtonModifier = entryModifier,
                                            )
                                        }
                                    }

                                    SettingsGroup.BACKUP_STORAGE -> {
                                        item {
                                            ExportImportSettingsCard(
                                                onExport = { exportLauncher.launch("fijerena_settings.json") },
                                                onImport = {
                                                    importLauncher.launch(
                                                        arrayOf("application/json", "application/octet-stream", "*/*"),
                                                    )
                                                },
                                                onQuickImport = {
                                                    val path = exportManager.getQuickImportPath()
                                                    if (path != null) {
                                                        pendingImportPath = path
                                                    } else {
                                                        viewModel.setExportImportMessage(
                                                            resources.getString(R.string.settings_quick_import_not_found),
                                                        )
                                                    }
                                                },
                                                exportImportMessage = uiState.exportImportMessage,
                                                scale = scale,
                                                isDevMode = uiState.isDevMode,
                                                exportButtonModifier = entryModifier,
                                            )
                                        }
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
                                        item {
                                            GuideMaintenanceCard(
                                                viewModel = epgViewModel,
                                                onOpen = { openPicker(SettingPicker.GUIDE_MAINTENANCE) },
                                                focusRequester = returnFocus.requesterFor(SettingPicker.GUIDE_MAINTENANCE.returnKey),
                                            )
                                        }
                                    }

                                    SettingsGroup.ABOUT_ADVANCED -> {
                                        item {
                                            AboutSettingsCard(scale = scale, rowModifier = entryModifier)
                                        }
                                        // Developer mode is switched on each profile's page; Diagnostics
                                        // stays here for the profile in use.
                                        if (uiState.isDevMode) {
                                            item {
                                                DeveloperSettingsCard(
                                                    onDiagnostics = {
                                                        returnFocus.leaveFrom(RETURN_DIAGNOSTICS, listState)
                                                        onDiagnostics()
                                                    },
                                                    scale = scale,
                                                    diagnosticsButtonFocusRequester = returnFocus.requesterFor(RETURN_DIAGNOSTICS),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

// Keys for the controls that navigate away from Settings — see rememberNavReturnFocus.
private const val RETURN_PROVIDERS = "providers"
private const val RETURN_EDIT_SOURCE = "editSource"
private const val RETURN_FILTERS = "filters"
private const val RETURN_LIVE_SYNC = "liveSync"
private const val RETURN_DIAGNOSTICS = "diagnostics"
private const val RETURN_EPG = "epg"

// Rail ≈ 30 % of the width, pane ≈ 70 % (plan Part I, B).
private const val RAIL_WEIGHT = 0.3f
private const val PANE_WEIGHT = 0.7f

/** The seven groups of the shared Settings IA (plan Part I, A), in rail order. */
enum class SettingsGroup(
    @StringRes val titleRes: Int,
) {
    PROFILES(R.string.settings_profiles_title),
    SOURCE_GUIDE(R.string.settings_group_source_guide),
    PLAYBACK(R.string.settings_playback_section_title),
    DISPLAY(R.string.settings_group_display),
    LIVE_SYNC(R.string.live_sync_title),
    BACKUP_STORAGE(R.string.settings_group_backup_storage),
    ABOUT_ADVANCED(R.string.settings_group_about_advanced),
    ;

    /** The pane key of this group's first focusable row — where Right from the rail lands. */
    val entryKey: String get() = "$name:first"
}

/**
 * The rows that drill into a pane in place of the group's rows — a [SettingsPickerPane] for the
 * choice settings, a sub-pane for the guide's device-wide controls (T6); [returnKey] names the row
 * for rememberNavReturnFocus.
 */
enum class SettingPicker(
    val returnKey: String,
) {
    THEME("picker:theme"),
    LOOK("picker:look"),
    TEXT_SIZE("picker:textSize"),
    WATCH_DELAY("picker:watchDelay"),
    LANGUAGE("picker:language"),
    GUIDE_AUTO_REFRESH("picker:guideAutoRefresh"),
    GUIDE_MAINTENANCE("picker:guideMaintenance"),
}

/** Title on the left; the active profile's avatar and name and the active source on the right (T-7). */
@Composable
private fun SettingsHeader(
    activeProfile: ProfileUi?,
    providerName: String,
    scale: Float,
) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (activeProfile != null) {
                ProfileAvatar(
                    name = activeProfile.name,
                    colorIndex = activeProfile.colorIndex,
                    size = TvDimensions.iconMedium.scaled(scale),
                    fontSize =
                        MaterialTheme.typography.titleSmall.fontSize
                            .scaled(scale),
                )
                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
            }
            Text(
                text = listOfNotNull(activeProfile?.name, providerName.ifEmpty { null }).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Content filters for <profile> are set per source" — one row, opens Edit Source on its filters. */
@Composable
private fun FiltersHintRow(
    profileName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    scale: Float,
    focusRequester: FocusRequester?,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        TvInputListItem(
            selected = false,
            onClick = onClick,
            modifier = Modifier.padding(Spacing.md.scaled(scale)).then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            enabled = enabled,
            trailingContent = {
                Text(
                    text = "${stringResource(R.string.settings_edit_this_source)} ›",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            },
            headlineContent = { Text(stringResource(R.string.settings_filters_hint, profileName)) },
        )
    }
}
