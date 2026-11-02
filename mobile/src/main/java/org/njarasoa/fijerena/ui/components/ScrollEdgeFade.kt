package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * A horizontal list fades out at an end where more items wait off screen, so the item cut at the
 * edge reads as "more", not as a hard cut against what sits next to the list (the bin and the
 * guide icons after Live TV's category chips).
 */
@Composable
fun Modifier.scrollEdgeFade(
    state: LazyListState,
    width: Dp = CinemaSpacing.xl,
): Modifier {
    val start = state.canScrollBackward
    val end = state.canScrollForward
    if (!start && !end) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = width.toPx().coerceAtMost(size.width / 2)
            // look-ok: a mask for the fade (only its alpha counts), not a colour on screen
            val shown = Color.Black
            // look-ok: a mask for the fade (only its alpha counts), not a colour on screen
            val hidden = Color.Transparent
            if (start) {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(hidden, shown), startX = 0f, endX = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (end) {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(shown, hidden), startX = size.width - fade, endX = size.width),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
