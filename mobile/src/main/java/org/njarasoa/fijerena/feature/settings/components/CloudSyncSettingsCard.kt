package org.njarasoa.fijerena.feature.settings.components

import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun CloudSyncSettingsCard(
    signedInEmail: String?,
    syncStatus: DriveSettingsSyncManager.SyncStatus,
    signInError: String?,
    onSignInErrorChange: (String?) -> Unit,
    syncManager: DriveSettingsSyncManager,
    coroutineScope: CoroutineScope,
    signInLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    SettingsSection(title = stringResource(R.string.settings_cloud_sync_section_title)) {
        Text(
            text = stringResource(R.string.settings_cloud_sync_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))

        if (signedInEmail != null) {
            // Signed in: show account + sync controls
            Text(
                text = signedInEmail,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            val syncingText = stringResource(R.string.provider_syncing)
            val syncedText = stringResource(R.string.sync_status_synced)
            val errorFormat = stringResource(R.string.epg_database_error)
            val readyText = stringResource(R.string.sync_status_ready)
            val statusText =
                when (syncStatus) {
                    is DriveSettingsSyncManager.SyncStatus.Syncing -> syncingText
                    is DriveSettingsSyncManager.SyncStatus.Synced -> syncedText
                    is DriveSettingsSyncManager.SyncStatus.Error ->
                        String.format(errorFormat, syncStatus.message)
                    else -> readyText
                }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color =
                    when (syncStatus) {
                        is DriveSettingsSyncManager.SyncStatus.Synced -> MaterialTheme.colorScheme.primary
                        is DriveSettingsSyncManager.SyncStatus.Error -> CinemaError
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                    },
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CinemaButton(
                    onClick = { coroutineScope.launch { syncManager.syncNow() } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_cloud_sync_now_button))
                }
                CinemaOutlinedButton(
                    onClick = { coroutineScope.launch { syncManager.signOut() } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_sign_out))
                }
            }
        } else {
            // Not signed in: show sign-in button
            CinemaButton(
                onClick = {
                    onSignInErrorChange(null)
                    signInLauncher.launch(syncManager.getSignInIntent())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_sign_in_google_button))
            }
            if (signInError != null) {
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = signInError,
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaError,
                )
            }
        }
    }
}
