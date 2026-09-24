package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * "Shrink Database" — manually sweeps `xtream_v2.db` for rows left behind by a deleted provider
 * and reclaims the freed disk space. See [org.njarasoa.fijerena.core.network.provider.ProviderRepository.pruneOrphanedCatalogData].
 */
@Composable
fun DatabaseMaintenanceCard(
    isPruning: Boolean,
    resultMessage: String?,
    onShrinkClick: () -> Unit,
    scale: Float,
    isDevMode: Boolean = false,
    lastShrinkAtMs: Long = 0L,
    lastShrinkDurationMs: Long = 0L,
    lastShrinkRowsRemoved: Long = 0L,
    lastShrinkBytesReclaimed: Long = 0L,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_shrink_database_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = stringResource(R.string.settings_shrink_database_desc),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale),
                    ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onShrinkClick,
                text =
                    stringResource(
                        if (isPruning) R.string.settings_shrink_database_button_running else R.string.settings_shrink_database_button,
                    ),
                enabled = !isPruning,
                modifier = Modifier.fillMaxWidth(),
            )
            if (resultMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                Text(
                    text = resultMessage,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale),
                        ),
                    color = CinemaTextSecondary,
                )
            }
            if (isDevMode && lastShrinkAtMs > 0L) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                val context = LocalContext.current
                val time = NumberUtils.formatTimestamp(context, lastShrinkAtMs)
                val duration = NumberUtils.formatDuration(lastShrinkDurationMs)
                Text(
                    text = stringResource(R.string.settings_shrink_database_dev_stats_time, time, duration),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale),
                        ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium),
                )
                val bytesStr = NumberUtils.formatBytes(lastShrinkBytesReclaimed)
                Text(
                    text = stringResource(R.string.settings_shrink_database_dev_stats_delta, lastShrinkRowsRemoved, bytesStr),
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale),
                        ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                )
            }
        }
    }
}
