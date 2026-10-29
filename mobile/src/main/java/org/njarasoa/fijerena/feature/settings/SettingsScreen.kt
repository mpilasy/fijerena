package org.njarasoa.fijerena.feature.settings

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.liveSyncStatusLine
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.utils.rememberDeveloperModeTapHandler
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsUiState
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbKeyViewModel
import org.njarasoa.fijerena.feature.settings.components.GuideMaintenanceRow
import org.njarasoa.fijerena.feature.settings.components.ImportConflictDialog
import org.njarasoa.fijerena.feature.settings.components.ImportOptionsDialog
import org.njarasoa.fijerena.feature.settings.components.ProfilesSettingsRows
import org.njarasoa.fijerena.feature.settings.components.SettingsGroupHeader
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.feature.settings.components.TmdbAttribution
import org.njarasoa.fijerena.feature.settings.components.TmdbKeyDialog
import org.njarasoa.fijerena.feature.settings.components.TmdbKeySettingsRow
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.components.mobileClockTopBarInsets
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings as one grouped preference list: Your Profile (the personal rows of the profile in use,
 * in place) and About, which everyone opens. With Expert mode an Admin row between them opens one
 * page with every Expert group after the PIN (the admin settings plan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    // Null when Settings is a tab root (no back arrow).
    onBack: (() -> Unit)?,
    onManageProviders: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onDeviceInfo: () -> Unit = {},
    onLicenses: () -> Unit = {},
    onTerms: () -> Unit = {},
    onLiveSync: () -> Unit = {},
    onGuideSources: () -> Unit = {},
    /** Admin → Profiles: a profile's page with its Admin rows too. */
    onAdminProfile: (profileId: String) -> Unit = {},
    onParentalControls: () -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    // A profile just added opens its page (sources, filters, PIN are the next things to set) once
    // the list shows it — its page closes itself on a list without it.
    var openWhenListed by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(profiles, openWhenListed) {
        if (profiles.any { it.id == openWhenListed }) {
            openWhenListed?.let(onAdminProfile)
            openWhenListed = null
        }
    }
    val profilesMessage by profilesViewModel.message.collectAsStateWithLifecycle()
    // Settings opens without the PIN; Admin (shared setup, this device, the list of profiles) asks
    // the PIN of the profile in use once, on the way into its page.
    val inUse by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    var guardedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guarded: (() -> Unit) -> Unit = { action ->
        if (inUse?.let { profilesViewModel.needsPin(it.id) } == true) guardedAction = action else action()
    }
    guardedAction?.let { action ->
        inUse?.let { profile ->
            PinDialog(
                state =
                    rememberPinPromptState(
                        profilesViewModel.pins,
                        PinRequest.Check(profile.id, stringResource(R.string.pin_enter_for, profile.name)),
                    ) {
                        guardedAction = null
                        action()
                    },
                onCancel = { guardedAction = null },
            )
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Guide auto-refresh and maintenance are device-wide, so no provider id (A-9).
    val epgViewModel: EpgManagementViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val tmdbKeyViewModel: TmdbKeyViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val lifecycleOwner = LocalLifecycleOwner.current
    // Back from a profile's page: the profile in use may have had its developer mode changed; a
    // TMDB 401 elsewhere may have turned the key's status to refused.
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            viewModel.refreshDevMode()
            tmdbKeyViewModel.refresh()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            epgViewModel.userMessage.collect { message ->
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

    var showLanguagePicker by remember { mutableStateOf(false) }
    var showWatchDelayPicker by remember { mutableStateOf(false) }
    var showRecentSizePicker by remember { mutableStateOf(false) }
    if (showRecentSizePicker) {
        // The TV's presets; a size kept from a source before the move stays as its own choice.
        val sizes = (RECENT_ROW_SIZES + uiState.recentRowSize).distinct().sorted()
        SettingsPickerDialog(
            title = stringResource(R.string.settings_recent_row_size_title),
            options = sizes.map { it.toString() to it },
            selected = uiState.recentRowSize,
            onSelect = { size ->
                viewModel.updateRecentRowSize(size)
                showRecentSizePicker = false
            },
            onDismiss = { showRecentSizePicker = false },
        )
    }
    var showTmdbKeyDialog by remember { mutableStateOf(false) }

    if (showTmdbKeyDialog) {
        TmdbKeyDialog(viewModel = tmdbKeyViewModel, onDismiss = { showTmdbKeyDialog = false })
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

    // Admin open as its own page, in place of the list; Back returns to the list. A language change
    // (Activity recreate) and the round trip through a screen it opens come back to it. It opens
    // after the PIN of the profile in use, once per session (PinSession); the rows inside ask nothing
    // more.
    var adminOpen by rememberSaveable { mutableStateOf(false) }
    // Expert mode turned off, or the session locked again (a long time in the background): back to
    // the list, so the Admin rows are not open without the PIN.
    val adminLocked = adminOpen && (!uiState.isDevMode || inUse?.let { profilesViewModel.needsPin(it.id) } == true)
    LaunchedEffect(adminLocked) { if (adminLocked) adminOpen = false }
    BackHandler(enabled = adminOpen) { adminOpen = false }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = mobileClockTopBarInsets(),
                title = {
                    CinemaText(
                        stringResource(if (adminOpen) R.string.settings_group_admin else R.string.settings_title),
                        role = TextRole.Title,
                    )
                },
                navigationIcon = {
                    val back = if (adminOpen) ({ adminOpen = false }) else onBack
                    if (back != null) {
                        IconButton(onClick = back) {
                            Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                        }
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
            contentPadding = PaddingValues(bottom = Spacing.lg + LocalDockInset.current),
        ) {
            if (!adminOpen) {
                // === Your Profile === first, since it decides whose favourites and history the rest
                // is about: the personal rows of the profile in use, in place, no PIN; Add Profile
                // for a grown-up that is not an admin.
                item {
                    SettingsGroupHeader(stringResource(R.string.profile_own_page_title))
                    inUse?.let { profile -> key(profile.id) { YourProfileRows(profile, profilesViewModel) } }
                    ProfilesRows(profilesViewModel, profiles, profilesMessage, uiState, admin = false, onEditProfile = {}) {
                        openWhenListed = it
                    }
                }

                // With Expert mode: Admin, every Expert group on one page.
                if (uiState.isDevMode) {
                    item {
                        SettingsListRow(title = stringResource(R.string.settings_group_admin), onClick = { guarded { adminOpen = true } })
                    }
                }

                // === About ===
                item {
                    SettingsGroupHeader(stringResource(R.string.settings_about_section_title))
                    // Tapping the version 7 times turns Expert mode on while no profile is Expert (DeveloperModeTaps).
                    val onVersionClick = rememberDeveloperModeTapHandler(uiState.expertExists, viewModel::enableDevMode)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onVersionClick)
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) {
                        CinemaText(stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME))
                        CinemaText(
                            stringResource(R.string.app_disclaimer_no_content),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                        // GPL-3.0 "Appropriate Legal Notices": copyright, licence, no warranty, source.
                        CinemaText(stringResource(R.string.app_copyright), role = TextRole.BodySmall, tone = TextTone.Secondary)
                        CinemaText(stringResource(R.string.app_license_notice), role = TextRole.BodySmall, tone = TextTone.Secondary)
                        CinemaText(
                            stringResource(R.string.settings_about_build_format, BuildConfig.GIT_HASH),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                        CinemaText(
                            stringResource(R.string.settings_about_built_format, BuildConfig.BUILD_TIME),
                            role = TextRole.BodySmall,
                            tone = TextTone.Secondary,
                        )
                    }
                    SettingsListRow(
                        title = stringResource(R.string.device_info_open),
                        summary = stringResource(R.string.device_info_desc),
                        onClick = onDeviceInfo,
                    )
                    // Logs (code name Diagnostics): read-only, for everyone who has Settings.
                    SettingsListRow(
                        title = stringResource(R.string.settings_diagnostics_title),
                        summary = stringResource(R.string.settings_diagnostics_desc),
                        onClick = onDiagnostics,
                    )
                    SettingsListRow(
                        title = stringResource(R.string.terms_title),
                        summary = stringResource(R.string.terms_desc),
                        onClick = onTerms,
                    )
                    SettingsListRow(
                        title = stringResource(R.string.licenses_title),
                        summary = stringResource(R.string.licenses_desc),
                        onClick = onLicenses,
                    )
                    TmdbAttribution()
                }
            } else {
                // Admin: each Expert group under its own header, in TV's rail order.
                ExpertGroup.visibleFor(admin = true).forEach { group ->
                    item { SettingsGroupHeader(stringResource(group.titleRes)) }
                    when (group) {
                        // The list of every profile; a profile's row opens its page, with its Admin rows.
                        ExpertGroup.PROFILES -> {
                            item {
                                ProfilesRows(profilesViewModel, profiles, profilesMessage, uiState, admin = true, onAdminProfile) {
                                    openWhenListed = it
                                }
                            }
                        }

                        // Its own screen.
                        ExpertGroup.PARENTAL_CONTROLS -> {
                            item {
                                SettingsListRow(
                                    title = stringResource(R.string.parental_controls_title),
                                    summary = stringResource(R.string.profile_parental_controls_desc),
                                    onClick = onParentalControls,
                                )
                            }
                        }

                        // This device's settings.
                        ExpertGroup.THIS_DEVICE -> {
                            item {
                                SettingsListRow(
                                    title = stringResource(R.string.settings_language),
                                    summary =
                                        when (uiState.language) {
                                            "en" -> stringResource(R.string.settings_language_en)
                                            "mg" -> stringResource(R.string.settings_language_mg)
                                            "fr" -> stringResource(R.string.settings_language_fr)
                                            else -> uiState.language
                                        },
                                    onClick = { showLanguagePicker = true },
                                )
                                SettingsListRow(
                                    title = stringResource(R.string.settings_recent_row_size_title),
                                    summary = uiState.recentRowSize.toString(),
                                    onClick = { showRecentSizePicker = true },
                                )
                                SettingsListRow(
                                    title = stringResource(R.string.settings_watch_delay_row_title),
                                    summary = stringResource(R.string.settings_seconds_short_format, uiState.watchDelaySeconds),
                                    onClick = { showWatchDelayPicker = true },
                                )
                                SettingsListRow(
                                    title = stringResource(R.string.settings_show_clock_title),
                                    summary = stringResource(R.string.settings_show_clock_desc),
                                    trailing = { CinemaSwitch(checked = uiState.showClock, onCheckedChange = viewModel::updateShowClock) },
                                    onClick = { viewModel.updateShowClock(!uiState.showClock) },
                                )
                            }
                        }

                        ExpertGroup.SOURCE_GUIDE -> {
                            item {
                                // Switching, editing and adding sources live on the Sources list (D6).
                                SettingsListRow(
                                    title = stringResource(R.string.settings_provider_manage_button),
                                    summary = manageSourcesSummary(uiState),
                                    onClick = onManageProviders,
                                )
                                // Only sources with live channels have guide sources: one page for all of
                                // them (the one guide sources page plan), shown when the device has one.
                                if (uiState.hasGuideSources) {
                                    SettingsListRow(
                                        title = stringResource(R.string.epg_management_screen_title),
                                        summary = stringResource(R.string.settings_guide_sources_desc),
                                        onClick = onGuideSources,
                                    )
                                }
                                GuideMaintenanceRow(viewModel = epgViewModel)
                                TmdbKeySettingsRow(viewModel = tmdbKeyViewModel, onOpen = { showTmdbKeyDialog = true })
                                SettingsListRow(
                                    title = stringResource(R.string.tmdb_charts_title),
                                    summary = stringResource(R.string.tmdb_charts_desc),
                                    trailing = {
                                        CinemaSwitch(
                                            checked = uiState.showTmdbCharts,
                                            onCheckedChange = viewModel::updateShowTmdbCharts,
                                        )
                                    },
                                    onClick = { viewModel.updateShowTmdbCharts(!uiState.showTmdbCharts) },
                                )
                            }
                        }

                        // Shared setup and backups (with source logins).
                        ExpertGroup.SYNC_BACKUP -> {
                            item {
                                LiveSyncRow(onOpen = onLiveSync)
                                SettingsListRow(
                                    title = stringResource(R.string.settings_export_button),
                                    summary = stringResource(R.string.settings_export_import_desc),
                                    trailing = {},
                                    onClick = { exportLauncher.launch("fijerena_settings.json") },
                                )
                                SettingsListRow(
                                    title = stringResource(R.string.settings_import_button),
                                    summary = stringResource(R.string.settings_import_desc),
                                    trailing = {},
                                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                                )
                                // Only when there is a backup in Downloads to restore (a developer's shortcut).
                                val quickImportReady = remember { exportManager.getQuickImportPath() != null }
                                if (quickImportReady) {
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
                            }
                        }
                    }
                }
                // The last row, under no header of its own.
                item {
                    // Housekeeping: the catalogue's leftovers. Shrink Database ends the Admin page.
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
                        trailing = {
                            if (uiState.isPruningDatabase) {
                                CinemaSpinner(modifier = Modifier.size(MobileDimensions.progressIndicatorSmall))
                            }
                        },
                        onClick = viewModel::pruneDatabase,
                        enabled = !uiState.isPruningDatabase,
                    )
                    uiState.databaseMaintenanceMessage?.let { SettingsMessage(it) }
                    // Only when the last run found something: "0 records deleted" says nothing.
                    if (uiState.lastShrinkAtMs > 0L &&
                        (uiState.lastShrinkRowsRemoved > 0 || uiState.lastShrinkBytesReclaimed > 0)
                    ) {
                        val time = NumberUtils.formatTimestamp(LocalContext.current, uiState.lastShrinkAtMs)
                        val duration = NumberUtils.formatDuration(uiState.lastShrinkDurationMs)
                        SettingsMessage(stringResource(R.string.settings_shrink_database_dev_stats_time, time, duration))
                        val bytesStr = NumberUtils.formatBytes(uiState.lastShrinkBytesReclaimed)
                        SettingsMessage(
                            stringResource(
                                R.string.settings_shrink_database_dev_stats_delta,
                                uiState.lastShrinkRowsRemoved,
                                bytesStr,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The phone's Expert groups, in TV's rail order: each one a section of the Admin page, which opens
 * after the PIN (the admin settings plan). Only Expert mode lists them.
 */
enum class ExpertGroup(
    @StringRes val titleRes: Int,
) {
    // With Expert mode, the list of every profile (SG-D2).
    PROFILES(R.string.settings_profiles_title),
    PARENTAL_CONTROLS(R.string.parental_controls_title),
    THIS_DEVICE(R.string.settings_group_this_device),
    SOURCE_GUIDE(R.string.settings_group_source_guide),
    SYNC_BACKUP(R.string.settings_group_sync_backup),
    ;

    companion object {
        /** The Expert groups Settings lists: all of them with Expert mode ([admin]), else none. */
        fun visibleFor(admin: Boolean): List<ExpertGroup> = if (admin) entries else emptyList()
    }
}

/**
 * Profiles' rows: on the Admin page ([admin]) every profile, else none (Settings shows the viewer's
 * own rows in place). Add Profile
 * for any grown-up, with no PIN (the profile rules plan, PR-D2), an Expert's in Admin; only an Expert
 * edits another profile, so only an Expert's new profile opens its page ([onAdded]).
 */
@Composable
private fun ProfilesRows(
    profilesViewModel: ProfilesViewModel,
    profiles: List<ProfileUi>,
    message: String?,
    uiState: SettingsUiState,
    admin: Boolean,
    onEditProfile: (String) -> Unit,
    onAdded: (String) -> Unit,
) {
    ProfilesSettingsRows(
        profiles = profiles,
        message = message,
        newProfileColorIndex = profilesViewModel::nextFreeColorIndex,
        onAdd = { name, colorIndex, child ->
            profilesViewModel.addProfile(name, colorIndex, child) { if (uiState.isDevMode) onAdded(it) }
        },
        onEdit = onEditProfile,
        onDismissMessage = profilesViewModel::clearMessage,
        expertMode = admin,
        canAddProfile = !uiState.isChildProfile && admin == uiState.isDevMode,
        pinNudge = profilesViewModel.pins.takeIf { profilesViewModel.needsAdminPin() },
        pinNudgeLastExpert = profiles.firstOrNull { it.isActive }?.let { profilesViewModel.isLastExpert(it.id) } == true,
        noAdminPin = profilesViewModel.adminPinHolders().isEmpty(),
    )
}

/** Live sync's line in Settings: whether it's on, and the way into its own screen. */
@Composable
private fun LiveSyncRow(onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val status by SyncManager.getInstance(app).status.collectAsStateWithLifecycle()
    SettingsListRow(
        title = stringResource(R.string.live_sync_title),
        summary = liveSyncStatusLine(status),
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
                    CinemaTextField(
                        value = customText,
                        onValueChange = { newValue ->
                            customText = newValue
                            newValue.toIntOrNull()?.let(onSecondsChange)
                        },
                        label = stringResource(R.string.settings_watch_delay_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText =
                            stringResource(
                                R.string.settings_watch_delay_range_format,
                                AppSettings.MIN_WATCH_DELAY_SECONDS,
                                AppSettings.MAX_WATCH_DELAY_SECONDS,
                            ),
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun SettingsMessage(text: String) {
    CinemaText(
        text,
        role = TextRole.BodySmall,
        tone = TextTone.Secondary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
    )
}

private val WATCH_DELAY_PRESETS = listOf(5, 10, 15, 30, 60, 120)
private const val WATCH_DELAY_CUSTOM = -1

/** Manage sources' value: the profile's sources; their details are on the Sources list. */
@Composable
private fun manageSourcesSummary(uiState: SettingsUiState): String =
    uiState.profileSources.joinToString { it.second }.ifEmpty { stringResource(R.string.provider_none_label) }

// Display's Items in Recent presets, as on TV; 25 (AppSettings.DEFAULT_WATCH_HISTORY_SIZE) is one.
private val RECENT_ROW_SIZES = listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100)
