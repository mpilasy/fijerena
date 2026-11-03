package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.njarasoa.fijerena.core.network.profile.SwitchLock
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelOutline
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.panelOutline
import org.njarasoa.fijerena.core.ui.components.profileColourName
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.components.scrollEdgeFade
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaSwitchRow
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings → Profiles: the profile rows (a tap opens the profile's page, [onEdit]) and "Add
 * profile". Without Admin ([adminMode] false) no profile row shows (Settings shows the
 * personal rows of the profile in use in place); Add profile shows for a grown-up ([canAddProfile]),
 * not for a kid. See the live sync plan → User profiles.
 */
@Composable
fun ProfilesSettingsRows(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int, child: Boolean, nerd: Boolean) -> Unit,
    onEdit: (id: String) -> Unit,
    onDismissMessage: () -> Unit,
    adminMode: Boolean = true,
    /** The profile in use may add a profile: any grown-up (the profile rules plan, PR-5). */
    canAddProfile: Boolean = adminMode,
    /**
     * Set when a child profile exists and no Admin has a PIN ([ProfilesViewModel.needsAdminPin]):
     * a first row offers the profile in use a PIN, chosen here with these [pins].
     */
    pinNudge: ProfilePins? = null,
    /** The profile in use is the only Admin: choosing its PIN warns how hard a forgotten one is. */
    pinNudgeLastAdmin: Boolean = false,
    /** No Admin has a PIN: see [ProfileAddDialog]. */
    noAdminPin: Boolean = false,
) {
    var adding by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    var pinChosen by remember { mutableStateOf(false) }
    val nudgeFor = profiles.firstOrNull { it.isActive }?.takeIf { adminMode && pinNudge != null && !pinChosen }

    if (nudgeFor != null) {
        SettingsListRow(
            title = stringResource(R.string.settings_pin_nudge_title),
            summary = stringResource(R.string.settings_pin_nudge_desc),
            leading = { CinemaIcon(CinemaIcons.Lock, contentDescription = null, tone = TextTone.Secondary) },
            onClick = { choosingPin = true },
        )
    }
    if (choosingPin && nudgeFor != null && pinNudge != null) {
        val prompt =
            rememberPinPromptState(
                pinNudge,
                PinRequest.Choose(nudgeFor.id, if (pinNudgeLastAdmin) stringResource(R.string.pin_last_admin_warning) else null),
            ) {
                choosingPin = false
                pinChosen = true
            }
        PinDialog(state = prompt, onCancel = { choosingPin = false })
    }

    if (adminMode) {
        profiles.forEach { profile ->
            SettingsListRow(
                title = profile.name,
                titleRole = TextRole.Heading,
                summary = if (profile.isActive) stringResource(R.string.settings_profiles_active_marker) else null,
                leading = {
                    ProfileAvatar(
                        name = profile.name,
                        colorIndex = profile.colorIndex,
                        size = MobileDimensions.iconLarge,
                        initialRole = TextRole.Label,
                    )
                },
                onClick = {
                    onDismissMessage()
                    onEdit(profile.id)
                },
            )
        }
    }
    if (message != null) {
        CinemaText(message, tone = TextTone.Danger, modifier = Modifier.padding(horizontal = Spacing.md))
    }
    if (canAddProfile) {
        // Add is an icon action at the end of the list it adds to, as on every Settings page.
        IconAction(
            onClick = {
                onDismissMessage()
                adding = true
            },
            icon = CinemaIcons.Add,
            label = stringResource(R.string.settings_profiles_add),
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }

    if (adding) {
        ProfileAddDialog(
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color, child, nerd ->
                onAdd(name, color, child, nerd)
                adding = false
            },
            onDismiss = { adding = false },
            noAdminPin = noAdminPin,
            // Only an Admin adds a Nerd (ProfileRules).
            nerdSwitch = adminMode,
        )
    }
}

/**
 * New profile: a name, a colour, whether it is a child's and, for an Admin ([nerdSwitch]), whether
 * it is a Nerd (never a child's). Its other settings start off; it's edited on its page afterwards.
 */
@Composable
internal fun ProfileAddDialog(
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int, child: Boolean, nerd: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** No Admin has a PIN: a Child switch turned on warns that the child lock does nothing yet. */
    noAdminPin: Boolean = false,
    /** The profile in use is an Admin: the Nerd switch shows (only an Admin adds a Nerd). */
    nerdSwitch: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var child by remember { mutableStateOf(false) }
    var nerd by remember { mutableStateOf(false) }
    val showsNerd = nerdSwitch && !child
    var showNameError by remember { mutableStateOf(false) }
    val bodyScroll = rememberScrollState()

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.profile_dialog_add_title),
        text = {
            Column(
                // The body scrolls, its edge faded while more is below, so the last switch keeps
                // its height above the keyboard.
                modifier = Modifier.scrollEdgeFade(bodyScroll).verticalScroll(bodyScroll),
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
                CinemaSwitchRow(
                    label = stringResource(R.string.profile_child_title),
                    checked = child,
                    onCheckedChange = { child = it },
                    supportingText = stringResource(R.string.profile_child_desc_phone),
                )
                if (SwitchLock.ON && child && noAdminPin) {
                    CinemaText(stringResource(R.string.profile_child_no_pin_warning), tone = TextTone.Danger)
                }
                if (showsNerd) {
                    CinemaSwitchRow(
                        label = stringResource(R.string.profile_nerd_title),
                        checked = nerd,
                        onCheckedChange = { nerd = it },
                        supportingText = stringResource(R.string.profile_nerd_desc),
                    )
                }
            }
        },
        main =
            DialogAction(stringResource(R.string.profile_save_button)) {
                if (name.isBlank()) {
                    showNameError = true
                } else {
                    onSave(name, colorIndex, child, showsNerd && nerd)
                }
            },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
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

@Composable
private fun ColorSwatch(
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = profileColourName(index)
    Row(
        modifier =
            Modifier
                .size(MobileDimensions.iconLarge)
                .clip(CinemaTheme.shapes.identity)
                .panelOutline(if (selected) PanelOutline.Marked else PanelOutline.None, PanelCorner.Identity)
                // look-ok: a colour swatch of the profile colour picker, not a list row
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
    ) {
        ProfileAvatar(
            name = "",
            colorIndex = index,
            size = MobileDimensions.iconLarge,
            initialRole = TextRole.Label,
        )
    }
}

private const val SWATCHES_PER_ROW = 4
