@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Live TV zap feedback (LT5, docs/plans/archive/20261003_ux-overhaul-plan.md → L-7): "Tuning · <channel>"
 * with a small spinner, centred over a dimmed picture, from the moment a channel is chosen until
 * it plays. Drawn over whatever the surface shows — the engine's `stop()` + new media source
 * closes PlayerView's shutter, so after the first moment of a zap that is black, not the last
 * frame. Not focusable; the remote keeps zapping. [compact] is the preview pane's size.
 */
@Composable
fun TvTuningOverlay(
    channelName: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val background = CinemaBackground
    val scrim = remember(background) { background.copy(alpha = CinemaAlpha.scrim) }
    val pill = remember(background) { background.copy(alpha = CinemaAlpha.glass) }
    val radius = CornerRadius.medium
    val pillShape = remember(radius) { RoundedCornerShape(radius) }
    Box(
        modifier = modifier.fillMaxSize().background(scrim),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(Spacing.md)
                    .background(pill, pillShape)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) TvDimensions.iconSmall else TvDimensions.iconMedium),
                color = CinemaAccent,
                strokeWidth = TvDimensions.borderFocused,
            )
            Text(
                text = stringResource(R.string.live_tuning_format, channelName),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = CinemaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
