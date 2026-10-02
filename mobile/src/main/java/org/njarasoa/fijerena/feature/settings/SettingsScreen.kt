package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.AboutSettingsCard
import org.njarasoa.fijerena.feature.settings.components.DatabaseMaintenanceCard
import org.njarasoa.fijerena.feature.settings.components.DeveloperSettingsCard
import org.njarasoa.fijerena.feature.settings.components.EpgSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ExportImportSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ImportConflictDialog
import org.njarasoa.fijerena.feature.settings.components.ImportOptionsDialog
import org.njarasoa.fijerena.feature.settings.components.LanguageSettingsCard
import org.njarasoa.fijerena.feature.settings.components.LiveSyncSettingsCard
import org.njarasoa.fijerena.feature.settings.components.PlaybackSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ProfilesSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ProviderSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ThemeSettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onUiStyleChanged: (String) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onCellularBuffers: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onLiveSync: () -> Unit = {},
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

    // Import options dialog
    if (showImportOptionsDialog && pendingParsedImport != null) {
        ImportOptionsDialog(
            parsed = pendingParsedImport!!,
            initialOptions = pendingImportOptions,
            onDismiss = {
                showImportOptionsDialog = false
                if (!showConflictDialog) pendingParsedImport = null
            },
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
        )
    }

    // Conflict resolution dialog
    if (showConflictDialog && pendingParsedImport != null) {
        ImportConflictDialog(
            conflicts = pendingParsedImport!!.conflictingProviders,
            onDismiss = {
                showConflictDialog = false
                pendingParsedImport = null
            },
            onOverwrite = {
                showConflictDialog = false
                val parsed = pendingParsedImport!!
                val options = pendingImportOptions
                pendingParsedImport = null
                viewModel.doImport(parsed, SettingsExportManager.ConflictResolution.OVERWRITE, options)
            },
            onDuplicate = {
                showConflictDialog = false
                val parsed = pendingParsedImport!!
                val options = pendingImportOptions
                pendingParsedImport = null
                viewModel.doImport(parsed, SettingsExportManager.ConflictResolution.DUPLICATE, options)
            },
            onSkip = {
                showConflictDialog = false
                val parsed = pendingParsedImport!!
                val options = pendingImportOptions
                pendingParsedImport = null
                viewModel.doImport(parsed, SettingsExportManager.ConflictResolution.SKIP, options)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            // === Profiles === first, since it decides whose favourites and history the rest is about.
            ProfilesSettingsCard(
                profiles = profiles,
                message = profilesMessage,
                newProfileColorIndex = profilesViewModel::nextFreeColorIndex,
                onAdd = profilesViewModel::addProfile,
                onUpdate = profilesViewModel::updateProfile,
                onDelete = profilesViewModel::deleteProfile,
                onDismissMessage = profilesViewModel::clearMessage,
            )

            // === Provider ===
            ProviderSettingsCard(
                uiState = uiState,
                onManageProviders = onManageProviders,
            )

            // === Playback ===
            PlaybackSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
            )

            // === Theme ===
            ThemeSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
                onThemeChanged = onThemeChanged,
                onUiStyleChanged = onUiStyleChanged,
            )

            // === Language ===
            LanguageSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
            )

            // === EPG Data ===
            EpgSettingsCard(
                context = context,
                uiState = uiState,
            )

            // === Developer Mode ===
            DeveloperSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
                onCellularBuffers = onCellularBuffers,
                onDiagnostics = onDiagnostics,
            )

            // === Live sync ===
            LiveSyncSettingsCard(onOpen = onLiveSync)

            // === Export / Import ===
            ExportImportSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
                exportManager = exportManager,
                exportLauncher = exportLauncher,
                importLauncher = importLauncher,
                onPendingImportPathChange = { path ->
                    coroutineScope.launch {
                        val parseResult = exportManager.parseImportPath(path)
                        parseResult
                            .onSuccess { parsed ->
                                pendingParsedImport = parsed
                                pendingImportOptions = SettingsExportManager.ImportOptions()
                                showImportOptionsDialog = true
                            }.onFailure { e ->
                                viewModel.setExportImportMessage(resources.getString(R.string.settings_import_failed, e.message ?: ""))
                            }
                    }
                },
            )

            // === Database Maintenance ===
            DatabaseMaintenanceCard(
                uiState = uiState,
                viewModel = viewModel,
            )

            // === About ===
            AboutSettingsCard()
        }
    }
}
