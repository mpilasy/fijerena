package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A marquee modifier that bounces text back and forth instead of scrolling infinitely.
 *
 * When content is wider than its container, it animates left-to-right-to-left.
 * When content fits, no animation occurs.
 *
 * Implemented as a Modifier.Node to avoid the per-element composition overhead of `composed {}`.
 * This matters in live TV lists with 100-200+ stream names that could each have a marquee.
 *
 * @param velocity scroll speed in dp/second
 * @param delayMillis pause at each end before reversing
 */
fun Modifier.bounceMarquee(
    velocity: Dp = 30.dp,
    delayMillis: Int = 1200
): Modifier = this then BounceMarqueeElement(velocity, delayMillis)

private data class BounceMarqueeElement(
    val velocity: Dp,
    val delayMillis: Int
) : ModifierNodeElement<BounceMarqueeNode>() {
    override fun create() = BounceMarqueeNode(velocity, delayMillis)
    override fun update(node: BounceMarqueeNode) {
        node.update(velocity, delayMillis)
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "bounceMarquee"
        properties["velocity"] = velocity
        properties["delayMillis"] = delayMillis
    }
}

private class BounceMarqueeNode(
    private var velocity: Dp,
    private var delayMillis: Int
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

    private var overflowPx = 0
    private var fraction = 0f
    private var velocityPxPerSec = 0f
    private var animationJob: Job? = null

    fun update(newVelocity: Dp, newDelayMillis: Int) {
        velocity = newVelocity
        delayMillis = newDelayMillis
        // Restart animation if running, since timing parameters changed
        if (overflowPx > 0) startAnimation()
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        // Cache velocity in px/sec for animation calculations
        velocityPxPerSec = velocity.toPx()

        // Pass through if already in infinite-width context
        if (constraints.maxWidth == Constraints.Infinity) {
            val placeable = measurable.measure(constraints)
            updateOverflow(0)
            return layout(placeable.width, placeable.height) {
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

        updateOverflow((placeable.width - containerWidth).coerceAtLeast(0))

        return layout(containerWidth, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun ContentDrawScope.draw() {
        // Clip to container bounds + translate for bounce animation
        clipRect {
            val offset = if (overflowPx > 0) -overflowPx * fraction else 0f
            translate(left = offset) {
                this@draw.drawContent()
            }
        }
    }

    private fun updateOverflow(newOverflow: Int) {
        if (newOverflow == overflowPx) return
        overflowPx = newOverflow
        if (newOverflow > 0) {
            startAnimation()
        } else {
            stopAnimation()
            fraction = 0f
        }
    }

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            // Initial pause before first scroll
            delay(delayMillis.toLong())

            while (isActive) {
                val durationMs = if (velocityPxPerSec > 0f) {
                    ((overflowPx / velocityPxPerSec) * 1000f).toLong().coerceAtLeast(500L)
                } else { 1000L }

                // Forward sweep: fraction 0 -> 1
                animateLinear(durationMs) { fraction = it }
                delay(delayMillis.toLong())

                // Reverse sweep: fraction 1 -> 0
                animateLinear(durationMs) { fraction = 1f - it }
                delay(delayMillis.toLong())
            }
        }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    /**
     * Frame-synchronized linear animation using withFrameNanos.
     * Each frame updates progress and invalidates draw without triggering recomposition.
     */
    private suspend fun animateLinear(durationMs: Long, onProgress: (Float) -> Unit) {
        if (durationMs <= 0L) return
        val durationNanos = durationMs * 1_000_000L
        val startNanos = withFrameNanos { it }
        var progress = 0f
        while (progress < 1f) {
            val nowNanos = withFrameNanos { it }
            progress = ((nowNanos - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
            onProgress(progress)
            invalidateDraw()
        }
    }

    override fun onDetach() {
        stopAnimation()
    }
}
