package org.njarasoa.fijerena.feature.settings

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.GuideAutoRefreshRow
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenanceRow
import org.njarasoa.fijerena.feature.settings.components.ImportConflictDialog
import org.njarasoa.fijerena.feature.settings.components.ImportOptionsDialog
import org.njarasoa.fijerena.feature.settings.components.ProfilesSettingsRows
import org.njarasoa.fijerena.feature.settings.components.SettingsGroupHeader
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.feature.settings.components.SettingsScope
import org.njarasoa.fijerena.feature.settings.components.formatProgrammeCount
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings as one grouped preference list: the seven shared groups of
 * `docs/plans/archive/20261003_ux-overhaul-plan.md` (Part I, A) as section headers, value rows that open
 * pickers, switches inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onLiveSync: () -> Unit = {},
    onGuideSources: (providerId: Long) -> Unit = {},
    onEditSource: (providerId: Long) -> Unit = {},
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
    // Guide auto-refresh and maintenance are device-wide, so no provider id (A-9).
    val epgViewModel: EpgManagementViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            epgViewModel.toastMessage.collect { message ->
                Toast.makeText(context, message.asString(context), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val exportManager = remember { SettingsExportManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var pendingParsedImport by remember { mutableStateOf<SettingsExportManager.ParsedImport?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportOptions by remember { mutableStateOf(SettingsExportManager.ImportOptions()) }

    // SAF launcher for export (create file)
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val success = exportManager.exportToUri(uri)
                    viewModel.setExportImportMessage(
                        if (success) {
                            resources.getString(R.string.settings_export_success)
                        } else {
                            resources.getString(R.string.settings_export_failed)
                        },
                    )
                }
            }
        }

    // SAF launcher for import (open file)
    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val parseResult = exportManager.parseImportUri(uri)
                    parseResult
                        .onSuccess { parsed ->
                            pendingParsedImport = parsed
                            pendingImportOptions = SettingsExportManager.ImportOptions()
                            showImportOptionsDialog = true
                        }.onFailure { e ->
                            viewModel.setExportImportMessage(resources.getString(R.string.settings_import_failed, e.message ?: ""))
                        }
                }
            }
        }

    // Imports what was parsed with [resolution], once: a second tap after the first cleared the
    // pending import does nothing rather than crash or import twice (R-20).
    val resolveImport: (SettingsExportManager.ConflictResolution) -> Unit = { resolution ->
        showConflictDialog = false
        pendingParsedImport?.let { parsed ->
            pendingParsedImport = null
            viewModel.doImport(parsed, resolution, pendingImportOptions)
        }
    }

    // Import options dialog. The dialog shows what was parsed when it composed; its buttons read
    // the live state.
    val parsedImport = pendingParsedImport
    if (showImportOptionsDialog && parsedImport != null) {
        ImportOptionsDialog(
            parsed = parsedImport,
            initialOptions = pendingImportOptions,
            onDismiss = {
                showImportOptionsDialog = false
                if (!showConflictDialog) pendingParsedImport = null
            },
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
        )
    }

    if (showConflictDialog && parsedImport != null) {
        ImportConflictDialog(
            conflicts = parsedImport.conflictingProviders,
            onDismiss = {
                showConflictDialog = false
                pendingParsedImport = null
            },
            onOverwrite = { resolveImport(SettingsExportManager.ConflictResolution.OVERWRITE) },
            onDuplicate = { resolveImport(SettingsExportManager.ConflictResolution.DUPLICATE) },
            onSkip = { resolveImport(SettingsExportManager.ConflictResolution.SKIP) },
        )
    }

    // Value-row pickers
    var showThemePicker by remember { mutableStateOf(false) }
    var showUiStylePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showWatchDelayPicker by remember { mutableStateOf(false) }

    if (showThemePicker) {
        SettingsPickerDialog(
            title = stringResource(R.string.settings_theme_section_title),
            options = AllPalettes.map { it.displayName to it.id },
            selected = uiState.themeId,
            onSelect = { id ->
                viewModel.updateTheme(id)
                onThemeChanged(id)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false },
        )
    }
    if (showUiStylePicker) {
        SettingsPickerDialog(
            title = stringResource(R.string.settings_ui_style_section_title),
            options = AllUiStyles.map { it.displayName to it.id },
            selected = uiState.uiStyleId,
            onSelect = { id ->
                viewModel.updateUiStyle(id)
                onUiStyleChanged(id)
                showUiStylePicker = false
            },
            onDismiss = { showUiStylePicker = false },
        )
    }
    if (showLanguagePicker) {
        SettingsPickerDialog(
            title = stringResource(R.string.settings_language),
            options =
                listOf(
                    stringResource(R.string.settings_language_en) to "en",
                    stringResource(R.string.settings_language_mg) to "mg",
                    stringResource(R.string.settings_language_fr) to "fr",
                ),
            selected = uiState.language,
            onSelect = { code ->
                viewModel.updateLanguage(code)
                showLanguagePicker = false
                (context as? android.app.Activity)?.recreate()
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
    if (showWatchDelayPicker) {
        WatchDelayPickerDialog(
            seconds = uiState.watchDelaySeconds,
            onSecondsChange = viewModel::updateWatchDelay,
            onDismiss = { showWatchDelayPicker = false },
        )
    }

    val activeProviderId = uiState.activeProviderId
    val activeProfile = profiles.firstOrNull { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                    }
                },
                actions = {
                    if (activeProfile != null) {
                        ProfileAvatar(
                            name = activeProfile.name,
                            colorIndex = activeProfile.colorIndex,
                            size = MobileDimensions.iconLarge,
                            fontSize = MaterialTheme.typography.titleSmall.fontSize,
                            modifier = Modifier.padding(end = Spacing.md),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(bottom = Spacing.lg),
        ) {
            // === 1. Profiles === first, since it decides whose favourites and history the rest is about.
            item {
                SettingsGroupHeader(stringResource(R.string.settings_profiles_title))
                ProfilesSettingsRows(
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
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_filters_hint, activeProfile?.name ?: ""),
                    scope = SettingsScope.SOURCE,
                    onClick = { activeProviderId?.let(onEditSource) },
                    enabled = activeProviderId != null,
                )
            }

            // === 2. Source & guide ===
            item {
                SettingsGroupHeader(stringResource(R.string.settings_group_source_guide))
                SettingsListRow(
                    title = uiState.providerName.ifEmpty { stringResource(R.string.provider_none_label) },
                    summary = activeSourceSummary(uiState),
                    scope = SettingsScope.SOURCE,
                    onClick = { activeProviderId?.let(onEditSource) },
                    enabled = activeProviderId != null,
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_switch_source),
                    onClick = onManageProviders,
                )
                GuideSourcesRow(
                    uiState = uiState,
                    onGuideSources = onGuideSources,
                )
                GuideAutoRefreshRow(viewModel = epgViewModel)
            }

            // === 3. Playback ===
            item {
                SettingsGroupHeader(stringResource(R.string.settings_playback_section_title))
                SettingsListRow(
                    title = stringResource(R.string.settings_watch_delay_row_title),
                    summary = stringResource(R.string.settings_seconds_short_format, uiState.watchDelaySeconds),
                    scope = SettingsScope.DEVICE,
                    onClick = { showWatchDelayPicker = true },
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_per_source_playback_hint),
                    scope = SettingsScope.SOURCE,
                    onClick = { activeProviderId?.let(onEditSource) },
                    enabled = activeProviderId != null,
                )
            }

            // === 4. Display ===
            item {
                SettingsGroupHeader(stringResource(R.string.settings_group_display))
                SettingsListRow(
                    title = stringResource(R.string.settings_theme_section_title),
                    summary = AllPalettes.firstOrNull { it.id == uiState.themeId }?.displayName ?: uiState.themeId,
                    scope = SettingsScope.DEVICE,
                    onClick = { showThemePicker = true },
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_ui_style_section_title),
                    summary = AllUiStyles.firstOrNull { it.id == uiState.uiStyleId }?.displayName ?: uiState.uiStyleId,
                    scope = SettingsScope.DEVICE,
                    onClick = { showUiStylePicker = true },
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_language),
                    summary =
                        when (uiState.language) {
                            "en" -> stringResource(R.string.settings_language_en)
                            "mg" -> stringResource(R.string.settings_language_mg)
                            "fr" -> stringResource(R.string.settings_language_fr)
                            else -> uiState.language
                        },
                    scope = SettingsScope.DEVICE,
                    onClick = { showLanguagePicker = true },
                )
            }

            // === 5. Live sync ===
            item {
                SettingsGroupHeader(stringResource(R.string.live_sync_title))
                LiveSyncRow(onOpen = onLiveSync)
            }

            // === 6. Backup & storage ===
            item {
                SettingsGroupHeader(stringResource(R.string.settings_group_backup_storage))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CinemaOutlinedButton(
                        onClick = { exportLauncher.launch("fijerena_settings.json") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_export))
                    }
                    CinemaOutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_import))
                    }
                }
                if (uiState.isDevMode) {
                    val fileNotFoundText = stringResource(R.string.settings_quick_import_not_found)
                    SettingsListRow(
                        title = stringResource(R.string.settings_quick_import_button),
                        trailing = {},
                        onClick = {
                            val path = exportManager.getQuickImportPath()
                            if (path != null) {
                                coroutineScope.launch {
                                    val parseResult = exportManager.parseImportPath(path)
                                    parseResult
                                        .onSuccess { parsed ->
                                            pendingParsedImport = parsed
                                            pendingImportOptions = SettingsExportManager.ImportOptions()
                                            showImportOptionsDialog = true
                                        }.onFailure { e ->
                                            viewModel.setExportImportMessage(
                                                resources.getString(R.string.settings_import_failed, e.message ?: ""),
                                            )
                                        }
                                }
                            } else {
                                viewModel.setExportImportMessage(fileNotFoundText)
                            }
                        },
                    )
                }
                uiState.exportImportMessage?.let { SettingsMessage(it) }
                SettingsListRow(
                    title =
                        stringResource(
                            if (uiState.isPruningDatabase) {
                                R.string.settings_shrink_database_button_running
                            } else {
                                R.string.settings_shrink_database_button
                            },
                        ),
                    summary = stringResource(R.string.settings_shrink_database_desc),
                    scope = SettingsScope.DEVICE,
                    trailing = {
                        if (uiState.isPruningDatabase) {
                            CircularProgressIndicator(modifier = Modifier.size(MobileDimensions.progressIndicatorSmall))
                        }
                    },
                    onClick = { viewModel.pruneDatabase() },
                    enabled = !uiState.isPruningDatabase,
                )
                uiState.databaseMaintenanceMessage?.let { SettingsMessage(it) }
                if (uiState.isDevMode && uiState.lastShrinkAtMs > 0L) {
                    val time = NumberUtils.formatTimestamp(LocalContext.current, uiState.lastShrinkAtMs)
                    val duration = NumberUtils.formatDuration(uiState.lastShrinkDurationMs)
                    SettingsMessage(stringResource(R.string.settings_shrink_database_dev_stats_time, time, duration))
                    val bytesStr = NumberUtils.formatBytes(uiState.lastShrinkBytesReclaimed)
                    SettingsMessage(
                        stringResource(R.string.settings_shrink_database_dev_stats_delta, uiState.lastShrinkRowsRemoved, bytesStr),
                    )
                }
                GuideMaintenanceRow(viewModel = epgViewModel)
            }

            // === 7. About & advanced ===
            item {
                SettingsGroupHeader(stringResource(R.string.settings_group_about_advanced))
                Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                    Text(
                        text = stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                    Text(
                        text = stringResource(R.string.settings_about_build_format, BuildConfig.GIT_HASH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                    Text(
                        text = stringResource(R.string.settings_about_built_format, BuildConfig.BUILD_TIME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                }
                // Developer mode is switched on each profile's page; Diagnostics stays here for the
                // profile in use.
                if (uiState.isDevMode) {
                    SettingsListRow(
                        title = stringResource(R.string.settings_diagnostics_open),
                        onClick = onDiagnostics,
                    )
                }
            }
        }
    }
}

/**
 * Active source summary: URL, then subscription facts when the source reports them; an expired
 * subscription date stands out in the error colour.
 */
@Composable
private fun activeSourceSummary(uiState: SettingsUiState): AnnotatedString? {
    val expiresLabel = stringResource(R.string.settings_provider_expires_label)
    val isExpired = uiState.subscriptionStatus?.equals("Expired", ignoreCase = true) == true
    val errorColor = MaterialTheme.colorScheme.error
    val trailingLines =
        listOfNotNull(
            uiState.subscriptionMaxCons?.let { "${stringResource(R.string.settings_provider_max_connections_label)} $it" },
            if (uiState.subscriptionIsTrial) stringResource(R.string.settings_provider_trial_account_label) else null,
        )
    val summary =
        buildAnnotatedString {
            uiState.currentUrl.ifEmpty { null }?.let { append(it) }
            uiState.subscriptionExpiry?.let { expiry ->
                if (length > 0) append("\n")
                append("$expiresLabel ")
                if (isExpired) withStyle(SpanStyle(color = errorColor)) { append(expiry) } else append(expiry)
            }
            trailingLines.forEach { line ->
                if (length > 0) append("\n")
                append(line)
            }
        }
    return summary.takeIf { it.isNotEmpty() }
}

/** Guide sources row: index status as the summary; opens EPG Management for the active source. */
@Composable
private fun GuideSourcesRow(
    uiState: SettingsUiState,
    onGuideSources: (providerId: Long) -> Unit,
) {
    val context = LocalContext.current
    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
    val indexState by epgIndexer.state.collectAsStateWithLifecycle()
    var sourceCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.epgRefreshTrigger) {
        sourceCount = epgIndexer.getSourceCount()
    }
    val summaryText =
        when (val idx = indexState) {
            is EpgIndexState.Indexed -> {
                stringResource(
                    R.string.epg_summary_channels_programmes,
                    formatProgrammeCount(idx.channelCount),
                    formatProgrammeCount(idx.programmeCount),
                )
            }

            is EpgIndexState.Indexing -> {
                stringResource(R.string.epg_summary_indexing, idx.progressPercent)
            }

            is EpgIndexState.Optimizing -> {
                stringResource(R.string.epg_database_optimizing)
            }

            is EpgIndexState.NotIndexed -> {
                if (sourceCount > 0) {
                    stringResource(R.string.epg_summary_not_indexed, sourceCount)
                } else {
                    stringResource(R.string.epg_summary_no_sources)
                }
            }

            is EpgIndexState.Failed -> {
                stringResource(R.string.epg_database_error, idx.reason)
            }
        }
    // Guide sources belong to a source; with no active one there is nowhere to go.
    val activeProviderId = uiState.activeProviderId
    SettingsListRow(
        title = stringResource(R.string.epg_sources_header),
        summary = summaryText,
        scope = SettingsScope.SOURCE,
        onClick = { activeProviderId?.let(onGuideSources) },
        enabled = activeProviderId != null,
    )
}

