@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.feature.provider.components.CategoryFilterDialog
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
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
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    )
                },
            )
        }
        if (message != null) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = CinemaError)
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
 * mode and Play next episode automatically, Content filters › (the source in use, for this
 * profile; Xtream only), Switch to this profile, Save / Cancel, and Delete last. Left and Back
 * leave it unsaved, like Cancel. Content filters are saved by their own editor's Save, at once.
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
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var colorIndex by rememberSaveable(profile.id) { mutableIntStateOf(profile.colorIndex) }
    val stored = remember(profile.id) { viewModel.settingsOf(profile.id) }
    var devMode by rememberSaveable(profile.id) { mutableStateOf(stored.devMode) }
    var autoplayNextEpisode by rememberSaveable(profile.id) { mutableStateOf(stored.autoplayNextEpisode) }
    var showNameError by remember { mutableStateOf(false) }
    var choosingColour by rememberSaveable { mutableStateOf(false) }
    var returnToColour by remember { mutableStateOf(false) }
    var editingFilters by remember { mutableStateOf<CategoryFilters?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Set when a dialog opened from the page closes: focus goes back to the row that opened it.
    var refocus by remember { mutableStateOf<FocusRequester?>(null) }
    val nameFocus = remember { FocusRequester() }
    val colourFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.loadSourceInUse() }
    val sourceInUse by viewModel.sourceInUse.collectAsStateWithLifecycle()
    // Content filters act on Xtream catalogues only (as they did in Edit Source).
    val filtersSource = sourceInUse?.takeIf { it.type == "XTREAM" }

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
    } else {
        LaunchedEffect(Unit) {
            (if (returnToColour) colourFocus else nameFocus).requestFocusWithRetry()
            returnToColour = false
        }
        LaunchedEffect(refocus) {
            refocus?.requestFocusWithRetry()
            refocus = null
        }

        // Left and Back leave the page (Left = back one level). Taken in onKeyEvent, after the focused
        // control: the name's text field keeps Left for its cursor and Back to cancel its edit. A Back
        // whose press the field took is swallowed on release too, or it would also close the page.
        var backPressed by remember { mutableStateOf(false) }
        BackHandler(onBack = onBack)
        Column(
            modifier =
                Modifier.fillMaxSize().onKeyEvent { event ->
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
            Text(
                text = "‹ ${stringResource(R.string.profile_dialog_edit_title)}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.xs),
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
                    editButtonFocusRequester = nameFocus,
                )
                if (showNameError) {
                    Text(stringResource(R.string.profile_error_name_required), color = CinemaError)
                }
                SettingsRow(
                    title = stringResource(R.string.profile_color_label),
                    description = null,
                    onClick = { choosingColour = true },
                    focusRequester = colourFocus,
                    leading = { ColourDot(colorIndex, scale) },
                )
                TvSwitchRow(
                    checked = devMode,
                    onCheckedChange = { devMode = it },
                    label = stringResource(R.string.settings_developer_mode_title),
                    description = stringResource(R.string.settings_developer_mode_desc),
                )
                TvSwitchRow(
                    checked = autoplayNextEpisode,
                    onCheckedChange = { autoplayNextEpisode = it },
                    label = stringResource(R.string.settings_autoplay_next_episode_title),
                    description = stringResource(R.string.settings_autoplay_next_episode_desc),
                )
                if (filtersSource != null) {
                    SettingsRow(
                        title = stringResource(R.string.profile_content_filters_title),
                        description = stringResource(R.string.provider_category_filters_desc),
                        value = filtersSource.name,
                        onClick = {
                            viewModel.loadCategoryFilters(filtersSource.id, profile.id) { editingFilters = it }
                        },
                        focusRequester = filtersFocus,
                    )
                }
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
                    CinemaSecondaryButton(
                        onClick = { confirmDelete = true },
                        text = stringResource(R.string.profile_delete_button),
                        modifier = Modifier.fillMaxWidth().focusRequester(deleteFocus),
                    )
                }
            }
        }

        val filters = editingFilters
        if (filters != null && filtersSource != null) {
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
                    editButtonFocusRequester = nameFocusRequester,
                )
                if (showNameError) {
                    Text(stringResource(R.string.profile_error_name_required), color = CinemaError)
                }
                Text(stringResource(R.string.profile_color_label), color = CinemaTextSecondary)
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
            CinemaDialogActionButton(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSave(name, colorIndex)
                    }
                },
            ) { androidx.compose.material3.Text(stringResource(R.string.profile_save_button)) }
        },
        dismissButton = {
            // Cancel is secondary: resting container, accent label. Save keeps the fill.
            CinemaDialogActionButton(
                onClick = onDismiss,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = TvFocusTokens.restingContainer,
                        contentColor = CinemaAccent,
                    ),
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
            MaterialTheme.typography.titleSmall.fontSize
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
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
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
                        Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaTextPrimary))
                    } else {
                        Border.None
                    },
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaAccentLight)),
            ),
    ) {}
}
