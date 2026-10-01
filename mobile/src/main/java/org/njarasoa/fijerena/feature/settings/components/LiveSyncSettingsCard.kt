package org.njarasoa.fijerena.feature.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.SyncManager
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton

/** Live sync's line in Settings: whether it's on, and the way into its own screen. */
@Composable
fun LiveSyncSettingsCard(onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val status by SyncManager.getInstance(app).status.collectAsStateWithLifecycle()
    SettingsSection(title = stringResource(R.string.live_sync_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.serverUrl ?: stringResource(R.string.live_sync_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                )
            }
            Spacer(modifier = Modifier.width(CinemaSpacing.md))
            CinemaButton(onClick = onOpen) {
                Text(stringResource(if (status.linked) R.string.live_sync_manage else R.string.live_sync_open))
            }
        }
    }
}
