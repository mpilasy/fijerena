@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaProfileColors
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

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
    scale: Float,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProfileUi?>(null) }
    var deleting by remember { mutableStateOf<ProfileUi?>(null) }

    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_profiles_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)),
                color = CinemaAccent,
            )
            Text(
                text = stringResource(R.string.settings_profiles_description),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                profiles.forEach { profile ->
                    TvInputListItem(
                        selected = false,
                        onClick = {
                            onDismissMessage()
                            editing = profile
                        },
                        leadingContent = {
                            ProfileAvatar(
                                name = profile.name,
                                colorIndex = profile.colorIndex,
                                size = TvDimensions.iconMedium.scaled(scale),
                                fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale),
                            )
                        },
                        supportingContent =
                            if (profile.isActive) {
                                { Text(stringResource(R.string.settings_profiles_active_marker)) }
                            } else {
                                null
                            },
                        headlineContent = { Text(profile.name) },
                    )
                }
            }
            if (message != null) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)),
                    color = CinemaError,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            CinemaSecondaryButton(
                onClick = {
                    onDismissMessage()
                    adding = true
                },
                text = stringResource(R.string.settings_profiles_add),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            scale = scale,
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
            scale = scale,
        )
    }

    deleting?.let { profile ->
        ConfirmActionDialog(
            title = stringResource(R.string.profile_delete_confirm_title, profile.name),
            text = stringResource(R.string.profile_delete_confirm_text),
            confirmText = stringResource(R.string.profile_delete_button),
            onConfirm = {
                onDelete(profile.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
internal fun ProfileEditDialog(
    title: String,
    initialName: String,
    initialColorIndex: Int,
    onSave: (name: String, colorIndex: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    scale: Float,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorIndex by remember { mutableIntStateOf(initialColorIndex) }
    var showNameError by remember { mutableStateOf(false) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                ReadOnlyFieldWithEdit(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = stringResource(R.string.profile_name_label),
                )
                if (showNameError) {
                    Text(stringResource(R.string.profile_error_name_required), color = CinemaError)
                }
                Text(stringResource(R.string.profile_color_label), color = CinemaTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                    CinemaProfileColors.palette.indices.forEach { index ->
                        ColorSwatch(
                            index = index,
                            selected = index == colorIndex,
                            onClick = { colorIndex = index },
                            scale = scale,
                        )
                    }
                }
                if (onDelete != null) {
                    CinemaSecondaryButton(
                        onClick = onDelete,
                        text = stringResource(R.string.profile_delete_button),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
            CinemaDialogActionButton(onClick = onDismiss) {
                androidx.compose.material3.Text(stringResource(R.string.common_cancel))
            }
        },
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
