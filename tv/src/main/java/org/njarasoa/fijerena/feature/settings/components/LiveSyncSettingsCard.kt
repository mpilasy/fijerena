package org.njarasoa.fijerena.feature.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/** Live sync's line in Settings: whether it's on, and the way into its own screen. */
@Composable
fun LiveSyncSettingsCard(
    onOpen: () -> Unit,
    scale: Float,
) {
    val app = LocalContext.current.applicationContext as Application
    val status by SyncManager.getInstance(app).status.collectAsStateWithLifecycle()
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Row(modifier = Modifier.padding(Spacing.md.scaled(scale)), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.live_sync_title),
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
                    text = status.serverUrl ?: stringResource(R.string.live_sync_off),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize =
                                MaterialTheme.typography.bodySmall.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
            CinemaPrimaryButton(
                onClick = onOpen,
                text = stringResource(if (status.linked) R.string.live_sync_manage else R.string.live_sync_open),
            )
        }
    }
}
