package org.njarasoa.fijerena.ui.components.modifiers

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import androidx.compose.ui.geometry.CornerRadius as ComposeCornerRadius

/**
 * Standard TV Focus Modifier
 * Applies consistent focus behavior across all focusable elements:
 * - 1.1x scale on focus with 200ms animated transition
 * - 2dp Electric Blue border when focused
 * - Rounded corners matching the design system
 *
 * Implemented as a Modifier.Node to avoid the per-element composition overhead of `composed {}`.
 */
@Composable
fun Modifier.tvFocusable(
    focusScale: Float = TvFocusTokens.focusedScale,
    borderWidth: Dp = TvFocusTokens.focusBorderWidth,
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium,
): Modifier =
    this
        .focusable()
        .then(TvFocusableElement(focusScale, borderWidth, borderColor, cornerRadius))

/**
 * No Scale Focus Modifier
 * For elements that should only show border on focus without scaling:
 * - No scale
 * - 2dp animated border
 *
 * Useful for cards in dense grids where scaling would cause overlaps.
 */
@Composable
fun Modifier.tvFocusableNoScale(
    borderColor: Color = CinemaAccentLight,
    cornerRadius: Dp = CornerRadius.medium,
): Modifier =
    tvFocusable(
        focusScale = TvFocusTokens.defaultScale,
        borderWidth = TvFocusTokens.focusBorderWidth,
        borderColor = borderColor,
        cornerRadius = cornerRadius,
    )

/**
 * Content-First Focus Modifier (borderless)
 * For image-based cards where the content is the star:
 * - 1.05x scale on focus (gentler than standard)
 * - Subtle border only when focused
 * - No bright border, no 1.1x scale
 */
@Composable
fun Modifier.tvFocusableContent(cornerRadius: Dp = CornerRadius.medium): Modifier =
    tvFocusable(
        focusScale = TvFocusTokens.focusedScaleContent,
        borderWidth = TvFocusTokens.focusBorderWidth,
        borderColor = CinemaAccentLight,
        cornerRadius = cornerRadius,
    )

private data class TvFocusableElement(
    val focusScale: Float,
    val borderWidth: Dp,
    val borderColor: Color,
    val cornerRadius: Dp,
) : ModifierNodeElement<TvFocusableNode>() {
    override fun create() = TvFocusableNode(focusScale, borderWidth, borderColor, cornerRadius)

    override fun update(node: TvFocusableNode) {
        node.update(focusScale, borderWidth, borderColor, cornerRadius)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tvFocusable"
        properties["focusScale"] = focusScale
        properties["borderWidth"] = borderWidth
        properties["borderColor"] = borderColor
        properties["cornerRadius"] = cornerRadius
    }
}

private class TvFocusableNode(
    private var focusScale: Float,
    private var borderWidth: Dp,
    private var borderColor: Color,
    private var cornerRadius: Dp,
) : Modifier.Node(),
    FocusEventModifierNode,
    LayoutModifierNode,
    DrawModifierNode {
    private var isFocused = false
    private var currentScale = TvFocusTokens.defaultScale
    private var animationJob: Job? = null

    // Cached px values
    private var borderWidthPx = 0f
    private var cornerRadiusPx = 0f

    fun update(
        newFocusScale: Float,
        newBorderWidth: Dp,
        newBorderColor: Color,
        newCornerRadius: Dp,
    ) {
        focusScale = newFocusScale
        borderWidth = newBorderWidth
        borderColor = newBorderColor
        cornerRadius = newCornerRadius
        invalidateMeasurement()
        invalidateDraw()
    }

    override fun onFocusEvent(focusState: FocusState) {
        val wasFocused = isFocused
        isFocused = focusState.isFocused
        if (wasFocused != isFocused) {
            startScaleAnimation()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Cache dp-to-px conversions
        borderWidthPx = borderWidth.toPx()
        cornerRadiusPx = cornerRadius.toPx()

        val placeable = measurable.measure(constraints)
        val scaledWidth = (placeable.width * currentScale).toInt()
        val scaledHeight = (placeable.height * currentScale).toInt()
        return layout(scaledWidth, scaledHeight) {
            // Center the placeable within the scaled bounds
            val offsetX = (scaledWidth - placeable.width) / 2
            val offsetY = (scaledHeight - placeable.height) / 2
            placeable.place(offsetX, offsetY)
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (isFocused && borderWidthPx > 0f) {
            drawRoundRect(
                color = borderColor,
                style = Stroke(width = borderWidthPx),
                cornerRadius = ComposeCornerRadius(cornerRadiusPx),
            )
        }
    }

    private fun startScaleAnimation() {
        animationJob?.cancel()
        val targetScale = if (isFocused) focusScale else TvFocusTokens.defaultScale
        val startScale = currentScale
        if (startScale == targetScale) return

        animationJob =
            coroutineScope.launch {
                val durationMs = CinemaAnimation.focusDurationMs.toLong()
                val durationNanos = durationMs * 1_000_000L
                val startNanos = withFrameNanos { it }
                var progress = 0f
                while (progress < 1f) {
                    val nowNanos = withFrameNanos { it }
                    progress = ((nowNanos - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                    currentScale = startScale + (targetScale - startScale) * progress
                    invalidateMeasurement()
                    invalidateDraw()
                }
                currentScale = targetScale
            }
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }
}
