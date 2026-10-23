package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * A colour theme's own ground in place of the accent wash or glow ([CinemaThemePalette.scenery]):
 * one static draw behind the content, no animation.
 */
@Immutable
sealed interface Scenery {
    /** The plain [CinemaColorRoles.background]: no wash, no glow (Console). */
    data object Flat : Scenery

    /**
     * A painted sky over rolling hills (Meadow, docs/plans/20261021_meadow-look-plan.md): the sky
     * from [skyTop] to [skyBottom], three hill bands back to front in [hills], and at [night] a few
     * stars and a moon.
     */
    data class Meadow(
        val skyTop: Color,
        val skyBottom: Color,
        val hills: List<Color>,
        val night: Boolean,
        val starColor: Color,
        val moonColor: Color,
    ) : Scenery
}

/** Paints [scenery] over the whole draw area. */
internal fun DrawScope.drawScenery(
    scenery: Scenery,
    background: Color,
) {
    when (scenery) {
        Scenery.Flat -> {
            drawRect(background)
        }

        is Scenery.Meadow -> {
            drawRect(Brush.verticalGradient(listOf(scenery.skyTop, scenery.skyBottom)))
            if (scenery.night) drawNightSky(scenery)
            scenery.hills.forEachIndexed { index, colour -> drawHill(index, colour) }
        }
    }
}

private fun DrawScope.drawNightSky(scenery: Scenery.Meadow) {
    val unit = minOf(size.width, size.height)
    STARS.forEach { (x, y, r) ->
        drawCircle(scenery.starColor.copy(alpha = STAR_ALPHA), radius = unit * r, center = Offset(size.width * x, size.height * y))
    }
    // From the shorter side: on a tall phone the moon stays up behind the header, clear of tabs and rows.
    val moon = Offset(size.width * MOON_X, unit * MOON_Y)
    drawCircle(
        Brush.radialGradient(
            listOf(scenery.moonColor.copy(alpha = MOON_HALO_ALPHA), Color.Transparent),
            center = moon,
            radius =
                unit * MOON_HALO,
        ),
        radius = unit * MOON_HALO,
        center = moon,
    )
    drawCircle(scenery.moonColor, radius = unit * MOON_RADIUS, center = moon)
}

/** Hill [index] (0 = farthest): a wave across the width, each nearer band lower on the screen. */
private fun DrawScope.drawHill(
    index: Int,
    colour: Color,
) {
    val w = size.width
    val h = size.height
    val top = h * (HILL_TOP + index * HILL_STEP)
    val rise = h * HILL_RISE
    // Each band's crests sit at other places, so the bands do not line up.
    val shift = index * HILL_SHIFT
    val path =
        Path().apply {
            moveTo(0f, top)
            cubicTo(w * (0.15f + shift), top - rise, w * (0.35f + shift), top - rise * 0.4f, w * 0.5f, top)
            cubicTo(w * (0.65f - shift), top + rise * 0.6f, w * (0.85f - shift), top - rise, w, top - rise * 0.3f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    drawPath(path, colour)
}

/** Star positions and sizes as fractions of the screen, in the sky above the hills. */
private val STARS =
    listOf(
        Triple(0.26f, 0.06f, 0.0016f),
        Triple(0.44f, 0.03f, 0.0011f),
        Triple(0.56f, 0.09f, 0.0016f),
        Triple(0.67f, 0.025f, 0.0011f),
        Triple(0.79f, 0.07f, 0.0016f),
        Triple(0.93f, 0.04f, 0.0011f),
        Triple(0.35f, 0.15f, 0.0011f),
        Triple(0.72f, 0.18f, 0.0013f),
    )

private const val STAR_ALPHA = 0.85f
private const val MOON_X = 0.88f
private const val MOON_Y = 0.16f
private const val MOON_RADIUS = 0.024f
private const val MOON_HALO = 0.08f
private const val MOON_HALO_ALPHA = 0.25f
private const val HILL_TOP = 0.74f
private const val HILL_STEP = 0.07f
private const val HILL_RISE = 0.06f
private const val HILL_SHIFT = 0.05f
