package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import org.njarasoa.fijerena.core.ui.components.CinemaDialogActionButton
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
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
        title = { Text(stringResource(R.string.provider_duplicate_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.provider_duplicate_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = { onConfirm(name.trim().ifBlank { defaultName }) }) {
                Text(stringResource(R.string.provider_duplicate_button))
            }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
        title = { Text(stringResource(R.string.provider_copy_to_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.provider_copy_to_target_label), style = MaterialTheme.typography.titleSmall)
                targets.forEach { candidate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = targetId == candidate.id, onClick = { targetId = candidate.id })
                        Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                        Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                Text(stringResource(R.string.provider_copy_to_options_label), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copySettings, onCheckedChange = { copySettings = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    Text(stringResource(R.string.provider_copy_to_settings_label), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyFavorites, onCheckedChange = { copyFavorites = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    Text(stringResource(R.string.provider_copy_to_favorites_label), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyWatchHistory, onCheckedChange = { copyWatchHistory = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    Text(stringResource(R.string.provider_copy_to_watch_history_label), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyConnection, onCheckedChange = { copyConnection = it })
                    Spacer(modifier = Modifier.width(CinemaSpacing.xs))
                    Text(stringResource(R.string.provider_copy_to_connection_label), style = MaterialTheme.typography.bodyMedium)
                }
                if (copyConnection) {
                    val targetName = targets.firstOrNull { it.id == targetId }?.name.orEmpty()
                    Text(
                        stringResource(R.string.provider_copy_to_connection_warning_format, targetName),
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaError,
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
            ) { Text(stringResource(R.string.provider_copy_to_button)) }
        },
        dismissButton = {
            CinemaOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
