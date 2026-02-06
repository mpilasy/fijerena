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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
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

    // Global settings
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    var selectedThemeId by remember { mutableStateOf(appSettings.themeId) }

    // EPG URL state
    var epgUrl by remember { mutableStateOf(appSettings.epgUrl) }
    var isEditingEpgUrl by remember { mutableStateOf(false) }
    var newEpgUrl by remember { mutableStateOf("") }
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

            // === EPG URL ===
            SettingsSection(title = "External EPG Source (XMLTV)") {
                Text(
                    text = "Provide an XMLTV URL for TV Guide data (overrides provider EPG)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

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
                        Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                        OutlinedButton(onClick = {
                            isEditingEpgUrl = true
                            newEpgUrl = epgUrl
                        }) {
                            Text("Edit")
                        }
                    }
                    if (epgUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        Button(
                            onClick = {
                                epgUrl = ""
                                appSettings.epgUrl = ""
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
                    Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm, Alignment.End)
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
                                appSettings.epgUrl = url
                            },
                            enabled = newEpgUrl.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
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

