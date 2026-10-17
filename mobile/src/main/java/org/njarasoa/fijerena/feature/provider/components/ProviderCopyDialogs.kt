package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.provider.ProviderCopyManager
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/** Name-edit dialog for [ProviderCopyManager.duplicateProvider]. */
@Composable
fun DuplicateProviderDialog(
    provider: ProviderEntity,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit,
) {
    val defaultName = stringResource(R.string.provider_duplicate_name_default_format, provider.name)
    var name by remember(provider.id) { mutableStateOf(defaultName) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.provider_duplicate_title), role = TextRole.Title) },
        text = {
            CinemaTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.provider_duplicate_name_label),
                keyboardOptions = FieldKeyboards.Name,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = { onConfirm(name.trim().ifBlank { defaultName }) }, kind = ButtonKind.Main) {
                CinemaText(stringResource(R.string.provider_duplicate_button), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}

/** Target picker + option checkboxes for [ProviderCopyManager.copyProviderData]. */
@Composable
fun CopyProviderDialog(
    source: ProviderEntity,
    targets: List<ProviderEntity>,
    onDismiss: () -> Unit,
    onConfirm: (targetId: Long, options: ProviderCopyManager.CopyOptions) -> Unit,
) {
    var targetId by remember(source.id) { mutableStateOf(targets.firstOrNull()?.id) }
    var copyConnection by remember { mutableStateOf(false) }
    var copySettings by remember { mutableStateOf(true) }
    var copyFavorites by remember { mutableStateOf(true) }
    var copyWatchHistory by remember { mutableStateOf(true) }

    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.provider_copy_to_title), role = TextRole.Title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CinemaText(stringResource(R.string.provider_copy_to_target_label), role = TextRole.Heading)
                targets.forEach { candidate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CinemaRadioButton(selected = targetId == candidate.id, onClick = { targetId = candidate.id })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        CinemaText(candidate.name)
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                CinemaText(stringResource(R.string.provider_copy_to_options_label), role = TextRole.Heading)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinemaCheckbox(checked = copySettings, onCheckedChange = { copySettings = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    CinemaText(stringResource(R.string.provider_copy_to_settings_label))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinemaCheckbox(checked = copyFavorites, onCheckedChange = { copyFavorites = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    CinemaText(stringResource(R.string.provider_copy_to_favorites_label))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinemaCheckbox(checked = copyWatchHistory, onCheckedChange = { copyWatchHistory = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    CinemaText(stringResource(R.string.provider_copy_to_watch_history_label))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinemaCheckbox(checked = copyConnection, onCheckedChange = { copyConnection = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    CinemaText(stringResource(R.string.provider_copy_to_connection_label))
                }
                if (copyConnection) {
                    val targetName = targets.firstOrNull { it.id == targetId }?.name.orEmpty()
                    CinemaText(
                        stringResource(R.string.provider_copy_to_connection_warning_format, targetName),
                        role = TextRole.BodySmall,
                        tone = TextTone.Danger,
                    )
                }
            }
        },
        confirmButton = {
            CinemaDialogActionButton(
                onClick = {
                    val resolvedTargetId = targetId ?: return@CinemaDialogActionButton
                    onConfirm(
                        resolvedTargetId,
                        ProviderCopyManager.CopyOptions(
                            copyConnection = copyConnection,
                            copyProviderSettings = copySettings,
                            copyFavorites = copyFavorites,
                            copyWatchHistory = copyWatchHistory,
                        ),
                    )
                },
                kind = ButtonKind.Main,
            ) { CinemaText(stringResource(R.string.provider_copy_to_button), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}
