package org.njarasoa.fijerena.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaOrange
import org.njarasoa.fijerena.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
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

/**
 * AccentBlock - Gradient fill for StandardCardContainer imageCard slot.
 * Provides content-type-aware visual identity.
 */
@Composable
fun AccentBlock(
    contentType: ContentCardType,
    modifier: Modifier = Modifier
) {
    val gradient = when (contentType) {
        ContentCardType.LIVE_TV -> Brush.verticalGradient(
            colors = listOf(CinemaOrange, CinemaOrangeDark)
        )
        ContentCardType.MOVIE -> Brush.verticalGradient(
            colors = listOf(CinemaAccent, CinemaAccentDark)
        )
        ContentCardType.TV_SHOW -> Brush.verticalGradient(
            colors = listOf(CinemaAccentLight, CinemaAccent)
        )
        ContentCardType.DEFAULT -> Brush.verticalGradient(
            colors = listOf(CinemaSurfaceVariant, CinemaSurface)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = gradient,
                shape = RoundedCornerShape(CornerRadius.medium)
            )
    )
}
