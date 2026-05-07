package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.ImportConflictDialog
import org.njarasoa.fijerena.feature.settings.components.ImportOptionsDialog
import org.njarasoa.fijerena.feature.settings.components.PlaybackSettingsCard
import org.njarasoa.fijerena.feature.settings.components.AboutSettingsCard
import org.njarasoa.fijerena.feature.settings.components.CloudSyncSettingsCard
import org.njarasoa.fijerena.feature.settings.components.DeveloperSettingsCard
import org.njarasoa.fijerena.feature.settings.components.EpgSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ExportImportSettingsCard
import org.njarasoa.fijerena.feature.settings.components.ProviderSettingsCard
import org.njarasoa.fijerena.feature.settings.components.SettingsSection
import org.njarasoa.fijerena.feature.settings.components.ThemeSettingsCard
import org.njarasoa.fijerena.feature.settings.components.formatProgrammeCount
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onManageEpg: () -> Unit = {},
    onCellularBuffers: () -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val syncManager = remember { DriveSettingsSyncManager(context.applicationContext, providerRepo) }
    val exportManager = remember { SettingsExportManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    // Export/Import transient state
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

    // Process export in LaunchedEffect
    LaunchedEffect(pendingExportUri) {
        val uri = pendingExportUri ?: return@LaunchedEffect
        val success = exportManager.exportToUri(uri)
        viewModel.setExportImportMessage(if (success) "Settings exported successfully" else "Export failed")
        pendingExportUri = null
    }

    // Parse import file (URI)
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportUri(uri)
        parseResult
            .onSuccess { parsed ->
                pendingParsedImport = parsed
                pendingImportOptions = SettingsExportManager.ImportOptions()
                showImportOptionsDialog = true
            }.onFailure { e ->
                viewModel.setExportImportMessage("Import failed: ${e.message}")
            }
        pendingImportUri = null
    }

    // Parse import file (Path)
    LaunchedEffect(pendingImportPath) {
        val path = pendingImportPath ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportPath(path)
        parseResult
            .onSuccess { parsed ->
                pendingParsedImport = parsed
                pendingImportOptions = SettingsExportManager.ImportOptions()
                showImportOptionsDialog = true
            }.onFailure { e ->
                viewModel.setExportImportMessage("Import failed: ${e.message}")
            }
        pendingImportPath = null
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
            }
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
            }
        )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
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
            )

            // === EPG Data ===
            EpgSettingsCard(
                context = context,
                uiState = uiState,
                onManageEpg = onManageEpg,
            )

            // === Developer Mode ===
            DeveloperSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
                onCellularBuffers = onCellularBuffers,
            )

            // === Cloud Sync (Google Drive) ===
            CloudSyncSettingsCard(
                signedInEmail = signedInEmail,
                syncStatus = syncStatus,
                signInError = signInError,
                onSignInErrorChange = { signInError = it },
                syncManager = syncManager,
                coroutineScope = coroutineScope,
                signInLauncher = signInLauncher,
            )

            // === Export / Import ===
            ExportImportSettingsCard(
                uiState = uiState,
                viewModel = viewModel,
                exportManager = exportManager,
                exportLauncher = exportLauncher,
                importLauncher = importLauncher,
                onPendingImportPathChange = { pendingImportPath = it },
            )

            // === About ===
            AboutSettingsCard()
        }
    }
}
