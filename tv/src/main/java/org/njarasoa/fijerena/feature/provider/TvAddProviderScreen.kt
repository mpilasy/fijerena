@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.smb.smbSourceConfig
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.model.addSourceTypes
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.SaveState
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.feature.provider.components.CacheManagementSection
import org.njarasoa.fijerena.feature.provider.components.CategoryFilterDialog
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.feature.provider.components.EDIT_SOURCE_FIRST_SETTING_KEY
import org.njarasoa.fijerena.feature.provider.components.JellyfinForm
import org.njarasoa.fijerena.feature.provider.components.ProviderDangerZoneSection
import org.njarasoa.fijerena.feature.provider.components.ProviderFiltersSection
import org.njarasoa.fijerena.feature.provider.components.ProviderSectionTitle
import org.njarasoa.fijerena.feature.provider.components.ProviderSettingsSection
import org.njarasoa.fijerena.feature.provider.components.ProviderTypeDropdown
import org.njarasoa.fijerena.feature.provider.components.QuickConnectDialog
import org.njarasoa.fijerena.feature.provider.components.RemoteM3uForm
import org.njarasoa.fijerena.feature.provider.components.SmbForm
import org.njarasoa.fijerena.feature.provider.components.XtreamForm
import org.njarasoa.fijerena.feature.provider.components.rowNeighbours
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.input.tvPane
import org.njarasoa.fijerena.ui.theme.*
import org.njarasoa.fijerena.ui.theme.TvDimensions

/** Edit Source's Connection column takes this share of the width, the settings column the rest (T-11). */
private const val CONNECTION_COLUMN_WEIGHT = 0.4f
private const val SETTINGS_COLUMN_WEIGHT = 0.6f

// Focus stops of the Connection column (its tvPane memory).
private const val KEY_NAME = "name"
private const val KEY_FIELDS = "fields"
private const val KEY_CANCEL = "cancel"
private const val KEY_SAVE = "save"

/**
 * Add Source (one centred form) and Edit Source (edit mode: two columns, plan Part I T5).
 *
 * Edit Source: the Connection column on the left (read-only type, the login fields, Cancel and
 * Save connection right under them — the only part that needs saving) and a scrolling column on
 * the right with Behaviour (applies immediately), Content filters for the active profile,
 * Library data and the Danger zone. Each column is a `tvPane`: Left/Right move between them,
 * Up/Down stay inside. First focus is the Name edit button, or Manage filters when
 * [focusFilters] is set (the Settings filters hint row; its nav argument is not wired yet).
 */
