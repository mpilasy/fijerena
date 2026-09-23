package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * One plain-text fact in a dot-separated detail meta line — see
 * docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md, Phase 4 (3b).
 */
@Composable
fun MetaText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/** One small pill-styled fact in a detail meta line (content rating, resolution). */
@Composable
fun MetaBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    RoundedCornerShape(CinemaCornerRadius.small),
                ).padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xs),
    )
}
