package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

@Composable
fun ChannelToast(
    channelName: String,
    currentEpgProgram: EpgProgram? = null
) {
    GlassPanel(
        modifier = Modifier.padding(top = CinemaSpacing.xl)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CinemaSpacing.lg,
                vertical = CinemaSpacing.sm
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = channelName,
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
                textAlign = TextAlign.Center
            )
            if (currentEpgProgram != null) {
                Text(
                    text = currentEpgProgram.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
