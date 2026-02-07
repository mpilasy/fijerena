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
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
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

    // Global settings (remain in AppSettings)
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    var uiScale by remember { mutableStateOf(appSettings.uiScale) }
    var selectedThemeId by remember { mutableStateOf(appSettings.themeId) }

    // EPG URL state
    var epgUrl by remember { mutableStateOf(appSettings.epgUrl) }
    var isEditingEpgUrl by remember { mutableStateOf(false) }
    var newEpgUrl by remember { mutableStateOf("") }

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

            // EPG URL
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
                Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                    Text(
                        text = "External EPG Source (XMLTV)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
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
                                        appSettings.epgUrl = ""
                                        EpgFileManager.getInstance(context.applicationContext).triggerDownload()
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
                                    appSettings.epgUrl = url
                                    EpgFileManager.getInstance(context.applicationContext).triggerDownload()
                                },
                                enabled = newEpgUrl.isNotBlank(),
                                text = "Save"
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
                    ) {
                        listOf(0.7f to "70%", 0.8f to "80%", 0.9f to "90%", 1.0f to "100%").chunked(2).forEach { rowItems ->
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

    } // End CompositionLocalProvider
}

