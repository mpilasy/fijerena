package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

/**
 * A detail-screen score, TV hero treatment: a dark rounded chip with the number over its label
 * (e.g. "8.7" over "Community Rating"). [RatingBadge] stays as-is for list rows — this is only
 * for the hero, and for any second score source (Rotten Tomatoes, say) should one ever exist.
 * See docs/plans/20260902_tv-detail-hero-ui-plan.md Phase 2.
 */
@Composable
fun ScoreChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(CinemaCornerRadius.medium))
                .background(Color.Black.copy(alpha = CinemaAlpha.detailScoreChipBackground))
                .padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xs),
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = CinemaTextPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CinemaTextSecondary)
    }
}
