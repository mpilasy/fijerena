@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius

/**
 * Content card type determines the accent gradient color.
 */
enum class ContentCardType {
    LIVE_TV,    // Orange gradient
    MOVIE,      // Blue gradient
    TV_SHOW,    // Light blue gradient
    DEFAULT     // Surface gradient
}

// Pre-allocated brushes — colors are compile-time constants, no need to recreate per composition
private val LiveTvBrush = Brush.verticalGradient(listOf(CinemaOrange, CinemaOrangeDark))
private val MovieBrush = Brush.verticalGradient(listOf(CinemaAccent, CinemaAccentDark))
private val TvShowBrush = Brush.verticalGradient(listOf(CinemaAccentLight, CinemaAccent))
private val DefaultBrush = Brush.verticalGradient(listOf(CinemaSurfaceVariant, CinemaSurface))

/**
 * AccentBlock - Gradient fill for StandardCardContainer imageCard slot.
 * Provides content-type-aware visual identity.
 * Optional fallbackLetter displays centered large letter over the gradient.
 */
@Composable
fun AccentBlock(
    contentType: ContentCardType,
    modifier: Modifier = Modifier,
    fallbackLetter: Char? = null
) {
    val gradient = when (contentType) {
        ContentCardType.LIVE_TV -> LiveTvBrush
        ContentCardType.MOVIE -> MovieBrush
        ContentCardType.TV_SHOW -> TvShowBrush
        ContentCardType.DEFAULT -> DefaultBrush
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = gradient,
                shape = RoundedCornerShape(CornerRadius.medium)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (fallbackLetter != null) {
            Text(
                text = fallbackLetter.uppercase(),
                color = CinemaTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
