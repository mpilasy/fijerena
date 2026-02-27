package org.njarasoa.fijerena.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A marquee modifier that bounces text back and forth instead of scrolling infinitely.
 *
 * When content is wider than its container, it animates left-to-right-to-left.
 * When content fits, no animation occurs.
 *
 * Uses two-pass measurement: first measures content with relaxed constraints to
 * get natural width, then clips and translates to create the bounce effect.
 *
 * @param velocity scroll speed in dp/second
 * @param delayMillis pause at each end before reversing
 */
fun Modifier.bounceMarquee(
    velocity: Dp = 30.dp,
    delayMillis: Int = 1200
): Modifier = composed {
    val density = LocalDensity.current
    var overflowPx by remember { mutableIntStateOf(0) }

    // Only create transition when content actually overflows
    val fraction = if (overflowPx > 0) {
        val durationMs = with(density) { ((overflowPx / velocity.toPx()) * 1000).toInt().coerceAtLeast(500) }
        val transition = rememberInfiniteTransition(label = "bounce_marquee")
        val animatedFraction by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = durationMs,
                    delayMillis = delayMillis,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce_offset"
        )
        animatedFraction
    } else {
        0f
    }

    this
        .clipToBounds()
        .layout { measurable, constraints ->
            // If we're already in an infinite-width context, just pass through
            if (constraints.maxWidth == Constraints.Infinity) {
                val placeable = measurable.measure(constraints)
                overflowPx = 0
                return@layout layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }

            val containerWidth = constraints.maxWidth

            // Measure content without width constraint to get its natural width.
            // Use 5x container width, capped conservatively to avoid Constraints overflow.
            val wideMax = (containerWidth.toLong() * 5).coerceAtMost(65_535L).toInt()
            val placeable = measurable.measure(
                constraints.copy(minWidth = 0, maxWidth = wideMax)
            )

            val contentWidth = placeable.width
            val overflow = (contentWidth - containerWidth).coerceAtLeast(0)
            overflowPx = overflow

            layout(containerWidth, placeable.height) {
                placeable.place(0, 0)
            }
        }
        .graphicsLayer {
            if (overflowPx > 0) {
                translationX = -overflowPx * fraction
            }
        }
}
