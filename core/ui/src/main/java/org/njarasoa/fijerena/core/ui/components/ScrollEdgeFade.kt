package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * Fades the top and the bottom edge of a scrolled body while there is more to scroll that way, so a
 * line cut by the edge reads as "scroll for more", not as text cut short (phone walk A, item 4).
 * Put it before the `verticalScroll(state)` of the same [state]. Draws nothing while it all fits.
 */
fun Modifier.scrollEdgeFade(state: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = CinemaSpacing.Base.xl.toPx()
            if (state.canScrollBackward) {
                drawRect(Brush.verticalGradient(listOf(Hidden, Shown), endY = fade), blendMode = BlendMode.DstIn)
            }
            if (state.canScrollForward) {
                drawRect(
                    Brush.verticalGradient(listOf(Shown, Hidden), startY = size.height - fade, endY = size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

// The mask's two ends: only their alpha counts, no colour the user sees.
private val Shown = Color.Black // look-ok: an alpha mask, not a colour
private val Hidden = Color.Transparent // look-ok: an alpha mask, not a colour
