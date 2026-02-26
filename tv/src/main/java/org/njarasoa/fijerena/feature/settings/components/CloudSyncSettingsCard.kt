package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun CloudSyncSettingsCard(
    syncStatus: DriveSettingsSyncManager.SyncStatus,
    signedInEmail: String?,
    signInError: String?,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    scale: Float
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = "Cloud Sync",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = "Sync provider settings across devices using your Google account",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

            if (signedInEmail != null) {
                // Signed in: show account info + sync controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = signedInEmail,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                            ),
                            color = CinemaTextPrimary
                        )
                        val statusText = when (syncStatus) {
                            is DriveSettingsSyncManager.SyncStatus.Syncing -> "Syncing..."
                            is DriveSettingsSyncManager.SyncStatus.Synced -> "Synced"
                            is DriveSettingsSyncManager.SyncStatus.Error ->
                                "Error: ${(syncStatus as DriveSettingsSyncManager.SyncStatus.Error).message}"
                            else -> "Ready"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = when (syncStatus) {
                                is DriveSettingsSyncManager.SyncStatus.Synced -> CinemaAccent
                                is DriveSettingsSyncManager.SyncStatus.Error -> CinemaError
                                else -> CinemaTextSecondary
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    CinemaIconButton(
                        onClick = onSyncNow,
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Sync Now") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                    CinemaDangerButton(
                        onClick = onSignOut,
                        text = "Sign Out"
                    )
                }
            } else {
                // Not signed in: show sign-in button
                CinemaPrimaryButton(
                    onClick = {
                        onSignIn()
                    },
                    text = "Sign in with Google"
                )
                if (signInError != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = signInError,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaError
                    )
                }
            }
        }
    }
}
