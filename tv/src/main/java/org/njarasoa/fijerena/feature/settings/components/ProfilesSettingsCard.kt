@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.ProfilePinFlow
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.components.rememberProfilePinFlow
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.home.SourceChoice
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.FilterSource
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileSourceUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.feature.provider.components.CategoryFilterDialog
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.TvPinDialog
import org.njarasoa.fijerena.ui.components.TvSourceChooserDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaButtonArea
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → Profiles: the profile rows (OK opens the profile's page, [onEdit]) and Add profile.
 * See `docs/plans/archive/20260929_live-sync-plan.md` → User profiles.
 */
@Composable
fun ProfilesSettingsCard(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int) -> Unit,
    onEdit: (ProfileUi) -> Unit,
    onDismissMessage: () -> Unit,
    scale: Float,
    /** Goes on the card's first focusable (the first profile row, else Add profile) — the pane's entry row. */
    firstRowModifier: Modifier = Modifier,
    /** Goes on each profile row: where Back from its page lands. */
    rowModifier: (ProfileUi) -> Modifier = { Modifier },
) {
    var adding by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_profiles_title),
        description = stringResource(R.string.settings_profiles_description),
    ) {
        profiles.forEachIndexed { index, profile ->
            SettingsRow(
                title = profile.name,
                description = if (profile.isActive) stringResource(R.string.settings_profiles_active_marker) else null,
                onClick = {
                    onDismissMessage()
                    onEdit(profile)
                },
                modifier = (if (index == 0) firstRowModifier else Modifier).then(rowModifier(profile)),
                leading = {
                    ProfileAvatar(
                        name = profile.name,
                        colorIndex = profile.colorIndex,
                        size = TvDimensions.iconMedium,
                        fontSize = textStyleOf(TextRole.Label).fontSize,
                    )
                },
            )
        }
        if (message != null) {
            CinemaText(message, role = TextRole.Body, tone = TextTone.Danger)
        }
        SettingsRow(
            title = stringResource(R.string.settings_profiles_add),
            description = null,
            onClick = {
                onDismissMessage()
                adding = true
            },
            modifier = if (profiles.isEmpty()) firstRowModifier else Modifier,
            chevron = false,
            leading = {
                Icon(
                    imageVector = CinemaIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(TvDimensions.iconMedium),
                )
            },
        )
    }

    if (adding) {
        ProfileAddDialog(
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color ->
                onAdd(name, color)
                adding = false
            },
            onDismiss = { adding = false },
            scale = scale,
        )
    }
}

/**
 * A profile's page, in place of the Profiles group's rows (D8): name, colour, its own Developer
 * mode and Play next episode automatically, Sources › (with two or more sources), Content
 * filters › (this profile's, on one of its Xtream sources — picked first when it has several, O4 of
 * docs/plans/20261012_multi-source-search-history-plan.md), Switch to this profile, Save /
 * Cancel, and Delete last. Left and Back leave it unsaved, like Cancel. Content filters are saved
 * by their own editor's Save, and Sources by each switch, at once.
 *
 * Without Expert mode the Profiles group shows the profile in use's page in place of its rows:
 * [focusOnOpen] false leaves focus on the Settings rail, and [entryModifier] (on the name row)
 * is where entering the pane lands; [title] reads "Your profile" there.
 */
