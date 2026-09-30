package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * Settings → Profiles: list, add, edit, delete. See `docs/plans/20260929_live-sync-plan.md` →
 * User profiles.
 */
@Composable
fun ProfilesSettingsCard(
    profiles: List<ProfileUi>,
    message: String?,
    newProfileColorIndex: () -> Int,
    onAdd: (name: String, colorIndex: Int) -> Unit,
    onUpdate: (id: String, name: String, colorIndex: Int) -> Unit,
    onDelete: (id: String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProfileUi?>(null) }
    var deleting by remember { mutableStateOf<ProfileUi?>(null) }

    SettingsSection(title = stringResource(R.string.settings_profiles_title)) {
        Text(
            text = stringResource(R.string.settings_profiles_description),
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
        profiles.forEach { profile ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismissMessage()
                            editing = profile
                        }.padding(vertical = CinemaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    name = profile.name,
                    colorIndex = profile.colorIndex,
                    size = MobileDimensions.iconLarge,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                )
                Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                Column {
                    Text(text = profile.name, style = MaterialTheme.typography.bodyLarge)
                    if (profile.isActive) {
                        Text(
                            text = stringResource(R.string.settings_profiles_active_marker),
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary,
                        )
                    }
                }
            }
        }
        if (message != null) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = CinemaError)
        }
        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
        OutlinedButton(
            onClick = {
                onDismissMessage()
                adding = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_profiles_add)) }
    }

    if (adding) {
        ProfileEditDialog(
            title = stringResource(R.string.profile_dialog_add_title),
            initialName = "",
            initialColorIndex = remember { newProfileColorIndex() },
            onSave = { name, color ->
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
            onSave = { name, color ->
                onUpdate(profile.id, name, color)
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
private fun ProfileEditDialog(
    title: String,
    initialName: String,
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    CinemaProfileColors.palette.indices.forEach { index ->
                        ColorSwatch(index = index, selected = index == colorIndex, onClick = { colorIndex = index })
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
                    onSave(name, colorIndex)
                }
            }) { Text(stringResource(R.string.profile_save_button)) }
        },
        dismissButton = {
            CinemaDialogTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
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
        ProfileAvatar(name = "", colorIndex = index, size = MobileDimensions.iconLarge, fontSize = MaterialTheme.typography.titleSmall.fontSize)
    }
}
