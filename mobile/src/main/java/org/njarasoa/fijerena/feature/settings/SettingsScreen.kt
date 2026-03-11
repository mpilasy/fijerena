package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onManageEpg: () -> Unit = {},
    onCellularBuffers: () -> Unit = {},
    onProviderChanged: () -> Unit
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
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> pendingExportUri = uri }

    // SAF launcher for import (open file)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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
        parseResult.onSuccess { parsed ->
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
        parseResult.onSuccess { parsed ->
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
        val parsed = pendingParsedImport!!
        var optProviders by remember { mutableStateOf(pendingImportOptions.importProviders) }
        var optEpg by remember { mutableStateOf(pendingImportOptions.importEpgSources) }
        var optGlobal by remember { mutableStateOf(pendingImportOptions.importGlobalSettings) }
        var optFavorites by remember { mutableStateOf(pendingImportOptions.importFavorites) }
        AlertDialog(
            onDismissRequest = {
                showImportOptionsDialog = false
                if (!showConflictDialog) pendingParsedImport = null
            },
            title = { Text("Select What to Import") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = optGlobal, onCheckedChange = { optGlobal = it })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text("General Settings", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (parsed.hasProviders) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = optProviders, onCheckedChange = { optProviders = it })
                            Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                            Text("Providers (${parsed.settings.providers.size})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (parsed.hasEpgSources) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = optEpg, onCheckedChange = { optEpg = it })
                            Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                            Text("EPG Sources (${parsed.settings.epgSources.size})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (parsed.hasFavorites) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = optFavorites, onCheckedChange = { optFavorites = it })
                            Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                            Text("Favorites", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val options = SettingsExportManager.ImportOptions(
                        importProviders = optProviders,
                        importEpgSources = optEpg,
                        importGlobalSettings = optGlobal,
                        importFavorites = optFavorites
                    )
                    pendingImportOptions = options
                    val p = pendingParsedImport!!
                    if (optProviders && parsed.hasConflicts) {
                        showConflictDialog = true
                        showImportOptionsDialog = false
                    } else {
                        pendingParsedImport = null
                        showImportOptionsDialog = false
                        viewModel.doImport(p, SettingsExportManager.ConflictResolution.SKIP, options)
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showImportOptionsDialog = false
                    pendingParsedImport = null
                }) { Text("Cancel") }
            }
        )
    }

    // Conflict resolution dialog
    if (showConflictDialog && pendingParsedImport != null) {
        val conflicts = pendingParsedImport!!.conflictingProviders
        AlertDialog(
            onDismissRequest = {
                showConflictDialog = false
                pendingParsedImport = null
            },
            title = { Text("Provider Conflict") },
            text = {
                Text(
                    "The following provider(s) already exist:\n\n" +
                        conflicts.joinToString("\n") { "• $it" } +
                        "\n\nWhat would you like to do?"
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)
                    ) {
                        Button(
                            onClick = {
                                showConflictDialog = false
                                val parsed = pendingParsedImport!!
                                val options = pendingImportOptions
                                pendingParsedImport = null
                                viewModel.doImport(parsed, SettingsExportManager.ConflictResolution.OVERWRITE, options)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Overwrite", maxLines = 1) }
                        Button(
                            onClick = {
                                showConflictDialog = false
                                val parsed = pendingParsedImport!!
                                val options = pendingImportOptions
                                pendingParsedImport = null
                                viewModel.doImport(parsed, SettingsExportManager.ConflictResolution.DUPLICATE, options)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Duplicate", maxLines = 1) }
                    }
                    Button(
                        onClick = {
                            showConflictDialog = false
                            val parsed = pendingParsedImport!!
                            val options = pendingImportOptions
                            pendingParsedImport = null
                            viewModel.doImport(parsed, SettingsExportManager.ConflictResolution.SKIP, options)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Skip Duplicates") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConflictDialog = false
                    pendingParsedImport = null
                }) { Text("Cancel") }
            }
        )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
        ) {
            // === Provider ===
            SettingsSection(title = "Provider") {
                Text(
                    text = uiState.providerName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = uiState.currentUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                if (uiState.subscriptionExpiry != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isExpired = uiState.subscriptionStatus?.equals("Expired", ignoreCase = true) == true
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Expires", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow))
                        Text(
                            text = uiState.subscriptionExpiry!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (uiState.subscriptionMaxCons != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Max connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow))
                            Text(uiState.subscriptionMaxCons!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (uiState.subscriptionIsTrial) {
                        Text("Trial account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onManageProviders,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Providers")
                }
            }

            // === Playback ===
            SettingsSection(title = "Playback") {
                var watchDelayText by remember(uiState.watchDelaySeconds) { 
                    mutableStateOf(uiState.watchDelaySeconds.toString()) 
                }
                Text(
                    text = "How long to watch a channel before it's added to Last Watched",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = watchDelayText,
                    onValueChange = { newValue ->
                        watchDelayText = newValue
                        newValue.toIntOrNull()?.let { seconds ->
                            viewModel.updateWatchDelay(seconds)
                        }
                    },
                    label = { Text("Watch delay (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("${AppSettings.MIN_WATCH_DELAY_SECONDS}–${AppSettings.MAX_WATCH_DELAY_SECONDS} seconds")
                    }
                )
            }

            // === Theme ===
            SettingsSection(title = "Theme") {
                Text(
                    text = "Select a color theme for the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AllPalettes.chunked(2).forEach { rowPalettes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPalettes.forEach { palette ->
                                val isSelected = uiState.themeId == palette.id
                                if (isSelected) {
                                    Button(
                                        onClick = { },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(palette.displayName, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateTheme(palette.id)
                                            onThemeChanged(palette.id)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(palette.displayName, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // === EPG Data ===
            SettingsSection(title = "EPG Data") {
                val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
                val indexState by epgIndexer.state.collectAsStateWithLifecycle()
                var sourceCount by remember { mutableStateOf(0) }
                LaunchedEffect(uiState.epgRefreshTrigger) {
                    sourceCount = epgIndexer.getSourceCount()
                }
                val summaryText = when (val idx = indexState) {
                    is EpgIndexState.Indexed -> "${formatProgrammeCount(idx.channelCount)} channels, ${formatProgrammeCount(idx.programmeCount)} programmes"
                    is EpgIndexState.Indexing -> "Indexing: ${idx.progressPercent}%"
                    is EpgIndexState.NotIndexed -> if (sourceCount > 0) "$sourceCount source(s) configured, not yet indexed" else "No sources configured"
                    is EpgIndexState.Failed -> "Error: ${idx.reason}"
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                Button(
                    onClick = onManageEpg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage EPG Data")
                }
            }

            // === Developer Mode ===
            SettingsSection(title = "Developer Mode") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable stats for nerds and debug features",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = uiState.isDevMode,
                        onCheckedChange = { enabled ->
                            viewModel.updateDevMode(enabled)
                        }
                    )
                }

                if (uiState.isDevMode) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Button(
                        onClick = onCellularBuffers,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configure Cellular Buffers")
                    }
                }
            }

            // === Cloud Sync (Google Drive) ===
            SettingsSection(title = "Cloud Sync") {
                Text(
                    text = "Sync provider settings across devices using your Google account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (signedInEmail != null) {
                    // Signed in: show account + sync controls
                    Text(
                        text = signedInEmail ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = when (syncStatus) {
                        is DriveSettingsSyncManager.SyncStatus.Syncing -> "Syncing..."
                        is DriveSettingsSyncManager.SyncStatus.Synced -> "Synced"
                        is DriveSettingsSyncManager.SyncStatus.Error ->
                            "Error: ${(syncStatus as DriveSettingsSyncManager.SyncStatus.Error).message}"
                        else -> "Ready"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (syncStatus) {
                            is DriveSettingsSyncManager.SyncStatus.Synced -> MaterialTheme.colorScheme.primary
                            is DriveSettingsSyncManager.SyncStatus.Error -> CinemaError
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { coroutineScope.launch { syncManager.syncNow() } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync Now")
                        }
                        OutlinedButton(
                            onClick = { coroutineScope.launch { syncManager.signOut() } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out")
                        }
                    }
                } else {
                    // Not signed in: show sign-in button
                    Button(
                        onClick = {
                            signInError = null
                            signInLauncher.launch(syncManager.getSignInIntent())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign in with Google")
                    }
                    if (signInError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = signInError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaError
                        )
                    }
                }
            }

            // === Export / Import ===
            SettingsSection(title = "Export / Import") {
                Text(
                    text = "Export all settings to a file or import from another device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("fijerena_settings.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val path = exportManager.getQuickImportPath()
                        if (path != null) {
                            pendingImportPath = path
                        } else {
                            viewModel.setExportImportMessage("Settings file not found in Downloads or app folder")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Quick Import from Downloads")
                }
                if (uiState.exportImportMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.exportImportMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                    )
                }
            }

            // === About ===
            SettingsSection(title = "About") {
                Text(
                    text = "Fijerena v1.0.0",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Premium native media player for Android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(CinemaSpacing.md)) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                content()
            }
        }
    }
}

private fun formatProgrammeCount(count: Int): String {
    return when {
        count >= 1000 -> "%.1fk".format(count / 1000.0)
        else -> count.toString()
    }
}
