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
import androidx.compose.ui.focus.focusProperties
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
import org.njarasoa.fijerena.core.network.UiThemes
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.profile.SwitchLock
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.PaletteSwatches
import org.njarasoa.fijerena.core.ui.components.PinRequest
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
import org.njarasoa.fijerena.core.ui.theme.packagedUiThemesFor
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.uiThemeNameRes
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
import org.njarasoa.fijerena.ui.theme.ProvideLookAndAccent
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.ThemePreview
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → Admin → Profiles: the profile rows (OK opens the profile's page, [onEdit]) and Add profile.
 * See the live sync plan → User profiles.
 */
@Composable
fun ProfilesSettingsCard(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int, child: Boolean) -> Unit,
    onEdit: (ProfileUi) -> Unit,
    onDismissMessage: () -> Unit,
    scale: Float,
    /** Goes on the card's first focusable (the first profile row, else Add profile) — the pane's entry row. */
    firstRowModifier: Modifier = Modifier,
    /** Goes on each profile row: where Back from its page lands. */
    rowModifier: (ProfileUi) -> Modifier = { Modifier },
    /**
     * Set when a child profile exists and no Expert has a PIN ([ProfilesViewModel.needsAdminPin]):
     * a first row offers the profile in use a PIN, chosen here with these [pins].
     */
    pinNudge: ProfilePins? = null,
    /** The profile in use is the only Expert: choosing its PIN warns how hard a forgotten one is. */
    pinNudgeLastExpert: Boolean = false,
    /** No Expert has a PIN: see [ProfileAddDialog]. */
    noAdminPin: Boolean = false,
) {
    var adding by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    var pinChosen by remember { mutableStateOf(false) }
    val nudgeFor = profiles.firstOrNull { it.isActive }?.takeIf { pinNudge != null && !pinChosen }
    // The nudge row goes once its PIN is chosen, and it had the focus: the first profile takes it.
    val firstProfile = remember { FocusRequester() }
    LaunchedEffect(pinChosen) {
        if (pinChosen) runCatching { firstProfile.requestFocus() }
    }

    // Admin's Profiles header names the card: no title of its own.
    SettingsSection(description = stringResource(R.string.settings_profiles_description)) {
        if (nudgeFor != null) {
            SettingsRow(
                title = stringResource(R.string.settings_pin_nudge_title),
                description = stringResource(R.string.settings_pin_nudge_desc),
                onClick = { choosingPin = true },
                modifier = firstRowModifier,
                leading = {
                    Icon(
                        imageVector = CinemaIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(TvDimensions.iconMedium),
                    )
                },
            )
        }
        profiles.forEachIndexed { index, profile ->
            SettingsRow(
                title = profile.name,
                description = if (profile.isActive) stringResource(R.string.settings_profiles_active_marker) else null,
                onClick = {
                    onDismissMessage()
                    onEdit(profile)
                },
                modifier =
                    (if (index == 0 && nudgeFor == null) firstRowModifier else Modifier)
                        .then(if (index == 0) Modifier.focusRequester(firstProfile) else Modifier)
                        .then(rowModifier(profile)),
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

    if (choosingPin && nudgeFor != null && pinNudge != null) {
        val prompt =
            rememberPinPromptState(
                pinNudge,
                PinRequest.Choose(nudgeFor.id, if (pinNudgeLastExpert) stringResource(R.string.pin_last_expert_warning) else null),
            ) {
                choosingPin = false
                pinChosen = true
            }
        TvPinDialog(state = prompt, onCancel = { choosingPin = false })
    }

    if (adding) {
        ProfileAddDialog(
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color, child ->
                onAdd(name, color, child)
                adding = false
            },
            onDismiss = { adding = false },
            scale = scale,
            noAdminPin = noAdminPin,
        )
    }
}

/**
 * A profile's page, in place of the Profiles group's rows (D8): name, colour, its own Developer
 * mode and Play next episode automatically, Sources › (with two or more sources), Content
 * filters › (this profile's, on one of its Xtream sources — picked first when it has several, O4 of
 * docs/plans/20261012_multi-source-search-history-plan.md), Switch to this profile, and Delete
 * last. Every change applies at once; the name applies when the page is left (Left or Back).
 * Content filters are saved by their own editor's Save.
 *
 * Your Profile shows the profile in use's page: [focusOnOpen] false leaves focus on the Settings
 * rail, and [entryModifier] (on the name row) is where entering the pane lands; [title] reads
 * "Your profile" there. [adminRows] (Child, Expert mode, Sources, Content filters, a kid's
 * Favourites and Parental controls) shows only in Admin → Profiles (the admin settings plan, AS-D1).
 *
 * [canAddProfile] (a grown-up's own page, without Expert mode) adds an Add profile row at the end,
 * above Switch and Delete; the new profile is not opened, since only an Expert edits another one.
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
    /** The profile in use has Expert mode (admin, for now): the Expert themes show. */
    adminView: Boolean = true,
    /** The page is Admin's (Admin → Profiles): the Admin rows show. */
    adminRows: Boolean = adminView,
    focusOnOpen: Boolean = true,
    entryModifier: Modifier = Modifier,
    title: String = stringResource(R.string.profile_dialog_edit_title),
    /** A child's page, admin view: "Parental controls ›" opens that group (the page is saved first). */
    onParentalControls: () -> Unit = {},
    canAddProfile: Boolean = false,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var colorIndex by rememberSaveable(profile.id) { mutableIntStateOf(profile.colorIndex) }
    val stored = remember(profile.id) { viewModel.settingsOf(profile.id) }
    var devMode by rememberSaveable(profile.id) { mutableStateOf(stored.devMode) }
    var autoplayNextEpisode by rememberSaveable(profile.id) { mutableStateOf(stored.autoplayNextEpisode) }
    var child by rememberSaveable(profile.id) { mutableStateOf(stored.child) }
    // A child's own page has no Name and no PIN; the page then starts on Colour.
    val showsNameAndPin = stored.showsNameAndPin(adminView)
    var showNameError by remember { mutableStateOf(false) }
    // Every change applies at once, as in the rest of Settings; the name when the page is left
    // (a blank one is dropped, the old name kept).
    val apply = {
        viewModel.updateProfile(
            profile.id,
            name.trim().ifEmpty { profile.name },
            colorIndex,
            stored.copy(devMode = devMode, autoplayNextEpisode = autoplayNextEpisode, child = child),
        )
    }
    val leave = {
        apply()
        onSaved()
    }
    var choosingColour by rememberSaveable { mutableStateOf(false) }
    var returnToColour by remember { mutableStateOf(false) }
    // The profile's colour theme and Look and Feel: saved as they are picked, like its sources.
    var themeId by remember(profile.id) { mutableStateOf(viewModel.themeOf(profile.id)) }
    var uiStyleId by remember(profile.id) { mutableStateOf(viewModel.uiStyleOf(profile.id)) }
    var posterBackdrop by remember(profile.id) { mutableStateOf(viewModel.posterBackdropOf(profile.id)) }
    // A packaged UI theme sets the colour theme and the Look and Feel (docs/plans/20261021_ui-themes-plan.md).
    var uiThemeId by remember(profile.id) { mutableStateOf(viewModel.uiThemeOf(profile.id)) }
    val lookLocked = uiThemeId != UiThemes.CUSTOM
    var choosingUiTheme by rememberSaveable { mutableStateOf(false) }
    var returnToUiTheme by remember { mutableStateOf(false) }
    val uiThemeFocus = remember { FocusRequester() }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var choosingLook by rememberSaveable { mutableStateOf(false) }
    var returnToTheme by remember { mutableStateOf(false) }
    var returnToLook by remember { mutableStateOf(false) }
    val themeFocus = remember { FocusRequester() }
    val lookFocus = remember { FocusRequester() }
    var choosingSources by rememberSaveable { mutableStateOf(false) }
    var returnToSources by remember { mutableStateOf(false) }
    // A child profile's Favourites pane (kids' Home plan, Phase 3), and how many it has.
    var choosingFavorites by rememberSaveable { mutableStateOf(false) }
    var returnToFavorites by remember { mutableStateOf(false) }
    val favoritesFocus = remember { FocusRequester() }
    var favoriteCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(profile.id, child, choosingFavorites) {
        if (child && adminRows && !choosingFavorites) favoriteCount = viewModel.favoritesOf(profile.id).size
    }
    var editingFilters by remember { mutableStateOf<Pair<FilterSource, CategoryFilters>?>(null) }
    var choosingFiltersSource by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val addFocus = remember { FocusRequester() }
    // Set when a dialog opened from the page closes: focus goes back to the row that opened it.
    var refocus by remember { mutableStateOf<FocusRequester?>(null) }
    val nameFocus = remember { FocusRequester() }
    val colourFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val sourcesFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    val pinFocus = remember { FocusRequester() }
    val childFocus = remember { FocusRequester() }
    val setPinFocus = remember { FocusRequester() }
    // "Set a PIN ›" under the Child switch: the PIN of the Expert profile in use.
    var choosingAdminPin by remember { mutableStateOf(false) }
    // The PIN row (the profile pin plan): saved as it is set, like the theme.
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
            onPick = {
                colorIndex = it
                apply()
            },
            onBack = {
                returnToColour = true
                choosingColour = false
            },
        )
    } else if (choosingUiTheme) {
        // Each UI theme's colour theme and Look; Custom is the profile's own (as last saved).
        val looks =
            mapOf(UiThemes.CUSTOM to (themeId to uiStyleId)) +
                packagedUiThemesFor(adminView, uiThemeId).associate { it.id to (it.themeId to it.uiStyleId) }
        SettingsPickerPane(
            title = stringResource(R.string.ui_theme_title),
            options =
                looks.map { (id, look) ->
                    // Each row in its theme's font and accent, with its colours beside it.
                    PickerOption(
                        stringResource(uiThemeNameRes(id)),
                        id,
                        preview = { PaletteSwatches(paletteById(look.first)) },
                        inLook = { ProvideLookAndAccent(look.first, look.second, it) },
                    )
                },
            selectedValue = uiThemeId,
            // The pane previews the focused UI theme; the app's theme changes only on OK.
            paneLook = { id, content -> looks[id]?.let { ThemePreview(it.first, it.second, content) } ?: content() },
            onPick = {
                uiThemeId = it
                viewModel.setUiTheme(profile.id, it)
                themeId = viewModel.themeOf(profile.id)
                uiStyleId = viewModel.uiStyleOf(profile.id)
            },
            onBack = {
                returnToUiTheme = true
                choosingUiTheme = false
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
    } else if (choosingFavorites) {
        ProfileFavoritesPane(
            profile = profile,
            viewModel = viewModel,
            onBack = {
                returnToFavorites = true
                choosingFavorites = false
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
                    returnToUiTheme -> uiThemeFocus
                    returnToTheme -> themeFocus
                    returnToLook -> lookFocus
                    returnToSources -> sourcesFocus
                    returnToFavorites -> favoritesFocus
                    focusOnOpen -> if (showsNameAndPin) nameFocus else colourFocus
                    else -> null
                }
            target?.requestFocusWithRetry()
            returnToColour = false
            returnToUiTheme = false
            returnToTheme = false
            returnToLook = false
            returnToSources = false
            returnToFavorites = false
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
        BackHandler(enabled = focusOnOpen || pageHasFocus, onBack = leave)
        Column(
            modifier =
                Modifier.fillMaxSize().onFocusChanged { pageHasFocus = it.hasFocus }.onKeyEvent { event ->
                    when (event.key) {
                        Key.Back -> {
                            if (event.type == KeyEventType.KeyDown) {
                                backPressed = true
                            } else if (backPressed) {
                                backPressed = false
                                leave()
                            }
                            true
                        }

                        Key.DirectionLeft -> {
                            if (event.type == KeyEventType.KeyDown) leave()
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
                if (showsNameAndPin) {
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
                }
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
                    modifier = if (showsNameAndPin) Modifier else entryModifier,
                    focusRequester = colourFocus,
                    leading = { ColourDot(colorIndex, scale) },
                )
                if (showsNameAndPin) {
                    SettingsRow(
                        title = stringResource(R.string.profile_pin_title),
                        description = stringResource(R.string.profile_pin_desc),
                        value = stringResource(if (pinFlow.hasPin) R.string.profile_pin_on else R.string.profile_pin_off),
                        onClick = pinFlow::start,
                        focusRequester = pinFocus,
                    )
                }
                CinemaText(stringResource(R.string.profile_section_look), role = TextRole.Heading, tone = TextTone.Primary)
                SettingsRow(
                    title = stringResource(R.string.ui_theme_title),
                    description = stringResource(R.string.ui_theme_desc),
                    value = stringResource(uiThemeNameRes(uiThemeId)),
                    onClick = { choosingUiTheme = true },
                    focusRequester = uiThemeFocus,
                )
                ThemeSettingsRows(
                    selectedThemeId = themeId,
                    onOpenThemePicker = { choosingTheme = true },
                    selectedUiStyleId = uiStyleId,
                    onOpenUiStylePicker = { choosingLook = true },
                    themeRowFocusRequester = themeFocus,
                    uiStyleRowFocusRequester = lookFocus,
                    enabled = !lookLocked,
                )
                TvSwitchRow(
                    checked = posterBackdrop,
                    onCheckedChange = {
                        posterBackdrop = it
                        viewModel.setPosterBackdrop(profile.id, it)
                    },
                    label = stringResource(R.string.profile_poster_backdrop_title),
                    description = stringResource(R.string.profile_poster_backdrop_desc),
                )
                CinemaText(stringResource(R.string.settings_playback_section_title), role = TextRole.Heading, tone = TextTone.Primary)
                TvSwitchRow(
                    checked = autoplayNextEpisode,
                    onCheckedChange = {
                        autoplayNextEpisode = it
                        apply()
                    },
                    label = stringResource(R.string.settings_autoplay_next_episode_title),
                    description = stringResource(R.string.settings_autoplay_next_episode_desc),
                )
                // The admin's (Expert mode, for now): Expert mode for this profile, its sources and its
                // content filters (the parental control).
                if (adminRows) {
                    CinemaText(stringResource(R.string.profile_section_admin), role = TextRole.Heading, tone = TextTone.Primary)
                }
                if (adminRows) {
                    // There is always an Expert: the only one can't turn it off, nor be a child's.
                    val listed by viewModel.profiles.collectAsStateWithLifecycle()
                    val lastExpert = remember(profile.id, listed) { viewModel.isLastExpert(profile.id) }
                    TvSwitchRow(
                        checked = child,
                        onCheckedChange = {
                            child = it
                            if (it) devMode = false
                            apply()
                        },
                        label = stringResource(R.string.profile_child_title),
                        description =
                            stringResource(
                                if (devMode && lastExpert) R.string.profile_child_last_expert else R.string.profile_child_desc,
                            ),
                        enabled = !(devMode && lastExpert),
                        modifier = Modifier.focusRequester(childFocus),
                    )
                    // No Expert has a PIN, so the child lock does nothing: the admin in use sets one here.
                    if (SwitchLock.ON && child && viewModel.adminPinHolders().isEmpty()) {
                        SettingsRow(
                            title = stringResource(R.string.profile_child_set_pin),
                            description = stringResource(R.string.profile_child_no_pin_warning),
                            onClick = { choosingAdminPin = true },
                            focusRequester = setPinFocus,
                        )
                    }
                    // What a child's Home shows: filled here, not by the child.
                    if (child) {
                        SettingsRow(
                            title = stringResource(R.string.profile_favorites_title),
                            description = stringResource(R.string.profile_favorites_row_desc),
                            value = favoriteCount?.toString(),
                            onClick = { choosingFavorites = true },
                            focusRequester = favoritesFocus,
                        )
                    }
                    // A child profile (adult categories hidden) is never Expert.
                    if (!child) {
                        TvSwitchRow(
                            checked = devMode,
                            onCheckedChange = {
                                devMode = it
                                apply()
                            },
                            label = stringResource(R.string.settings_developer_mode_title),
                            description =
                                stringResource(
                                    if (lastExpert) R.string.settings_developer_mode_last else R.string.settings_developer_mode_desc,
                                ),
                            enabled = !lastExpert,
                        )
                    }
                }
                // With one source there is nothing to choose: it stays on.
                // Sources and Content filters (the parental control) are admin-only.
                if (adminRows && sources.size > 1) {
                    SettingsRow(
                        title = stringResource(R.string.profile_sources_title),
                        description = null,
                        value = stringResource(R.string.profile_sources_count_format, sources.count { it.isOn }, sources.size),
                        onClick = { choosingSources = true },
                        focusRequester = sourcesFocus,
                    )
                }
                if (adminRows && filterSources.isNotEmpty()) {
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
                // The household's word lists, which decide what every child sees.
                if (adminRows && child) {
                    SettingsRow(
                        title = stringResource(R.string.parental_controls_title),
                        description = stringResource(R.string.profile_parental_controls_desc),
                        onClick = {
                            apply()
                            onParentalControls()
                        },
                    )
                }
                if (canAddProfile) {
                    SettingsRow(
                        title = stringResource(R.string.settings_profiles_add),
                        description = null,
                        onClick = { adding = true },
                        focusRequester = addFocus,
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
                CinemaButtonArea(modifier = Modifier.align(Alignment.End)) {
                    // Switching to the profile already in use would do nothing.
                    if (!profile.isActive) {
                        CinemaSecondaryButton(
                            onClick = {
                                apply()
                                onSwitch()
                            },
                            text = stringResource(R.string.profile_switch_to_button),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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

        if (choosingAdminPin) {
            everyone.firstOrNull { it.isActive }?.let { admin ->
                val warning = if (viewModel.isLastExpert(admin.id)) stringResource(R.string.pin_last_expert_warning) else null
                TvPinDialog(
                    state =
                        rememberPinPromptState(viewModel.pins, PinRequest.Choose(admin.id, warning)) {
                            choosingAdminPin = false
                            // The row is gone: focus goes back to the Child switch.
                            refocus = childFocus
                        },
                    onCancel = {
                        choosingAdminPin = false
                        refocus = setPinFocus
                    },
                )
            }
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
                title = { CinemaText(stringResource(R.string.profile_pin_title), role = TextRole.Title) },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CinemaButton(onClick = pinFlow::change, kind = ButtonKind.Main) {
                            CinemaText(stringResource(R.string.profile_pin_change), tone = TextTone.Inherit)
                        }
                        CinemaButton(
                            onClick = pinFlow::remove,
                            kind = ButtonKind.Danger,
                        ) { CinemaText(stringResource(R.string.profile_pin_remove), tone = TextTone.Inherit) }
                    }
                },
                dismissButton = {
                    CinemaButton(onClick = closeOptions, kind = ButtonKind.Other) {
                        CinemaText(stringResource(R.string.common_cancel), tone = TextTone.Inherit)
                    }
                },
            )
        }
        if (pinFlow.step == ProfilePinFlow.Step.CONFIRM_REMOVE) {
            // The last Expert PIN with a child profile: the child lock stops too, on every linked device.
            val lastAdminPin = remember(profile.id, everyone) { viewModel.isLastAdminPin(profile.id) }
            ConfirmActionDialog(
                title = stringResource(R.string.profile_pin_remove_confirm_title),
                text =
                    listOfNotNull(
                        stringResource(R.string.profile_pin_remove_confirm_text),
                        if (SwitchLock.ON && lastAdminPin) stringResource(R.string.profile_pin_remove_last_expert) else null,
                    ).joinToString(" "),
                confirmText = stringResource(R.string.profile_pin_remove),
                onConfirm = {
                    pinFlow.confirmRemove()
                    refocus = pinFocus
                },
                onDismiss = {
                    pinFlow.cancel()
                    refocus = pinFocus
                },
            )
        }
        if (adding) {
            ProfileAddDialog(
                initialColorIndex = remember { viewModel.nextFreeColorIndex() },
                onSave = { newName, color, newChild ->
                    viewModel.addProfile(newName, color, newChild)
                    adding = false
                    refocus = addFocus
                },
                onDismiss = {
                    adding = false
                    refocus = addFocus
                },
                scale = scale,
                noAdminPin = viewModel.adminPinHolders().isEmpty(),
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
                    description = if (source.filterable) null else stringResource(R.string.profile_source_no_filters),
                )
            }
        }
    }
}

/**
 * New profile: a name, a colour and whether it is a child's. Its own settings start off; it's
 * edited on its page afterwards.
 */
@Composable
internal fun ProfileAddDialog(
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int, child: Boolean) -> Unit,
    onDismiss: () -> Unit,
    scale: Float,
    /** No Expert has a PIN: a Child switch turned on warns that the child lock does nothing yet. */
    noAdminPin: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var child by remember { mutableStateOf(false) }
    var showNameError by remember { mutableStateOf(false) }
    // Without an initial focus the dialog opens with focus nowhere and D-pad presses go nowhere.
    val nameFocusRequester = remember { FocusRequester() }
    // Down from the last row goes to Save, not to Cancel below it on the left.
    val saveFocusRequester = remember { FocusRequester() }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.profile_dialog_add_title), role = TextRole.Title) },
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
                TvSwitchRow(
                    checked = child,
                    onCheckedChange = { child = it },
                    label = stringResource(R.string.profile_child_title),
                    description =
                        listOfNotNull(
                            stringResource(R.string.profile_child_desc),
                            if (SwitchLock.ON && child && noAdminPin) stringResource(R.string.profile_child_no_pin_warning) else null,
                        ).joinToString("\n"),
                    modifier = Modifier.focusProperties { down = saveFocusRequester },
                )
            }
        },
        confirmButton = {
            CinemaButton(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSave(name, colorIndex, child)
                    }
                },
                kind = ButtonKind.Main,
                modifier = Modifier.focusRequester(saveFocusRequester),
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
