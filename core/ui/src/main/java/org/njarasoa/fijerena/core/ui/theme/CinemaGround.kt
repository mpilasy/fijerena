package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The screen ground behind the content: the theme's [Scenery] when it has one (no tint); else
 * [CinemaColorRoles.background] with a wash of [tint] (or the accent) from the top; in a theme with a ground glow, that colour glows from the top right over
 * the background fading to [CinemaColorRoles.groundEnd] corner to corner.
 */
fun Modifier.cinemaGround(
    colors: CinemaColorRoles,
    tint: Color? = null,
): Modifier =
    drawBehind {
        colors.scenery?.let {
            drawScenery(it, colors.background)
            return@drawBehind
        }
        val glow = colors.groundGlow
        val end = colors.groundEnd
        if (glow == null || end == null) {
            drawRect(
                Brush.verticalGradient(
                    listOf((tint ?: colors.accentStrong).copy(alpha = CinemaAlpha.tint), colors.background, colors.background),
                ),
            )
        } else {
            drawRect(Brush.linearGradient(listOf(colors.background, end), start = Offset.Zero, end = Offset(size.width, size.height)))
            drawRect(
                Brush.radialGradient(
                    listOf(tint?.copy(alpha = CinemaAlpha.tint) ?: glow, Color.Transparent),
                    center = Offset(size.width * GLOW_X, 0f),
                    radius = maxOf(size.width, size.height) * GLOW_RADIUS,
                ),
            )
        }
    }

private const val GLOW_X = 0.85f
private const val GLOW_RADIUS = 0.9f