@Composable
fun ProfileEditPane(
    profile: ProfileUi,
    viewModel: ProfilesViewModel,
    canDelete: Boolean,
    onSaved: () -> Unit,
    onSwitch: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    scale: Float,
    /** The profile in use has Expert mode (admin, for now): Sources and Content filters show. */
    adminView: Boolean = true,
    focusOnOpen: Boolean = true,
    entryModifier: Modifier = Modifier,
    title: String = stringResource(R.string.profile_dialog_edit_title),
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var colorIndex by rememberSaveable(profile.id) { mutableIntStateOf(profile.colorIndex) }
    val stored = remember(profile.id) { viewModel.settingsOf(profile.id) }
    var devMode by rememberSaveable(profile.id) { mutableStateOf(stored.devMode) }
    var autoplayNextEpisode by rememberSaveable(profile.id) { mutableStateOf(stored.autoplayNextEpisode) }
    var showNameError by remember { mutableStateOf(false) }
    var choosingColour by rememberSaveable { mutableStateOf(false) }
    var returnToColour by remember { mutableStateOf(false) }
    // The profile's colour theme and Look and Feel: saved as they are picked, like its sources.
    var themeId by remember(profile.id) { mutableStateOf(viewModel.themeOf(profile.id)) }
    var uiStyleId by remember(profile.id) { mutableStateOf(viewModel.uiStyleOf(profile.id)) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var choosingLook by rememberSaveable { mutableStateOf(false) }
    var returnToTheme by remember { mutableStateOf(false) }
    var returnToLook by remember { mutableStateOf(false) }
    val themeFocus = remember { FocusRequester() }
    val lookFocus = remember { FocusRequester() }
    var choosingSources by rememberSaveable { mutableStateOf(false) }
    var returnToSources by remember { mutableStateOf(false) }
    var editingFilters by remember { mutableStateOf<Pair<FilterSource, CategoryFilters>?>(null) }
    var choosingFiltersSource by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Set when a dialog opened from the page closes: focus goes back to the row that opened it.
    var refocus by remember { mutableStateOf<FocusRequester?>(null) }
    val nameFocus = remember { FocusRequester() }
    val colourFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val sourcesFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val pinFocus = remember { FocusRequester() }
    // The PIN row (docs/plans/archive/20261017_profile-pin-plan.md): saved as it is set, like the theme.
    val everyone by viewModel.profiles.collectAsStateWithLifecycle()
    val onlyExpert = remember(profile.id, everyone) { viewModel.isLastExpert(profile.id) }
    val pinFlow =
        rememberProfilePinFlow(
            pins = viewModel.pins,
            profileId = profile.id,
            own = profile.isActive,
            lastExpert = stored.devMode && onlyExpert,
        )

    // Content filters act on Xtream catalogues only (as they did in Edit Source).
    val filterSources by remember(profile.id) { viewModel.filterSources(profile.id) }.collectAsStateWithLifecycle(emptyList())
    val editFilters = { source: FilterSource ->
        viewModel.loadCategoryFilters(source.id, profile.id) { editingFilters = source to it }
    }
    val sources by remember(profile.id) { viewModel.profileSources(profile.id) }.collectAsStateWithLifecycle(emptyList())

    if (choosingColour) {
        SettingsPickerPane(
            title = stringResource(R.string.profile_color_label),
            options =
                CinemaProfileColors.palette.indices.map { index ->
                    PickerOption(
                        label = stringResource(R.string.profile_color_option_format, index + 1),
                        value = index,
                        preview = { ColourDot(index, scale) },
                    )
                },
            selectedValue = colorIndex,
            onPick = { colorIndex = it },
            onBack = {
                returnToColour = true
                choosingColour = false
            },
        )
    } else if (choosingTheme) {
        ThemePickerPane(
            selectedThemeId = themeId,
            admin = adminView,
            onPick = {
                themeId = it
                viewModel.setTheme(profile.id, it)
            },
            onBack = {
                returnToTheme = true
                choosingTheme = false
            },
        )
    } else if (choosingLook) {
        UiStylePickerPane(
            selectedUiStyleId = uiStyleId,
            onPick = {
                uiStyleId = it
                viewModel.setUiStyle(profile.id, it)
            },
            onBack = {
                returnToLook = true
                choosingLook = false
            },
        )
    } else if (choosingSources) {
        ProfileSourcesPane(
            sources = sources,
            onToggle = { key, on -> viewModel.setProfileSource(profile.id, sources, key, on) },
            onBack = {
                returnToSources = true
                choosingSources = false
            },
        )
    } else {
        LaunchedEffect(Unit) {
            val target =
                when {
                    returnToColour -> colourFocus
                    returnToTheme -> themeFocus
                    returnToLook -> lookFocus
                    returnToSources -> sourcesFocus
                    focusOnOpen -> nameFocus
                    else -> null
                }
            target?.requestFocusWithRetry()
            returnToColour = false
            returnToSources = false
        }
        LaunchedEffect(refocus) {
            refocus?.requestFocusWithRetry()
            refocus = null
        }

        // Left and Back leave the page (Left = back one level). Taken in onKeyEvent, after the focused
        // control: the name's text field keeps Left for its cursor and Back to cancel its edit. A Back
        // whose press the field took is swallowed on release too, or it would also close the page.
        // Shown without focus (focusOnOpen false), Back belongs to the Settings rail until the page has it.
        var backPressed by remember { mutableStateOf(false) }
        var pageHasFocus by remember { mutableStateOf(false) }
        BackHandler(enabled = focusOnOpen || pageHasFocus, onBack = onBack)
        Column(
            modifier =
                Modifier.fillMaxSize().onFocusChanged { pageHasFocus = it.hasFocus }.onKeyEvent { event ->
                    when (event.key) {
                        Key.Back -> {
                            if (event.type == KeyEventType.KeyDown) {
                                backPressed = true
                            } else if (backPressed) {
                                backPressed = false
                                onBack()
                            }
                            true
                        }

                        Key.DirectionLeft -> {
                            if (event.type == KeyEventType.KeyDown) onBack()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        ) {
            CinemaText(
                "‹ $title",
                modifier = Modifier.padding(horizontal = Spacing.xs),
                role = TextRole.Title,
                tone = TextTone.Primary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ReadOnlyFieldWithEdit(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = stringResource(R.string.profile_name_label),
                    keyboardOptions = FieldKeyboards.Name,
                    modifier = entryModifier,
                    editButtonFocusRequester = nameFocus,
                )
                if (showNameError) {
                    CinemaText(
                        stringResource(R.string.profile_error_name_required),
                        role = TextRole.Body,
                        tone = TextTone.Danger,
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.profile_color_label),
                    description = null,
                    onClick = { choosingColour = true },
                    focusRequester = colourFocus,
                    leading = { ColourDot(colorIndex, scale) },
                )
                ThemeSettingsRows(
                    selectedThemeId = themeId,
                    onOpenThemePicker = { choosingTheme = true },
                    selectedUiStyleId = uiStyleId,
                    onOpenUiStylePicker = { choosingLook = true },
                    themeRowFocusRequester = themeFocus,
                    uiStyleRowFocusRequester = lookFocus,
                )
                SettingsRow(
                    title = stringResource(R.string.profile_pin_title),
                    description = stringResource(if (stored.devMode) R.string.profile_pin_desc_expert else R.string.profile_pin_desc),
                    value = stringResource(if (pinFlow.hasPin) R.string.profile_pin_on else R.string.profile_pin_off),
                    onClick = pinFlow::start,
                    focusRequester = pinFocus,
                )
                // Only for an Expert (the admin, for now), who may give it to or take it from any
                // profile; otherwise it is turned on by tapping the version in About 7 times
                // (DeveloperModeTaps).
                if (adminView) {
                    // There is always an Expert: the only one can't turn it off.
                    val listed by viewModel.profiles.collectAsStateWithLifecycle()
                    val lastExpert = remember(profile.id, listed) { viewModel.isLastExpert(profile.id) }
                    TvSwitchRow(
                        checked = devMode,
                        onCheckedChange = { devMode = it },
                        label = stringResource(R.string.settings_developer_mode_title),
                        description =
                            stringResource(
                                if (lastExpert) R.string.settings_developer_mode_last else R.string.settings_developer_mode_desc,
                            ),
                        enabled = !lastExpert,
                    )
                }
                TvSwitchRow(
                    checked = autoplayNextEpisode,
                    onCheckedChange = { autoplayNextEpisode = it },
                    label = stringResource(R.string.settings_autoplay_next_episode_title),
                    description = stringResource(R.string.settings_autoplay_next_episode_desc),
                )
                // With one source there is nothing to choose: it stays on.
                // Sources and Content filters (the parental control) are admin-only.
                if (adminView && sources.size > 1) {
                    SettingsRow(
                        title = stringResource(R.string.profile_sources_title),
                        description = null,
                        value = stringResource(R.string.profile_sources_count_format, sources.count { it.isOn }, sources.size),
                        onClick = { choosingSources = true },
                        focusRequester = sourcesFocus,
                    )
                }
                if (adminView && filterSources.isNotEmpty()) {
                    SettingsRow(
                        title = stringResource(R.string.profile_content_filters_title),
                        description = stringResource(R.string.provider_category_filters_desc),
                        value = filterSources.joinToString { it.name },
                        onClick = {
                            when (val choice = SourceChoice.of(filterSources)) {
                                is SourceChoice.One -> editFilters(choice.source)
                                is SourceChoice.Ask -> choosingFiltersSource = true
                                SourceChoice.None -> Unit
                            }
                        },
                        focusRequester = filtersFocus,
                    )
                }
                CinemaButtonArea(modifier = Modifier.align(Alignment.End)) {
                    // Switching to the profile already in use would do nothing.
                    if (!profile.isActive) {
                        CinemaSecondaryButton(
                            onClick = onSwitch,
                            text = stringResource(R.string.profile_switch_to_button),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CinemaPrimaryButton(
                        onClick = {
                            if (name.isBlank()) {
                                showNameError = true
                            } else {
                                viewModel.updateProfile(
                                    profile.id,
                                    name,
                                    colorIndex,
                                    stored.copy(devMode = devMode, autoplayNextEpisode = autoplayNextEpisode),
                                )
                                onSaved()
                            }
                        },
                        text = stringResource(R.string.profile_save_button),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CinemaSecondaryButton(
                        onClick = onBack,
                        text = stringResource(R.string.common_cancel),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The profile in use and the last one left can't be deleted (ProfileRepository refuses
                    // both); not offering the button beats offering it and then explaining why not.
                    if (canDelete) {
                        CinemaDangerButton(
                            onClick = { confirmDelete = true },
                            text = stringResource(R.string.profile_delete_button),
                            modifier = Modifier.fillMaxWidth().focusRequester(deleteFocus),
                        )
                    }
                }
            }
        }

        if (choosingFiltersSource) {
            TvSourceChooserDialog(
                sources = filterSources.map { it.id to it.name },
                onChosen = { id ->
                    choosingFiltersSource = false
                    filterSources.firstOrNull { it.id == id }?.let(editFilters)
                },
                onDismiss = {
                    choosingFiltersSource = false
                    refocus = filtersFocus
                },
            )
        }

        editingFilters?.let { (filtersSource, filters) ->
            CategoryFilterDialog(
                title = stringResource(R.string.provider_section_content_filters_format, filtersSource.name),
                currentFilters = filters,
                onSave = { newFilters ->
                    viewModel.saveCategoryFilters(filtersSource.id, profile.id, newFilters)
                    editingFilters = null
                    refocus = filtersFocus
                },
                onDismiss = {
                    editingFilters = null
                    refocus = filtersFocus
                },
            )
        }

        pinFlow.request?.let { request ->
            val prompt =
                rememberPinPromptState(viewModel.pins, request) {
                    pinFlow.promptDone()
                    if (pinFlow.step == null) refocus = pinFocus
                }
            TvPinDialog(
                state = prompt,
                onCancel = {
                    pinFlow.cancel()
                    refocus = pinFocus
                },
            )
        }
        if (pinFlow.step == ProfilePinFlow.Step.OPTIONS) {
            val closeOptions = {
                pinFlow.cancel()
                refocus = pinFocus
            }
            CinemaAlertDialog(
                onDismissRequest = closeOptions,
                containerColor = CinemaTheme.colors.background,
                titleContentColor = CinemaTheme.colors.textPrimary,
                title = { CinemaText(stringResource(R.string.profile_pin_title)) },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CinemaButton(onClick = pinFlow::change, kind = ButtonKind.Main) {
                            CinemaText(stringResource(R.string.profile_pin_change))
                        }
                        CinemaButton(
                            onClick = {
                                pinFlow.remove()
                                refocus = pinFocus
                            },
                            kind = ButtonKind.Danger,
                        ) { CinemaText(stringResource(R.string.profile_pin_remove)) }
                    }
                },
                dismissButton = {
                    CinemaButton(onClick = closeOptions, kind = ButtonKind.Other) {
                        CinemaText(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
        if (confirmDelete) {
            ConfirmActionDialog(
                title = stringResource(R.string.profile_delete_confirm_title, profile.name),
                text = stringResource(R.string.profile_delete_confirm_text),
                confirmText = stringResource(R.string.profile_delete_button),
                onConfirm = {
                    confirmDelete = false
                    viewModel.deleteProfile(profile.id)
                    onDeleted()
                },
                onDismiss = {
                    confirmDelete = false
                    refocus = deleteFocus
                },
            )
        }
    }
}

/**
 * A profile's Sources, in place of its page like the colour picker: every source on this device
 * with a switch, each change saved at once (multi-source plan, Phase 2). The explanation sits
 * above the list, always on screen, so no row hides it. Focus opens on the first source; Back and
 * Left leave (taken in `onPreviewKeyEvent` on the root, as in [SettingsPickerPane]). A switch that
 * may not go off — the profile's last source, or the one in use here — stays on when pressed.
 */
@Composable
private fun ProfileSourcesPane(
    sources: List<ProfileSourceUi>,
    onToggle: (providerKey: String, on: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWithRetry() }

    Column(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Back -> {
                        if (event.type == KeyEventType.KeyUp) onBack()
                        true
                    }

                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) onBack()
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) {
        CinemaText(
            "‹ ${stringResource(R.string.profile_sources_title)}",
            modifier = Modifier.padding(horizontal = Spacing.xs),
            role = TextRole.Title,
            tone = TextTone.Primary,
        )
        CinemaText(
            stringResource(R.string.profile_sources_desc),
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            role = TextRole.Body,
            tone = TextTone.Secondary,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            itemsIndexed(sources, key = { _, source -> source.providerKey }) { index, source ->
                TvSwitchRow(
                    checked = source.isOn,
                    onCheckedChange = { onToggle(source.providerKey, it) },
                    label = source.name,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

/** New profile: a name and a colour. Its own settings start off; it's edited on its page afterwards. */
@Composable
internal fun ProfileAddDialog(
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit,
    scale: Float,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var showNameError by remember { mutableStateOf(false) }
    // Without an initial focus the dialog opens with focus nowhere and D-pad presses go nowhere.
    val nameFocusRequester = remember { FocusRequester() }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(stringResource(R.string.profile_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                ReadOnlyFieldWithEdit(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = stringResource(R.string.profile_name_label),
                    keyboardOptions = FieldKeyboards.Name,
                    editButtonFocusRequester = nameFocusRequester,
                )
                if (showNameError) {
                    CinemaText(
                        stringResource(R.string.profile_error_name_required),
                        role = TextRole.Body,
                        tone = TextTone.Danger,
                    )
                }
                CinemaText(stringResource(R.string.profile_color_label), role = TextRole.Body, tone = TextTone.Secondary)
                // Two rows: the whole palette on one line doesn't fit the dialog's width.
                CinemaProfileColors.palette.indices.chunked(SWATCHES_PER_ROW).forEach { rowIndices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                        rowIndices.forEach { index ->
                            ColorSwatch(
                                index = index,
                                selected = index == colorIndex,
                                onClick = { colorIndex = index },
                                scale = scale,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CinemaButton(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSave(name, colorIndex)
                    }
                },
                kind = ButtonKind.Main,
            ) { androidx.compose.material3.Text(stringResource(R.string.profile_save_button)) }
        },
        dismissButton = {
            // Cancel is secondary: resting container, accent label. Save keeps the fill.
            CinemaButton(
                onClick = onDismiss,
                kind = ButtonKind.Other,
            ) {
                androidx.compose.material3.Text(stringResource(R.string.common_cancel))
            }
        },
        initialFocus = nameFocusRequester,
    )
}

private const val SWATCHES_PER_ROW = 4

/** A profile colour as a plain dot, for the page's Colour row and its picker. */
@Composable
private fun ColourDot(
    index: Int,
    scale: Float,
) {
    ProfileAvatar(
        name = "",
        colorIndex = index,
        size = TvDimensions.iconMedium.scaled(scale),
        fontSize =
            textStyleOf(TextRole.Label)
                .fontSize
                .scaled(scale),
    )
}

@Composable
private fun ColorSwatch(
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    scale: Float,
) {
    val color = CinemaProfileColors.forIndex(index)
    val label = stringResource(R.string.profile_color_option_format, index + 1)
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .size(TvDimensions.iconLarge.scaled(scale))
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
        shape = ClickableSurfaceDefaults.shape(shape = CinemaTheme.shapes.identity),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = color,
                focusedContainerColor = color,
                pressedContainerColor = color,
            ),
        scale =
            ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    if (selected) {
                        Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaTheme.colors.textPrimary))
                    } else {
                        Border.None
                    },
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaTheme.colors.accentSoft)),
            ),
    ) {}
}
