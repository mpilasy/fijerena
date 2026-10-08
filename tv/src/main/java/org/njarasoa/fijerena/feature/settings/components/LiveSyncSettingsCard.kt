package org.njarasoa.fijerena.feature.settings.components

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sync.SyncManager

/** Live sync's line in Settings: one row — its server (or Off) — whose OK opens its own screen. */
@Composable
fun LiveSyncSettingsCard(
    onOpen: () -> Unit,
    openRowFocusRequester: FocusRequester? = null,
    /** Goes on the row, the group's only focusable — the pane's entry row. */
    openRowModifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as Application
    val status by SyncManager.getInstance(app).status.collectAsStateWithLifecycle()
    SettingsSection {
        SettingsRow(
            title = stringResource(R.string.live_sync_title),
            description = status.serverUrl ?: stringResource(R.string.live_sync_off),
            value = stringResource(if (status.linked) R.string.live_sync_manage else R.string.live_sync_open),
            onClick = onOpen,
            modifier = openRowModifier,
            focusRequester = openRowFocusRequester,
        )
    }
}
