package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape

/**
 * A detail-screen score, TV hero treatment: a dark rounded chip with the number over its label
 * (e.g. "8.7" over "Community Rating"). [RatingBadge] stays as-is for list rows — this is only
 * for the hero, and for any second score source (Rotten Tomatoes, say) should one ever exist.
 * See the tv detail hero ui plan Phase 2.
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
                .clip(cornerShape(CinemaCornerRadius.medium))
                .background(CinemaTheme.colors.mediaScrim.copy(alpha = CinemaAlpha.detailScoreChipBackground))
                .padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xs),
    ) {
        CinemaText(text = value, role = TextRole.Heading)
        CinemaText(text = label, role = TextRole.Caption, tone = TextTone.Secondary)
    }
}
