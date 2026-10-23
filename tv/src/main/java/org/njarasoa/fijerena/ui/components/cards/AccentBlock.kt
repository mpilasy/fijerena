@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius

/**
 * Content card type determines the accent gradient color.
 */
enum class ContentCardType {
    LIVE_TV,
    MOVIE,
    TV_SHOW,
    DEFAULT,
}

/**
 * AccentBlock - Gradient fill for StandardCardContainer imageCard slot.
 * Provides content-type-aware visual identity.
 * Optional fallbackLetter displays centered large letter over the gradient.
 */
@Composable
fun AccentBlock(
    contentType: ContentCardType,
    modifier: Modifier = Modifier,
    fallbackLetter: Char? = null,
) {
    // From the theme on every composition (they were built once at class load, so a theme change
    // left the old colours).
    val c = CinemaTheme.colors
    val gradient =
        remember(contentType, c) {
            when (contentType) {
                ContentCardType.LIVE_TV -> Brush.verticalGradient(listOf(c.highlight, c.highlight.copy(alpha = CinemaAlpha.textHigh)))
                ContentCardType.MOVIE -> Brush.verticalGradient(listOf(c.accent, c.accentStrong))
                ContentCardType.TV_SHOW -> Brush.verticalGradient(listOf(c.accentSoft, c.accent))
                ContentCardType.DEFAULT -> Brush.verticalGradient(listOf(c.surfaceRaised, c.surface))
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    brush = gradient,
                    shape = cornerShape(CornerRadius.medium),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (fallbackLetter != null) {
            CinemaText(
                text = fallbackLetter.uppercase(),
                role = TextRole.Display,
                emphasis = true,
                textAlign = TextAlign.Center,
            )
        }
    }
}
