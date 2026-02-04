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
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    onProviderChanged: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var providerName by remember { mutableStateOf(appSettings.providerName) }
    var currentUrl by remember { mutableStateOf(repository.getCurrentUrl() ?: "") }
    var currentUsername by remember { mutableStateOf(repository.getCurrentUsername() ?: "") }
    var watchHistorySize by remember { mutableStateOf(appSettings.watchHistorySize.toString()) }
    var newWatchHistorySize by remember { mutableStateOf("") }
    var favoritesMaxSize by remember { mutableStateOf(appSettings.favoritesMaxSize.toString()) }
    var newFavoritesMaxSize by remember { mutableStateOf("") }
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    var autoResumeEnabled by remember { mutableStateOf(appSettings.autoResumeEnabled) }

    // Provider dialog state
    var showProviderDialog by remember { mutableStateOf(false) }
    var dialogProviderName by remember { mutableStateOf("") }
    var dialogUrl by remember { mutableStateOf("") }
    var dialogUsername by remember { mutableStateOf("") }
    var dialogPassword by remember { mutableStateOf("") }
    var isChangingProvider by remember { mutableStateOf(false) }
    var providerChangeError by remember { mutableStateOf<String?>(null) }

    var isEditingQueueSize by remember { mutableStateOf(false) }
    var isEditingFavoritesSize by remember { mutableStateOf(false) }

    // Confirmation dialog states
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearLiveTvCacheDialog by remember { mutableStateOf(false) }
    var showClearMoviesCacheDialog by remember { mutableStateOf(false) }
    var showClearTvShowsCacheDialog by remember { mutableStateOf(false) }

    // Cache stats
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var cacheRefreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(cacheRefreshTrigger) {
        cacheStats = repository.getCacheStats()
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    onClick = {
                        dialogProviderName = providerName
                        dialogUrl = currentUrl
                        dialogUsername = currentUsername
                        dialogPassword = repository.getCurrentPassword() ?: ""
                        providerChangeError = null
                        showProviderDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Provider")
                }
            }

            // === Auto-Resume ===
            SettingsSection(title = "Auto-Resume") {
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
                            appSettings.autoResumeEnabled = enabled
                        }
                    )
                }
            }

            // === Watch History Size ===
            SettingsSection(title = "Last Watched Queue Size") {
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
                                    appSettings.watchHistorySize = size
                                    watchHistorySize = size.toString()
                                    isEditingQueueSize = false
                                    newWatchHistorySize = ""
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
            SettingsSection(title = "Favorites Max Size") {
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
                                    appSettings.favoritesMaxSize = size
                                    favoritesMaxSize = size.toString()
                                    isEditingFavoritesSize = false
                                    newFavoritesMaxSize = ""
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
            SettingsSection(title = "Clear All Favorites") {
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
            SettingsSection(title = "Clear Playback Progress") {
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

            // === Cache Management ===
            SettingsSection(title = "Cache Management") {
                Text(
                    text = "Clear cached data to free up storage space",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                )
                Spacer(modifier = Modifier.height(12.dp))

                cacheStats?.let { stats ->
                    // Total cache
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Cache Size",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = formatBytes(stats.totalSize),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = { showClearCacheDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinemaError
                            )
                        ) {
                            Text("Clear All")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.divider)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Live TV
                    CacheRow(
                        label = "Live TV",
                        size = formatBytes(stats.liveTv.size),
                        detail = "${if (stats.liveTv.categoryCached) "1 category" else "No categories"}, ${stats.liveTv.streamListsCount} stream lists",
                        onClear = { showClearLiveTvCacheDialog = true },
                        clearEnabled = stats.liveTv.size > 0
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Movies
                    CacheRow(
                        label = "Movies",
                        size = formatBytes(stats.movies.size),
                        detail = "${if (stats.movies.categoryCached) "1 category" else "No categories"}, ${stats.movies.streamListsCount} stream lists",
                        onClear = { showClearMoviesCacheDialog = true },
                        clearEnabled = stats.movies.size > 0
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // TV Shows
                    CacheRow(
                        label = "TV Shows",
                        size = formatBytes(stats.tvShows.size),
                        detail = "${if (stats.tvShows.categoryCached) "1 category" else "No categories"}, ${stats.tvShows.streamListsCount} stream lists",
                        onClear = { showClearTvShowsCacheDialog = true },
                        clearEnabled = stats.tvShows.size > 0
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // EPG & Other
                    Text(
                        text = "EPG Data: ${stats.epgCount} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
                    Text(
                        text = "Other: ${formatBytes(stats.otherSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    )
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

            // === Logout ===
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // === Dialogs ===

    // Provider Edit Dialog
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { if (!isChangingProvider) showProviderDialog = false },
            title = { Text("Edit Provider Details") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = dialogProviderName,
                        onValueChange = { dialogProviderName = it },
                        label = { Text("Provider Name") },
                        placeholder = { Text("My Provider") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogUrl,
                        onValueChange = { dialogUrl = it },
                        label = { Text("URL") },
                        placeholder = { Text("http://example.com:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogUsername,
                        onValueChange = { dialogUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogPassword,
                        onValueChange = { dialogPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    providerChangeError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaError
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogUrl.isNotBlank() && dialogUsername.isNotBlank() && dialogPassword.isNotBlank()) {
                            isChangingProvider = true
                            providerChangeError = null

                            coroutineScope.launch {
                                when (val result = repository.login(
                                    dialogUrl,
                                    dialogUsername,
                                    dialogPassword,
                                    rememberMe = true
                                )) {
                                    is Result.Success -> {
                                        providerName = dialogProviderName.ifBlank { "My Provider" }
                                        appSettings.providerName = providerName
                                        currentUrl = dialogUrl
                                        currentUsername = dialogUsername
                                        isChangingProvider = false
                                        showProviderDialog = false
                                        onProviderChanged()
                                    }
                                    is Result.Error -> {
                                        providerChangeError = result.message ?: "Failed to update provider"
                                        isChangingProvider = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = dialogUrl.isNotBlank() && dialogUsername.isNotBlank() && dialogPassword.isNotBlank() && !isChangingProvider
                ) {
                    if (isChangingProvider) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MobileDimensions.iconSmall),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showProviderDialog = false },
                    enabled = !isChangingProvider
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Favorites Dialog
    if (showClearFavoritesDialog) {
        ConfirmationDialog(
            title = "Clear All Favorites?",
            message = "This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.",
            onConfirm = {
                repository.clearFavorites()
                showClearFavoritesDialog = false
            },
            onDismiss = { showClearFavoritesDialog = false }
        )
    }

    // Clear Progress Dialog
    if (showClearProgressDialog) {
        ConfirmationDialog(
            title = "Clear All Playback Progress?",
            message = "This will remove all saved playback positions. You will start from the beginning when playing any VOD content.",
            onConfirm = {
                repository.clearWatchHistory()
                showClearProgressDialog = false
            },
            onDismiss = { showClearProgressDialog = false }
        )
    }

    // Clear All Cache Dialog
    if (showClearCacheDialog) {
        ConfirmationDialog(
            title = "Clear All Cache?",
            message = "This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server.",
            onConfirm = {
                repository.clearCache()
                cacheRefreshTrigger++
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    // Clear Live TV Cache Dialog
    if (showClearLiveTvCacheDialog) {
        ConfirmationDialog(
            title = "Clear Live TV Cache?",
            message = "This will remove all cached Live TV data (categories and streams).",
            onConfirm = {
                repository.clearCacheForContentType("LIVE_TV")
                cacheRefreshTrigger++
                showClearLiveTvCacheDialog = false
            },
            onDismiss = { showClearLiveTvCacheDialog = false }
        )
    }

    // Clear Movies Cache Dialog
    if (showClearMoviesCacheDialog) {
        ConfirmationDialog(
            title = "Clear Movies Cache?",
            message = "This will remove all cached Movies data (categories and streams).",
            onConfirm = {
                repository.clearCacheForContentType("MOVIES")
                cacheRefreshTrigger++
                showClearMoviesCacheDialog = false
            },
            onDismiss = { showClearMoviesCacheDialog = false }
        )
    }

    // Clear TV Shows Cache Dialog
    if (showClearTvShowsCacheDialog) {
        ConfirmationDialog(
            title = "Clear TV Shows Cache?",
            message = "This will remove all cached TV Shows data (categories and streams).",
            onConfirm = {
                repository.clearCacheForContentType("TV_SHOWS")
                cacheRefreshTrigger++
                showClearTvShowsCacheDialog = false
            },
            onDismiss = { showClearTvShowsCacheDialog = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CacheRow(
    label: String,
    size: String,
    detail: String,
    onClear: () -> Unit,
    clearEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = size,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
            )
        }
        OutlinedButton(
            onClick = onClear,
            enabled = clearEnabled
        ) {
            Text("Clear")
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

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
