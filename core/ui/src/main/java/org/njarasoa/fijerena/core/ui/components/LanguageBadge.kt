package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
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
    val radius = CinemaCornerRadius.small
    val shape = remember(radius) { RoundedCornerShape(radius) }
    Text(
        text = text,
        style = style,
        color = textColor,
        modifier =
            modifier
                .clip(shape)
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
 * A provider's raw title shown clean: its "EN - " / "4K-NF - " / "4K:" tag as a [LanguageBadge],
 * then the title without it ([parseDisplayTitle]). One line; [textModifier] goes on the title text.
 */
@Composable
fun BadgedTitle(
    raw: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textModifier: Modifier = Modifier,
) {
    val parsed = remember(raw) { parseDisplayTitle(raw) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parsed.badge?.let { LanguageBadge(it) }
        Text(
            text = parsed.title.ifBlank { raw },
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).then(textModifier),
        )
    }
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
