@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * "Connection too slow for this video (needs ~60 Mbps, getting ~20 Mbps)" at the top of the
 * picture while the stream starves (P4, docs/plans/20261004_playback-capability-errors-plan.md).
 * Not focusable; the remote keeps working.
 */
@Composable
fun TvSlowConnectionBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val background = CinemaBackground
    val pill = remember(background) { background.copy(alpha = CinemaAlpha.glass) }
    val radius = CornerRadius.medium
    val pillShape = remember(radius) { RoundedCornerShape(radius) }
    Box(
        modifier = modifier.fillMaxSize().padding(top = Spacing.xl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = CinemaTextPrimary,
            modifier =
                Modifier
                    .background(pill, pillShape)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}
