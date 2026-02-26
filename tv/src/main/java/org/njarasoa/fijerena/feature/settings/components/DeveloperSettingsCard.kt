package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun DeveloperSettingsCard(
    isDevMode: Boolean,
    onDevModeChanged: (Boolean) -> Unit,
    scale: Float
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md.scaled(scale))
                .tvFocusableNoScale(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Developer Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
                Text(
                    text = "Enable stats for nerds and debug features",
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
            Switch(
                checked = isDevMode,
                onCheckedChange = onDevModeChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CinemaAccent,
                    checkedTrackColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                    uncheckedThumbColor = CinemaTextSecondary,
                    uncheckedTrackColor = CinemaSurfaceVariant
                )
            )
        }
    }
}
