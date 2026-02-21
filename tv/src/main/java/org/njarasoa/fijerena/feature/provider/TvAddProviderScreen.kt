@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.parseUrlCredentials
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale

@Composable
fun TvAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel = viewModel(
        factory = ProviderViewModelFactory(context)
    )
    val isEditMode = editId > 0L
    val appSettings = remember { org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
    var error by remember { mutableStateOf<String?>(null) }
    val saveState by viewModel.saveState.collectAsState()
    val isBusy = saveState is SaveState.Validating || saveState is SaveState.Saving

    // Quick Connect state (Jellyfin only)
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var qcCode by remember { mutableStateOf("") }
    var qcSecret by remember { mutableStateOf("") }
    var qcError by remember { mutableStateOf<String?>(null) }

    // Cache management state (edit mode only)
    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val syncManager = remember { DriveSettingsSyncManager(context.applicationContext, providerRepo) }
    val coroutineScope = rememberCoroutineScope()
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var cacheRefreshTrigger by remember { mutableIntStateOf(0) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearLiveTvCacheDialog by remember { mutableStateOf(false) }
    var showClearMoviesCacheDialog by remember { mutableStateOf(false) }
    var showClearTvShowsCacheDialog by remember { mutableStateOf(false) }

    // Provider settings state (edit mode only)
    var providerSettings by remember { mutableStateOf(ProviderSettings.DEFAULT) }
    var autoResumeEnabled by remember { mutableStateOf(true) }
    var watchHistorySize by remember { mutableStateOf("25") }
    var newWatchHistorySize by remember { mutableStateOf("") }
    var isEditingQueueSize by remember { mutableStateOf(false) }
    var favoritesMaxSize by remember { mutableStateOf("100") }
    var newFavoritesMaxSize by remember { mutableStateOf("") }
    var isEditingFavoritesSize by remember { mutableStateOf(false) }
    var cachingEnabled by remember { mutableStateOf(true) }
    var categoryFilters by remember { mutableStateOf(CategoryFilters()) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    val repository = remember {
        val accountManager = org.njarasoa.fijerena.core.network.AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }

    LaunchedEffect(editId, cacheRefreshTrigger) {
        if (isEditMode) {
            cacheStats = providerRepo.getCacheStatsForProvider(editId)
            val ps = providerRepo.getProviderSettings(editId)
            providerSettings = ps
            autoResumeEnabled = ps.autoResumeEnabled
            watchHistorySize = ps.watchHistorySize.toString()
            favoritesMaxSize = ps.favoritesMaxSize.toString()
            cachingEnabled = ps.cachingEnabled
            categoryFilters = ps.categoryFilters
        }
    }

    // Load existing provider data in edit mode directly from the repository
    LaunchedEffect(editId) {
        if (isEditMode) {
            val provider = providerRepo.getProviderById(editId)
            if (provider != null) {
                name = provider.name
                url = provider.url
                username = provider.username
                password = providerRepo.getPassword(editId) ?: ""
                selectedType = try { ProviderType.valueOf(provider.type) } catch (_: Exception) { ProviderType.XTREAM }
                if (provider.type == "SMB" && provider.config.isNotBlank()) {
                    try {
                        val json = org.json.JSONObject(provider.config)
                        host = json.optString("host", "")
                        shareName = json.optString("share", "")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
    val scale = LocalUiScale.current
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                ),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(modifier = Modifier.width(TvDimensions.formFieldWidth.scaled(scale))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg.scaled(scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Edit Provider" else "Add Provider",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Provider type dropdown (D-pad friendly)
                var typeDropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider Type") },
                        trailingIcon = {
                            Text(
                                text = if (typeDropdownExpanded) "▲" else "▼",
                                color = CinemaAccent
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { typeDropdownExpanded = true }
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                                ) {
                                    typeDropdownExpanded = true
                                    true
                                } else false
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CinemaTextPrimary,
                            unfocusedTextColor = CinemaTextPrimary,
                            cursorColor = CinemaAccent,
                            focusedBorderColor = CinemaAccent,
                            unfocusedBorderColor = CinemaTextSecondary,
                            focusedLabelColor = CinemaAccent,
                            unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            focusedContainerColor = CinemaSurfaceVariant,
                            focusedTrailingIconColor = CinemaAccent,
                            unfocusedTrailingIconColor = CinemaTextSecondary
                        )
                    )
                    DropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                        containerColor = CinemaSurface
                    ) {
                        ProviderType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = type.displayName,
                                        color = if (type == selectedType) CinemaAccent else CinemaTextPrimary
                                    )
                                },
                                onClick = {
                                    selectedType = type
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Name field (all types)
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Provider Name") },
                    placeholder = { Text("e.g. My IPTV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedContainerColor = CinemaSurfaceVariant,
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )

                // Type-specific fields
                when (selectedType) {
                    ProviderType.XTREAM -> {
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // URL field
                        OutlinedTextField(
                            value = url,
                            onValueChange = { newValue ->
                                val parsed = parseUrlCredentials(newValue)
                                if (parsed != null) {
                                    url = parsed.baseUrl
                                    parsed.username?.let { username = it }
                                    parsed.password?.let { password = it }
                                } else {
                                    url = newValue
                                }
                                error = null
                            },
                            label = { Text("Server URL") },
                            placeholder = { Text("http://provider.example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Username field
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                error = null
                            },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                error = null
                            },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )
                    }

                    ProviderType.JELLYFIN -> {
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Server URL field
                        OutlinedTextField(
                            value = url,
                            onValueChange = { newValue ->
                                val parsed = parseUrlCredentials(newValue)
                                if (parsed != null) {
                                    url = parsed.baseUrl
                                    parsed.username?.let { username = it }
                                    parsed.password?.let { password = it }
                                } else {
                                    url = newValue
                                }
                                error = null
                            },
                            label = { Text("Server URL") },
                            placeholder = { Text("http://192.168.1.100:8096") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Username field
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                error = null
                            },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                error = null
                            },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        if (!isEditMode) {
                            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                            Text(
                                text = "— or —",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                                color = CinemaTextSecondary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                            CinemaSecondaryButton(
                                onClick = {
                                    if (url.isBlank()) {
                                        error = "Enter the Jellyfin server URL first"
                                    } else {
                                        qcCode = ""
                                        qcSecret = ""
                                        qcError = null
                                        showQuickConnectDialog = true
                                    }
                                },
                                text = "Use Quick Connect",
                                enabled = !isBusy
                            )
                        }
                    }

                    ProviderType.SMB -> {
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Host/IP field
                        OutlinedTextField(
                            value = host,
                            onValueChange = {
                                host = it
                                error = null
                            },
                            label = { Text("Host / IP") },
                            placeholder = { Text("192.168.1.100") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Share Name field
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = {
                                shareName = it
                                error = null
                            },
                            label = { Text("Share Name") },
                            placeholder = { Text("media") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Username field (optional)
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                error = null
                            },
                            label = { Text("Username (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Password field (optional)
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                error = null
                            },
                            label = { Text("Password (optional)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )
                    }

                    ProviderType.LOCAL -> {
                        // LOCAL type only requires name - folder/file picker will be added later
                    }

                    ProviderType.REMOTE_M3U -> {
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                error = null
                            },
                            label = { Text("M3U Playlist URL") },
                            placeholder = { Text("https://example.com/playlist.m3u") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CinemaTextPrimary,
                                unfocusedTextColor = CinemaTextPrimary,
                                cursorColor = CinemaAccent,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaTextSecondary,
                                focusedLabelColor = CinemaAccent,
                                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                                focusedPlaceholderColor = CinemaTextSecondary,
                                unfocusedPlaceholderColor = CinemaTextSecondary
                            )
                        )
                    }
                }

                // Provider Settings (edit mode only)
                if (isEditMode) {
                    Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))
                    HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    Text(
                        text = "Provider Settings",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Auto-Resume
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusableNoScale(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Resume",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Resume VOD content from where you left off",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                        Switch(
                            checked = autoResumeEnabled,
                            onCheckedChange = { enabled ->
                                autoResumeEnabled = enabled
                                coroutineScope.launch {
                                    val newSettings = providerSettings.copy(autoResumeEnabled = enabled)
                                    providerRepo.updateProviderSettings(editId, newSettings)
                                    providerSettings = newSettings
                                    syncManager.syncProviderSettings(editId)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Watch History Size
                    Column {
                        Text(
                            text = "Last Watched Queue Size",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Items to keep in Last Watched category (1-100)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
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
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)),
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
                                            coroutineScope.launch {
                                                val newSettings = providerSettings.copy(watchHistorySize = size)
                                                providerRepo.updateProviderSettings(editId, newSettings)
                                                providerSettings = newSettings
                                                syncManager.syncProviderSettings(editId)
                                            }
                                        }
                                    },
                                    enabled = newWatchHistorySize.toIntOrNull()?.let { it in 1..100 } == true,
                                    text = "Save"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Favorites Max Size
                    Column {
                        Text(
                            text = "Favorites Max Size",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Maximum number of favorites to store (10-500)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
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
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize.scaled(scale)),
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
                                            coroutineScope.launch {
                                                val newSettings = providerSettings.copy(favoritesMaxSize = size)
                                                providerRepo.updateProviderSettings(editId, newSettings)
                                                providerSettings = newSettings
                                                syncManager.syncProviderSettings(editId)
                                            }
                                        }
                                    },
                                    enabled = newFavoritesMaxSize.toIntOrNull()?.let { it in 10..500 } == true,
                                    text = "Save"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Clear Favorites
                    Column {
                        Text(
                            text = "Clear All Favorites",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Remove all favorited streams from all content types",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        CinemaDangerButton(
                            onClick = { showClearFavoritesDialog = true },
                            text = "Clear All Favorites"
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                    // Clear Progress
                    Column {
                        Text(
                            text = "Clear Playback Progress",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Remove all saved positions (Continue Watching will be empty)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        CinemaDangerButton(
                            onClick = { showClearProgressDialog = true },
                            text = "Clear All Progress"
                        )
                    }

                    // Category Filters (Xtream only)
                    if (selectedType == ProviderType.XTREAM) {
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        Column {
                            Text(
                                text = "Category Filters",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hide or show categories based on name prefixes",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Mode: ${if (categoryFilters.mode == FilterMode.EXCLUDE) "Exclude" else "Include"}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                                    color = CinemaTextPrimary
                                )
                                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                                Text(
                                    text = "Prefixes: ${if (categoryFilters.prefixes.isEmpty()) "None" else categoryFilters.prefixes.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                                    color = CinemaTextSecondary
                                )
                            }
                            Text(
                                text = "Scripts: ${if (categoryFilters.allowedScripts.isEmpty()) "All" else categoryFilters.allowedScripts.joinToString(", ") { it.displayName }}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                                color = CinemaTextSecondary
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                            CinemaPrimaryButton(
                                onClick = { showCategoryFilterDialog = true },
                                text = "Manage Filters"
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                        // Enable Caching
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableNoScale(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Caching",
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cache categories and streams for faster loading",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                            Switch(
                                checked = cachingEnabled,
                                onCheckedChange = { enabled ->
                                    cachingEnabled = enabled
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(cachingEnabled = enabled)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        providerSettings = newSettings
                                        syncManager.syncProviderSettings(editId)
                                    }
                                }
                            )
                        }
                    }
                }

                // Cache Management (edit mode only)
                if (isEditMode) {
                    Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                    Text(
                        text = "Cache Management",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                    Text(
                        text = "Clear cached data to free up storage space",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

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
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)),
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.totalSize),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)),
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
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.liveTv.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.liveTv.categoryCached) "1 category" else "No categories"}, ${stats.liveTv.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
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
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.movies.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.movies.categoryCached) "1 category" else "No categories"}, ${stats.movies.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
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
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)),
                                    color = CinemaTextPrimary
                                )
                                Text(
                                    text = formatBytes(stats.tvShows.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                                    color = CinemaAccent
                                )
                                Text(
                                    text = "${if (stats.tvShows.categoryCached) "1 category" else "No categories"}, ${stats.tvShows.streamListsCount} stream lists",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
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
                        Text(
                            text = "EPG Data: ${stats.epgCount} channels",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                        Text(
                            text = "Other: ${formatBytes(stats.otherSize)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                        )
                    }
                }

                // Error message
                error?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                        color = CinemaError
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale), Alignment.CenterHorizontally)
                ) {
                    CinemaSecondaryButton(
                        onClick = onBack,
                        enabled = !isBusy,
                        text = "Cancel"
                    )

                    CinemaPrimaryButton(
                        onClick = {
                            // Validate based on selected provider type
                            val validationError = when (selectedType) {
                                ProviderType.XTREAM -> when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "Server URL is required"
                                    username.isBlank() -> "Username is required"
                                    password.isBlank() -> "Password is required"
                                    else -> null
                                }
                                ProviderType.JELLYFIN -> when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "Server URL is required"
                                    username.isBlank() -> "Username is required"
                                    password.isBlank() -> "Password is required"
                                    else -> null
                                }
                                ProviderType.SMB -> when {
                                    name.isBlank() -> "Provider name is required"
                                    host.isBlank() -> "Host / IP is required"
                                    shareName.isBlank() -> "Share name is required"
                                    else -> null
                                }
                                ProviderType.LOCAL -> when {
                                    name.isBlank() -> "Provider name is required"
                                    else -> null
                                }
                                ProviderType.REMOTE_M3U -> when {
                                    name.isBlank() -> "Provider name is required"
                                    url.isBlank() -> "M3U Playlist URL is required"
                                    else -> null
                                }
                            }

                            if (validationError != null) {
                                error = validationError
                            } else {
                                val saveUrl = if (selectedType == ProviderType.SMB) "" else url.trim()
                                val saveUsername = username.trim()
                                val savePassword = password.trim()
                                val saveConfig = if (selectedType == ProviderType.SMB) {
                                    """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                                } else ""

                                viewModel.validateAndSave(
                                    id = if (isEditMode) editId else null,
                                    name = name.trim(),
                                    url = saveUrl,
                                    username = saveUsername,
                                    password = savePassword,
                                    type = selectedType.name,
                                    config = saveConfig,
                                    onComplete = onSuccess
                                )
                            }
                        },
                        enabled = !isBusy,
                        text = when (saveState) {
                            is SaveState.Validating -> "Connecting..."
                            is SaveState.Saving -> "Saving..."
                            else -> if (isEditMode) "Update" else "Add"
                        }
                    )
                }

                // Validation failure dialog
                val failedState = saveState as? SaveState.ValidationFailed
                if (failedState != null) {
                    val saveUrl = if (selectedType == ProviderType.SMB) "" else url.trim()
                    val saveConfig = if (selectedType == ProviderType.SMB) {
                        """{"host":"${host.trim()}","share":"${shareName.trim()}"}"""
                    } else ""

                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.resetSaveState() },
                        title = {
                            Text(
                                "Connection Failed",
                                color = CinemaTextPrimary
                            )
                        },
                        text = {
                            Text(
                                failedState.errorMessage,
                                color = CinemaTextSecondary
                            )
                        },
                        confirmButton = {
                            CinemaDangerButton(
                                onClick = {
                                    viewModel.forceSave(
                                        id = if (isEditMode) editId else null,
                                        name = name.trim(),
                                        url = saveUrl,
                                        username = username.trim(),
                                        password = password.trim(),
                                        type = selectedType.name,
                                        config = saveConfig,
                                        onComplete = onSuccess
                                    )
                                },
                                text = "Save Anyway"
                            )
                        },
                        dismissButton = {
                            CinemaSecondaryButton(
                                onClick = { viewModel.resetSaveState() },
                                text = "Go Back"
                            )
                        },
                        containerColor = CinemaSurface
                    )
                }

                // Cache confirmation dialogs
                if (showClearCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearCacheDialog = false },
                        title = { Text("Clear All Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached data (Live TV, Movies, TV Shows, EPG). The app will need to re-download data from the server.", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearAllCacheForProvider(editId)
                                    cacheRefreshTrigger++
                                    showClearCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear All") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                if (showClearLiveTvCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearLiveTvCacheDialog = false },
                        title = { Text("Clear Live TV Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached Live TV data (categories and streams).", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearCacheForProviderContentType(editId, "LIVE_TV")
                                    cacheRefreshTrigger++
                                    showClearLiveTvCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearLiveTvCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                if (showClearMoviesCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearMoviesCacheDialog = false },
                        title = { Text("Clear Movies Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached Movies data (categories and streams).", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearCacheForProviderContentType(editId, "MOVIES")
                                    cacheRefreshTrigger++
                                    showClearMoviesCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearMoviesCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                if (showClearTvShowsCacheDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearTvShowsCacheDialog = false },
                        title = { Text("Clear TV Shows Cache?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all cached TV Shows data (categories and streams).", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    providerRepo.clearCacheForProviderContentType(editId, "TV_SHOWS")
                                    cacheRefreshTrigger++
                                    showClearTvShowsCacheDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearTvShowsCacheDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }
                // Clear Favorites Confirmation Dialog
                if (showClearFavoritesDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearFavoritesDialog = false },
                        title = { Text("Clear All Favorites?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all favorited streams from all content types (Live TV, Movies, TV Shows). This action cannot be undone.", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    repository.clearFavorites()
                                    showClearFavoritesDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear All") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearFavoritesDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                // Clear Progress Confirmation Dialog
                if (showClearProgressDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearProgressDialog = false },
                        title = { Text("Clear All Playback Progress?", color = CinemaTextPrimary) },
                        text = { Text("This will remove all saved playback positions. You will start from the beginning when playing any VOD content.", color = CinemaTextSecondary) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    repository.clearWatchHistory()
                                    showClearProgressDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaError, contentColor = androidx.compose.ui.graphics.Color.White)
                            ) { Text("Clear All") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showClearProgressDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                // Category Filter Dialog
                if (showCategoryFilterDialog) {
                    var filterMode by remember { mutableStateOf(categoryFilters.mode) }
                    var prefixesText by remember { mutableStateOf(categoryFilters.prefixes.joinToString(", ")) }
                    var selectedScripts by remember { mutableStateOf(categoryFilters.allowedScripts) }

                    AlertDialog(
                        onDismissRequest = { showCategoryFilterDialog = false },
                        title = { Text("Category Filters", color = CinemaTextPrimary) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
                            ) {
                                Text("Filter Mode:", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)), color = CinemaTextPrimary)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.tv.material3.Button(
                                        onClick = { filterMode = FilterMode.EXCLUDE },
                                        colors = androidx.tv.material3.ButtonDefaults.colors(
                                            containerColor = if (filterMode == FilterMode.EXCLUDE) CinemaAccent else CinemaSurfaceVariant
                                        )
                                    ) { Text("Exclude") }
                                    androidx.tv.material3.Button(
                                        onClick = { filterMode = FilterMode.INCLUDE },
                                        colors = androidx.tv.material3.ButtonDefaults.colors(
                                            containerColor = if (filterMode == FilterMode.INCLUDE) CinemaAccent else CinemaSurfaceVariant
                                        )
                                    ) { Text("Include") }
                                }
                                Text(
                                    if (filterMode == FilterMode.EXCLUDE) "Hide categories that start with these prefixes"
                                    else "Show only categories that start with these prefixes",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                                    color = CinemaTextSecondary
                                )
                                Text("Prefixes (comma-separated):", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)), color = CinemaTextPrimary)
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
                                        cursorColor = CinemaAccent
                                    )
                                )
                                Text("Language Script Filter:", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)), color = CinemaTextPrimary)
                                Text("Show only categories in selected scripts (none = show all)", style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)), color = CinemaTextSecondary)
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                                    ScriptType.entries.forEach { script ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = script in selectedScripts,
                                                onCheckedChange = { checked ->
                                                    selectedScripts = if (checked) selectedScripts + script else selectedScripts - script
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                                            Text(text = script.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)), color = CinemaTextPrimary)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val prefixes = prefixesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    val newFilters = CategoryFilters(mode = filterMode, prefixes = prefixes, allowedScripts = selectedScripts)
                                    categoryFilters = newFilters
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(categoryFilters = newFilters)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        providerSettings = newSettings
                                        syncManager.syncProviderSettings(editId)
                                    }
                                    showCategoryFilterDialog = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaAccent, contentColor = CinemaTextPrimary)
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showCategoryFilterDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }

                // Quick Connect dialog (Jellyfin)
                if (showQuickConnectDialog) {
                    LaunchedEffect(Unit) {
                        qcCode = ""
                        qcSecret = ""
                        qcError = null
                        val deviceId = android.provider.Settings.Secure.getString(
                            context.contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: "fijerena"
                        val api = JellyfinApiService(url.trimEnd('/'), deviceId)
                        val initResult = api.initiateQuickConnect()
                        if (initResult.isFailure) {
                            qcError = initResult.exceptionOrNull()?.message ?: "Failed to start Quick Connect"
                            return@LaunchedEffect
                        }
                        val init = initResult.getOrThrow()
                        qcCode = init.code
                        qcSecret = init.secret
                        // Poll every 3 s for up to 2 minutes
                        repeat(40) {
                            delay(3_000)
                            val poll = api.pollQuickConnect(qcSecret)
                            if (poll.isFailure) {
                                qcError = "Polling failed: ${poll.exceptionOrNull()?.message}"
                                return@LaunchedEffect
                            }
                            if (poll.getOrThrow().authenticated) {
                                val authResult = api.authenticateWithQuickConnect(qcSecret)
                                if (authResult.isFailure) {
                                    qcError = "Authentication failed: ${authResult.exceptionOrNull()?.message}"
                                    return@LaunchedEffect
                                }
                                val auth = authResult.getOrThrow()
                                showQuickConnectDialog = false
                                viewModel.quickConnectSave(
                                    name = name.ifBlank { auth.user.name },
                                    url = url.trimEnd('/'),
                                    username = username.ifBlank { auth.user.name },
                                    token = auth.accessToken,
                                    userId = auth.user.id,
                                    onComplete = onSuccess
                                )
                                return@LaunchedEffect
                            }
                        }
                        qcError = "Timed out waiting for approval. Please try again."
                    }

                    AlertDialog(
                        onDismissRequest = { showQuickConnectDialog = false },
                        title = { Text("Quick Connect", color = CinemaTextPrimary) },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                when {
                                    qcError != null -> {
                                        Text(
                                            text = qcError!!,
                                            color = CinemaError,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    qcCode.isEmpty() -> {
                                        CircularProgressIndicator(color = CinemaAccent)
                                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                        Text(
                                            text = "Connecting to server...",
                                            color = CinemaTextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Enter this code in Jellyfin:",
                                            color = CinemaTextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                        Text(
                                            text = qcCode,
                                            color = CinemaAccent,
                                            style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale))
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                                        Text(
                                            text = "Open Jellyfin → Dashboard → Quick Connect, then enter the code above.",
                                            color = CinemaTextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                                        CircularProgressIndicator(color = CinemaAccent)
                                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                        Text(
                                            text = "Waiting for approval...",
                                            color = CinemaTextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            Button(
                                onClick = { showQuickConnectDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = CinemaSurfaceVariant,
                                    contentColor = CinemaTextPrimary
                                )
                            ) { Text("Cancel") }
                        },
                        containerColor = CinemaSurface
                    )
                }
            }
            } // GlassPanel
        }
    }
    } // CompositionLocalProvider
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
