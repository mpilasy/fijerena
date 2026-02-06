@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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

/**
 * Provider-specific settings screen (TV).
 * Shows settings that depend on the active provider type.
 */
@Composable
fun TvProviderSettingsScreen(
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
        Text(
            text = "Provider Settings",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

        TvLazyColumn(
            contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
            modifier = Modifier.fillMaxSize()
        ) {
            // Auto-Resume
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Resume",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Resume VOD content from where you left off",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
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

            // Watch History Size
            item {
                Column {
                    Text(
                        text = "Last Watched Queue Size",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Items to keep in Last Watched category (1-100)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    if (!isEditingQueueSize) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = watchHistorySize,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    isEditingQueueSize = true
                                    newWatchHistorySize = watchHistorySize
                                },
                                text = "Edit"
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = newWatchHistorySize,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                        newWatchHistorySize = newValue
                                    }
                                },
                                label = { Text("Queue Size") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(TvDimensions.selectionListWidth.scaled(scale))
                            )
                            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                            CinemaSecondaryButton(
                                onClick = {
                                    isEditingQueueSize = false
                                    newWatchHistorySize = ""
                                },
                                text = "Cancel"
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaPrimaryButton(
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
                                enabled = newWatchHistorySize.toIntOrNull()?.let { it in 1..100 } == true,
                                text = "Save"
                            )
                        }
                    }
                }
            }

            // Favorites Max Size
            item {
                Column {
                    Text(
                        text = "Favorites Max Size",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Maximum number of favorites to store (10-500)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                    if (!isEditingFavoritesSize) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = favoritesMaxSize,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            CinemaSecondaryButton(
                                onClick = {
                                    isEditingFavoritesSize = true
                                    newFavoritesMaxSize = favoritesMaxSize
                                },
                                text = "Edit"
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = newFavoritesMaxSize,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                        newFavoritesMaxSize = newValue
                                    }
                                },
                                label = { Text("Max Size") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(TvDimensions.selectionListWidth.scaled(scale))
                            )
                            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                            CinemaSecondaryButton(
                                onClick = {
                                    isEditingFavoritesSize = false
                                    newFavoritesMaxSize = ""
                                },
                                text = "Cancel"
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            CinemaPrimaryButton(
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
                                enabled = newFavoritesMaxSize.toIntOrNull()?.let { it in 10..500 } == true,
                                text = "Save"
                            )
                        }
                    }
                }
            }

            // Clear Favorites
            item {
                Column {
                    Text(
                        text = "Clear All Favorites",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Remove all favorited streams from all content types",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    CinemaDangerButton(
                        onClick = { showClearFavoritesDialog = true },
                        text = "Clear All Favorites"
                    )
                }
            }

            // Clear Progress
            item {
                Column {
                    Text(
                        text = "Clear Playback Progress",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Remove all saved positions (Continue Watching will be empty)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    CinemaDangerButton(
                        onClick = { showClearProgressDialog = true },
                        text = "Clear All Progress"
                    )
                }
            }

            // Category Filters (Xtream only)
            if (providerType == "XTREAM") {
                item {
                    Column {
                        Text(
                            text = "Category Filters",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Hide or show categories based on name prefixes",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mode: ${if (categoryFilters.mode == FilterMode.EXCLUDE) "Exclude" else "Include"}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                ),
                                color = CinemaTextPrimary
                            )
                            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                            Text(
                                text = "Prefixes: ${if (categoryFilters.prefixes.isEmpty()) "None" else categoryFilters.prefixes.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                ),
                                color = CinemaTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Scripts: ${if (categoryFilters.allowedScripts.isEmpty()) "All" else categoryFilters.allowedScripts.joinToString(", ") { it.displayName }}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        CinemaPrimaryButton(
                            onClick = { showCategoryFilterDialog = true },
                            text = "Manage Filters"
                        )
                    }
                }
            }

            // External EPG Source (Xtream + Local)
            if (providerType in listOf("XTREAM", "LOCAL")) {
                item {
                    Column {
                        Text(
                            text = "External EPG Source (XMLTV)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                        Text(
                            text = "Provide an XMLTV URL for TV Guide data (overrides provider EPG)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

                        if (!isEditingEpgUrl) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (epgUrl.isBlank()) "Not configured" else epgUrl,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                    ),
                                    color = if (epgUrl.isBlank()) CinemaTextSecondary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                                CinemaSecondaryButton(
                                    onClick = {
                                        isEditingEpgUrl = true
                                        newEpgUrl = epgUrl
                                    },
                                    text = "Edit"
                                )
                                if (epgUrl.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                                    CinemaDangerButton(
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
                                        text = "Clear"
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = newEpgUrl,
                                    onValueChange = { newEpgUrl = it },
                                    label = { Text("XMLTV URL") },
                                    placeholder = { Text("https://epg.example.com/guide.xml.gz") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                                CinemaSecondaryButton(
                                    onClick = {
                                        isEditingEpgUrl = false
                                        newEpgUrl = ""
                                    },
                                    text = "Cancel"
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                                CinemaPrimaryButton(
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
                                    enabled = newEpgUrl.isNotBlank(),
                                    text = "Save"
                                )
                            }
                        }
                    }
                }
            }

            // Caching Enabled (Xtream only)
            if (providerType == "XTREAM") {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Caching",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                            Text(
                                text = "Cache categories and streams for faster loading",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                                ),
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
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
        }
    }

    // Clear Favorites Confirmation Dialog
    if (showClearFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { showClearFavoritesDialog = false },
            title = {
                Text(
                    "Clear All Favorites?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearFavorites()
                        showClearFavoritesDialog = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaError,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearFavoritesDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = Spacing.xs
        )
    }

    // Clear Progress Confirmation Dialog
    if (showClearProgressDialog) {
        AlertDialog(
            onDismissRequest = { showClearProgressDialog = false },
            title = {
                Text(
                    "Clear All Playback Progress?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove all saved playback positions. You will start from the beginning when playing any VOD content.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearWatchHistory()
                        showClearProgressDialog = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaError,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearProgressDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = Spacing.xs
        )
    }

    // Category Filter Dialog
    if (showCategoryFilterDialog) {
        var filterMode by remember { mutableStateOf(categoryFilters.mode) }
        var prefixesText by remember { mutableStateOf(categoryFilters.prefixes.joinToString(", ")) }
        var selectedScripts by remember { mutableStateOf(categoryFilters.allowedScripts) }

        AlertDialog(
            onDismissRequest = { showCategoryFilterDialog = false },
            title = {
                Text(
                    "Category Filters",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
                ) {
                    Text(
                        "Filter Mode:",
                        style = MaterialTheme.typography.titleSmall,
                        color = CinemaTextPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { filterMode = FilterMode.EXCLUDE },
                            colors = ButtonDefaults.colors(
                                containerColor = if (filterMode == FilterMode.EXCLUDE) CinemaAccent else CinemaSurfaceVariant
                            )
                        ) {
                            Text("Exclude")
                        }
                        Button(
                            onClick = { filterMode = FilterMode.INCLUDE },
                            colors = ButtonDefaults.colors(
                                containerColor = if (filterMode == FilterMode.INCLUDE) CinemaAccent else CinemaSurfaceVariant
                            )
                        ) {
                            Text("Include")
                        }
                    }
                    Text(
                        if (filterMode == FilterMode.EXCLUDE)
                            "Hide categories that start with these prefixes"
                        else
                            "Show only categories that start with these prefixes",
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    Text(
                        "Prefixes (comma-separated):",
                        style = MaterialTheme.typography.titleSmall,
                        color = CinemaTextPrimary
                    )
                    OutlinedTextField(
                        value = prefixesText,
                        onValueChange = { prefixesText = it },
                        label = { Text("Prefixes (comma-separated)") },
                        placeholder = { Text("e.g., XXX, Adult, 18+") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CinemaSurface,
                            unfocusedContainerColor = CinemaSurface,
                            focusedBorderColor = CinemaAccent,
                            unfocusedBorderColor = CinemaSurfaceVariant,
                            focusedTextColor = CinemaTextPrimary,
                            unfocusedTextColor = CinemaTextPrimary,
                            focusedLabelColor = CinemaAccent,
                            unfocusedLabelColor = CinemaTextSecondary,
                            focusedPlaceholderColor = CinemaTextSecondary,
                            unfocusedPlaceholderColor = CinemaTextSecondary,
                            cursorColor = CinemaAccent
                        )
                    )
                    Text(
                        "Examples: \"XXX\", \"Adult\", \"FR|\", \"EN|\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    Text(
                        "Language Script Filter:",
                        style = MaterialTheme.typography.titleSmall,
                        color = CinemaTextPrimary
                    )
                    Text(
                        "Show only categories in selected scripts (none = show all)",
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))
                    ) {
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
                                Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                                Text(
                                    text = script.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CinemaTextPrimary
                                )
                            }
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
                        val newFilters = CategoryFilters(
                            mode = filterMode,
                            prefixes = prefixes,
                            allowedScripts = selectedScripts
                        )
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
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaAccent,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showCategoryFilterDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = Spacing.xs
        )
    }
}
