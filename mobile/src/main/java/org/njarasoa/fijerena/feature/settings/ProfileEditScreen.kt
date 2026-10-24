package org.njarasoa.fijerena.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.navigation.ProfilePageRow
import org.njarasoa.fijerena.core.network.UiThemes
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PaletteSwatches
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfilePinFlow
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.components.rememberProfilePinFlow
import org.njarasoa.fijerena.core.ui.home.SourceChoice
import org.njarasoa.fijerena.core.ui.theme.AllPalettes
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.packagedUiThemesFor
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.palettesFor
import org.njarasoa.fijerena.core.ui.theme.styleById
import org.njarasoa.fijerena.core.ui.theme.uiThemeNameRes
import org.njarasoa.fijerena.core.ui.viewmodels.FilterSource
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.feature.contentselection.components.MobileSourceChooserDialog
import org.njarasoa.fijerena.feature.provider.components.MobileCategoryFilterDialog
import org.njarasoa.fijerena.feature.settings.components.ProfileColourPicker
import org.njarasoa.fijerena.feature.settings.components.ProfileNameField
import org.njarasoa.fijerena.feature.settings.components.ProfileSwitchRow
import org.njarasoa.fijerena.feature.settings.components.SettingsGroupHeader
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.ui.components.LeaveChildPinDialog
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.ProvideLook
import org.njarasoa.fijerena.ui.theme.ProvideLookAndAccent
import org.njarasoa.fijerena.ui.theme.Spacing
import java.text.Collator

