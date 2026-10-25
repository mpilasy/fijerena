@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.navigation.ProfilePageRow
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.uiThemeNameRes
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.core.ui.utils.rememberDeveloperModeTapHandler
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbKeyViewModel
import org.njarasoa.fijerena.feature.settings.components.*
import org.njarasoa.fijerena.feature.settings.components.SettingsRow
import org.njarasoa.fijerena.ui.components.TvContentText
import org.njarasoa.fijerena.ui.components.TvLeaveChildPinDialog
import org.njarasoa.fijerena.ui.components.TvPinDialog
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.rememberPaneFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.input.tvPane
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUiScaleChanged: (Float?) -> Unit = {},
    onManageProviders: () -> Unit = {},
    onLiveSync: () -> Unit = {},
    /** The one Guide Sources page, for every source (Sources & guide's Guide sources). */
    onGuideSources: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onDeviceInfo: () -> Unit = {},
    onLicenses: () -> Unit = {},
    onTerms: () -> Unit = {},
    onProfileSwitched: () -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val profilesMessage by profilesViewModel.message.collectAsStateWithLifecycle()
    // Becoming Expert (the 7 taps on the version) asks an Expert's PIN while one has a PIN
    // (the profile pin plan, Phase 4).
    var askingAdminPin by remember { mutableStateOf<List<String>?>(null) }
    val enableExpert = {
        val holders = profilesViewModel.adminPinHolders()
        if (holders.isEmpty()) {
            viewModel.enableDevMode()
            true
        } else {
            askingAdminPin = holders
            false
        }
    }
    askingAdminPin?.let { holders ->
        val enabledText = stringResource(R.string.settings_developer_mode_enabled)
        TvPinDialog(
            state =
                rememberPinPromptState(
                    profilesViewModel.pins,
                    PinRequest.AnyOf(holders, stringResource(R.string.pin_enter_admin)),
                ) {
                    askingAdminPin = null
                    viewModel.enableDevMode()
                    android.widget.Toast
                        .makeText(context, enabledText, android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
            onCancel = { askingAdminPin = null },
        )
    }
    // "Switch to this profile" away from a child profile asks an Expert's PIN (the child lock).
    TvLeaveChildPinDialog(profilesViewModel, onProfileSwitched)
    // Settings opens without the PIN; what changes shared setup or another profile (the admin's
    // groups, a profile's page) asks the PIN of the profile in use first.
    val inUse by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    var guardedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guarded: (() -> Unit) -> Unit = { action ->
        if (inUse?.let { profilesViewModel.needsPin(it.id) } == true) guardedAction = action else action()
    }
    guardedAction?.let { action ->
        inUse?.let { profile ->
            TvPinDialog(
                state =
                    rememberPinPromptState(
                        profilesViewModel.pins,
                        PinRequest.Check(profile.id, stringResource(R.string.pin_enter_for, profile.name)),
                    ) {
                        guardedAction = null
                        action()
                    },
                onCancel = { guardedAction = null },
            )
        }
    }
    // Add profile has its own rule, the same as on the picker (ProfilesViewModel.addProfilePins).
    var addProfileAction by remember { mutableStateOf<Pair<List<String>, () -> Unit>?>(null) }
    val addProfileGuarded: (() -> Unit) -> Unit = { action ->
        val holders = profilesViewModel.addProfilePinHolders()
        if (holders.isEmpty()) action() else addProfileAction = holders to action
    }
    addProfileAction?.let { (holders, action) ->
        TvPinDialog(
            state =
                rememberPinPromptState(profilesViewModel.pins, PinRequest.AnyOf(holders, stringResource(R.string.pin_enter_admin))) {
                    addProfileAction = null
                    action()
                },
            onCancel = { addProfileAction = null },
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Guide auto-refresh and maintenance are device-wide, so no provider id (A-9, T6).
    val epgViewModel: EpgManagementViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val tmdbKeyViewModel: TmdbKeyViewModel = viewModel(factory = SettingsViewModelFactory(context))

    val providerRepo = remember { ProviderRepository(context.applicationContext) }
    val exportManager = remember { SettingsExportManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var hadProviderOnLoad by remember { mutableStateOf<Boolean?>(null) }

    // Export/Import transient state (not in ViewModel yet as it involves SAF Launchers)
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }
    var pendingParsedImport by remember { mutableStateOf<SettingsExportManager.ParsedImport?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var pendingImportOptions by remember { mutableStateOf(SettingsExportManager.ImportOptions()) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> pendingExportUri = uri }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> pendingImportUri = uri }

    // Process export in LaunchedEffect (survives recomposition)
    LaunchedEffect(pendingExportUri) {
        val uri = pendingExportUri ?: return@LaunchedEffect
        val success = exportManager.exportToUri(uri)
        viewModel.setExportImportMessage(
            if (success) resources.getString(R.string.settings_export_success) else resources.getString(R.string.settings_export_failed),
        )
        pendingExportUri = null
    }

    // Parse import file (URI) and show options dialog
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportUri(uri)
        parseResult
            .onSuccess { parsed ->
                pendingParsedImport = parsed
                pendingImportOptions = SettingsExportManager.ImportOptions()
                showImportOptionsDialog = true
            }.onFailure { e ->
                viewModel.setExportImportMessage(resources.getString(R.string.settings_import_failed, e.message))
            }
        pendingImportUri = null
    }

    // Parse import file (Path) and show options dialog
    LaunchedEffect(pendingImportPath) {
        val path = pendingImportPath ?: return@LaunchedEffect
        val parseResult = exportManager.parseImportPath(path)
        parseResult
            .onSuccess { parsed ->
                pendingParsedImport = parsed
                pendingImportOptions = SettingsExportManager.ImportOptions()
                showImportOptionsDialog = true
            }.onFailure { e ->
                viewModel.setExportImportMessage(resources.getString(R.string.settings_import_failed, e.message))
            }
        pendingImportPath = null
    }

    // Re-check provider when returning from provider management screens
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            epgViewModel.userMessage.collect { message ->
                android.widget.Toast
                    .makeText(context, message.asString(context), android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            val hasProvider = providerRepo.getProviderCount() > 0
            val hadOnLoad = hadProviderOnLoad
            viewModel.refreshProviderInfo()
            viewModel.refreshDevMode()
            tmdbKeyViewModel.refresh()

            if (hadOnLoad == null) {
                hadProviderOnLoad = hasProvider
            } else if (hadOnLoad == false && hasProvider) {
                hadProviderOnLoad = true
                onProviderChanged()
            }
        }
    }

    val scale = LocalUiScale.current
    val onVersionClick = rememberDeveloperModeTapHandler(uiState.isDevMode, enableExpert)

    // Two panes (plan Part I, B): a rail of the groups on the left, the selected group's
    // rows on the right. The rail row is saveable so a language change (Activity recreate) and the
    // Back round trip from a child screen come back to the same group.
    var selectedGroup by rememberSaveable { mutableStateOf(SettingsGroup.PROFILES) }
    // Without Expert mode (admin, for now) the shared setup groups are not listed, and a child
    // profile sees only its page and About; a group left selected when Expert mode was turned off
    // falls back to Profiles.
    val visibleGroups = SettingsGroup.visibleFor(admin = uiState.isDevMode, child = uiState.isChildProfile)
    if (selectedGroup !in visibleGroups) selectedGroup = SettingsGroup.PROFILES
    val railPane = rememberPaneFocus()
    val contentPane = rememberPaneFocus()
    val focusManager = LocalFocusManager.current
    // One list state per group: a group change starts its pane at the top, and the Back
    // round trip restores the scroll of the group it left.
    val listState = rememberSaveable(selectedGroup, saver = LazyListState.Saver) { LazyListState() }
    railPane.bind(
        selectedKey = selectedGroup.name,
        firstKey = SettingsGroup.entries.first().name,
        listState = null,
        indexOf = { -1 },
    )
    // The pane's entry row is the group's first focusable (each card puts `paneItem` on it); the
    // remembered row of a previous group is not in this one, so entry falls through to it.
    val contentEntryKey = selectedGroup.entryKey
    contentPane.bind(
        selectedKey = null,
        firstKey = contentEntryKey,
        listState = listState,
        indexOf = { key -> if (key == contentEntryKey) 0 else -1 },
    )

    // Back from Manage Sources, Live sync, Diagnostics, EPG Management or Edit Source lands on
    // the control that opened it, in its group, at the scroll position the pane had.
    val returnFocus = rememberNavReturnFocus()
    NavReturnFocusEffect(returnFocus, listState = listState)

    // First open: the rail row of the selected group. A pending hand-back (Back from a child
    // screen, the Language recreate) or an open picker has the last word instead.
    var picker by rememberSaveable { mutableStateOf<SettingPicker?>(null) }
    // The profile whose page (P9) is open in the pane, as SettingPicker.PROFILE.
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    // The row the profile page opens at, when Display or Playback opened it (G1).
    var profilePageStart by rememberSaveable { mutableStateOf<ProfilePageRow?>(null) }
    // The list whose pane is open, as SettingPicker.PARENTAL_LIST.
    var parentalList by rememberSaveable { mutableStateOf<ParentalList?>(null) }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && picker == null) railPane.focusEntry()
    }

    // Choice settings drill into a picker that replaces the group's rows inside the pane. The row
    // that opened it is recorded like a navigation away, and gets focus back when the picker
    // closes — that close is not a lifecycle resume, so the hand-back runs here rather than in
    // NavReturnFocusEffect (which still covers Language's Activity recreate).
    val openPicker: (SettingPicker) -> Unit = { target ->
        returnFocus.leaveFrom(target.returnKey, listState)
        picker = target
    }
    val closePicker: () -> Unit = {
        picker = null
        coroutineScope.launch {
            returnFocus.restoreScroll(listState)
            returnFocus.requester.requestFocusWithRetry()
            returnFocus.clear()
        }
    }
    val openProfile: (ProfileUi) -> Unit = { profile ->
        returnFocus.leaveFrom(profileReturnKey(profile.id), listState)
        editingProfileId = profile.id
        picker = SettingPicker.PROFILE
    }
    // Display's and Playback's rows open the page of the profile in use at their row. As from
    // Profiles: an Expert gives the PIN first; without Expert mode it is the viewer's own page.
    val openOwnPageAt: (ProfilePageRow) -> Unit = { row ->
        inUse?.let { profile ->
            val open = {
                returnFocus.leaveFrom(profilePageReturnKey(row), listState)
                editingProfileId = profile.id
                profilePageStart = row
                picker = SettingPicker.PROFILE
            }
            if (uiState.isDevMode) guarded(open) else open()
        }
    }
    // A profile just added opens its page (sources, filters, PIN are the next things to set) once
    // the list shows it.
    var openWhenListed by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(profiles, openWhenListed) {
        profiles.firstOrNull { it.id == openWhenListed }?.let {
            openWhenListed = null
            openProfile(it)
        }
    }
    // A deleted profile has no row to go back to: the group's entry row takes focus instead.
    val closeProfile: (deleted: Boolean) -> Unit = { deleted ->
        picker = null
        editingProfileId = null
        profilePageStart = null
        coroutineScope.launch {
            returnFocus.restoreScroll(listState)
            if (deleted || !returnFocus.requester.requestFocusWithRetry()) contentPane.focusEntry()
            returnFocus.clear()
        }
    }
    // A row that leads to another group (Parental controls and a child's page lead to each other's
    // group): that group's rows, focus on its first row once they show. Back from there goes to
    // the rail, as in every group.
    var focusGroupEntry by remember { mutableStateOf(false) }
    val openGroup: (SettingsGroup) -> Unit = { group ->
        picker = null
        editingProfileId = null
        profilePageStart = null
        returnFocus.clear()
        selectedGroup = group
        focusGroupEntry = true
    }
    LaunchedEffect(selectedGroup, focusGroupEntry) {
        if (focusGroupEntry) {
            focusGroupEntry = false
            contentPane.focusEntry()
        }
    }

    // Back while in the pane returns to the rail row (Left does the same, via tvPane); Back on
    // the rail leaves Settings as before. Taken in onPreviewKeyEvent on the root — a BackHandler
    // misses the first press while a button holds focus (docs/NAVIGATION_GUIDE.md, "TV Back on
    // Detail Screens"); the BackHandler stays as the fallback. A picker takes its own Back.
    var paneHasFocus by remember { mutableStateOf(false) }
    val backToRail = { coroutineScope.launch { railPane.focusEntry() } }
    // Without Expert mode a profile can only edit itself: Profiles shows the page of the profile in
    // use in place of the list of profiles, without taking focus from the rail. The page takes its
    // own Back (after the name field, which keeps Back to cancel its edit) and leaves to the rail;
    // a new key drops what was left unsaved.
    val ownProfilePage = picker == null && selectedGroup == SettingsGroup.PROFILES && !uiState.isDevMode
    var ownProfilePageKey by remember { mutableIntStateOf(0) }
    val leaveOwnProfilePage = {
        ownProfilePageKey++
        backToRail()
    }
    BackHandler(enabled = paneHasFocus && picker == null && !ownProfilePage) { backToRail() }

    Box(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.key == Key.Back && paneHasFocus && picker == null && !ownProfilePage) {
                    if (event.type == KeyEventType.KeyUp) backToRail()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            // The profile in use, as the subtitle (T-7); there is no source in use any more.
            TvScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = profiles.firstOrNull { it.isActive }?.name,
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Rail: Up/Down swap the pane live, Right (or OK) enters it on its first row, Left
                // goes to the navigation rail. tvPane takes Left (no pane on that side), so each row
                // moves focus itself, along its leftToRail.
                Column(
                    modifier =
                        Modifier
                            .weight(RAIL_WEIGHT)
                            .fillMaxHeight()
                            .tvPane(railPane, exitRight = contentPane, exitUp = false),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    visibleGroups.forEach { group ->
                        TvInputListItem(
                            selected = group == selectedGroup,
                            onClick = {
                                selectedGroup = group
                                coroutineScope.launch { contentPane.focusEntry() }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .paneItem(railPane, group.name)
                                    .onFocusChanged { if (it.isFocused) selectedGroup = group }
                                    .onKeyEvent { event ->
                                        val isLeft = event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown
                                        if (isLeft) focusManager.moveFocus(FocusDirection.Left)
                                        isLeft
                                    }.leftToRail(),
                            headlineContent = { TvContentText(stringResource(group.titleFor(uiState.isDevMode)), role = TextRole.Heading) },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.lg))

                // Pane: the group's rows, or the open picker in their place, on one panel the
                // height of the rail. Left (and Back) go back to the rail row; Up at the top stays
                // (the header has nothing to focus).
                GlassPanel(
                    modifier =
                        Modifier
                            .weight(PANE_WEIGHT)
                            .fillMaxHeight()
                            .onFocusChanged { paneHasFocus = it.hasFocus }
                            .tvPane(contentPane, exitLeft = railPane, exitUp = false),
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
                        when (picker ?: SettingPicker.PROFILE.takeIf { ownProfilePage }) {
                            SettingPicker.TEXT_SIZE -> {
                                UiScalePickerPane(
                                    uiScale = uiState.uiScale,
                                    onPick = { newScale ->
                                        viewModel.updateUiScale(newScale)
                                        onUiScaleChanged(newScale)
                                    },
                                    onBack = closePicker,
                                )
                            }

                            SettingPicker.RECENT_SIZE -> {
                                RecentRowSizePickerPane(
                                    size = uiState.recentRowSize,
                                    onPick = { size -> viewModel.updateRecentRowSize(size) },
                                    onBack = closePicker,
                                )
                            }

                            SettingPicker.WATCH_DELAY -> {
                                WatchDelayPickerPane(
                                    watchDelaySeconds = uiState.watchDelaySeconds,
                                    onPick = { seconds -> viewModel.updateWatchDelay(seconds) },
                                    onBack = closePicker,
                                )
                            }

                            SettingPicker.LANGUAGE -> {
                                LanguagePickerPane(
                                    selectedLanguage = uiState.language,
                                    onPick = { newLang ->
                                        viewModel.updateLanguage(newLang)
                                        (context as? android.app.Activity)?.recreate()
                                    },
                                    onBack = closePicker,
                                )
                            }

                            SettingPicker.GUIDE_MAINTENANCE -> {
                                GuideMaintenancePane(viewModel = epgViewModel, onBack = closePicker)
                            }

                            SettingPicker.TMDB_KEY -> {
                                TmdbKeyPane(viewModel = tmdbKeyViewModel, onBack = closePicker)
                            }

                            SettingPicker.PARENTAL_LIST -> {
                                parentalList?.let { ParentalListPane(list = it, onBack = closePicker) }
                            }

                            SettingPicker.PARENTAL_CHECK -> {
                                ParentalCheckPane(onBack = closePicker)
                            }

                            SettingPicker.PROFILE -> {
                                val profile = profiles.firstOrNull { if (ownProfilePage) it.isActive else it.id == editingProfileId }
                                // Gone (deleted on another device): close. An empty list is still loading.
                                if (profile == null) {
                                    LaunchedEffect(profiles) { if (profiles.isNotEmpty() && !ownProfilePage) closeProfile(true) }
                                } else {
                                    key(ownProfilePageKey) {
                                        ProfileEditPane(
                                            profile = profile,
                                            viewModel = profilesViewModel,
                                            canDelete = !profile.isActive && profiles.size > 1,
                                            onSaved = {
                                                viewModel.refreshDevMode()
                                                if (ownProfilePage) leaveOwnProfilePage() else closeProfile(false)
                                            },
                                            onSwitch = { profilesViewModel.switchTo(profile.id, onProfileSwitched) },
                                            onDeleted = { closeProfile(true) },
                                            onBack = { if (ownProfilePage) leaveOwnProfilePage() else closeProfile(false) },
                                            scale = scale,
                                            adminView = uiState.isDevMode,
                                            focusOnOpen = !ownProfilePage,
                                            title =
                                                if (ownProfilePage || profilePageStart != null) {
                                                    stringResource(R.string.profile_own_page_title)
                                                } else {
                                                    stringResource(R.string.profile_dialog_edit_title)
                                                },
                                            startAt = profilePageStart,
                                            onParentalControls = {
                                                viewModel.refreshDevMode()
                                                openGroup(SettingsGroup.PARENTAL_CONTROLS)
                                            },
                                            entryModifier =
                                                if (ownProfilePage) {
                                                    Modifier.paneItem(
                                                        contentPane,
                                                        contentEntryKey,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                        )
                                    }
                                }
                            }

                            null -> {
                                val entryModifier = Modifier.paneItem(contentPane, contentEntryKey)
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = Spacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    when (selectedGroup) {
                                        SettingsGroup.PROFILES -> {
                                            item {
                                                ProfilesSettingsCard(
                                                    profiles = profiles,
                                                    message = profilesMessage,
                                                    newProfileColorIndex = profilesViewModel::nextFreeColorIndex,
                                                    onAdd = { name, colorIndex, child ->
                                                        addProfileGuarded {
                                                            profilesViewModel.addProfile(name, colorIndex, child) { openWhenListed = it }
                                                        }
                                                    },
                                                    onEdit = { profile -> guarded { openProfile(profile) } },
                                                    onDismissMessage = profilesViewModel::clearMessage,
                                                    scale = scale,
                                                    firstRowModifier = entryModifier,
                                                    rowModifier = { profile ->
                                                        returnFocus.requesterFor(profileReturnKey(profile.id))?.let {
                                                            Modifier.focusRequester(it)
                                                        } ?: Modifier
                                                    },
                                                    pinNudge = profilesViewModel.pins.takeIf { profilesViewModel.needsAdminPin() },
                                                    pinNudgeLastExpert =
                                                        profiles.firstOrNull { it.isActive }?.let {
                                                            profilesViewModel.isLastExpert(
                                                                it.id,
                                                            )
                                                        } ==
                                                            true,
                                                    noAdminPin = profilesViewModel.adminPinHolders().isEmpty(),
                                                )
                                            }
                                        }

                                        SettingsGroup.PARENTAL_CONTROLS -> {
                                            item {
                                                ParentalControlsCard(
                                                    // A list changes shared setup: the PIN first, like Sources.
                                                    onOpenList = { list ->
                                                        guarded {
                                                            returnFocus.leaveFrom(parentalListReturnKey(list), listState)
                                                            parentalList = list
                                                            picker = SettingPicker.PARENTAL_LIST
                                                        }
                                                    },
                                                    onOpenCheck = {
                                                        returnFocus.leaveFrom(PARENTAL_CHECK_RETURN_KEY, listState)
                                                        picker = SettingPicker.PARENTAL_CHECK
                                                    },
                                                    onOpenProfiles = { openGroup(SettingsGroup.PROFILES) },
                                                    focusRequesterFor = returnFocus::requesterFor,
                                                    firstRowModifier = entryModifier,
                                                )
                                            }
                                        }

                                        SettingsGroup.PLAYBACK -> {
                                            item {
                                                PlaybackSettingsCard(
                                                    watchDelaySeconds = uiState.watchDelaySeconds,
                                                    onOpenWatchDelayPicker = { openPicker(SettingPicker.WATCH_DELAY) },
                                                    watchDelayRowFocusRequester =
                                                        returnFocus.requesterFor(
                                                            SettingPicker.WATCH_DELAY.returnKey,
                                                        ),
                                                    watchDelayRowModifier = entryModifier,
                                                )
                                            }
                                            item {
                                                val autoplay =
                                                    remember(inUse, picker) {
                                                        inUse?.let { profilesViewModel.settingsOf(it.id).autoplayNextEpisode } == true
                                                    }
                                                ProfilePageRowLink(
                                                    title = stringResource(R.string.settings_autoplay_next_episode_title),
                                                    value = stringResource(if (autoplay) R.string.common_on else R.string.common_off),
                                                    onClick = { openOwnPageAt(ProfilePageRow.AUTOPLAY) },
                                                    focusRequester =
                                                        returnFocus.requesterFor(
                                                            profilePageReturnKey(ProfilePageRow.AUTOPLAY),
                                                        ),
                                                )
                                            }
                                        }

                                        SettingsGroup.DISPLAY -> {
                                            item {
                                                SettingsSection(description = stringResource(R.string.settings_scope_device_section)) {
                                                    UiScaleSettingsRow(
                                                        uiScale = uiState.uiScale,
                                                        onOpenPicker = { openPicker(SettingPicker.TEXT_SIZE) },
                                                        rowFocusRequester = returnFocus.requesterFor(SettingPicker.TEXT_SIZE.returnKey),
                                                        modifier = entryModifier,
                                                    )
                                                    RecentRowSizeRow(
                                                        size = uiState.recentRowSize,
                                                        onOpenPicker = { openPicker(SettingPicker.RECENT_SIZE) },
                                                        rowFocusRequester = returnFocus.requesterFor(SettingPicker.RECENT_SIZE.returnKey),
                                                    )
                                                    LanguageSettingsRow(
                                                        selectedLanguage = uiState.language,
                                                        onOpenPicker = { openPicker(SettingPicker.LANGUAGE) },
                                                        rowFocusRequester = returnFocus.requesterFor(SettingPicker.LANGUAGE.returnKey),
                                                    )
                                                }
                                            }
                                            item {
                                                val uiThemeId =
                                                    remember(inUse, picker) { inUse?.let { profilesViewModel.uiThemeOf(it.id) } }
                                                ProfilePageRowLink(
                                                    title = stringResource(R.string.settings_theme_and_look_title),
                                                    value = uiThemeId?.let { stringResource(uiThemeNameRes(it)) },
                                                    onClick = { openOwnPageAt(ProfilePageRow.LOOK) },
                                                    focusRequester = returnFocus.requesterFor(profilePageReturnKey(ProfilePageRow.LOOK)),
                                                )
                                            }
                                        }

                                        SettingsGroup.SOURCE_GUIDE -> {
                                            item {
                                                ProviderSettingsCard(
                                                    sourceNames = uiState.profileSources.map { it.second },
                                                    onManageProviders = {
                                                        guarded {
                                                            returnFocus.leaveFrom(RETURN_PROVIDERS, listState)
                                                            onManageProviders()
                                                        }
                                                    },
                                                    manageRowFocusRequester = returnFocus.requesterFor(RETURN_PROVIDERS),
                                                    manageRowModifier = entryModifier,
                                                )
                                            }
                                            // The guide: its sources (per source) and its data, side by side.
                                            item {
                                                SettingsSection(title = stringResource(R.string.provider_section_guide)) {
                                                    // Only sources with live channels have guide sources: one page for
                                                    // all of them, shown when the device has one.
                                                    if (uiState.hasGuideSources) {
                                                        SettingsRow(
                                                            title = stringResource(R.string.epg_management_screen_title),
                                                            description = stringResource(R.string.settings_guide_sources_desc),
                                                            onClick = {
                                                                guarded {
                                                                    returnFocus.leaveFrom(RETURN_GUIDE_SOURCES, listState)
                                                                    onGuideSources()
                                                                }
                                                            },
                                                            focusRequester = returnFocus.requesterFor(RETURN_GUIDE_SOURCES),
                                                        )
                                                    }
                                                    GuideMaintenanceRow(
                                                        viewModel = epgViewModel,
                                                        onOpen = { guarded { openPicker(SettingPicker.GUIDE_MAINTENANCE) } },
                                                        focusRequester =
                                                            returnFocus.requesterFor(
                                                                SettingPicker.GUIDE_MAINTENANCE.returnKey,
                                                            ),
                                                    )
                                                }
                                            }
                                            item {
                                                TmdbKeySettingsRow(
                                                    viewModel = tmdbKeyViewModel,
                                                    onOpen = { guarded { openPicker(SettingPicker.TMDB_KEY) } },
                                                    focusRequester = returnFocus.requesterFor(SettingPicker.TMDB_KEY.returnKey),
                                                )
                                            }
                                        }

                                        SettingsGroup.SYNC_BACKUP -> {
                                            item {
                                                LiveSyncSettingsCard(
                                                    onOpen = {
                                                        guarded {
                                                            returnFocus.leaveFrom(RETURN_LIVE_SYNC, listState)
                                                            onLiveSync()
                                                        }
                                                    },
                                                    openRowFocusRequester = returnFocus.requesterFor(RETURN_LIVE_SYNC),
                                                    openRowModifier = entryModifier,
                                                )
                                            }
                                            item {
                                                ExportImportSettingsCard(
                                                    onExport = { guarded { exportLauncher.launch("fijerena_settings.json") } },
                                                    onImport = {
                                                        guarded {
                                                            importLauncher.launch(
                                                                arrayOf("application/json", "application/octet-stream", "*/*"),
                                                            )
                                                        }
                                                    },
                                                    onQuickImport = {
                                                        guarded {
                                                            val path = exportManager.getQuickImportPath()
                                                            if (path != null) {
                                                                pendingImportPath = path
                                                            } else {
                                                                viewModel.setExportImportMessage(
                                                                    resources.getString(R.string.settings_quick_import_not_found),
                                                                )
                                                            }
                                                        }
                                                    },
                                                    exportImportMessage = uiState.exportImportMessage,
                                                    isDevMode = uiState.isDevMode,
                                                    quickImportReady = remember { exportManager.getQuickImportPath() != null },
                                                )
                                            }
                                        }

                                        SettingsGroup.ABOUT_ADVANCED -> {
                                            item {
                                                AboutSettingsCard(
                                                    onDeviceInfo = {
                                                        returnFocus.leaveFrom(RETURN_DEVICE_INFO, listState)
                                                        onDeviceInfo()
                                                    },
                                                    onLicenses = {
                                                        returnFocus.leaveFrom(RETURN_LICENSES, listState)
                                                        onLicenses()
                                                    },
                                                    onTerms = {
                                                        returnFocus.leaveFrom(RETURN_TERMS, listState)
                                                        onTerms()
                                                    },
                                                    // Developer mode is switched on each profile's page;
                                                    // Diagnostics shows here for the profile in use.
                                                    onDiagnostics =
                                                        if (uiState.isDevMode) {
                                                            {
                                                                returnFocus.leaveFrom(RETURN_DIAGNOSTICS, listState)
                                                                onDiagnostics()
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    onVersionClick = onVersionClick,
                                                    rowModifier = entryModifier,
                                                    deviceInfoFocusRequester = returnFocus.requesterFor(RETURN_DEVICE_INFO),
                                                    licensesFocusRequester = returnFocus.requesterFor(RETURN_LICENSES),
                                                    termsFocusRequester = returnFocus.requesterFor(RETURN_TERMS),
                                                    diagnosticsFocusRequester = returnFocus.requesterFor(RETURN_DIAGNOSTICS),
                                                )
                                            }
                                            // Housekeeping for the admin: the catalogue's leftovers.
                                            if (uiState.isDevMode) {
                                                item {
                                                    DatabaseMaintenanceCard(
                                                        isPruning = uiState.isPruningDatabase,
                                                        resultMessage = uiState.databaseMaintenanceMessage,
                                                        onShrinkClick = { guarded { viewModel.pruneDatabase() } },
                                                        isDevMode = uiState.isDevMode,
                                                        lastShrinkAtMs = uiState.lastShrinkAtMs,
                                                        lastShrinkDurationMs = uiState.lastShrinkDurationMs,
                                                        lastShrinkRowsRemoved = uiState.lastShrinkRowsRemoved,
                                                        lastShrinkBytesReclaimed = uiState.lastShrinkBytesReclaimed,
                                                    )
                                                }
                                            }
                                            item { TmdbAttributionRow() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Import options dialog. The dialog shows what was parsed when it composed; its buttons read
        // the live state, so a second click (OK auto-repeat) after the first cleared it does
        // nothing rather than crash or import twice (R-20).
        val parsedImport = pendingParsedImport
        if (showImportOptionsDialog && parsedImport != null) {
            ImportOptionsDialog(
                parsed = parsedImport,
                initialOptions = pendingImportOptions,
                onConfirm = { options ->
                    pendingParsedImport?.let { p ->
                        pendingImportOptions = options
                        if (options.importProviders && p.hasConflicts) {
                            showConflictDialog = true
                            showImportOptionsDialog = false
                        } else {
                            pendingParsedImport = null
                            showImportOptionsDialog = false
                            viewModel.doImport(p, SettingsExportManager.ConflictResolution.SKIP, options)
                        }
                    }
                },
                onCancel = {
                    showImportOptionsDialog = false
                    if (!showConflictDialog) pendingParsedImport = null
                },
            )
        }

        if (showConflictDialog && parsedImport != null) {
            ConflictResolutionDialog(
                conflicts = parsedImport.conflictingProviders,
                onResolve = { resolution ->
                    showConflictDialog = false
                    pendingParsedImport?.let { parsed ->
                        pendingParsedImport = null
                        viewModel.doImport(parsed, resolution, pendingImportOptions)
                    }
                },
                onCancel = {
                    showConflictDialog = false
                    pendingParsedImport = null
                },
            )
        }
    }
}

// Keys for the controls that navigate away from Settings — see rememberNavReturnFocus.
private const val RETURN_PROVIDERS = "providers"
private const val RETURN_LIVE_SYNC = "liveSync"
private const val RETURN_DIAGNOSTICS = "diagnostics"
private const val RETURN_DEVICE_INFO = "deviceInfo"
private const val RETURN_LICENSES = "licenses"
private const val RETURN_TERMS = "terms"
private const val RETURN_GUIDE_SOURCES = "guideSources"

private fun profileReturnKey(profileId: String) = "profile:$profileId"

private fun profilePageReturnKey(row: ProfilePageRow) = "profilePage:${row.name}"

/**
 * A row of Display or Playback that is the profile's, not this device's: its [value] here, set on
 * the page of the profile in use, which [onClick] opens at that row (G1).
 */
@Composable
private fun ProfilePageRowLink(
    title: String,
    value: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    SettingsRow(
        title = title,
        description = stringResource(R.string.settings_profile_row_desc),
        value = value,
        onClick = onClick,
        focusRequester = focusRequester,
    )
}

// Rail ≈ 30 % of the width, pane ≈ 70 % (plan Part I, B).
private const val RAIL_WEIGHT = 0.3f
private const val PANE_WEIGHT = 0.7f

/**
 * The groups of Settings, in rail order (the settings reorg plan);
 * Parental controls since docs/plans/20261020_parental-controls-plan.md.
 */
enum class SettingsGroup(
    @StringRes val titleRes: Int,
    /**
     * Shared setup only Expert mode (admin, for now) sees: Parental controls, sources and guides,
     * Live sync, backups.
     */
    val adminOnly: Boolean = false,
    /** A child profile sees it too (its own page). The other groups change shared or device setup. */
    val forChild: Boolean = false,
) {
    PROFILES(R.string.settings_profiles_title, forChild = true),
    PARENTAL_CONTROLS(R.string.parental_controls_title, adminOnly = true),
    PLAYBACK(R.string.settings_playback_section_title),
    DISPLAY(R.string.settings_group_display),
    SOURCE_GUIDE(R.string.settings_group_source_guide, adminOnly = true),
    SYNC_BACKUP(R.string.settings_group_sync_backup, adminOnly = true),
    ABOUT_ADVANCED(R.string.settings_group_about_advanced, forChild = true),
    ;

    /**
     * The group's name. Without Expert mode Profiles is the viewer's own page, "Your profile", and
     * About & advanced is plain About, having nothing advanced.
     */
    @StringRes
    fun titleFor(admin: Boolean): Int =
        when {
            admin -> titleRes
            this == PROFILES -> R.string.profile_own_page_title
            this == ABOUT_ADVANCED -> R.string.settings_about_section_title
            else -> titleRes
        }

    /** The pane key of this group's first focusable row — where Right from the rail lands. */
    val entryKey: String get() = "$name:first"

    companion object {
        /**
         * The groups the rail lists: without Expert mode ([admin] false), not the admin-only ones;
         * for a child profile ([child]), only its own page and About.
         */
        fun visibleFor(
            admin: Boolean,
            child: Boolean = false,
        ): List<SettingsGroup> = entries.filter { if (child) it.forChild else admin || !it.adminOnly }
    }
}

/**
 * The rows that drill into a pane in place of the group's rows — a [SettingsPickerPane] for the
 * choice settings, a sub-pane for the guide's maintenance (T6); [returnKey] names the row
 * for rememberNavReturnFocus.
 */
enum class SettingPicker(
    val returnKey: String,
) {
    TEXT_SIZE("picker:textSize"),
    RECENT_SIZE("picker:recentSize"),
    WATCH_DELAY("picker:watchDelay"),
    LANGUAGE("picker:language"),
    GUIDE_MAINTENANCE("picker:guideMaintenance"),
    TMDB_KEY("picker:tmdbKey"),

    /** A Parental controls list; its row returns focus by `parentalListReturnKey`, not by this key. */
    PARENTAL_LIST("picker:parentalList"),
    PARENTAL_CHECK("picker:parentalCheck"),

    /** A profile's page; its rows return focus by `profileReturnKey`, not by this key. */
    PROFILE("picker:profile"),
}
