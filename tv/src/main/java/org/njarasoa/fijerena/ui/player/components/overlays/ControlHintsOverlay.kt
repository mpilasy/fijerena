package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
fun ControlHintsOverlay(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.glass)),
        contentAlignment = Center
    ) {
        TvGlassPanel(
            modifier = Modifier
                .width(TvDimensions.dialogWidthLarge)
                .padding(Spacing.xxl)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Header
                Text(
                    text = "🎮 Player Controls",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Control hints
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlHint("OK Button", "Show/hide controls")
                    ControlHint("Double-tap OK", "Toggle stats overlay")
                    ControlHint("BACK Button", "Exit player")
                    ControlHint("D-pad Up/Down", "Change channel (Live TV)")
                    ControlHint("Pause/Resume", "Control playback")
                    ControlHint("Audio Button", "Select audio track")
                    ControlHint("Subtitle Button", "Enable/disable subtitles")
                    ControlHint("Quality Button", "Select video quality")
                    ControlHint("Favorite Button", "Add/remove from favorites")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Got it!")
                    }
                    Button(
                        onClick = onDontShowAgain,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = CinemaSurfaceVariant
                        )
                    ) {
                        Text("Don't show again")
                    }
                }

                // Auto-dismiss info
                Text(
                    text = "This message will auto-dismiss in 7 seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = CinemaAlpha.textDisabled),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ControlHint(control: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = control,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(TvDimensions.audioTrackSelectorWidth)
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = CinemaAlpha.textDisabled)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}
