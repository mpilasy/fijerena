package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileSettings
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings → Profiles: the profile rows, "Add profile", and the edit / delete dialogs. See
 * `docs/plans/archive/20260929_live-sync-plan.md` → User profiles.
 */
@Composable
fun ProfilesSettingsRows(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int) -> Unit,
    onUpdate: (id: String, name: String, colorIndex: Int, settings: ProfileSettings) -> Unit,
    settingsOf: (id: String) -> ProfileSettings,
    onDelete: (id: String) -> Unit,
    onSwitchTo: (id: String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProfileUi?>(null) }
    var deleting by remember { mutableStateOf<ProfileUi?>(null) }

    profiles.forEach { profile ->
        SettingsListRow(
            title = profile.name,
            summary = if (profile.isActive) stringResource(R.string.settings_profiles_active_marker) else null,
            leading = {
                ProfileAvatar(
                    name = profile.name,
                    colorIndex = profile.colorIndex,
                    size = MobileDimensions.iconLarge,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                )
            },
            onClick = {
                onDismissMessage()
                editing = profile
            },
        )
    }
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaError,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
    }
    SettingsListRow(
        title = stringResource(R.string.settings_profiles_add),
        leading = { Icon(CinemaIcons.Add, contentDescription = null, tint = CinemaTextSecondary) },
        trailing = {},
        onClick = {
            onDismissMessage()
            adding = true
        },
    )

    if (adding) {
        ProfileEditDialog(
            title = stringResource(R.string.profile_dialog_add_title),
            initialName = "",
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color, _ ->
                onAdd(name, color)
                adding = false
            },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }

    editing?.let { profile ->
        ProfileEditDialog(
            title = stringResource(R.string.profile_dialog_edit_title),
            initialName = profile.name,
            initialColorIndex = profile.colorIndex,
            initialSettings = remember(profile.id) { settingsOf(profile.id) },
            onSave = { name, color, settings ->
                onUpdate(profile.id, name, color, settings ?: settingsOf(profile.id))
                editing = null
            },
            // The active profile and the last one left can't be deleted (ProfileRepository refuses
            // both); not offering the button beats offering it and then explaining why not.
            onDelete =
                if (!profile.isActive && profiles.size > 1) {
                    {
                        editing = null
                        deleting = profile
                    }
                } else {
                    null
                },
            // Switching to the profile already in use would do nothing.
            onSwitch =
                if (!profile.isActive) {
                    {
                        editing = null
                        onSwitchTo(profile.id)
                    }
                } else {
                    null
                },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { profile ->
        CinemaAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profile_delete_confirm_title, profile.name)) },
            text = { Text(stringResource(R.string.profile_delete_confirm_text)) },
            confirmButton = {
                CinemaDialogTextButton(onClick = {
                    onDelete(profile.id)
                    deleting = null
                }) { Text(stringResource(R.string.profile_delete_button), color = CinemaError) }
            },
            dismissButton = {
                CinemaDialogTextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
internal fun ProfileEditDialog(
    title: String,
    initialName: String,
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int, settings: ProfileSettings?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSwitch: (() -> Unit)? = null,
    /** The profile's own settings, shown as switches; null (a new profile) shows none. */
    initialSettings: ProfileSettings? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var settings by remember { mutableStateOf(initialSettings) }
    var showNameError by remember { mutableStateOf(false) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    isError = showNameError,
                    supportingText =
                        if (showNameError) {
                            { Text(stringResource(R.string.profile_error_name_required)) }
                        } else {
                            null
                        },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.profile_color_label), color = CinemaTextSecondary)
                // Two rows: the whole palette on one line doesn't fit a phone-width dialog.
                CinemaProfileColors.palette.indices.chunked(SWATCHES_PER_ROW).forEach { rowIndices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                        rowIndices.forEach { index ->
                            ColorSwatch(index = index, selected = index == colorIndex, onClick = { colorIndex = index })
                        }
                    }
                }
                settings?.let { current ->
                    ProfileSwitchRow(
                        title = stringResource(R.string.settings_developer_mode_title),
                        checked = current.devMode,
                        onCheckedChange = { settings = current.copy(devMode = it) },
                    )
                    ProfileSwitchRow(
                        title = stringResource(R.string.settings_autoplay_next_episode_title),
                        checked = current.autoplayNextEpisode,
                        onCheckedChange = { settings = current.copy(autoplayNextEpisode = it) },
                    )
                }
                if (onSwitch != null) {
                    OutlinedButton(onClick = onSwitch, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_switch_to_button))
                    }
                }
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_delete_button), color = CinemaError)
                    }
                }
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = {
                if (name.isBlank()) {
                    showNameError = true
                } else {
                    onSave(name, colorIndex, settings)
                }
            }) { Text(stringResource(R.string.profile_save_button)) }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** A profile setting in the dialog: the title, and the switch on the right; the whole row toggles it. */
@Composable
private fun ProfileSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
                .clip(CircleShape)
                .then(if (selected) Modifier.border(MobileDimensions.strokeWidth, CinemaTextPrimary, CircleShape) else Modifier)
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
            fontSize = MaterialTheme.typography.titleSmall.fontSize,
        )
    }
}

private const val SWATCHES_PER_ROW = 4
