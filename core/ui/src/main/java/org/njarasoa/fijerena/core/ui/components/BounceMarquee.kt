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
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * A marquee that bounces text back and forth instead of scrolling for ever; no animation when the
 * content fits.
 *
 * At rest (the pause at the start, and before the first scroll) the content is laid out in the
 * width it is given, so a text with `TextOverflow.Ellipsis` shows its start and the "…"; only while
 * it scrolls, or waits at the far end, is it laid out at full width. Callers give `Ellipsis`, so a
 * text never starts in the middle of a word.
 *
 * A Modifier.Node, not `composed {}`: Live TV lists have 100-200+ names that can each have a marquee.
 *
 * @param velocity scroll speed in dp/second
 * @param delayMillis pause at each end before reversing
 */
fun Modifier.bounceMarquee(
    velocity: Dp = CinemaSpacing.Base.xl - CinemaSpacing.Base.xxxs,
    delayMillis: Int = 1200,
): Modifier = this then BounceMarqueeElement(velocity, delayMillis)

private data class BounceMarqueeElement(
    val velocity: Dp,
    val delayMillis: Int,
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
    private var delayMillis: Int,
) : Modifier.Node(),
    LayoutModifierNode,
    DrawModifierNode {
    private var overflowPx = 0
    private var layoutWidthPx = 0
    private var fraction = 0f
    private var velocityPxPerSec = 0f
    private var animationJob: Job? = null

    /** True while the text is not away from its start: it is then laid out in its own width. */
    private var atRest = true

    fun update(
        newVelocity: Dp,
        newDelayMillis: Int,
    ) {
        velocity = newVelocity
        delayMillis = newDelayMillis
        // Restart animation if running, since timing parameters changed
        if (overflowPx > 0) {
            startAnimation()
            invalidateMeasurement()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        velocityPxPerSec = velocity.toPx()

        // Pass through in an infinite-width context. Otherwise the content's single-line intrinsic
        // width gives the overflow. At rest the content is measured in the given width, so the text
        // ellipsises from its start; while it scrolls it has no width limit (5x the container, capped
        // to avoid Constraints overflow).
        val isInfiniteWidth = constraints.maxWidth == Constraints.Infinity
        val containerWidth = constraints.maxWidth
        val naturalWidth = if (isInfiniteWidth) 0 else measurable.maxIntrinsicWidth(constraints.maxHeight)
        updateOverflow(if (isInfiniteWidth) 0 else (naturalWidth - containerWidth).coerceAtLeast(0))
        val placeable =
            if (isInfiniteWidth || atRest) {
                measurable.measure(constraints)
            } else {
                val wideMax = (containerWidth.toLong() * 5).coerceAtMost(65_535L).toInt()
                measurable.measure(constraints.copy(minWidth = 0, maxWidth = wideMax))
            }

        // As wide as the text, up to the space given: a short title must not take the whole row.
        val width = if (isInfiniteWidth) placeable.width else placeable.width.coerceIn(constraints.minWidth, containerWidth)
        layoutWidthPx = width
        return layout(width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun ContentDrawScope.draw() {
        // Clip to this node's own width: the draw scope has the size of the wider text measured under
        // it, so a plain clipRect lets the text run on under the items after it.
        clipRect(right = layoutWidthPx.toFloat()) {
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
            atRest = true
        }
    }

    private fun setAtRest(rest: Boolean) {
        if (rest == atRest) return
        atRest = rest
        invalidateMeasurement()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        // Start again from the start, at rest. The measure pass that called this reads atRest
        // after it; a call from update() lays the node out again.
        fraction = 0f
        atRest = true
        animationJob =
            coroutineScope.launch {
                delay(delayMillis.toLong())

                while (isActive) {
                    val durationMs =
                        if (velocityPxPerSec > 0f) {
                            ((overflowPx / velocityPxPerSec) * 1000f).toLong().coerceAtLeast(500L)
                        } else {
                            1000L
                        }

                    setAtRest(false)
                    animateLinear(durationMs) { fraction = it }
                    delay(delayMillis.toLong())

                    animateLinear(durationMs) { fraction = 1f - it }
                    setAtRest(true)
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
    private suspend fun animateLinear(
        durationMs: Long,
        onProgress: (Float) -> Unit,
    ) {
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
