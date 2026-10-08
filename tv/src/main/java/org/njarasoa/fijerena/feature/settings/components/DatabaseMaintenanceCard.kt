package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.utils.NumberUtils

/**
 * "Shrink Database" — manually sweeps `xtream_v2.db` for rows left behind by a deleted provider
 * and reclaims the freed disk space. See [org.njarasoa.fijerena.core.network.provider.ProviderRepository.pruneOrphanedCatalogData].
 * [moreRows] follow in the same section (the guide's maintenance row).
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
    SettingsSection(
        title = stringResource(R.string.settings_shrink_database_title),
        description =
            stringResource(R.string.settings_shrink_database_desc) + "\n" +
                stringResource(R.string.settings_scope_device_section),
    ) {
        SettingsRow(
            title =
                stringResource(
                    if (isPruning) R.string.settings_shrink_database_button_running else R.string.settings_shrink_database_button,
                ),
            description = null,
            onClick = onShrinkClick,
            enabled = !isPruning,
            chevron = false,
        )
        if (resultMessage != null) SettingsNote(resultMessage)
        if (isDevMode && lastShrinkAtMs > 0L) {
            val context = LocalContext.current
            val time = NumberUtils.formatTimestamp(context, lastShrinkAtMs)
            val duration = NumberUtils.formatDuration(lastShrinkDurationMs)
            SettingsNote(
                stringResource(R.string.settings_shrink_database_dev_stats_time, time, duration),
                alpha = CinemaAlpha.textMedium,
            )
            val bytesStr = NumberUtils.formatBytes(lastShrinkBytesReclaimed)
            SettingsNote(
                stringResource(R.string.settings_shrink_database_dev_stats_delta, lastShrinkRowsRemoved, bytesStr),
                alpha = CinemaAlpha.textLow,
            )
        }
        moreRows()
    }
}
