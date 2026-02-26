package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun ExportImportSettingsCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onQuickImport: () -> Unit,
    exportImportMessage: String?,
    scale: Float
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs.scaled(scale))) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = "Export / Import",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = "Export all settings to a file or import from another device",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
            ) {
                CinemaPrimaryButton(
                    onClick = onExport,
                    text = "Export Settings",
                    modifier = Modifier.weight(1f)
                )
                CinemaSecondaryButton(
                    onClick = onImport,
                    text = "Import Settings",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onQuickImport,
                text = "Quick Import from Downloads",
                modifier = Modifier.fillMaxWidth()
            )
            if (exportImportMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                Text(
                    text = exportImportMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary
                )
            }
        }
    }
}
