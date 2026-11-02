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
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.CinemaCheckRow
import org.njarasoa.fijerena.ui.components.CinemaRadioRow
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
        title = stringResource(R.string.provider_duplicate_title),
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
        main = DialogAction(stringResource(R.string.provider_duplicate_button)) { onConfirm(name.trim().ifBlank { defaultName }) },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
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
        title = stringResource(R.string.provider_copy_to_title),
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CinemaText(stringResource(R.string.provider_copy_to_target_label), role = TextRole.Heading)
                targets.forEach { candidate ->
                    CinemaRadioRow(
                        selected = targetId == candidate.id,
                        onClick = { targetId = candidate.id },
                        label = candidate.name,
                        labelIsName = true,
                    )
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                CinemaText(stringResource(R.string.provider_copy_to_options_label), role = TextRole.Heading)
                CinemaCheckRow(
                    checked = copySettings,
                    onCheckedChange = { copySettings = it },
                    label = stringResource(R.string.provider_copy_to_settings_label),
                )
                CinemaCheckRow(
                    checked = copyFavorites,
                    onCheckedChange = { copyFavorites = it },
                    label = stringResource(R.string.provider_copy_to_favorites_label),
                )
                CinemaCheckRow(
                    checked = copyWatchHistory,
                    onCheckedChange = { copyWatchHistory = it },
                    label = stringResource(R.string.provider_copy_to_watch_history_label),
                )
                CinemaCheckRow(
                    checked = copyConnection,
                    onCheckedChange = { copyConnection = it },
                    label = stringResource(R.string.provider_copy_to_connection_label),
                )
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
        main =
            DialogAction(stringResource(R.string.provider_copy_to_button)) {
                val resolvedTargetId = targetId ?: return@DialogAction
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
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
