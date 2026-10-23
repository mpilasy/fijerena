package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings → Profiles: the profile rows (a tap opens the profile's page, [onEdit]) and "Add
 * profile". Without Expert mode ([expertMode] false) a profile can only edit itself: one row, the
 * profile in use, opens its own page, and there is no Add profile. See
 * the live sync plan → User profiles.
 */
@Composable
fun ProfilesSettingsRows(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int, child: Boolean) -> Unit,
    onEdit: (id: String) -> Unit,
    onDismissMessage: () -> Unit,
    expertMode: Boolean = true,
    /**
     * Set when a child profile exists and no Expert has a PIN ([ProfilesViewModel.needsAdminPin]):
     * a first row offers the profile in use a PIN, chosen here with these [pins].
     */
    pinNudge: ProfilePins? = null,
    /** The profile in use is the only Expert: choosing its PIN warns how hard a forgotten one is. */
    pinNudgeLastExpert: Boolean = false,
) {
    var adding by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    var pinChosen by remember { mutableStateOf(false) }
    val nudgeFor = profiles.firstOrNull { it.isActive }?.takeIf { expertMode && pinNudge != null && !pinChosen }

    if (nudgeFor != null) {
        SettingsListRow(
            title = stringResource(R.string.settings_pin_nudge_title),
            summary = stringResource(R.string.settings_pin_nudge_desc),
            leading = { Icon(CinemaIcons.Lock, contentDescription = null, tint = CinemaTheme.colors.textSecondary) },
            onClick = { choosingPin = true },
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
        PinDialog(state = prompt, onCancel = { choosingPin = false })
    }

    profiles.filter { expertMode || it.isActive }.forEach { profile ->
        SettingsListRow(
            title = profile.name,
            summary = if (profile.isActive) stringResource(R.string.settings_profiles_active_marker) else null,
            leading = {
                ProfileAvatar(
                    name = profile.name,
                    colorIndex = profile.colorIndex,
                    size = MobileDimensions.iconLarge,
                    fontSize = textStyleOf(TextRole.Label).fontSize,
                )
            },
            onClick = {
                onDismissMessage()
                onEdit(profile.id)
            },
        )
    }
    if (message != null) {
        CinemaText(message, tone = TextTone.Danger, modifier = Modifier.padding(horizontal = Spacing.md))
    }
    if (expertMode) {
        SettingsListRow(
            title = stringResource(R.string.settings_profiles_add),
            leading = { Icon(CinemaIcons.Add, contentDescription = null, tint = CinemaTheme.colors.textSecondary) },
            trailing = {},
            onClick = {
                onDismissMessage()
                adding = true
            },
        )
    }

    if (adding) {
        ProfileAddDialog(
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color, child ->
                onAdd(name, color, child)
                adding = false
            },
            onDismiss = { adding = false },
        )
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
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var child by remember { mutableStateOf(false) }
    var showNameError by remember { mutableStateOf(false) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.profile_dialog_add_title), role = TextRole.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                ProfileNameField(
                    name = name,
                    onNameChange = {
                        name = it
                        showNameError = false
                    },
                    showError = showNameError,
                )
                ProfileColourPicker(colorIndex = colorIndex, onPick = { colorIndex = it })
                ProfileSwitchRow(
                    title = stringResource(R.string.profile_child_title),
                    checked = child,
                    onCheckedChange = { child = it },
                    summary = stringResource(R.string.profile_child_desc),
                )
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSave(name, colorIndex, child)
                    }
                },
                kind = ButtonKind.Other,
            ) { CinemaText(stringResource(R.string.profile_save_button), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}

/** A profile's name, with the "Enter a name" error under it. */
@Composable
internal fun ProfileNameField(
    name: String,
    onNameChange: (String) -> Unit,
    showError: Boolean,
) {
    CinemaTextField(
        value = name,
        onValueChange = onNameChange,
        label = stringResource(R.string.profile_name_label),
        keyboardOptions = FieldKeyboards.Name,
        isError = showError,
        supportingText = if (showError) stringResource(R.string.profile_error_name_required) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Colour" and the palette as swatches, the chosen one ringed. */
@Composable
internal fun ProfileColourPicker(
    colorIndex: Int,
    onPick: (Int) -> Unit,
) {
    CinemaText(stringResource(R.string.profile_color_label), tone = TextTone.Secondary)
    // Two rows: the whole palette on one line doesn't fit a phone-width dialog.
    CinemaProfileColors.palette.indices.chunked(SWATCHES_PER_ROW).forEach { rowIndices ->
        Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
            rowIndices.forEach { index ->
                ColorSwatch(index = index, selected = index == colorIndex, onClick = { onPick(index) })
            }
        }
    }
}

/**
 * A profile setting on its page: the title (and an optional [summary] under it), and the switch on
 * the right; the whole row toggles it.
 */
@Composable
internal fun ProfileSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CinemaText(title)
            if (summary != null) {
                CinemaText(summary, tone = TextTone.Secondary)
            }
        }
        CinemaSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ColorSwatch(
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.profile_color_option_format, index + 1)
    Row(
        modifier =
            Modifier
                .size(MobileDimensions.iconLarge)
                .clip(CinemaTheme.shapes.identity)
                .then(
                    if (selected) {
                        Modifier.border(
                            MobileDimensions.strokeWidth,
                            CinemaTheme.colors.textPrimary,
                            CinemaTheme.shapes.identity,
                        )
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
    ) {
        ProfileAvatar(
            name = "",
            colorIndex = index,
            size = MobileDimensions.iconLarge,
            fontSize = textStyleOf(TextRole.Label).fontSize,
        )
    }
}

private const val SWATCHES_PER_ROW = 4
