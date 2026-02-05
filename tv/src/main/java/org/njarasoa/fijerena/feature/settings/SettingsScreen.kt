@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
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
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
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
    onManageProviders: () -> Unit = {},
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
    val coroutineScope = rememberCoroutineScope()

    // Drive sync state
    val syncStatus by syncManager.syncStatus.collectAsState()
    val signedInEmail by syncManager.signedInEmail.collectAsState()

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            syncManager.handleSignInResult(result.data)
        }
    }

    // Initialize sync on startup
    LaunchedEffect(Unit) {
        syncManager.initialize()
    }

    // Get active provider info from ProviderEntity (not legacy AppSettings)
    var providerName by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var currentUsername by remember { mutableStateOf("") }
    var activeProviderId by remember { mutableStateOf<Long?>(null) }

    // Provider-level settings (loaded from ProviderSettings)
    var providerSettings by remember { mutableStateOf(ProviderSettings.DEFAULT) }

    LaunchedEffect(Unit) {
        val activeProvider = providerRepo.getActiveProvider()
        providerName = activeProvider?.name ?: "No provider"
        currentUrl = activeProvider?.url ?: ""
        currentUsername = activeProvider?.username ?: ""
        activeProviderId = activeProvider?.id
        activeProvider?.id?.let { id ->
            providerSettings = providerRepo.getProviderSettings(id)
        }
    }

    // Provider-level settings state (from providerSettings)
    var watchHistorySize by remember(providerSettings) { mutableStateOf(providerSettings.watchHistorySize.toString()) }
    var newWatchHistorySize by remember { mutableStateOf("") }
    var favoritesMaxSize by remember(providerSettings) { mutableStateOf(providerSettings.favoritesMaxSize.toString()) }
    var newFavoritesMaxSize by remember { mutableStateOf("") }
    var autoResumeEnabled by remember(providerSettings) { mutableStateOf(providerSettings.autoResumeEnabled) }
    var cachingEnabled by remember(providerSettings) { mutableStateOf(providerSettings.cachingEnabled) }
    var cacheExpiryHours by remember(providerSettings) { mutableStateOf(providerSettings.cacheExpiryHours.toString()) }
    var categoryFilters by remember(providerSettings) { mutableStateOf(providerSettings.categoryFilters) }

    // Global settings (remain in AppSettings)
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }

    var isEditingQueueSize by remember { mutableStateOf(false) }
    var isEditingFavoritesSize by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearLiveTvCacheDialog by remember { mutableStateOf(false) }
    var showClearMoviesCacheDialog by remember { mutableStateOf(false) }
    var showClearTvShowsCacheDialog by remember { mutableStateOf(false) }
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var cacheRefreshTrigger by remember { mutableStateOf(0) }
    var uiScale by remember { mutableStateOf(appSettings.uiScale) }
    var selectedThemeId by remember { mutableStateOf(appSettings.themeId) }

    // Load cache stats
    LaunchedEffect(cacheRefreshTrigger) {
        cacheStats = repository.getCacheStats()
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalUiScale provides uiScale) {
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
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            CinemaSecondaryButton(
                onClick = onBack,
                text = "Back"
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
                Column {
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

            // Theme Selection
            item {
                Column {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRestorer { selectedThemeFocusRequester },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                    ) {
                        AllPalettes.forEach { palette ->
                            val isSelected = selectedThemeId == palette.id
                            if (isSelected) {
                                CinemaPrimaryButton(
                                    onClick = { },
                                    text = palette.displayName,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(selectedThemeFocusRequester)
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
                        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale).scaled(scale)))
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

            // Category Filters
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
                    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                    CinemaPrimaryButton(
                        onClick = { showCategoryFilterDialog = true },
                        text = "Manage Filters"
                    )
                }
            }

            // Caching Enabled
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

            // Cache Management
            item {
                Column {
                    Text(
                        text = "Cache Management",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Clear cached data to free up storage space",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Cache stats
                    cacheStats?.let { stats ->
                        // Total cache size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Cache Size",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.totalSize),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaAccent
                                )
                            }
                            CinemaDangerButton(
                                onClick = { showClearCacheDialog = true },
                                text = "Clear All"
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                        HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Live TV Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live TV",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                                Text(
                                    text = formatBytes(stats.liveTv.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.liveTv.categoryCached) "1 category" else "No categories"}, ${stats.liveTv.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showClearLiveTvCacheDialog = true },
                                text = "Clear",
                                enabled = stats.liveTv.size > 0
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Movies Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Movies",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                                Text(
                                    text = formatBytes(stats.movies.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.movies.categoryCached) "1 category" else "No categories"}, ${stats.movies.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showClearMoviesCacheDialog = true },
                                text = "Clear",
                                enabled = stats.movies.size > 0
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // TV Shows Cache
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TV Shows",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)
                                    ),
                                    color = CinemaTextPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                                Text(
                                    text = formatBytes(stats.tvShows.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                                    ),
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.tvShows.categoryCached) "1 category" else "No categories"}, ${stats.tvShows.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            CinemaSecondaryButton(
                                onClick = { showClearTvShowsCacheDialog = true },
                                text = "Clear",
                                enabled = stats.tvShows.size > 0
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // EPG & Other
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "EPG Data: ${stats.epgCount} channels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                                Text(
                                    text = "Other: ${formatBytes(stats.otherSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                    ) {
                        listOf(0.7f to "70%", 0.8f to "80%", 0.9f to "90%", 1.0f to "100%").forEach { (scale, label) ->
                            val isSelected = uiScale == scale
                            if (isSelected) {
                                CinemaPrimaryButton(
                                    onClick = { },
                                    text = label,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                CinemaSecondaryButton(
                                    onClick = {
                                        uiScale = scale
                                        appSettings.uiScale = scale
                                    },
                                    text = label,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Developer Mode
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

            // Cloud Sync (Google Drive)
            item {
                Column {
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
                            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                            CinemaSecondaryButton(
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
                                signInLauncher.launch(syncManager.getSignInIntent())
                            },
                            text = "Sign in with Google"
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
            tonalElevation = 6.dp.scaled(scale)
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
            tonalElevation = 6.dp.scaled(scale)
        )
    }

    // Clear All Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    "Clear All Cache?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server. This action cannot be undone.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearCache()
                        cacheRefreshTrigger++
                        showClearCacheDialog = false
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
                    onClick = { showClearCacheDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = 6.dp.scaled(scale)
        )
    }

    // Clear Live TV Cache Dialog
    if (showClearLiveTvCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearLiveTvCacheDialog = false },
            title = {
                Text(
                    "Clear Live TV Cache?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove all cached Live TV data (categories and streams). This action cannot be undone.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearCacheForContentType("LIVE_TV")
                        cacheRefreshTrigger++
                        showClearLiveTvCacheDialog = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaError,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearLiveTvCacheDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = 6.dp.scaled(scale)
        )
    }

    // Clear Movies Cache Dialog
    if (showClearMoviesCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearMoviesCacheDialog = false },
            title = {
                Text(
                    "Clear Movies Cache?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove all cached Movies data (categories and streams). This action cannot be undone.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearCacheForContentType("MOVIES")
                        cacheRefreshTrigger++
                        showClearMoviesCacheDialog = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaError,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearMoviesCacheDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = 6.dp.scaled(scale)
        )
    }

    // Clear TV Shows Cache Dialog
    if (showClearTvShowsCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearTvShowsCacheDialog = false },
            title = {
                Text(
                    "Clear TV Shows Cache?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove all cached TV Shows data (categories and streams). This action cannot be undone.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearCacheForContentType("TV_SHOWS")
                        cacheRefreshTrigger++
                        showClearTvShowsCacheDialog = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaError,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearTvShowsCacheDialog = false },
                    colors = ButtonDefaults.colors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = CinemaTextPrimary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CinemaSurface,
            tonalElevation = 6.dp.scaled(scale)
        )
    }

    // Category Filter Dialog
    if (showCategoryFilterDialog) {
        var filterMode by remember { mutableStateOf(categoryFilters.mode) }
        var prefixesText by remember { mutableStateOf(categoryFilters.prefixes.joinToString(", ")) }

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
                    TextField(
                        value = prefixesText,
                        onValueChange = { prefixesText = it },
                        placeholder = { Text("e.g., XXX, Adult, 18+") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                    Text(
                        "Examples: \"XXX\", \"Adult\", \"FR|\", \"EN|\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
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
                            prefixes = prefixes
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
            tonalElevation = 6.dp.scaled(scale)
        )
    }
    } // End CompositionLocalProvider
}

/**
 * Format bytes to human-readable string (KB/MB/GB)
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