/**
 * A profile's page (`Screen.ProfileEdit`, D8): name, colour, its own Developer mode and Play next
 * episode automatically, Sources › (with two or more sources), Content filters › (this profile's,
 * on one of its Xtream sources — picked first when it has several, O4 of
 * docs/plans/20261012_multi-source-search-history-plan.md), Switch to this profile, and Delete
 * last. Every change applies at once; the name applies when the page is left. Content filters
 * are saved by their own editor's Save. See the sources guide profiles plan → P9.
 *
 * [startAt] (Settings' Display and Playback rows) scrolls the page to that row when it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProfileEditScreen(
    profileId: String,
    // Settings' own: saving and deleting run in its scope, so leaving this page doesn't cancel them.
    viewModel: ProfilesViewModel,
    onBack: () -> Unit,
    onProfileSwitched: () -> Unit,
    startAt: ProfilePageRow? = null,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    // Content filters act on Xtream catalogues only (as they did in Edit Source).
    val filterSources by remember(profileId) { viewModel.filterSources(profileId) }.collectAsStateWithLifecycle(emptyList())
    val sources by remember(profileId) { viewModel.profileSources(profileId) }.collectAsStateWithLifecycle(emptyList())
    val profile = profiles.firstOrNull { it.id == profileId }

    // The page's own leave (applies a changed name first); onBack until the profile has loaded. A
    // plain holder, not state: it is set during composition.
    val leavePage = remember { LeaveAction(onBack) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Without Expert mode a profile only edits itself: "Your profile", as on TV.
                    val admin = remember { viewModel.viewerIsAdmin }
                    CinemaText(
                        stringResource(if (admin) R.string.profile_dialog_edit_title else R.string.profile_own_page_title),
                        role = TextRole.Title,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { leavePage.action() }) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        // Until the list has loaded there is nothing to edit; a profile deleted elsewhere closes it.
        if (profile == null) {
            LaunchedEffect(profiles) { if (profiles.isNotEmpty()) onBack() }
        } else {
            val stored = remember(profileId) { viewModel.settingsOf(profileId) }
            var name by rememberSaveable(profileId) { mutableStateOf(profile.name) }
            var colorIndex by rememberSaveable(profileId) { mutableIntStateOf(profile.colorIndex) }
            var devMode by rememberSaveable(profileId) { mutableStateOf(stored.devMode) }
            var autoplayNextEpisode by rememberSaveable(profileId) { mutableStateOf(stored.autoplayNextEpisode) }
            var child by rememberSaveable(profileId) { mutableStateOf(stored.child) }
            var showNameError by remember { mutableStateOf(false) }
            var editingFilters by remember { mutableStateOf<Pair<FilterSource, CategoryFilters>?>(null) }
            var choosingFiltersSource by remember { mutableStateOf(false) }
            val editFilters = { source: FilterSource ->
                viewModel.loadCategoryFilters(source.id, profileId) { editingFilters = source to it }
            }
            var confirmDelete by remember { mutableStateOf(false) }
            // Sources, Content filters, Expert mode and the expert-only themes are admin-only.
            val adminView = remember { viewModel.viewerIsAdmin }
            // A child's own page has no Name and no PIN.
            val showsNameAndPin = stored.showsNameAndPin(adminView)
            // The profile's colour theme and Look and Feel: saved as they are picked, like its sources.
            var themeId by remember(profileId) { mutableStateOf(viewModel.themeOf(profileId)) }
            var uiStyleId by remember(profileId) { mutableStateOf(viewModel.uiStyleOf(profileId)) }
            var posterBackdrop by remember(profileId) { mutableStateOf(viewModel.posterBackdropOf(profileId)) }
            // A packaged UI theme sets the colour theme and the Look and Feel (docs/plans/20261021_ui-themes-plan.md).
            var uiThemeId by remember(profileId) { mutableStateOf(viewModel.uiThemeOf(profileId)) }
            val lookLocked = uiThemeId != UiThemes.CUSTOM
            var choosingUiTheme by rememberSaveable { mutableStateOf(false) }
            if (choosingUiTheme) {
                // Each UI theme's colour theme and Look; Custom is the profile's own (as last saved).
                val looks =
                    mapOf(UiThemes.CUSTOM to (themeId to uiStyleId)) +
                        packagedUiThemesFor(adminView, uiThemeId).associate { it.id to (it.themeId to it.uiStyleId) }
                SettingsPickerDialog(
                    title = stringResource(R.string.ui_theme_title),
                    options = looks.keys.map { stringResource(uiThemeNameRes(it)) to it },
                    selected = uiThemeId,
                    // Each row in its theme's font and accent, with its colours beside it.
                    preview = { id -> looks[id]?.let { PaletteSwatches(paletteById(it.first)) } },
                    inLook = { id, row -> looks[id]?.let { ProvideLookAndAccent(it.first, it.second, row) } ?: row() },
                    onSelect = { id ->
                        uiThemeId = id
                        viewModel.setUiTheme(profileId, id)
                        themeId = viewModel.themeOf(profileId)
                        uiStyleId = viewModel.uiStyleOf(profileId)
                        choosingUiTheme = false
                    },
                    onDismiss = { choosingUiTheme = false },
                )
            }
            var choosingTheme by rememberSaveable { mutableStateOf(false) }
            var choosingLook by rememberSaveable { mutableStateOf(false) }
            if (choosingTheme) {
                SettingsPickerDialog(
                    title = stringResource(R.string.settings_theme_section_title),
                    // Alphabetical in the app's language.
                    options =
                        palettesFor(adminView, themeId)
                            .map {
                                stringResource(it.nameRes) to it.id
                            }.sortedWith(compareBy(Collator.getInstance()) { it.first }),
                    selected = themeId,
                    preview = { id -> PaletteSwatches(paletteById(id)) },
                    onSelect = { id ->
                        themeId = id
                        viewModel.setTheme(profileId, id)
                        choosingTheme = false
                    },
                    onDismiss = { choosingTheme = false },
                )
            }
            if (choosingLook) {
                SettingsPickerDialog(
                    title = stringResource(R.string.settings_ui_style_section_title),
                    options =
                        AllUiStyles
                            .map {
                                stringResource(
                                    it.nameRes,
                                ) to it.id
                            }.sortedWith(compareBy(Collator.getInstance()) { it.first }),
                    selected = uiStyleId,
                    // Each row drawn in its own Look: font, corners, focus.
                    inLook = { id, row -> ProvideLook(styleById(id), row) },
                    onSelect = { id ->
                        uiStyleId = id
                        viewModel.setUiStyle(profileId, id)
                        choosingLook = false
                    },
                    onDismiss = { choosingLook = false },
                )
            }
            // The PIN row (the profile pin plan): saved as it is set, like the theme.
            val onlyExpert = remember(profileId, profiles) { viewModel.isLastExpert(profileId) }
            val pinFlow =
                rememberProfilePinFlow(
                    pins = viewModel.pins,
                    profileId = profileId,
                    own = profile.isActive,
                    lastExpert = stored.devMode && onlyExpert,
                )
            pinFlow.request?.let { request ->
                PinDialog(
                    state = rememberPinPromptState(viewModel.pins, request, pinFlow::promptDone),
                    onCancel = pinFlow::cancel,
                )
            }
            if (pinFlow.step == ProfilePinFlow.Step.OPTIONS) {
                CinemaAlertDialog(
                    onDismissRequest = pinFlow::cancel,
                    title = { CinemaText(stringResource(R.string.profile_pin_title), role = TextRole.Title, tone = TextTone.Inherit) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                            CinemaButton(onClick = pinFlow::change, kind = ButtonKind.Main, modifier = Modifier.fillMaxWidth()) {
                                CinemaText(stringResource(R.string.profile_pin_change), role = TextRole.Label, tone = TextTone.Inherit)
                            }
                            CinemaButton(onClick = pinFlow::remove, kind = ButtonKind.Danger, modifier = Modifier.fillMaxWidth()) {
                                CinemaText(stringResource(R.string.profile_pin_remove), role = TextRole.Label, tone = TextTone.Inherit)
                            }
                        }
                    },
                    confirmButton = {
                        CinemaDialogActionButton(onClick = pinFlow::cancel, kind = ButtonKind.Other) {
                            CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
                        }
                    },
                )
            }
            if (pinFlow.step == ProfilePinFlow.Step.CONFIRM_REMOVE) {
                // The last Expert PIN with a child profile: the child lock stops too, on every linked device.
                val lastAdminPin = remember(profileId, profiles) { viewModel.isLastAdminPin(profileId) }
                CinemaAlertDialog(
                    onDismissRequest = pinFlow::cancel,
                    title = { CinemaText(stringResource(R.string.profile_pin_remove_confirm_title), role = TextRole.Title) },
                    text = {
                        CinemaText(
                            listOfNotNull(
                                stringResource(R.string.profile_pin_remove_confirm_text),
                                if (lastAdminPin) stringResource(R.string.profile_pin_remove_last_expert) else null,
                            ).joinToString(" "),
                            tone = TextTone.Secondary,
                        )
                    },
                    confirmButton = {
                        CinemaDialogActionButton(onClick = pinFlow::confirmRemove, kind = ButtonKind.Danger) {
                            CinemaText(stringResource(R.string.profile_pin_remove), role = TextRole.Label, tone = TextTone.Inherit)
                        }
                    },
                    dismissButton = {
                        CinemaDialogActionButton(onClick = pinFlow::cancel, kind = ButtonKind.Other) {
                            CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
                        }
                    },
                )
            }
            // "Set a PIN ›" under the Child switch: the PIN of the Expert profile in use.
            var choosingAdminPin by remember { mutableStateOf(false) }
            if (choosingAdminPin) {
                profiles.firstOrNull { it.isActive }?.let { admin ->
                    val warning = if (viewModel.isLastExpert(admin.id)) stringResource(R.string.pin_last_expert_warning) else null
                    PinDialog(
                        state = rememberPinPromptState(viewModel.pins, PinRequest.Choose(admin.id, warning)) { choosingAdminPin = false },
                        onCancel = { choosingAdminPin = false },
                    )
                }
            }
            // "Switch to this profile" away from a child profile asks an Expert's PIN (the child lock).
            LeaveChildPinDialog(viewModel, onProfileSwitched)
            var editingSources by rememberSaveable { mutableStateOf(false) }
            // The profile in use and the last one left can't be deleted (ProfileRepository refuses both).
            val canDelete = !profile.isActive && profiles.size > 1

            // Every change applies at once, as in the rest of Settings; the name when the page is left
            // (a blank one is dropped, the old name kept).
            val apply = { nameNow: String ->
                viewModel.updateProfile(
                    profileId,
                    nameNow.trim().ifEmpty { profile.name },
                    colorIndex,
                    stored.copy(devMode = devMode, autoplayNextEpisode = autoplayNextEpisode, child = child),
                )
            }
            val leave = {
                apply(name)
                onBack()
            }
            BackHandler(onBack = leave)
            leavePage.action = leave
            val inset = Modifier.padding(horizontal = Spacing.md)
            // The row the page opens at: once, when its place is known.
            val scrollState = rememberScrollState()
            var startAtY by remember { mutableStateOf<Int?>(null) }
            var scrolledToStart by rememberSaveable { mutableStateOf(startAt == null) }
            LaunchedEffect(startAtY) {
                startAtY?.takeIf { !scrolledToStart }?.let {
                    scrollState.scrollTo(it)
                    scrolledToStart = true
                }
            }
            val startMarker = { row: ProfilePageRow ->
                if (row == startAt) Modifier.onPlaced { startAtY = it.positionInParent().y.toInt() } else Modifier
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
                        .padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            ) {
                // The profile: name, colour, PIN (a child's own page: colour only).
                Column(modifier = inset, verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                    if (showsNameAndPin) {
                        ProfileNameField(
                            name = name,
                            onNameChange = {
                                name = it
                                showNameError = it.isBlank()
                            },
                            showError = showNameError,
                        )
                    }
                    ProfileColourPicker(colorIndex = colorIndex, onPick = {
                        colorIndex = it
                        apply(name)
                    })
                }
                if (showsNameAndPin) {
                    SettingsListRow(
                        title = stringResource(R.string.profile_pin_title),
                        summary =
                            stringResource(if (pinFlow.hasPin) R.string.profile_pin_on else R.string.profile_pin_off) + " · " +
                                stringResource(R.string.profile_pin_desc),
                        onClick = pinFlow::start,
                    )
                }

                SettingsGroupHeader(stringResource(R.string.profile_section_look), modifier = startMarker(ProfilePageRow.LOOK))
                SettingsListRow(
                    title = stringResource(R.string.ui_theme_title),
                    summary = stringResource(uiThemeNameRes(uiThemeId)) + " · " + stringResource(R.string.ui_theme_desc),
                    onClick = { choosingUiTheme = true },
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_theme_section_title),
                    summary = AllPalettes.firstOrNull { it.id == themeId }?.let { stringResource(it.nameRes) } ?: themeId,
                    onClick = { choosingTheme = true },
                    enabled = !lookLocked,
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_ui_style_section_title),
                    summary = AllUiStyles.firstOrNull { it.id == uiStyleId }?.let { stringResource(it.nameRes) } ?: uiStyleId,
                    onClick = { choosingLook = true },
                    enabled = !lookLocked,
                )
                ProfileSwitchRow(
                    title = stringResource(R.string.profile_poster_backdrop_title),
                    summary = stringResource(R.string.profile_poster_backdrop_desc),
                    checked = posterBackdrop,
                    onCheckedChange = {
                        posterBackdrop = it
                        viewModel.setPosterBackdrop(profileId, it)
                    },
                    modifier = inset,
                )

                SettingsGroupHeader(
                    stringResource(R.string.settings_playback_section_title),
                    modifier = startMarker(ProfilePageRow.AUTOPLAY),
                )
                ProfileSwitchRow(
                    title = stringResource(R.string.settings_autoplay_next_episode_title),
                    checked = autoplayNextEpisode,
                    onCheckedChange = {
                        autoplayNextEpisode = it
                        apply(name)
                    },
                    modifier = inset,
                )

                // The admin's (Expert mode, for now): Expert mode for this profile, its sources and its
                // content filters (the parental control).
                if (adminView) {
                    SettingsGroupHeader(stringResource(R.string.profile_section_admin))
                    // There is always an Expert: the only one can't turn it off, nor be a child's.
                    val listed by viewModel.profiles.collectAsStateWithLifecycle()
                    val lastExpert = remember(profileId, listed) { viewModel.isLastExpert(profileId) }
                    ProfileSwitchRow(
                        title = stringResource(R.string.profile_child_title),
                        checked = child,
                        onCheckedChange = {
                            child = it
                            if (it) devMode = false
                            apply(name)
                        },
                        summary =
                            stringResource(
                                if (devMode && lastExpert) R.string.profile_child_last_expert else R.string.profile_child_desc,
                            ),
                        enabled = !(devMode && lastExpert),
                        modifier = inset,
                    )
                    // No Expert has a PIN, so the child lock does nothing: the admin in use sets one here.
                    if (child && viewModel.adminPinHolders().isEmpty()) {
                        SettingsListRow(
                            title = stringResource(R.string.profile_child_set_pin),
                            summary = stringResource(R.string.profile_child_no_pin_warning),
                            onClick = { choosingAdminPin = true },
                        )
                    }
                    // A child profile (adult categories hidden) is never Expert.
                    if (!child) {
                        ProfileSwitchRow(
                            title = stringResource(R.string.settings_developer_mode_title),
                            checked = devMode,
                            onCheckedChange = {
                                devMode = it
                                apply(name)
                            },
                            summary = if (lastExpert) stringResource(R.string.settings_developer_mode_last) else null,
                            enabled = !lastExpert,
                            modifier = inset,
                        )
                    }
                    // With one source there is nothing to choose: it stays on.
                    if (sources.size > 1) {
                        SettingsListRow(
                            title = stringResource(R.string.profile_sources_title),
                            summary = stringResource(R.string.profile_sources_count_format, sources.count { it.isOn }, sources.size),
                            onClick = { editingSources = true },
                        )
                    }
                    if (filterSources.isNotEmpty()) {
                        SettingsListRow(
                            title = stringResource(R.string.profile_content_filters_title),
                            summary = filterSources.joinToString { it.name },
                            onClick = {
                                when (val choice = SourceChoice.of(filterSources)) {
                                    is SourceChoice.One -> editFilters(choice.source)
                                    is SourceChoice.Ask -> choosingFiltersSource = true
                                    SourceChoice.None -> Unit
                                }
                            },
                        )
                    }
                }

                Column(
                    modifier = inset.padding(top = CinemaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                ) {
                    // Switching to the profile already in use would do nothing.
                    if (!profile.isActive) {
                        CinemaOutlinedButton(
                            onClick = {
                                apply(name)
                                viewModel.switchTo(profileId, onProfileSwitched)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { CinemaText(stringResource(R.string.profile_switch_to_button), role = TextRole.Label, tone = TextTone.Inherit) }
                    }
                    if (canDelete) {
                        CinemaButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Danger) {
                            CinemaText(stringResource(R.string.profile_delete_button), role = TextRole.Label, tone = TextTone.Inherit)
                        }
                    }
                }
            }

            editingFilters?.let { (filtersSource, filters) ->
                MobileCategoryFilterDialog(
                    title = stringResource(R.string.provider_section_content_filters_format, filtersSource.name),
                    currentFilters = filters,
                    onSave = { newFilters ->
                        viewModel.saveCategoryFilters(filtersSource.id, profileId, newFilters)
                        editingFilters = null
                    },
                    onDismiss = { editingFilters = null },
                )
            }

            if (choosingFiltersSource) {
                MobileSourceChooserDialog(
                    sources = filterSources.map { it.id to it.name },
                    onChosen = { id ->
                        choosingFiltersSource = false
                        filterSources.firstOrNull { it.id == id }?.let(editFilters)
                    },
                    onDismiss = { choosingFiltersSource = false },
                )
            }

            if (editingSources) {
                // Each switch is saved at once, like the filters' own Save; Close only closes.
                CinemaAlertDialog(
                    onDismissRequest = { editingSources = false },
                    title = { CinemaText(stringResource(R.string.profile_sources_title), role = TextRole.Title) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                        ) {
                            CinemaText(stringResource(R.string.profile_sources_desc), tone = TextTone.Secondary)
                            sources.forEach { source ->
                                ProfileSwitchRow(
                                    title = source.name,
                                    checked = source.isOn,
                                    onCheckedChange = { viewModel.setProfileSource(profileId, sources, source.providerKey, it) },
                                    summary = if (source.filterable) null else stringResource(R.string.profile_source_no_filters),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = { editingSources = false },
                            kind = ButtonKind.Other,
                        ) { CinemaText(stringResource(R.string.common_close), role = TextRole.Label, tone = TextTone.Inherit) }
                    },
                )
            }

            if (confirmDelete) {
                CinemaAlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { CinemaText(stringResource(R.string.profile_delete_confirm_title, profile.name), role = TextRole.Title) },
                    text = { CinemaText(stringResource(R.string.profile_delete_confirm_text), tone = TextTone.Secondary) },
                    confirmButton = {
                        CinemaDialogActionButton(
                            onClick = {
                                confirmDelete = false
                                viewModel.deleteProfile(profileId)
                                onBack()
                            },
                            kind = ButtonKind.Danger,
                        ) { CinemaText(stringResource(R.string.profile_delete_button), role = TextRole.Label, tone = TextTone.Inherit) }
                    },
                    dismissButton = {
                        CinemaDialogActionButton(
                            onClick = { confirmDelete = false },
                            kind = ButtonKind.Other,
                        ) { CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit) }
                    },
                )
            }
        }
    }
}

/** Holds what the top bar's back arrow does, set once the page knows how to apply its name. */
private class LeaveAction(
    var action: () -> Unit,
)
