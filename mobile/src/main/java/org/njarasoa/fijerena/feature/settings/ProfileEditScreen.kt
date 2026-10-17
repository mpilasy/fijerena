package org.njarasoa.fijerena.feature.settings

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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
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
import org.njarasoa.fijerena.core.ui.theme.palettesFor
import org.njarasoa.fijerena.core.ui.viewmodels.FilterSource
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.feature.contentselection.components.MobileSourceChooserDialog
import org.njarasoa.fijerena.feature.provider.components.MobileCategoryFilterDialog
import org.njarasoa.fijerena.feature.settings.components.ProfileColourPicker
import org.njarasoa.fijerena.feature.settings.components.ProfileNameField
import org.njarasoa.fijerena.feature.settings.components.ProfileSwitchRow
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.feature.settings.components.SettingsScope
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A profile's page (`Screen.ProfileEdit`, D8): name, colour, its own Developer mode and Play next
 * episode automatically, Sources › (with two or more sources), Content filters › (this profile's,
 * on one of its Xtream sources — picked first when it has several, O4 of
 * docs/plans/20261012_multi-source-search-history-plan.md), Switch to this profile, Save / Cancel,
 * and Delete last.
 * Content filters are saved by their own editor's Save, and Sources by each switch, at once; the
 * rest by Save. See docs/plans/archive/20261003_sources-guide-profiles-plan.md → P9.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProfileEditScreen(
    profileId: String,
    // Settings' own: saving and deleting run in its scope, so leaving this page doesn't cancel them.
    viewModel: ProfilesViewModel,
    onBack: () -> Unit,
    onProfileSwitched: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    // Content filters act on Xtream catalogues only (as they did in Edit Source).
    val filterSources by remember(profileId) { viewModel.filterSources(profileId) }.collectAsStateWithLifecycle(emptyList())
    val sources by remember(profileId) { viewModel.profileSources(profileId) }.collectAsStateWithLifecycle(emptyList())
    val profile = profiles.firstOrNull { it.id == profileId }

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
                    IconButton(onClick = onBack) {
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
            var showNameError by remember { mutableStateOf(false) }
            var editingFilters by remember { mutableStateOf<Pair<FilterSource, CategoryFilters>?>(null) }
            var choosingFiltersSource by remember { mutableStateOf(false) }
            val editFilters = { source: FilterSource ->
                viewModel.loadCategoryFilters(source.id, profileId) { editingFilters = source to it }
            }
            var confirmDelete by remember { mutableStateOf(false) }
            // Sources, Content filters, Expert mode and the expert-only themes are admin-only.
            val adminView = remember { viewModel.viewerIsAdmin }
            // The profile's colour theme and Look and Feel: saved as they are picked, like its sources.
            var themeId by remember(profileId) { mutableStateOf(viewModel.themeOf(profileId)) }
            var uiStyleId by remember(profileId) { mutableStateOf(viewModel.uiStyleOf(profileId)) }
            var choosingTheme by rememberSaveable { mutableStateOf(false) }
            var choosingLook by rememberSaveable { mutableStateOf(false) }
            if (choosingTheme) {
                SettingsPickerDialog(
                    title = stringResource(R.string.settings_theme_section_title),
                    options = palettesFor(adminView, themeId).map { stringResource(it.nameRes) to it.id },
                    selected = themeId,
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
                    options = AllUiStyles.map { stringResource(it.nameRes) to it.id },
                    selected = uiStyleId,
                    onSelect = { id ->
                        uiStyleId = id
                        viewModel.setUiStyle(profileId, id)
                        choosingLook = false
                    },
                    onDismiss = { choosingLook = false },
                )
            }
            // The PIN row (docs/plans/archive/20261017_profile-pin-plan.md): saved as it is set, like the theme.
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
            var editingSources by rememberSaveable { mutableStateOf(false) }
            // The profile in use and the last one left can't be deleted (ProfileRepository refuses both).
            val canDelete = !profile.isActive && profiles.size > 1

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            ) {
                ProfileNameField(
                    name = name,
                    onNameChange = {
                        name = it
                        showNameError = false
                    },
                    showError = showNameError,
                )
                ProfileColourPicker(colorIndex = colorIndex, onPick = { colorIndex = it })
                SettingsListRow(
                    title = stringResource(R.string.settings_theme_section_title),
                    summary = AllPalettes.firstOrNull { it.id == themeId }?.let { stringResource(it.nameRes) } ?: themeId,
                    onClick = { choosingTheme = true },
                )
                SettingsListRow(
                    title = stringResource(R.string.settings_ui_style_section_title),
                    summary = AllUiStyles.firstOrNull { it.id == uiStyleId }?.let { stringResource(it.nameRes) } ?: uiStyleId,
                    onClick = { choosingLook = true },
                )
                SettingsListRow(
                    title = stringResource(R.string.profile_pin_title),
                    summary =
                        stringResource(if (pinFlow.hasPin) R.string.profile_pin_on else R.string.profile_pin_off) + " · " +
                            stringResource(if (stored.devMode) R.string.profile_pin_desc_expert else R.string.profile_pin_desc),
                    onClick = pinFlow::start,
                )
                // Only for an Expert (the admin, for now), who may give it to or take it from any
                // profile; otherwise it is turned on by tapping the version in About 7 times
                // (DeveloperModeTaps).
                if (adminView) {
                    // There is always an Expert: the only one can't turn it off.
                    val listed by viewModel.profiles.collectAsStateWithLifecycle()
                    val lastExpert = remember(profileId, listed) { viewModel.isLastExpert(profileId) }
                    ProfileSwitchRow(
                        title = stringResource(R.string.settings_developer_mode_title),
                        checked = devMode,
                        onCheckedChange = { devMode = it },
                        summary = if (lastExpert) stringResource(R.string.settings_developer_mode_last) else null,
                        enabled = !lastExpert,
                    )
                }
                ProfileSwitchRow(
                    title = stringResource(R.string.settings_autoplay_next_episode_title),
                    checked = autoplayNextEpisode,
                    onCheckedChange = { autoplayNextEpisode = it },
                )
                // With one source there is nothing to choose: it stays on.
                if (adminView && sources.size > 1) {
                    SettingsListRow(
                        title = stringResource(R.string.profile_sources_title),
                        summary = stringResource(R.string.profile_sources_count_format, sources.count { it.isOn }, sources.size),
                        onClick = { editingSources = true },
                    )
                }
                if (adminView && filterSources.isNotEmpty()) {
                    SettingsListRow(
                        title = stringResource(R.string.profile_content_filters_title),
                        summary = filterSources.joinToString { it.name },
                        scope = SettingsScope.SOURCE,
                        onClick = {
                            when (val choice = SourceChoice.of(filterSources)) {
                                is SourceChoice.One -> editFilters(choice.source)
                                is SourceChoice.Ask -> choosingFiltersSource = true
                                SourceChoice.None -> Unit
                            }
                        },
                    )
                }
                // Switching to the profile already in use would do nothing.
                if (!profile.isActive) {
                    CinemaOutlinedButton(
                        onClick = { viewModel.switchTo(profileId, onProfileSwitched) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { CinemaText(stringResource(R.string.profile_switch_to_button), role = TextRole.Label, tone = TextTone.Inherit) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                    CinemaOutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
                    }
                    CinemaButton(
                        onClick = {
                            if (name.isBlank()) {
                                showNameError = true
                            } else {
                                viewModel.updateProfile(
                                    profileId,
                                    name,
                                    colorIndex,
                                    stored.copy(devMode = devMode, autoplayNextEpisode = autoplayNextEpisode),
                                )
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { CinemaText(stringResource(R.string.profile_save_button), role = TextRole.Label, tone = TextTone.Inherit) }
                }
                if (canDelete) {
                    CinemaButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Danger) {
                        CinemaText(stringResource(R.string.profile_delete_button), role = TextRole.Label, tone = TextTone.Inherit)
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
