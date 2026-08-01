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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.CinemaError

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
    SettingsSection(title = "Cloud Sync") {
        Text(
            text = "Sync provider settings across devices using your Google account",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (signedInEmail != null) {
            // Signed in: show account + sync controls
            Text(
                text = signedInEmail ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val statusText =
                when (syncStatus) {
                    is DriveSettingsSyncManager.SyncStatus.Syncing -> "Syncing..."
                    is DriveSettingsSyncManager.SyncStatus.Synced -> "Synced"
                    is DriveSettingsSyncManager.SyncStatus.Error ->
                        "Error: ${(syncStatus as DriveSettingsSyncManager.SyncStatus.Error).message}"
                    else -> "Ready"
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CinemaButton(
                    onClick = { coroutineScope.launch { syncManager.syncNow() } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Sync Now")
                }
                CinemaOutlinedButton(
                    onClick = { coroutineScope.launch { syncManager.signOut() } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Sign Out")
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
                Text("Sign in with Google")
            }
            if (signInError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = signInError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaError,
                )
            }
        }
    }
}
