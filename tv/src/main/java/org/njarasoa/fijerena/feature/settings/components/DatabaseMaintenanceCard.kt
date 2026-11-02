package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.SettingsRowKind
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog

/**
 * "Shrink Database" — manually sweeps `xtream_v2.db` for rows left behind by a deleted provider
 * and reclaims the freed disk space. See [org.njarasoa.fijerena.core.network.provider.ProviderRepository.pruneOrphanedCatalogData].
 * It asks first (a confirm dialog). [moreRows] follow in the same section (the guide's maintenance row).
 */
@Composable
fun DatabaseMaintenanceCard(
    isPruning: Boolean,
    resultMessage: String?,
    onShrinkClick: () -> Unit,
    isDevMode: Boolean = false,
    lastShrinkAtMs: Long = 0L,
    lastShrinkDurationMs: Long = 0L,
    lastShrinkRowsRemoved: Long = 0L,
    lastShrinkBytesReclaimed: Long = 0L,
    moreRows: @Composable ColumnScope.() -> Unit = {},
) {
    // Heavy maintenance asks first (rule 7), as on the phone.
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        CinemaAlertDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(R.string.settings_shrink_database_confirm_title),
            text = { CinemaText(stringResource(R.string.settings_shrink_database_confirm_message), tone = TextTone.Secondary) },
            main =
                DialogAction(stringResource(R.string.settings_shrink_database_confirm_action)) {
                    confirming = false
                    onShrinkClick()
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { confirming = false },
        )
    }
    // A row at the root of Admin's page, with no title of its own above it (as on the phone).
    SettingsSection {
        SettingsRow(
            title =
                stringResource(
                    if (isPruning) R.string.settings_shrink_database_button_running else R.string.settings_shrink_database_button,
                ),
            description =
                stringResource(R.string.settings_shrink_database_desc) + "\n" +
                    stringResource(R.string.settings_scope_device_section),
            onClick = { confirming = true },
            enabled = !isPruning,
            kind = SettingsRowKind.Action,
        )
        if (resultMessage != null) SettingsNote(resultMessage)
        // Only when the last run found something: "0 records deleted" says nothing.
        if (isDevMode && lastShrinkAtMs > 0L && (lastShrinkRowsRemoved > 0 || lastShrinkBytesReclaimed > 0)) {
            val context = LocalContext.current
            val time = NumberUtils.formatTimestamp(context, lastShrinkAtMs)
            val duration = NumberUtils.formatDuration(LocalResources.current, lastShrinkDurationMs)
            SettingsNote(
                stringResource(R.string.settings_shrink_database_dev_stats_time, time, duration),
                alpha = CinemaAlpha.textMedium,
            )
            val bytesStr = NumberUtils.formatBytes(LocalResources.current, lastShrinkBytesReclaimed)
            SettingsNote(
                pluralStringResource(
                    R.plurals.settings_shrink_database_dev_stats_delta,
                    lastShrinkRowsRemoved.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    lastShrinkRowsRemoved,
                    bytesStr,
                ),
                alpha = CinemaAlpha.textLow,
            )
        }
        moreRows()
    }
}
