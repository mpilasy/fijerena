package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
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
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun AboutSettingsCard(scale: Float) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = CinemaAccent
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Text(
                text = "Fijerena v${org.njarasoa.fijerena.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = "Build: ${org.njarasoa.fijerena.BuildConfig.GIT_HASH}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
            Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
            Text(
                text = "Built: ${org.njarasoa.fijerena.BuildConfig.BUILD_TIME}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
            )
        }
    }
}
