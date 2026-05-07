package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha

@Composable
fun AboutSettingsCard() {
    SettingsSection(title = "About") {
        Text(
            text = "Fijerena v1.0.0",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Premium native media player for Android",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
    }
}
