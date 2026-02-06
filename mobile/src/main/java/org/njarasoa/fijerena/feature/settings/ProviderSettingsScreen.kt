package org.njarasoa.fijerena.feature.settings

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
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.*

/**
 * Provider-specific settings screen (Mobile).
 * Shows settings that depend on the active provider type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProviderSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }
    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val syncManager = remember { DriveSettingsSyncManager(context.applicationContext, providerRepo) }
    val coroutineScope = rememberCoroutineScope()

    var activeProviderId by remember { mutableStateOf<Long?>(null) }
    var providerType by remember { mutableStateOf("XTREAM") }
    var providerSettings by remember { mutableStateOf(ProviderSettings.DEFAULT) }

    LaunchedEffect(Unit) {
        val activeProvider = providerRepo.getActiveProvider()
        activeProviderId = activeProvider?.id
        providerType = activeProvider?.type ?: "XTREAM"
        activeProvider?.id?.let { id ->
            providerSettings = providerRepo.getProviderSettings(id)
        }
    }

    // Provider-level settings state
    var watchHistorySize by remember(providerSettings) { mutableStateOf(providerSettings.watchHistorySize.toString()) }
    var newWatchHistorySize by remember { mutableStateOf("") }
    var favoritesMaxSize by remember(providerSettings) { mutableStateOf(providerSettings.favoritesMaxSize.toString()) }
    var newFavoritesMaxSize by remember { mutableStateOf("") }
    var autoResumeEnabled by remember(providerSettings) { mutableStateOf(providerSettings.autoResumeEnabled) }
    var cachingEnabled by remember(providerSettings) { mutableStateOf(providerSettings.cachingEnabled) }
    var categoryFilters by remember(providerSettings) { mutableStateOf(providerSettings.categoryFilters) }

    var epgUrl by remember(providerSettings) { mutableStateOf(providerSettings.epgUrl) }
    var isEditingEpgUrl by remember { mutableStateOf(false) }
    var newEpgUrl by remember { mutableStateOf("") }

    var isEditingQueueSize by remember { mutableStateOf(false) }
    var isEditingFavoritesSize by remember { mutableStateOf(false) }

    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Settings") },
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
            // === Auto-Resume ===
            ProviderSettingsSection(title = "Auto-Resume") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Resume VOD content from where you left off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = autoResumeEnabled,
                        onCheckedChange = { enabled ->
                            autoResumeEnabled = enabled
                            activeProviderId?.let { id ->
                                coroutineScope.launch {
                                    val newSettings = providerSettings.copy(autoResumeEnabled = enabled)
                                    providerRepo.updateProviderSettings(id, newSettings)
                                    providerSettings = newSettings
                                    syncManager.syncProviderSettings(id)
                                }
                            }
                        }
                    )
                }
            }

            // === Watch History Size ===
            ProviderSettingsSection(title = "Last Watched Queue Size") {
                Text(
                    text = "Items to keep in Last Watched category (1-100)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (!isEditingQueueSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = watchHistorySize,
                            style = MaterialTheme.typography.titleLarge
                        )
                        OutlinedButton(onClick = {
                            isEditingQueueSize = true
                            newWatchHistorySize = watchHistorySize
                        }) {
                            Text("Edit")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newWatchHistorySize,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                newWatchHistorySize = newValue
                            }
                        },
                        label = { Text("Queue Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = {
                            isEditingQueueSize = false
                            newWatchHistorySize = ""
                        }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val size = newWatchHistorySize.toIntOrNull()
                                if (size != null && size in 1..100) {
                                    watchHistorySize = size.toString()
                                    isEditingQueueSize = false
                                    newWatchHistorySize = ""
                                    activeProviderId?.let { id ->
                                        coroutineScope.launch {
                                            val newSettings = providerSettings.copy(watchHistorySize = size)
                                            providerRepo.updateProviderSettings(id, newSettings)
                                            providerSettings = newSettings
                                            syncManager.syncProviderSettings(id)
                                        }
                                    }
                                }
                            },
                            enabled = newWatchHistorySize.toIntOrNull()?.let { it in 1..100 } == true
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // === Favorites Max Size ===
            ProviderSettingsSection(title = "Favorites Max Size") {
                Text(
                    text = "Maximum number of favorites to store (10-500)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (!isEditingFavoritesSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = favoritesMaxSize,
                            style = MaterialTheme.typography.titleLarge
                        )
                        OutlinedButton(onClick = {
                            isEditingFavoritesSize = true
                            newFavoritesMaxSize = favoritesMaxSize
                        }) {
                            Text("Edit")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newFavoritesMaxSize,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                newFavoritesMaxSize = newValue
                            }
                        },
                        label = { Text("Max Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = {
                            isEditingFavoritesSize = false
                            newFavoritesMaxSize = ""
                        }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val size = newFavoritesMaxSize.toIntOrNull()
                                if (size != null && size in 10..500) {
                                    favoritesMaxSize = size.toString()
                                    isEditingFavoritesSize = false
                                    newFavoritesMaxSize = ""
                                    activeProviderId?.let { id ->
                                        coroutineScope.launch {
                                            val newSettings = providerSettings.copy(favoritesMaxSize = size)
                                            providerRepo.updateProviderSettings(id, newSettings)
                                            providerSettings = newSettings
                                            syncManager.syncProviderSettings(id)
                                        }
                                    }
                                }
                            },
                            enabled = newFavoritesMaxSize.toIntOrNull()?.let { it in 10..500 } == true
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // === Clear Favorites ===
            ProviderSettingsSection(title = "Clear All Favorites") {
                Text(
                    text = "Remove all favorited streams from all content types",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showClearFavoritesDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Favorites")
                }
            }

            // === Clear Playback Progress ===
            ProviderSettingsSection(title = "Clear Playback Progress") {
                Text(
                    text = "Remove all saved positions (Continue Watching will be empty)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showClearProgressDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Progress")
                }
            }

            // === Category Filters (Xtream only) ===
            if (providerType == "XTREAM") {
                ProviderSettingsSection(title = "Category Filters") {
                    Text(
                        text = "Hide categories by prefix (e.g., 'Adult', 'XXX')",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mode: ${categoryFilters.mode.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (categoryFilters.prefixes.isEmpty()) "No filters configured"
                                       else "${categoryFilters.prefixes.size} prefix(es): ${categoryFilters.prefixes.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                            Text(
                                text = "Scripts: ${if (categoryFilters.allowedScripts.isEmpty()) "All" else categoryFilters.allowedScripts.joinToString(", ") { it.displayName }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                        OutlinedButton(onClick = { showCategoryFilterDialog = true }) {
                            Text("Edit")
                        }
                    }
                }
            }

            // === External EPG Source (Xtream + Local) ===
            if (providerType in listOf("XTREAM", "LOCAL")) {
                ProviderSettingsSection(title = "External EPG Source (XMLTV)") {
                    Text(
                        text = "Provide an XMLTV URL for TV Guide data (overrides provider EPG)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isEditingEpgUrl) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (epgUrl.isBlank()) "Not configured" else epgUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (epgUrl.isBlank())
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                isEditingEpgUrl = true
                                newEpgUrl = epgUrl
                            }) {
                                Text("Edit")
                            }
                        }
                        if (epgUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    epgUrl = ""
                                    activeProviderId?.let { id ->
                                        coroutineScope.launch {
                                            val newSettings = providerSettings.copy(epgUrl = "")
                                            providerRepo.updateProviderSettings(id, newSettings)
                                            providerSettings = newSettings
                                            syncManager.syncProviderSettings(id)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CinemaError
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear EPG URL")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newEpgUrl,
                            onValueChange = { newEpgUrl = it },
                            label = { Text("XMLTV URL") },
                            placeholder = { Text("https://epg.example.com/guide.xml.gz") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            OutlinedButton(onClick = {
                                isEditingEpgUrl = false
                                newEpgUrl = ""
                            }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    val url = newEpgUrl.trim()
                                    epgUrl = url
                                    isEditingEpgUrl = false
                                    newEpgUrl = ""
                                    activeProviderId?.let { id ->
                                        coroutineScope.launch {
                                            val newSettings = providerSettings.copy(epgUrl = url)
                                            providerRepo.updateProviderSettings(id, newSettings)
                                            providerSettings = newSettings
                                            syncManager.syncProviderSettings(id)
                                        }
                                    }
                                },
                                enabled = newEpgUrl.isNotBlank()
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // === Caching (Xtream only) ===
            if (providerType == "XTREAM") {
                ProviderSettingsSection(title = "Caching") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable caching for faster loading",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = cachingEnabled,
                            onCheckedChange = { enabled ->
                                cachingEnabled = enabled
                                activeProviderId?.let { id ->
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(cachingEnabled = enabled)
                                        providerRepo.updateProviderSettings(id, newSettings)
                                        providerSettings = newSettings
                                        syncManager.syncProviderSettings(id)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // === Dialogs ===

    if (showClearFavoritesDialog) {
        ProviderConfirmationDialog(
            title = "Clear All Favorites?",
            message = "This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.",
            onConfirm = {
                repository.clearFavorites()
                showClearFavoritesDialog = false
            },
            onDismiss = { showClearFavoritesDialog = false }
        )
    }

    if (showClearProgressDialog) {
        ProviderConfirmationDialog(
            title = "Clear All Playback Progress?",
            message = "This will remove all saved playback positions. You will start from the beginning when playing any VOD content.",
            onConfirm = {
                repository.clearWatchHistory()
                showClearProgressDialog = false
            },
            onDismiss = { showClearProgressDialog = false }
        )
    }

    if (showCategoryFilterDialog) {
        ProviderCategoryFilterDialog(
            currentFilters = categoryFilters,
            onSave = { newFilters ->
                categoryFilters = newFilters
                activeProviderId?.let { id ->
                    coroutineScope.launch {
                        val newSettings = providerSettings.copy(categoryFilters = newFilters)
                        providerRepo.updateProviderSettings(id, newSettings)
                        providerSettings = newSettings
                        syncManager.syncProviderSettings(id)
                    }
                }
                showCategoryFilterDialog = false
            },
            onDismiss = { showCategoryFilterDialog = false }
        )
    }
}

@Composable
private fun ProviderSettingsSection(
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
private fun ProviderConfirmationDialog(
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

@Composable
private fun ProviderCategoryFilterDialog(
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