/** Live sync's line in Settings: whether it's on, and the way into its own screen. */
@Composable
private fun LiveSyncRow(onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val status by SyncManager.getInstance(app).status.collectAsStateWithLifecycle()
    SettingsListRow(
        title = stringResource(R.string.live_sync_title),
        summary = status.serverUrl ?: stringResource(R.string.live_sync_off),
        scope = SettingsScope.SYNCED,
        onClick = onOpen,
    )
}

/**
 * Watch delay picker: presets plus "Custom", which reveals the free 5–120 field. A stored value
 * outside the presets is listed as its own "(custom)" entry so it reads as selected.
 */
@Composable
private fun WatchDelayPickerDialog(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var customOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf(seconds.toString()) }
    val presetOptions =
        WATCH_DELAY_PRESETS.map { preset -> stringResource(R.string.settings_seconds_short_format, preset) to preset }
    val storedOption =
        if (seconds in WATCH_DELAY_PRESETS) {
            emptyList()
        } else {
            listOf(
                stringResource(
                    R.string.settings_custom_value_format,
                    stringResource(R.string.settings_seconds_short_format, seconds),
                ) to seconds,
            )
        }
    SettingsPickerDialog(
        title = stringResource(R.string.settings_watch_delay_label),
        options = presetOptions + storedOption + (stringResource(R.string.common_custom) to WATCH_DELAY_CUSTOM),
        selected = if (customOpen) WATCH_DELAY_CUSTOM else seconds,
        onSelect = { value ->
            if (value == WATCH_DELAY_CUSTOM) {
                customOpen = true
            } else {
                onSecondsChange(value)
                onDismiss()
            }
        },
        onDismiss = onDismiss,
        footer =
            if (customOpen) {
                {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { newValue ->
                            customText = newValue
                            newValue.toIntOrNull()?.let(onSecondsChange)
                        },
                        label = { Text(stringResource(R.string.settings_watch_delay_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.settings_watch_delay_range_format,
                                    AppSettings.MIN_WATCH_DELAY_SECONDS,
                                    AppSettings.MAX_WATCH_DELAY_SECONDS,
                                ),
                            )
                        },
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun SettingsMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
    )
}

private val WATCH_DELAY_PRESETS = listOf(5, 10, 15, 30, 60, 120)
private const val WATCH_DELAY_CUSTOM = -1