@Composable
fun TvAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    focusFilters: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: ProviderViewModel =
        viewModel(
            factory = ProviderViewModelFactory(context),
        )
    val isEditMode = editId > 0L
    val appSettings =
        remember {
            org.njarasoa.fijerena.core.network
                .AppSettings(context.applicationContext)
        }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var streamOutputFormat by remember { mutableStateOf("m3u8") }
    var playlistType by remember { mutableStateOf("m3u_plus") }
    var host by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProviderType.XTREAM) }
    // The type of the source being edited: kept on the type list even when it's dev-mode only (R-22).
    var editedType by remember { mutableStateOf<ProviderType?>(null) }
    val isDevMode = remember { appSettings.isDevMode }
    var error by remember { mutableStateOf<String?>(null) }
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    // A background data sync must never block saving or leaving this screen — it runs
    // independently of the form (ProviderSyncManager's own scope), so it isn't disturbed by
    // either.
    val isBusy = saveState is SaveState.Validating || saveState is SaveState.Saving

    // The connection as loaded (edit mode): Back with anything different asks before dropping it.
    // Behaviour settings save as they change, so they never count as unsaved (A-7).
    var loadedConnection by remember { mutableStateOf<List<String>?>(null) }
    val hasUnsavedConnectionEdits =
        isEditMode && loadedConnection.let { it != null && it != listOf(name, url, username, password, host, shareName) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Quick Connect state (Jellyfin only)
    var showQuickConnectDialog by remember { mutableStateOf(false) }

    // Cache management state (edit mode only)
    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var cacheStats by remember { mutableStateOf<XtreamRepository.CacheStats?>(null) }
    var currentProvider by remember { mutableStateOf<org.njarasoa.fijerena.core.network.provider.ProviderEntity?>(null) }

    // Update currentProvider when providers list changes
    LaunchedEffect(providers, editId) {
        if (isEditMode) {
            currentProvider = providers.find { it.id == editId }
        }
    }

    var cacheRefreshTrigger by remember { mutableIntStateOf(0) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearLiveTvCacheDialog by remember { mutableStateOf(false) }
    var showClearMoviesCacheDialog by remember { mutableStateOf(false) }
    var showClearTvShowsCacheDialog by remember { mutableStateOf(false) }

    // Provider settings state (edit mode only)
    var providerSettings by remember { mutableStateOf(ProviderSettings.DEFAULT) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showClearProgressDialog by remember { mutableStateOf(false) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }

    val repository =
        remember {
            val accountManager =
                org.njarasoa.fijerena.core.network
                    .AccountManager(context.applicationContext)
            XtreamRepository(accountManager, context.applicationContext)
        }

    LaunchedEffect(editId, cacheRefreshTrigger) {
        if (isEditMode) {
            cacheStats = providerRepo.getCacheStatsForProvider(editId)
            val ps = providerRepo.getProviderSettings(editId)
            providerSettings = ps
            streamOutputFormat = ps.streamOutputFormat
            playlistType = ps.playlistType
        }
    }

    // A sync started here outlives this screen, so pick it back up when we come back to it
    LaunchedEffect(editId) {
        if (isEditMode) viewModel.observeRunningSync(editId)
    }

    // Refresh UI data when sync completes
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success || syncState is SyncState.Error) {
            cacheRefreshTrigger++
        }
    }

    // Load existing provider data in edit mode directly from the repository
    LaunchedEffect(editId) {
        if (isEditMode) {
            val provider = providerRepo.getProviderById(editId)
            if (provider != null) {
                name = provider.name
                url = provider.url
                // This profile's login: its own for Jellyfin, the shared one otherwise.
                val login = withContext(Dispatchers.IO) { providerRepo.getLogin(provider) }
                username = login.username
                password = login.password
                selectedType =
                    try {
                        ProviderType.valueOf(provider.type)
                    } catch (_: Exception) {
                        // cancellation-ok: no suspension point in the try
                        ProviderType.XTREAM
                    }
                editedType = selectedType
                if (provider.type == "SMB" && provider.config.isNotBlank()) {
                    try {
                        val json = org.json.JSONObject(provider.config)
                        host = json.optString("host", "")
                        shareName = json.optString("share", "")
                    } catch (e: Exception) {
                        // cancellation-ok: no suspension point in the try
                        android.util.Log.e("TvAddProviderScreen", "Failed to parse SMB provider config", e)
                    }
                }
            }
            loadedConnection = listOf(name, url, username, password, host, shareName)
        }
    }

    // Validate the connection fields for the selected type, then save through the ViewModel.
    val submitConnection: () -> Unit = {
        // Validate based on selected provider type
        val validationError =
            when (selectedType) {
                ProviderType.XTREAM -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        url.isBlank() -> resources.getString(R.string.provider_error_url_required)
                        username.isBlank() -> resources.getString(R.string.provider_error_username_required)
                        password.isBlank() -> resources.getString(R.string.provider_error_password_required)
                        else -> null
                    }
                }

                ProviderType.JELLYFIN -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        url.isBlank() -> resources.getString(R.string.provider_error_url_required)
                        username.isBlank() -> resources.getString(R.string.provider_error_username_required)
                        password.isBlank() -> resources.getString(R.string.provider_error_password_required)
                        else -> null
                    }
                }

                ProviderType.SMB -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        host.isBlank() -> resources.getString(R.string.provider_error_host_required)
                        shareName.isBlank() -> resources.getString(R.string.provider_error_share_required)
                        else -> null
                    }
                }

                ProviderType.LOCAL -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        else -> null
                    }
                }

                ProviderType.REMOTE_M3U -> {
                    when {
                        name.isBlank() -> resources.getString(R.string.provider_error_name_required)
                        url.isBlank() -> resources.getString(R.string.provider_error_m3u_url_required)
                        else -> null
                    }
                }
            }

        if (validationError != null) {
            error = validationError
        } else {
            val saveUrl = if (selectedType == ProviderType.SMB) "" else url.trim()
            val saveUsername = username.trim()
            val savePassword = password.trim()
            val saveConfig =
                if (selectedType == ProviderType.SMB) {
                    smbSourceConfig(host.trim(), shareName.trim())
                } else {
                    ""
                }

            viewModel.validateAndSave(
                id = if (isEditMode) editId else null,
                name = name.trim(),
                url = saveUrl,
                username = saveUsername,
                password = savePassword,
                type = selectedType.name,
                config = saveConfig,
                onComplete = onSuccess,
                initialSettings =
                    ProviderSettings(
                        streamOutputFormat = streamOutputFormat,
                        playlistType = playlistType,
                    ),
            )
        }
    }
    val submitLabel =
        when (saveState) {
            is SaveState.Validating -> {
                stringResource(R.string.provider_connecting)
            }

            is SaveState.Saving -> {
                stringResource(R.string.provider_saving)
            }

            else -> {
                if (isEditMode) {
                    stringResource(R.string.provider_save_connection_button)
                } else {
                    stringResource(R.string.common_add)
                }
            }
        }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
        val scale = LocalUiScale.current
        val typography = MaterialTheme.typography
        val scaledDisplaySmall =
            remember(scale, typography) {
                typography.displaySmall.copy(fontSize = typography.displaySmall.fontSize.scaled(scale))
            }
        val scaledBodyMedium =
            remember(scale, typography) {
                typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
            }

        // Type, name and the type's own fields: the same in both modes. In edit mode the type is
        // read-only and skipped by focus, the name field takes [nameModifier] and the type's
        // fields sit in one focus stop of the Connection pane ([fieldsModifier]).
        val nameFocusRequester = remember { FocusRequester() }
        val connectionFields: @Composable (Modifier, Modifier?) -> Unit = { nameModifier, fieldsModifier ->
            // Provider type dropdown (D-pad friendly). Read-only and skipped by focus
            // when editing: the type of an existing source cannot change, so the first
            // D-pad stop is the Name field's edit button.
            ProviderTypeDropdown(
                types = addSourceTypes(isDevMode, editedType),
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                enabled = !isEditMode,
            )

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            // Name field (all types)
            ReadOnlyFieldWithEdit(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = stringResource(R.string.provider_name_label),
                placeholder = stringResource(R.string.provider_name_placeholder_xtream),
                modifier = nameModifier,
                editButtonFocusRequester = if (isEditMode) nameFocusRequester else null,
            )

            // Type-specific fields
            val typeFields: @Composable () -> Unit = {
                when (selectedType) {
                    ProviderType.XTREAM -> {
                        XtreamForm(
                            url = url,
                            onUrlChange = { url = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            onErrorChange = { error = it },
                            onOutputFormatChange = { streamOutputFormat = it },
                            onPlaylistTypeChange = { playlistType = it },
                        )
                    }

                    ProviderType.JELLYFIN -> {
                        JellyfinForm(
                            url = url,
                            onUrlChange = { url = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            isEditMode = isEditMode,
                            isBusy = isBusy,
                            onErrorChange = { error = it },
                            onQuickConnectClick = {
                                showQuickConnectDialog = true
                            },
                        )
                    }

                    ProviderType.SMB -> {
                        SmbForm(
                            host = host,
                            onHostChange = { host = it },
                            shareName = shareName,
                            onShareNameChange = { shareName = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            onErrorChange = { error = it },
                        )
                    }

                    ProviderType.LOCAL -> {
                        // LOCAL type only requires name - folder/file picker will be added later
                    }

                    ProviderType.REMOTE_M3U -> {
                        RemoteM3uForm(
                            url = url,
                            onUrlChange = { url = it },
                            onErrorChange = { error = it },
                        )
                    }
                }
            }
            if (fieldsModifier != null) {
                Column(modifier = fieldsModifier.fillMaxWidth()) { typeFields() }
            } else {
                typeFields()
            }
        }

        val errorText: @Composable () -> Unit = {
            error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                Text(
                    text = errorMsg,
                    style = scaledBodyMedium,
                    color = CinemaError,
                )
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            if (isEditMode) {
                // Back with unsaved connection edits asks first. Taken at the root
                // (docs/NAVIGATION_GUIDE.md, "TV Back on Detail Screens"): the press is claimed
                // on its way back up (onKeyEvent), so a field being edited still gets it first
                // and cancels its edit, and acted on in onPreviewKeyEvent on release, before
                // anything below can swallow it. The BackHandler stays as the fallback.
                var backClaimed by remember { mutableStateOf(false) }
                BackHandler(enabled = hasUnsavedConnectionEdits) { showDiscardDialog = true }

                val connectionPane = rememberPaneFocus()
                val settingsPane = rememberPaneFocus()
                connectionPane.bind(selectedKey = null, firstKey = KEY_NAME, listState = null, indexOf = { -1 })
                settingsPane.bind(selectedKey = null, firstKey = EDIT_SOURCE_FIRST_SETTING_KEY, listState = null, indexOf = { -1 })
                val cancelFocusRequester = remember { FocusRequester() }
                val saveFocusRequester = remember { FocusRequester() }
                val manageFiltersFocusRequester = remember { FocusRequester() }

                // First focus once the source has loaded (its type decides whether there are
                // filters): the Name edit button (T-12), or Manage filters for the filters hint.
                val connectionLoaded = loadedConnection != null
                LaunchedEffect(connectionLoaded) {
                    if (connectionLoaded) {
                        if (focusFilters && selectedType == ProviderType.XTREAM) {
                            manageFiltersFocusRequester.requestFocusWithRetry(fallback = nameFocusRequester)
                        } else {
                            nameFocusRequester.requestFocusWithRetry()
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Back && event.type == KeyEventType.KeyUp && backClaimed) {
                                    backClaimed = false
                                    showDiscardDialog = true
                                    true
                                } else {
                                    false
                                }
                            }.onKeyEvent { event ->
                                if (event.key == Key.Back && event.type == KeyEventType.KeyDown && hasUnsavedConnectionEdits) {
                                    backClaimed = true
                                    true
                                } else {
                                    false
                                }
                            }.padding(
                                horizontal = Spacing.tvSafeMarginHorizontal,
                                vertical = Spacing.tvSafeMarginVertical,
                            ),
                ) {
                    Text(
                        text = stringResource(R.string.provider_edit_title),
                        style = scaledDisplaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Connection: the only part that needs saving, with its buttons right under it.
                        GlassPanel(
                            modifier =
                                Modifier
                                    .weight(CONNECTION_COLUMN_WEIGHT)
                                    .tvPane(connectionPane, exitRight = settingsPane, exitUp = false),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(Spacing.lg.scaled(scale)),
                            ) {
                                ProviderSectionTitle(title = stringResource(R.string.provider_section_connection))
                                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

                                connectionFields(
                                    Modifier.paneItem(connectionPane, KEY_NAME),
                                    Modifier.paneItem(connectionPane, KEY_FIELDS),
                                )

                                errorText()

                                Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))

                                // Down from the last field lands on Cancel, not on whichever
                                // button happens to sit under the field's edit button.
                                Row(
                                    modifier =
                                        Modifier
                                            .focusProperties {
                                                onEnter = {
                                                    if (requestedFocusDirection == FocusDirection.Down) {
                                                        cancelFocusRequester.requestFocus(FocusDirection.Enter)
                                                    }
                                                }
                                            }.focusGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                                ) {
                                    CinemaSecondaryButton(
                                        onClick = onBack,
                                        enabled = !isBusy,
                                        text = stringResource(R.string.common_cancel),
                                        modifier =
                                            Modifier
                                                .paneItem(connectionPane, KEY_CANCEL)
                                                .focusRequester(cancelFocusRequester)
                                                .rowNeighbours(right = saveFocusRequester),
                                    )
                                    CinemaPrimaryButton(
                                        onClick = submitConnection,
                                        enabled = !isBusy,
                                        text = submitLabel,
                                        modifier =
                                            Modifier
                                                .paneItem(connectionPane, KEY_SAVE)
                                                .focusRequester(saveFocusRequester)
                                                .rowNeighbours(left = cancelFocusRequester),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(Spacing.lg.scaled(scale)))

                        // Everything that applies on its own, then the destructive actions last.
                        GlassPanel(
                            modifier =
                                Modifier
                                    .weight(SETTINGS_COLUMN_WEIGHT)
                                    .tvPane(settingsPane, exitLeft = connectionPane, exitUp = false),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(Spacing.lg.scaled(scale)),
                            ) {
                                ProviderSettingsSection(
                                    providerType = selectedType,
                                    providerSettings = providerSettings,
                                    onUpdateSettings = { newSettings ->
                                        coroutineScope.launch {
                                            providerRepo.updateProviderSettings(editId, newSettings)
                                            providerSettings = newSettings
                                            streamOutputFormat = newSettings.streamOutputFormat
                                            playlistType = newSettings.playlistType
                                        }
                                    },
                                    pane = settingsPane,
                                )

                                if (selectedType == ProviderType.XTREAM) {
                                    SectionDivider()
                                    ProviderFiltersSection(
                                        providerSettings = providerSettings,
                                        onManageFiltersClick = { showCategoryFilterDialog = true },
                                        pane = settingsPane,
                                        manageFocusRequester = manageFiltersFocusRequester,
                                    )
                                }

                                SectionDivider()
                                CacheManagementSection(
                                    cacheStats = cacheStats,
                                    pane = settingsPane,
                                    syncState = syncState,
                                    isXtream = selectedType == ProviderType.XTREAM,
                                    lastSyncedAtMs = currentProvider?.lastSyncedAtMs ?: 0L,
                                    lastSyncDurationMs = currentProvider?.lastSyncDurationMs ?: 0L,
                                    lastSyncError = currentProvider?.lastSyncError,
                                    lastSyncInserted = currentProvider?.lastSyncInserted ?: 0,
                                    lastSyncUpdated = currentProvider?.lastSyncUpdated ?: 0,
                                    lastSyncDeleted = currentProvider?.lastSyncDeleted ?: 0,
                                    onSyncClick = { viewModel.syncProvider(editId) },
                                )

                                SectionDivider()
                                ProviderDangerZoneSection(
                                    cacheStats = cacheStats,
                                    pane = settingsPane,
                                    onClearFavoritesClick = { showClearFavoritesDialog = true },
                                    onClearProgressClick = { showClearProgressDialog = true },
                                    onClearAllCacheClick = { showClearCacheDialog = true },
                                    onClearLiveTvClick = { showClearLiveTvCacheDialog = true },
                                    onClearMoviesClick = { showClearMoviesCacheDialog = true },
                                    onClearTvShowsClick = { showClearTvShowsCacheDialog = true },
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = Spacing.tvSafeMarginHorizontal,
                                vertical = Spacing.tvSafeMarginVertical,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassPanel(modifier = Modifier.width(TvDimensions.formFieldWidth.scaled(scale))) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(Spacing.lg.scaled(scale)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.provider_add_title),
                                style = scaledDisplaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                            connectionFields(Modifier, null)

                            errorText()

                            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

                            // Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale), Alignment.CenterHorizontally),
                            ) {
                                CinemaSecondaryButton(
                                    onClick = onBack,
                                    enabled = !isBusy,
                                    text = stringResource(R.string.common_cancel),
                                )

                                CinemaPrimaryButton(
                                    onClick = submitConnection,
                                    enabled = !isBusy,
                                    text = submitLabel,
                                )
                            }
                        }
                    } // GlassPanel
                }
            }

            // Discard unsaved connection edits (edit mode, Back)
            if (showDiscardDialog) {
                CinemaAlertDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = { Text(stringResource(R.string.provider_discard_changes_title), color = CinemaTextPrimary) },
                    text = { Text(stringResource(R.string.provider_discard_changes_message), color = CinemaTextSecondary) },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                showDiscardDialog = false
                                onBack()
                            },
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = CinemaError,
                                    contentColor = CinemaTextPrimary,
                                ),
                        ) { Text(stringResource(R.string.provider_discard_button)) }
                    },
                    dismissButton = {
                        CinemaDialogActionButton(
                            onClick = { showDiscardDialog = false },
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = CinemaSurfaceVariant,
                                    contentColor = CinemaTextPrimary,
                                ),
                        ) { Text(stringResource(R.string.provider_keep_editing_button)) }
                    },
                    containerColor = CinemaSurface,
                )
            }

            // Validation failure dialog
            val failedState = saveState as? SaveState.ValidationFailed
            if (failedState != null) {
                val saveUrl = if (selectedType == ProviderType.SMB) "" else url.trim()
                val saveConfig =
                    if (selectedType == ProviderType.SMB) {
                        smbSourceConfig(host.trim(), shareName.trim())
                    } else {
                        ""
                    }

                CinemaAlertDialog(
                    onDismissRequest = { viewModel.resetSaveState() },
                    title = {
                        Text(
                            "Connection Failed",
                            color = CinemaTextPrimary,
                        )
                    },
                    text = {
                        Text(
                            failedState.errorMessage,
                            color = CinemaTextSecondary,
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
                                    onComplete = onSuccess,
                                    initialSettings =
                                        ProviderSettings(
                                            streamOutputFormat = streamOutputFormat,
                                            playlistType = playlistType,
                                        ),
                                )
                            },
                            text = stringResource(R.string.provider_save_anyway),
                        )
                    },
                    dismissButton = {
                        CinemaSecondaryButton(
                            onClick = { viewModel.resetSaveState() },
                            text = stringResource(R.string.provider_go_back),
                        )
                    },
                    containerColor = CinemaSurface,
                )
            }

            // Cache confirmation dialogs
            if (showClearCacheDialog) {
                ConfirmActionDialog(
                    title = stringResource(R.string.provider_clear_cache_all_title),
                    text = stringResource(R.string.provider_clear_cache_all_message),
                    confirmText = stringResource(R.string.provider_clear_all_button),
                    onConfirm = {
                        coroutineScope.launch {
                            providerRepo.clearAllCacheForProvider(editId)
                            cacheRefreshTrigger++
                            showClearCacheDialog = false
                        }
                    },
                    onDismiss = { showClearCacheDialog = false },
                )
            }

            if (showClearLiveTvCacheDialog) {
                ConfirmActionDialog(
                    title = stringResource(R.string.provider_clear_cache_live_title),
                    text = stringResource(R.string.provider_clear_cache_live_message),
                    confirmText = stringResource(R.string.provider_clear_button),
                    onConfirm = {
                        coroutineScope.launch {
                            providerRepo.clearCacheForProviderContentType(editId, ContentType.LIVE_TV)
                            cacheRefreshTrigger++
                            showClearLiveTvCacheDialog = false
                        }
                    },
                    onDismiss = { showClearLiveTvCacheDialog = false },
                )
            }

            if (showClearMoviesCacheDialog) {
                ConfirmActionDialog(
                    title = stringResource(R.string.provider_clear_cache_movies_title),
                    text = stringResource(R.string.provider_clear_cache_movies_message),
                    confirmText = stringResource(R.string.provider_clear_button),
                    onConfirm = {
                        coroutineScope.launch {
                            providerRepo.clearCacheForProviderContentType(editId, ContentType.MOVIES)
                            cacheRefreshTrigger++
                            showClearMoviesCacheDialog = false
                        }
                    },
                    onDismiss = { showClearMoviesCacheDialog = false },
                )
            }

            if (showClearTvShowsCacheDialog) {
                ConfirmActionDialog(
                    title = stringResource(R.string.provider_clear_cache_tvshows_title),
                    text = stringResource(R.string.provider_clear_cache_tvshows_message),
                    confirmText = stringResource(R.string.provider_clear_button),
                    onConfirm = {
                        coroutineScope.launch {
                            providerRepo.clearCacheForProviderContentType(editId, ContentType.TV_SHOWS)
                            cacheRefreshTrigger++
                            showClearTvShowsCacheDialog = false
                        }
                    },
                    onDismiss = { showClearTvShowsCacheDialog = false },
                )
            }

            // Clear Favorites Confirmation Dialog
            if (showClearFavoritesDialog) {
                ConfirmActionDialog(
                    title = stringResource(R.string.provider_clear_favorites_title),
                    text = stringResource(R.string.provider_clear_favorites_message),
                    confirmText = stringResource(R.string.provider_clear_all_button),
                    onConfirm = {
                        coroutineScope.launch {
                            // The live per-provider store, not a fresh XtreamRepository on provider 0:
                            // going through AppContainer keeps the cached instance's in-memory
                            // favorites view consistent with what was just removed from disk.
                            AppContainer.getInstance(context).getMediaRepository(editId).clearFavorites()
                        }
                        showClearFavoritesDialog = false
                    },
                    onDismiss = { showClearFavoritesDialog = false },
                )
            }

            // Clear Progress Confirmation Dialog
            if (showClearProgressDialog) {
                ConfirmActionDialog(
                    title = stringResource(R.string.provider_clear_progress_title),
                    text = stringResource(R.string.provider_clear_progress_message),
                    confirmText = stringResource(R.string.provider_clear_all_button),
                    onConfirm = {
                        coroutineScope.launch {
                            AppContainer.getInstance(context).getMediaRepository(editId).clearWatchHistory()
                        }
                        showClearProgressDialog = false
                    },
                    onDismiss = { showClearProgressDialog = false },
                )
            }

            // Category Filter Dialog
            if (showCategoryFilterDialog) {
                CategoryFilterDialog(
                    currentFilters = providerSettings.categoryFilters,
                    onSave = { newFilters ->
                        coroutineScope.launch {
                            val newSettings = providerSettings.copy(categoryFilters = newFilters)
                            providerRepo.updateProviderSettings(editId, newSettings)
                            providerSettings = newSettings
                        }
                        showCategoryFilterDialog = false
                    },
                    onDismiss = { showCategoryFilterDialog = false },
                )
            }

            // Quick Connect dialog (Jellyfin)
            if (showQuickConnectDialog) {
                QuickConnectDialog(
                    url = url,
                    onSuccess = { nameVal, usernameVal, token, userId ->
                        showQuickConnectDialog = false
                        viewModel.quickConnectSave(
                            // Editing: sign this profile in to this provider rather
                            // than add a second one, as whoever Jellyfin says it is.
                            id = if (isEditMode) editId else null,
                            name = name.ifBlank { nameVal },
                            url = url.trimEnd('/'),
                            username = if (isEditMode) usernameVal else username.ifBlank { usernameVal },
                            token = token,
                            userId = userId,
                            onComplete = onSuccess,
                        )
                    },
                    onDismiss = { showQuickConnectDialog = false },
                )
            }
        }
    } // CompositionLocalProvider
}

/** Separator between two sections of the Edit Source settings column. */
@Composable
private fun SectionDivider() {
    val scale = LocalUiScale.current
    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
    HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
}
