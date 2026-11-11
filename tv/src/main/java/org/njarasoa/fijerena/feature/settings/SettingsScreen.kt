@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.SettingsRowKind
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.nightAudioDescription
import org.njarasoa.fijerena.core.ui.components.rememberNightAudioOn
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.home.ChartRows
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.utils.LocaleManager
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.core.ui.viewmodels.TmdbKeyViewModel
import org.njarasoa.fijerena.feature.settings.components.*
import org.njarasoa.fijerena.feature.settings.components.ReminderTimePickerPane
import org.njarasoa.fijerena.feature.settings.components.ReminderTimeSettingsRow
import org.njarasoa.fijerena.feature.settings.components.SettingsRow
import org.njarasoa.fijerena.ui.components.TvContentText
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvLeaveChildPinDialog
import org.njarasoa.fijerena.ui.components.TvPinDialog
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
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
    onAbout: () -> Unit = {},
    onProfileSwitched: () -> Unit = {},
    onProviderChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    // Show Key Input: this device's, TV only, so read here and not through the shared view model.
    val appSettings = remember { AppSettings(context.applicationContext) }
    val keyInputUntil by remember { appSettings.observeKeyInputUntil() }
        .collectAsStateWithLifecycle(initialValue = appSettings.keyInputUntil)
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val profilesMessage by profilesViewModel.message.collectAsStateWithLifecycle()
    // "Switch to this profile" away from a child profile asks an Admin's PIN (the child lock).
    TvLeaveChildPinDialog(profilesViewModel, onProfileSwitched)
    // Settings opens without the PIN; Admin (shared setup, this device, every profile) asks the PIN
    // of the profile in use once, on the way into its pane (the admin settings plan).
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
                viewModel.setExportImportMessage(
                    resources.getString(R.string.settings_import_failed, friendlyErrorMessage(e, context, uiState.isNerdMode)),
                )
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
                viewModel.setExportImportMessage(
                    resources.getString(R.string.settings_import_failed, friendlyErrorMessage(e, context, uiState.isNerdMode)),
                )
            }
        pendingImportPath = null
    }

    // Re-check provider when returning from provider management screens
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            epgViewModel.userMessage.collect { message ->
                AppNotices.show(message.asString(context))
            }
        }
    }
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            val hasProvider = providerRepo.getProviderCount() > 0
            val hadOnLoad = hadProviderOnLoad
            viewModel.refreshProviderInfo()
            viewModel.refreshAdmin()
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

    // Two panes (plan Part I, B): a rail of the groups on the left, the selected group's
    // rows on the right. The rail row is saveable so a language change (Activity recreate) and the
    // Back round trip from a child screen come back to the same group.
    var selectedGroup by rememberSaveable { mutableStateOf(SettingsGroup.PROFILES) }
    // For a profile that is not an Admin, the Admin sections are not listed (a child profile has no
    // Settings); an Admin section left selected when the Admin flag was turned off falls back to Your Profile.
    val visibleGroups = SettingsGroup.visibleFor(admin = uiState.isAdmin)
    if (selectedGroup !in visibleGroups) selectedGroup = SettingsGroup.PROFILES
    val railPane = rememberPaneFocus()
    val contentPane = rememberPaneFocus()
    val focusManager = LocalFocusManager.current
    // One list state per group: a group starts at the top, and the Back round trip restores the
    // scroll of the one it left.
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
            returnFocus.key?.let(returnFocus::requesterOf)?.requestFocusWithRetry()
            returnFocus.clear()
        }
    }
    val openProfile: (ProfileUi) -> Unit = { profile ->
        returnFocus.leaveFrom(profileReturnKey(profile.id), listState)
        editingProfileId = profile.id
        picker = SettingPicker.PROFILE
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
        coroutineScope.launch {
            returnFocus.restoreScroll(listState)
            if (deleted || returnFocus.key?.let(returnFocus::requesterOf)?.requestFocusWithRetry() != true) contentPane.focusEntry()
            returnFocus.clear()
        }
    }
    // A row that leads to another Admin section (Parental controls and a child's page lead to each
    // other's section) selects that section on the rail and focuses its first row. That row carries
    // its own requester in [sectionEntries]: it attaches only when the section shows, so the retry
    // waits for it. The PIN was typed on the way into the first section, so none is asked.
    val sectionEntries = remember { SettingsGroup.entries.filter { it.adminOnly }.associateWith { FocusRequester() } }
    val openSection: (SettingsGroup) -> Unit = { group ->
        picker = null
        editingProfileId = null
        returnFocus.clear()
        selectedGroup = group
        coroutineScope.launch { sectionEntries.getValue(group).requestFocusWithRetry() }
    }

    // Back while in the pane returns to the rail row (Left does the same, via tvPane); Back on
    // the rail leaves Settings as before. Taken in onPreviewKeyEvent on the root — a BackHandler
    // misses the first press while a button holds focus (docs/NAVIGATION_GUIDE.md, "TV Back on
    // Detail Screens"); the BackHandler stays as the fallback. A picker takes its own Back.
    var paneHasFocus by remember { mutableStateOf(false) }
    val backToRail = { coroutineScope.launch { railPane.focusEntry() } }
    // Your Profile shows the page of the profile in use, for every profile (an Admin's too, with
    // only its own rows), without taking focus from the rail. The page takes its own Back (after the
    // name field, which keeps Back to cancel its edit) and leaves to the rail; a new key drops what
    // was left unsaved.
    val ownProfilePage = picker == null && selectedGroup == SettingsGroup.PROFILES
    var ownProfilePageKey by remember { mutableIntStateOf(0) }
    val leaveOwnProfilePage = {
        ownProfilePageKey++
        backToRail()
    }
    val paneBack: () -> Unit = { backToRail() }
    BackHandler(enabled = paneHasFocus && picker == null && !ownProfilePage) { paneBack() }

    // An Admin section's pane is entered (Right or OK on its rail row) after the PIN of the profile
    // in use, once per session (PinSession), for all five; Cancel leaves focus on the rail row. The
    // rows inside ask nothing more. Until then the pane says only that the PIN is needed.
    val groupLocked: (SettingsGroup) -> Boolean = { group ->
        group.adminOnly && inUse?.let { profilesViewModel.needsPin(it.id) } == true
    }
    val enterGroup: (SettingsGroup) -> Unit = { group ->
        val enter = { coroutineScope.launch { contentPane.focusEntry() } }
        if (groupLocked(group)) guarded { enter() } else enter()
    }
    // The session locked again (a long time in the background) while in an Admin section: back to
    // the rail, so its rows are not open without the PIN.
    val selectedGroupLocked = groupLocked(selectedGroup)
    LaunchedEffect(selectedGroupLocked) {
        if (selectedGroupLocked) {
            val inPane = paneHasFocus
            if (inPane) {
                picker = null
                editingProfileId = null
                returnFocus.clear()
                railPane.focusEntry()
            }
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.key == Key.Back && paneHasFocus && picker == null && !ownProfilePage) {
                    if (event.type == KeyEventType.KeyUp) paneBack()
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
                            .tvPane(railPane, exitRight = contentPane, exitUp = false)
                            // A large text size can make the rail taller than the screen: the
                            // focused row scrolls into view.
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    visibleGroups.forEach { group ->
                        // The Admin sections under a heading of their own (text only, not a focus
                        // stop), so Profiles does not read as part of Your Profile.
                        if (group.adminOnly && group == visibleGroups.first { it.adminOnly }) {
                            CinemaText(
                                stringResource(R.string.settings_group_admin),
                                modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm),
                                role = TextRole.Label,
                                tone = TextTone.Secondary,
                            )
                        }
                        TvInputListItem(
                            selected = group == selectedGroup,
                            onClick = {
                                selectedGroup = group
                                enterGroup(group)
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .paneItem(railPane, group.name)
                                    .onFocusChanged { if (it.isFocused) selectedGroup = group }
                                    .onKeyEvent { event ->
                                        val keyDown = event.type == KeyEventType.KeyDown
                                        val isLeft = keyDown && event.key == Key.DirectionLeft
                                        // Right into a locked Admin asks the PIN first; else the pane
                                        // (tvPane) takes it.
                                        val isLockedRight = keyDown && event.key == Key.DirectionRight && groupLocked(group)
                                        if (isLeft) focusManager.moveFocus(FocusDirection.Left)
                                        if (isLockedRight) enterGroup(group)
                                        isLeft || isLockedRight
                                    }.leftToRail(),
                            headlineContent = { TvContentText(stringResource(group.titleRes), role = TextRole.Heading) },
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

                            SettingPicker.KEY_INPUT -> {
                                KeyInputDurationPane(
                                    onPick = { minutes ->
                                        appSettings.keyInputUntil = System.currentTimeMillis() + minutes * MS_PER_MINUTE
                                    },
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

                            SettingPicker.REMINDER_TIME -> {
                                ReminderTimePickerPane(
                                    leadMinutes = uiState.reminderLeadMinutes,
                                    onPick = { minutes -> viewModel.updateReminderLeadMinutes(minutes) },
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
                                                viewModel.refreshAdmin()
                                                if (ownProfilePage) leaveOwnProfilePage() else closeProfile(false)
                                            },
                                            onSwitch = { profilesViewModel.switchTo(profile.id, onProfileSwitched) },
                                            onDeleted = { closeProfile(true) },
                                            onBack = { if (ownProfilePage) leaveOwnProfilePage() else closeProfile(false) },
                                            scale = scale,
                                            adminView = uiState.isAdmin,
                                            // A profile's Admin rows show only in Admin → Profiles, also
                                            // for the Admin's own profile (the admin settings plan, AS-D1).
                                            adminRows = uiState.isAdmin && !ownProfilePage,
                                            focusOnOpen = !ownProfilePage,
                                            // A grown-up who is not an Admin adds a profile from its own page.
                                            canAddProfile = ownProfilePage && !uiState.isAdmin && !uiState.isChildProfile,
                                            title =
                                                if (ownProfilePage) {
                                                    stringResource(R.string.profile_own_page_title)
                                                } else {
                                                    stringResource(R.string.profile_dialog_edit_title)
                                                },
                                            onParentalControls = {
                                                viewModel.refreshAdmin()
                                                openSection(SettingsGroup.PARENTAL_CONTROLS)
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
                                // The first row of an Admin section: the pane's entry row, and where a
                                // row that leads to the section lands.
                                val sectionEntryModifier =
                                    sectionEntries[selectedGroup]?.let { entryModifier.focusRequester(it) } ?: entryModifier
                                Column(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(vertical = Spacing.xs),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        // A locked Admin section shows no rows, only that the PIN is needed (AD-D2).
                                        if (selectedGroupLocked) {
                                            item(key = "pinNeeded") {
                                                TvEmptyState(
                                                    message = stringResource(R.string.settings_section_pin_needed),
                                                    modifier = Modifier.fillParentMaxSize(),
                                                    icon = CinemaIcons.Lock,
                                                    takeFocus = false,
                                                )
                                            }
                                        } else {
                                            when (selectedGroup) {
                                                // Shown as the page of the profile in use, above.
                                                SettingsGroup.PROFILES -> {}

                                                // An Admin section's rows, in their order.
                                                SettingsGroup.ADMIN_PROFILES,
                                                SettingsGroup.PARENTAL_CONTROLS,
                                                SettingsGroup.THIS_DEVICE,
                                                SettingsGroup.SOURCE_GUIDE,
                                                SettingsGroup.SYNC_BACKUP,
                                                -> {
                                                    item(key = selectedGroup.name) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                                                            when (selectedGroup) {
                                                                SettingsGroup.ADMIN_PROFILES -> {
                                                                    ProfilesSettingsCard(
                                                                        profiles = profiles,
                                                                        message = profilesMessage,
                                                                        newProfileColorIndex = profilesViewModel::nextFreeColorIndex,
                                                                        // No PIN: any grown-up may add a profile (the profile rules plan, PR-D2).
                                                                        onAdd = { name, colorIndex, child, nerd ->
                                                                            profilesViewModel.addProfile(name, colorIndex, child, nerd) {
                                                                                openWhenListed = it
                                                                            }
                                                                        },
                                                                        onEdit = openProfile,
                                                                        onDismissMessage = profilesViewModel::clearMessage,
                                                                        scale = scale,
                                                                        firstRowModifier = sectionEntryModifier,
                                                                        rowModifier = { profile ->
                                                                            returnFocus.requesterFor(profileReturnKey(profile.id))?.let {
                                                                                Modifier.focusRequester(it)
                                                                            } ?: Modifier
                                                                        },
                                                                        pinNudge =
                                                                            profilesViewModel.pins.takeIf {
                                                                                profilesViewModel
                                                                                    .needsAdminPin()
                                                                            },
                                                                        pinNudgeLastAdmin =
                                                                            profiles.firstOrNull { it.isActive }?.let {
                                                                                profilesViewModel.isLastAdmin(it.id)
                                                                            } == true,
                                                                        noAdminPin = profilesViewModel.adminPinHolders().isEmpty(),
                                                                    )
                                                                }

                                                                SettingsGroup.PARENTAL_CONTROLS -> {
                                                                    ParentalControlsCard(
                                                                        onOpenList = { list ->
                                                                            returnFocus.leaveFrom(parentalListReturnKey(list), listState)
                                                                            parentalList = list
                                                                            picker = SettingPicker.PARENTAL_LIST
                                                                        },
                                                                        onOpenCheck = {
                                                                            returnFocus.leaveFrom(PARENTAL_CHECK_RETURN_KEY, listState)
                                                                            picker = SettingPicker.PARENTAL_CHECK
                                                                        },
                                                                        onOpenProfiles = { openSection(SettingsGroup.ADMIN_PROFILES) },
                                                                        focusRequesterFor = returnFocus::requesterFor,
                                                                        firstRowModifier = sectionEntryModifier,
                                                                    )
                                                                }

                                                                SettingsGroup.THIS_DEVICE -> {
                                                                    SettingsSection {
                                                                        LanguageSettingsRow(
                                                                            selectedLanguage = uiState.language,
                                                                            onOpenPicker = { openPicker(SettingPicker.LANGUAGE) },
                                                                            rowFocusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.LANGUAGE.returnKey,
                                                                                ),
                                                                            modifier = sectionEntryModifier,
                                                                        )
                                                                        UiScaleSettingsRow(
                                                                            uiScale = uiState.uiScale,
                                                                            onOpenPicker = { openPicker(SettingPicker.TEXT_SIZE) },
                                                                            rowFocusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.TEXT_SIZE.returnKey,
                                                                                ),
                                                                        )
                                                                        RecentRowSizeRow(
                                                                            size = uiState.recentRowSize,
                                                                            onOpenPicker = { openPicker(SettingPicker.RECENT_SIZE) },
                                                                            rowFocusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.RECENT_SIZE.returnKey,
                                                                                ),
                                                                        )
                                                                        WatchDelaySettingsRow(
                                                                            watchDelaySeconds = uiState.watchDelaySeconds,
                                                                            onOpenPicker = { openPicker(SettingPicker.WATCH_DELAY) },
                                                                            rowFocusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.WATCH_DELAY.returnKey,
                                                                                ),
                                                                        )
                                                                        ReminderTimeSettingsRow(
                                                                            leadMinutes = uiState.reminderLeadMinutes,
                                                                            onOpenPicker = { openPicker(SettingPicker.REMINDER_TIME) },
                                                                            rowFocusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.REMINDER_TIME.returnKey,
                                                                                ),
                                                                        )
                                                                        TvSwitchRow(
                                                                            checked = uiState.showClock,
                                                                            onCheckedChange = viewModel::updateShowClock,
                                                                            label = stringResource(R.string.settings_show_clock_title),
                                                                            description = stringResource(R.string.settings_show_clock_desc),
                                                                        )
                                                                        TvSwitchRow(
                                                                            checked = rememberNightAudioOn(),
                                                                            onCheckedChange = viewModel::updateNightAudio,
                                                                            label = stringResource(R.string.night_audio_title),
                                                                            description = nightAudioDescription(),
                                                                        )
                                                                        KeyInputSettingsRow(
                                                                            until = keyInputUntil,
                                                                            onTurnOn = { openPicker(SettingPicker.KEY_INPUT) },
                                                                            onTurnOff = { appSettings.keyInputUntil = 0L },
                                                                            rowFocusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.KEY_INPUT.returnKey,
                                                                                ),
                                                                        )
                                                                    }
                                                                }

                                                                SettingsGroup.SOURCE_GUIDE -> {
                                                                    ProviderSettingsCard(
                                                                        sourceNames = uiState.profileSources.map { it.second },
                                                                        onManageProviders = {
                                                                            returnFocus.leaveFrom(RETURN_PROVIDERS, listState)
                                                                            onManageProviders()
                                                                        },
                                                                        manageRowFocusRequester =
                                                                            returnFocus.requesterFor(
                                                                                RETURN_PROVIDERS,
                                                                            ),
                                                                        manageRowModifier = sectionEntryModifier,
                                                                    )
                                                                    // The guide: its sources (per source) and its data, side by side.
                                                                    SettingsSection(
                                                                        title = stringResource(R.string.provider_section_guide),
                                                                    ) {
                                                                        // Only sources with live channels have guide sources: one page for
                                                                        // all of them, shown when the device has one.
                                                                        if (uiState.hasGuideSources) {
                                                                            SettingsRow(
                                                                                title =
                                                                                    stringResource(
                                                                                        R.string.epg_management_screen_title,
                                                                                    ),
                                                                                description =
                                                                                    stringResource(
                                                                                        R.string.settings_guide_sources_desc,
                                                                                    ),
                                                                                onClick = {
                                                                                    returnFocus.leaveFrom(RETURN_GUIDE_SOURCES, listState)
                                                                                    onGuideSources()
                                                                                },
                                                                                focusRequester =
                                                                                    returnFocus.requesterFor(
                                                                                        RETURN_GUIDE_SOURCES,
                                                                                    ),
                                                                            )
                                                                        }
                                                                        GuideMaintenanceRow(
                                                                            viewModel = epgViewModel,
                                                                            onOpen = { openPicker(SettingPicker.GUIDE_MAINTENANCE) },
                                                                            focusRequester =
                                                                                returnFocus.requesterFor(
                                                                                    SettingPicker.GUIDE_MAINTENANCE.returnKey,
                                                                                ),
                                                                        )
                                                                    }
                                                                    TmdbKeySettingsRow(
                                                                        viewModel = tmdbKeyViewModel,
                                                                        onOpen = { openPicker(SettingPicker.TMDB_KEY) },
                                                                        focusRequester =
                                                                            returnFocus.requesterFor(
                                                                                SettingPicker.TMDB_KEY.returnKey,
                                                                            ),
                                                                    )
                                                                    TvSwitchRow(
                                                                        checked = uiState.showTmdbCharts,
                                                                        onCheckedChange = viewModel::updateShowTmdbCharts,
                                                                        label = stringResource(R.string.tmdb_charts_title),
                                                                        description = stringResource(R.string.tmdb_charts_desc),
                                                                    )
                                                                    if (uiState.showTmdbCharts) {
                                                                        var chartsRefreshed by remember { mutableStateOf(false) }
                                                                        val chartsContext = LocalContext.current
                                                                        SettingsRow(
                                                                            title = stringResource(R.string.tmdb_charts_refresh_title),
                                                                            description =
                                                                                stringResource(
                                                                                    if (chartsRefreshed) {
                                                                                        R.string.tmdb_charts_refresh_done
                                                                                    } else {
                                                                                        R.string.tmdb_charts_refresh_desc
                                                                                    },
                                                                                ),
                                                                            // An action, not a page.
                                                                            kind = SettingsRowKind.Action,
                                                                            onClick = {
                                                                                coroutineScope.launch {
                                                                                    ChartRows.refresh(chartsContext.applicationContext)
                                                                                    chartsRefreshed = true
                                                                                }
                                                                            },
                                                                        )
                                                                    }
                                                                    TvSwitchRow(
                                                                        checked = uiState.showSports,
                                                                        onCheckedChange = viewModel::updateShowSports,
                                                                        label = stringResource(R.string.sports_switch_title),
                                                                        description = stringResource(R.string.sports_switch_desc),
                                                                    )
                                                                    // Housekeeping, the catalogue's leftovers: the section's last row.
                                                                    DatabaseMaintenanceCard(
                                                                        isPruning = uiState.isPruningDatabase,
                                                                        resultMessage = uiState.databaseMaintenanceMessage,
                                                                        onShrinkClick = viewModel::pruneDatabase,
                                                                        nerd = uiState.isNerdMode,
                                                                        lastShrinkAtMs = uiState.lastShrinkAtMs,
                                                                        lastShrinkDurationMs = uiState.lastShrinkDurationMs,
                                                                        lastShrinkRowsRemoved = uiState.lastShrinkRowsRemoved,
                                                                        lastShrinkBytesReclaimed = uiState.lastShrinkBytesReclaimed,
                                                                    )
                                                                }

                                                                SettingsGroup.SYNC_BACKUP -> {
                                                                    LiveSyncSettingsCard(
                                                                        onOpen = {
                                                                            returnFocus.leaveFrom(RETURN_LIVE_SYNC, listState)
                                                                            onLiveSync()
                                                                        },
                                                                        openRowFocusRequester = returnFocus.requesterFor(RETURN_LIVE_SYNC),
                                                                        openRowModifier = sectionEntryModifier,
                                                                    )
                                                                    ExportImportSettingsCard(
                                                                        onExport = { exportLauncher.launch("fijerena_settings.json") },
                                                                        onImport = {
                                                                            importLauncher.launch(
                                                                                arrayOf(
                                                                                    "application/json",
                                                                                    "application/octet-stream",
                                                                                    "*/*",
                                                                                ),
                                                                            )
                                                                        },
                                                                        onQuickImport = {
                                                                            val path = exportManager.getQuickImportPath()
                                                                            if (path != null) {
                                                                                pendingImportPath = path
                                                                            } else {
                                                                                viewModel.setExportImportMessage(
                                                                                    resources.getString(
                                                                                        R.string.settings_quick_import_not_found,
                                                                                    ),
                                                                                )
                                                                            }
                                                                        },
                                                                        exportImportMessage = uiState.exportImportMessage,
                                                                        isAdmin = uiState.isAdmin,
                                                                        quickImportReady =
                                                                            remember {
                                                                                exportManager.getQuickImportPath() != null
                                                                            },
                                                                    )
                                                                }

                                                                SettingsGroup.PROFILES, SettingsGroup.ABOUT -> {}
                                                            }
                                                        }
                                                    }
                                                }

                                                SettingsGroup.ABOUT -> {
                                                    item {
                                                        AboutSettingsCard(
                                                            onAbout = {
                                                                returnFocus.leaveFrom(RETURN_ABOUT, listState)
                                                                onAbout()
                                                            },
                                                            onDeviceInfo = {
                                                                returnFocus.leaveFrom(RETURN_DEVICE_INFO, listState)
                                                                onDeviceInfo()
                                                            },
                                                            onDiagnostics = {
                                                                returnFocus.leaveFrom(RETURN_DIAGNOSTICS, listState)
                                                                onDiagnostics()
                                                            },
                                                            rowModifier = entryModifier,
                                                            aboutFocusRequester = returnFocus.requesterFor(RETURN_ABOUT),
                                                            deviceInfoFocusRequester = returnFocus.requesterFor(RETURN_DEVICE_INFO),
                                                            diagnosticsFocusRequester = returnFocus.requesterFor(RETURN_DIAGNOSTICS),
                                                        )
                                                    }
                                                }
                                            }
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
private const val RETURN_ABOUT = "about"
private const val RETURN_GUIDE_SOURCES = "guideSources"

private fun profileReturnKey(profileId: String) = "profile:$profileId"

// Rail ≈ 30 % of the width, pane ≈ 70 % (plan Part I, B).
private const val MS_PER_MINUTE = 60_000L

private const val RAIL_WEIGHT = 0.3f
private const val PANE_WEIGHT = 0.7f

/**
 * The groups of Settings, in rail order (the settings reorg plan): Your Profile (PROFILES), the
 * five Admin sections under an "Admin" heading (the Admin sections plan),
 * and About.
 */
enum class SettingsGroup(
    @StringRes val titleRes: Int,
    /**
     * An Admin section, which only an Admin sees, and whose pane is entered after the PIN of the
     * profile in use (when it has one), once per session for all five: every profile, the
     * household's shared setup and this device's settings. Shrink Database ends Sources & Guide.
     */
    val adminOnly: Boolean = false,
) {
    PROFILES(R.string.profile_own_page_title),
    ADMIN_PROFILES(R.string.settings_profiles_title, adminOnly = true),
    PARENTAL_CONTROLS(R.string.parental_controls_title, adminOnly = true),
    THIS_DEVICE(R.string.settings_group_this_device, adminOnly = true),
    SOURCE_GUIDE(R.string.settings_group_source_guide, adminOnly = true),
    SYNC_BACKUP(R.string.settings_group_sync_backup, adminOnly = true),
    ABOUT(R.string.settings_about_section_title),
    ;

    /** The pane key of this group's first focusable row — where Right from the rail lands. */
    val entryKey: String get() = "$name:first"

    companion object {
        /** The groups the rail lists: for a profile that is not an Admin ([admin] false), no Admin section. */
        fun visibleFor(admin: Boolean): List<SettingsGroup> = entries.filter { admin || !it.adminOnly }
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
    KEY_INPUT("picker:keyInput"),
    REMINDER_TIME("picker:reminderTime"),
    LANGUAGE("picker:language"),
    GUIDE_MAINTENANCE("picker:guideMaintenance"),
    TMDB_KEY("picker:tmdbKey"),

    /** A Parental controls list; its row returns focus by `parentalListReturnKey`, not by this key. */
    PARENTAL_LIST("picker:parentalList"),
    PARENTAL_CHECK("picker:parentalCheck"),

    /** A profile's page; its rows return focus by `profileReturnKey`, not by this key. */
    PROFILE("picker:profile"),
}
