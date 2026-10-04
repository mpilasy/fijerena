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
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Settings → Profiles: the profile rows (a tap opens the profile's page, [onEdit]) and "Add
 * profile". See `docs/plans/archive/20260929_live-sync-plan.md` → User profiles.
 */
@Composable
fun ProfilesSettingsRows(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int) -> Unit,
    onEdit: (id: String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

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
                onEdit(profile.id)
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
        ProfileAddDialog(
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color ->
                onAdd(name, color)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/** New profile: a name and a colour. Its own settings start off; it's edited on its page afterwards. */
@Composable
internal fun ProfileAddDialog(
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var showNameError by remember { mutableStateOf(false) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_dialog_add_title)) },
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
            }
        },
        confirmButton = {
            CinemaDialogTextButton(onClick = {
                if (name.isBlank()) {
                    showNameError = true
                } else {
                    onSave(name, colorIndex)
                }
            }) { Text(stringResource(R.string.profile_save_button)) }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.profile_name_label)) },
        isError = showError,
        supportingText =
            if (showError) {
                { Text(stringResource(R.string.profile_error_name_required)) }
            } else {
                null
            },
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
    Text(stringResource(R.string.profile_color_label), color = CinemaTextSecondary)
    // Two rows: the whole palette on one line doesn't fit a phone-width dialog.
    CinemaProfileColors.palette.indices.chunked(SWATCHES_PER_ROW).forEach { rowIndices ->
        Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
            rowIndices.forEach { index ->
                ColorSwatch(index = index, selected = index == colorIndex, onClick = { onPick(index) })
            }
        }
    }
}

/** A profile setting on its page: the title, and the switch on the right; the whole row toggles it. */
@Composable
internal fun ProfileSwitchRow(
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
