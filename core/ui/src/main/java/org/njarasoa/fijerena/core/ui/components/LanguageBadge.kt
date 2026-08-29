package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

/**
 * Shared badge primitive used for status indicators, codecs, ratings, and tags.
 */
@Composable
fun CinemaBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = CinemaSurfaceVariant,
    textColor: Color = CinemaTextSecondary,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    Text(
        text = text,
        style = style,
        color = textColor,
        modifier =
            modifier
                .clip(RoundedCornerShape(CinemaCornerRadius.small))
                .background(backgroundColor)
                .padding(horizontal = CinemaSpacing.xs, vertical = CinemaSpacing.xxs),
    )
}

/**
 * Small pill for the language/region code
 * [parseDisplayTitle][org.njarasoa.fijerena.core.player.domain.parseDisplayTitle] strips off a
 * title — rendered next to the title, never baked back into it.
 */
@Composable
fun LanguageBadge(
    code: String,
    modifier: Modifier = Modifier,
) {
    CinemaBadge(
        text = code,
        modifier = modifier,
        backgroundColor = CinemaSurfaceVariant,
        textColor = CinemaTextSecondary,
    )
}

/**
 * Star rating badge standardizing the `"★ ${formatRating(rating)}"` pattern across mobile and TV.
 */
@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
    textColor: Color = CinemaAccent,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(
        text = "★ ${formatRating(rating)}",
        style = style,
        color = textColor,
        maxLines = 1,
        modifier = modifier,
    )
}
