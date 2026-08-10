package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.ui.R
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
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@Composable
fun CloudSyncSettingsCard(
    syncStatus: DriveSettingsSyncManager.SyncStatus,
    signedInEmail: String?,
    signInError: String?,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    scale: Float,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_cloud_sync_section_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = stringResource(R.string.settings_cloud_sync_desc),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize =
                            MaterialTheme.typography.bodySmall.fontSize
                                .scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

            if (signedInEmail != null) {
                // Signed in: show account info + sync controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = signedInEmail,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodyMedium.fontSize
                                            .scaled(scale),
                                ),
                            color = CinemaTextPrimary,
                        )
                        val statusText =
                            when (syncStatus) {
                                is DriveSettingsSyncManager.SyncStatus.Syncing -> stringResource(R.string.provider_syncing)
                                is DriveSettingsSyncManager.SyncStatus.Synced -> stringResource(R.string.sync_status_synced)
                                is DriveSettingsSyncManager.SyncStatus.Error ->
                                    stringResource(R.string.epg_database_error, syncStatus.message)
                                else -> stringResource(R.string.sync_status_ready)
                            }
                        Text(
                            text = statusText,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodySmall.fontSize
                                            .scaled(scale),
                                ),
                            color =
                                when (syncStatus) {
                                    is DriveSettingsSyncManager.SyncStatus.Synced -> CinemaAccent
                                    is DriveSettingsSyncManager.SyncStatus.Error -> CinemaError
                                    else -> CinemaTextSecondary
                                },
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    CinemaIconButton(
                        onClick = onSyncNow,
                        icon = { Icon(CinemaIcons.Sync, contentDescription = stringResource(R.string.settings_cloud_sync_now_button)) },
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                    CinemaDangerButton(
                        onClick = onSignOut,
                        text = stringResource(R.string.common_sign_out),
                    )
                }
            } else {
                // Not signed in: show sign-in button
                CinemaPrimaryButton(
                    onClick = {
                        onSignIn()
                    },
                    text = stringResource(R.string.settings_sign_in_google_button),
                )
                if (signInError != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    Text(
                        text = signInError,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize =
                                    MaterialTheme.typography.bodySmall.fontSize
                                        .scaled(scale),
                            ),
                        color = CinemaError,
                    )
                }
            }
        }
    }
}

