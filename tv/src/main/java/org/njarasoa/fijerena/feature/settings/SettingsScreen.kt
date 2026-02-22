@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
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
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale

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

    // Import options dialog (selective import)
    if (showImportOptionsDialog && pendingParsedImport != null) {
        // Handle Back button to close dialog
        androidx.activity.compose.BackHandler {
            showImportOptionsDialog = false
            if (!showConflictDialog) {
                pendingParsedImport = null
            }
        }

        val parsed = pendingParsedImport!!
        var optProviders by remember { mutableStateOf(pendingImportOptions.importProviders) }
        var optEpg by remember { mutableStateOf(pendingImportOptions.importEpgSources) }
        var optGlobal by remember { mutableStateOf(pendingImportOptions.importGlobalSettings) }
        var optFavorites by remember { mutableStateOf(pendingImportOptions.importFavorites) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = CinemaAlpha.overlayHeavy)),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(
                modifier = Modifier
                    .width(TvDimensions.dialogWidth)
                    .padding(Spacing.xl)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "📥 Import Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = CinemaAccent
                    )

                    Text(
                        text = "Select components to import:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = optGlobal, onCheckedChange = { optGlobal = it })
                            Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                            Text("General Settings", style = MaterialTheme.typography.bodyLarge, color = CinemaTextPrimary)
                        }
                        if (parsed.hasProviders) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = optProviders, onCheckedChange = { optProviders = it })
                                Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                                Text("Providers (${parsed.settings.providers.size})", style = MaterialTheme.typography.bodyLarge, color = CinemaTextPrimary)
                            }
                        }
                        if (parsed.hasEpgSources) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = optEpg, onCheckedChange = { optEpg = it })
                                Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                                Text("EPG Sources (${parsed.settings.epgSources.size})", style = MaterialTheme.typography.bodyLarge, color = CinemaTextPrimary)
                            }
                        }
                        if (parsed.hasFavorites) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = optFavorites, onCheckedChange = { optFavorites = it })
                                Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                                Text("Favorites", style = MaterialTheme.typography.bodyLarge, color = CinemaTextPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CinemaSecondaryButton(
                            onClick = {
                                showImportOptionsDialog = false
                                pendingParsedImport = null
                            },
                            text = "Cancel",
                            modifier = Modifier.padding(end = Spacing.md)
                        )
                        CinemaPrimaryButton(
                            onClick = {
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
                            },
                            text = "Import"
                        )
                    }
                }
            }
        }
    }

    // Conflict resolution dialog
    if (showConflictDialog && pendingParsedImport != null) {
        // Handle Back button to close dialog
        androidx.activity.compose.BackHandler {
            showConflictDialog = false
            pendingParsedImport = null
        }

        val conflicts = pendingParsedImport!!.conflictingProviders

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = CinemaAlpha.overlayHeavy)),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(
                modifier = Modifier
                    .width(TvDimensions.dialogWidth)
                    .padding(Spacing.xl)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "⚠️ Provider Conflict",
                        style = MaterialTheme.typography.headlineSmall,
                        color = CinemaError
                    )

                    Text(
                        text = "The following provider(s) already exist:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary
                    )

                    Text(
                        text = conflicts.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextPrimary,
                        modifier = Modifier.padding(start = Spacing.md)
                    )

                    Text(
                        text = "What would you like to do?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End, // Changed from SpaceBetween to End for better grouping
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         CinemaSecondaryButton(
                            onClick = {
                                showConflictDialog = false
                                val parsed = pendingParsedImport!!
                                val options = pendingImportOptions
                                pendingParsedImport = null
                                doImport(parsed, SettingsExportManager.ConflictResolution.SKIP, options)
                            },
                            text = "Skip",
                            modifier = Modifier.padding(end = Spacing.md)
                        )
                        CinemaSecondaryButton(
                            onClick = {
                                showConflictDialog = false
                                val parsed = pendingParsedImport!!
                                val options = pendingImportOptions
                                pendingParsedImport = null
                                doImport(parsed, SettingsExportManager.ConflictResolution.DUPLICATE, options)
                            },
                            text = "Duplicate",
                            modifier = Modifier.padding(end = Spacing.md)
                        )
                        CinemaPrimaryButton(
                            onClick = {
                                showConflictDialog = false
                                val parsed = pendingParsedImport!!
                                val options = pendingImportOptions
                                pendingParsedImport = null
                                doImport(parsed, SettingsExportManager.ConflictResolution.OVERWRITE, options)
                            },
                            text = "Overwrite"
                        )
                    }
                }
            }
        }
    }

    // Drive sync state
    val syncStatus by syncManager.syncStatus.collectAsState()
    val signedInEmail by syncManager.signedInEmail.collectAsState()

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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) {
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
            val scale = LocalUiScale.current
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
            modifier = Modifier.fillMaxSize()
        ) {
            // Provider Details
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
                Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                    Text(
                        text = "Provider",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = providerName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                            )
                        }
                        CinemaSecondaryButton(
                            onClick = onManageProviders,
                            text = "Manage Providers"
                        )
                    }
                }
                }
            }

            // Theme Selection
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
                Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Select a color theme for the app",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    val selectedThemeFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer { selectedThemeFocusRequester },
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                    ) {
                        AllPalettes.chunked(2).forEachIndexed { rowIndex, rowPalettes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                            ) {
                                rowPalettes.forEach { palette ->
                                    val isSelected = selectedThemeId == palette.id
                                    if (isSelected) {
                                        CinemaPrimaryButton(
                                            onClick = { },
                                            text = palette.displayName,
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (rowIndex == 0) Modifier.focusRequester(selectedThemeFocusRequester)
                                                    else Modifier
                                                )
                                        )
                                    } else {
                                        CinemaSecondaryButton(
                                            onClick = {
                                                selectedThemeId = palette.id
                                                appSettings.themeId = palette.id
                                                onThemeChanged(palette.id)
                                            },
                                            text = palette.displayName,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            // EPG Data
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
                Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                    Text(
                        text = "EPG Data",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))

                    val epgIndexer = remember { EpgIndexer.getInstance(context.applicationContext) }
                    val indexState by epgIndexer.state.collectAsState()
                    var sourceCount by remember { mutableStateOf(0) }
                    LaunchedEffect(epgRefreshTrigger) {
                        sourceCount = epgIndexer.getSourceCount()
                    }
                    val summaryText = when (val idx = indexState) {
                        is EpgIndexState.Indexed -> "${formatProgrammeCount(idx.programmeCount)} programmes, ${formatProgrammeCount(idx.channelCount)} channels"
                        is EpgIndexState.Indexing -> "Indexing: ${idx.progressPercent}%"
                        is EpgIndexState.NotIndexed -> if (sourceCount > 0) "$sourceCount source(s) configured, not yet indexed" else "No sources configured"
                        is EpgIndexState.Failed -> "Error: ${idx.reason}"
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    CinemaSecondaryButton(
                        onClick = onManageEpg,
                        text = "Manage EPG Data"
                    )
                }
                }
            }

            // UI Scale
            item {
                Column {
                    Text(
                        text = "Category/Grid UI Scale",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Adjust font, spacing, and element sizes for category/grid views",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    // Scale options as buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                    ) {
                        listOf(0.4f to "40%", 0.6f to "60%", 0.8f to "80%", 1.0f to "100%").chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                            ) {
                                rowItems.forEach { (scaleValue, label) ->
                                    val isSelected = uiScale == scaleValue
                                    if (isSelected) {
                                        CinemaPrimaryButton(
                                            onClick = { },
                                            text = label,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        CinemaSecondaryButton(
                                            onClick = {
                                                uiScale = scaleValue
                                                appSettings.uiScale = scaleValue
                                                onUiScaleChanged(scaleValue)
                                            },
                                            text = label,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Developer Mode
            item {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusableNoScale()
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md.scaled(scale)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Enable stats for nerds and debug features",
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                    Switch(
                        checked = isDevMode,
                        onCheckedChange = { enabled ->
                            isDevMode = enabled
                            appSettings.isDevMode = enabled
                        }
                    )
                }
                }
            }

            // Cloud Sync (Google Drive)
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
                Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                    Text(
                        text = "Cloud Sync",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Sync provider settings across devices using your Google account",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    if (signedInEmail != null) {
                        // Signed in: show account info + sync controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = signedInEmail ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextPrimary
                                )
                                val statusText = when (syncStatus) {
                                    is DriveSettingsSyncManager.SyncStatus.Syncing -> "Syncing..."
                                    is DriveSettingsSyncManager.SyncStatus.Synced -> "Synced"
                                    is DriveSettingsSyncManager.SyncStatus.Error ->
                                        "Error: ${(syncStatus as DriveSettingsSyncManager.SyncStatus.Error).message}"
                                    else -> "Ready"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                    ),
                                    color = when (syncStatus) {
                                        is DriveSettingsSyncManager.SyncStatus.Synced -> CinemaAccent
                                        is DriveSettingsSyncManager.SyncStatus.Error -> CinemaError
                                        else -> CinemaTextSecondary
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                            CinemaPrimaryButton(
                                onClick = {
                                    coroutineScope.launch { syncManager.syncNow() }
                                },
                                text = "Sync Now"
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaDangerButton(
                                onClick = {
                                    coroutineScope.launch { syncManager.signOut() }
                                },
                                text = "Sign Out"
                            )
                        }
                    } else {
                        // Not signed in: show sign-in button
                        CinemaPrimaryButton(
                            onClick = {
                                signInError = null
                                signInLauncher.launch(syncManager.getSignInIntent())
                            },
                            text = "Sign in with Google"
                        )
                        if (signInError != null) {
                            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                            Text(
                                text = signInError ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaError
                            )
                        }
                    }
                }
                }
            }

            // Export / Import Settings
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
                Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                    Text(
                        text = "Export / Import",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Export all settings to a file or import from another device",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                    ) {
                        CinemaPrimaryButton(
                            onClick = { exportLauncher.launch("fijerena_settings.json") },
                            text = "Export Settings",
                            modifier = Modifier.weight(1f)
                        )
                        CinemaSecondaryButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                            text = "Import Settings",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaSecondaryButton(
                        onClick = {
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
                        text = "Quick Import from Downloads",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exportImportMessage != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                        Text(
                            text = exportImportMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary
                        )
                    }
                }
                }
            }

            // About this app
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaAccent
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = "Fijerena v${org.njarasoa.fijerena.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaTextPrimary
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Build: ${org.njarasoa.fijerena.BuildConfig.GIT_HASH}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Built: ${org.njarasoa.fijerena.BuildConfig.BUILD_TIME}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                    }
                }
            }

        }
    }
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

