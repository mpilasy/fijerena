package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.ui.theme.MobileDimensions

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

    LaunchedEffect(Unit) {
        val activeProvider = providerRepo.getActiveProvider()
        providerName = activeProvider?.name ?: "No provider"
        currentUrl = activeProvider?.url ?: ""
        currentUsername = activeProvider?.username ?: ""
        activeProviderId = activeProvider?.id
    }

    // Global settings
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    var selectedThemeId by remember { mutableStateOf(appSettings.themeId) }

    // Export/Import state
    var exportImportMessage by remember { mutableStateOf<String?>(null) }
    var epgRefreshTrigger by remember { mutableStateOf(0) }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
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

    // Parse import file and show options dialog
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

    // Helper to perform import and refresh UI state
    fun doImport(parsed: SettingsExportManager.ParsedImport, resolution: SettingsExportManager.ConflictResolution, options: SettingsExportManager.ImportOptions) {
        coroutineScope.launch {
            val result = exportManager.importFromParsed(parsed, resolution, options)
            exportImportMessage = result.toSummary()
            if (result.isSuccess) {
                if (options.importGlobalSettings) {
                    selectedThemeId = appSettings.themeId
                    onThemeChanged(appSettings.themeId)
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

    // Import options dialog (selective import)
    if (showImportOptionsDialog && pendingParsedImport != null) {
        val parsed = pendingParsedImport!!
        var optProviders by remember { mutableStateOf(pendingImportOptions.importProviders) }
        var optEpg by remember { mutableStateOf(pendingImportOptions.importEpgSources) }
        var optGlobal by remember { mutableStateOf(pendingImportOptions.importGlobalSettings) }
        var optFavorites by remember { mutableStateOf(pendingImportOptions.importFavorites) }
        AlertDialog(
            onDismissRequest = {
                showImportOptionsDialog = false
                // Only clean up if not transitioning to conflict dialog
                if (!showConflictDialog) {
                    pendingParsedImport = null
                }
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
                        doImport(p, SettingsExportManager.ConflictResolution.SKIP, options)
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
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    Button(onClick = {
                        showConflictDialog = false
                        val parsed = pendingParsedImport!!
                        val options = pendingImportOptions
                        pendingParsedImport = null
                        doImport(parsed, SettingsExportManager.ConflictResolution.OVERWRITE, options)
                    }) { Text("Overwrite") }
                    Button(onClick = {
                        showConflictDialog = false
                        val parsed = pendingParsedImport!!
                        val options = pendingImportOptions
                        pendingParsedImport = null
                        doImport(parsed, SettingsExportManager.ConflictResolution.DUPLICATE, options)
                    }) { Text("Duplicate") }
                }
            },
            dismissButton = {
                Button(onClick = {
                    showConflictDialog = false
                    val parsed = pendingParsedImport!!
                    val options = pendingImportOptions
                    pendingParsedImport = null
                    doImport(parsed, SettingsExportManager.ConflictResolution.SKIP, options)
                }) { Text("Skip") }
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
                    text = providerName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = currentUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
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
                var watchDelayText by remember { mutableStateOf(appSettings.watchDelaySeconds.toString()) }
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
                            appSettings.watchDelaySeconds = seconds
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
                                val isSelected = selectedThemeId == palette.id
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
                                            selectedThemeId = palette.id
                                            appSettings.themeId = palette.id
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
                LaunchedEffect(epgRefreshTrigger) {
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
                        checked = isDevMode,
                        onCheckedChange = { enabled ->
                            isDevMode = enabled
                            appSettings.isDevMode = enabled
                        }
                    )
                }

                if (isDevMode) {
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
                if (exportImportMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exportImportMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                    )
                }
            }

            // === About ===
            SettingsSection(title = "About") {
                Text(
                    text = "Fijerena v${org.njarasoa.fijerena.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                Text(
                    text = "Build: ${org.njarasoa.fijerena.BuildConfig.GIT_HASH}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                )
                Text(
                    text = "Built: ${org.njarasoa.fijerena.BuildConfig.BUILD_TIME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CinemaSpacing.md)
        ) {
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

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaError
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatEpgFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatTimestamp(millis: Long): String {
    val format = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    format.timeZone = java.util.TimeZone.getDefault()
    return format.format(java.util.Date(millis))
}

private fun formatProgrammeCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun CategoryFilterDialog(
    currentFilters: CategoryFilters,
    onSave: (CategoryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(currentFilters.mode) }
    var prefixesText by remember { mutableStateOf(currentFilters.prefixes.joinToString(", ")) }
    var selectedScripts by remember { mutableStateOf(currentFilters.allowedScripts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category Filters") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Filter mode:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == FilterMode.EXCLUDE,
                        onClick = { mode = FilterMode.EXCLUDE },
                        label = { Text("Exclude") }
                    )
                    FilterChip(
                        selected = mode == FilterMode.INCLUDE,
                        onClick = { mode = FilterMode.INCLUDE },
                        label = { Text("Include Only") }
                    )
                }
                Text(
                    text = if (mode == FilterMode.EXCLUDE)
                        "Categories starting with these prefixes will be hidden"
                    else
                        "Only categories starting with these prefixes will be shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                OutlinedTextField(
                    value = prefixesText,
                    onValueChange = { prefixesText = it },
                    label = { Text("Prefixes (comma-separated)") },
                    placeholder = { Text("Adult, XXX, 18+") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
                Text(
                    text = "Language Script Filter:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Show only categories in selected scripts (none = show all)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                ScriptType.entries.forEach { script ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = script in selectedScripts,
                            onCheckedChange = { checked ->
                                selectedScripts = if (checked) {
                                    selectedScripts + script
                                } else {
                                    selectedScripts - script
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = script.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prefixes = prefixesText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSave(CategoryFilters(
                        mode = mode,
                        prefixes = prefixes,
                        allowedScripts = selectedScripts
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

