package org.njarasoa.fijerena.ui.components.modifiers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.panelShape
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import androidx.compose.ui.geometry.CornerRadius as ComposeCornerRadius

/**
 * Standard TV focus modifier: the style's focus scale (animated) and an accent border on focus. A
 * Modifier.Node, so it has none of the per-element composition cost of `composed {}`.
 */
@Composable
fun Modifier.tvFocusable(
    focusScale: Float = TvFocusTokens.focusedScale,
    borderWidth: Dp = TvFocusTokens.focusBorderWidth,
    borderColor: Color = CinemaTheme.colors.accentSoft,
    cornerRadius: Dp = CornerRadius.medium,
): Modifier =
    // The focus-event node must sit *before* focusable(): onFocusEvent reports the focus state of
    // the focus target below it in the chain, so placed after it the node never saw an event.
    this
        .then(TvFocusableElement(focusScale, borderWidth, borderColor, cornerRadius))
        .focusable()

/** A border on focus without scaling, for cards in dense grids where scaling would overlap. */
@Composable
fun Modifier.tvFocusableNoScale(
    borderColor: Color = CinemaTheme.colors.accentSoft,
    cornerRadius: Dp = CornerRadius.medium,
): Modifier =
    tvFocusable(
        focusScale = TvFocusTokens.defaultScale,
        borderWidth = TvFocusTokens.focusBorderWidth,
        borderColor = borderColor,
        cornerRadius = cornerRadius,
    )

/** For image-based cards: half the standard scale-up on focus. */
@Composable
fun Modifier.tvFocusableContent(cornerRadius: Dp = CornerRadius.medium): Modifier =
    tvFocusable(
        focusScale = TvFocusTokens.focusedScaleContent,
        borderWidth = TvFocusTokens.focusBorderWidth,
        borderColor = CinemaTheme.colors.accentSoft,
        cornerRadius = cornerRadius,
    )

/**
 * The focus look of a picker that opens a menu (a title's copy picker): a faint accent fill and an
 * accent outline in the card corner while [isFocused], nothing at rest.
 */
@Composable
fun Modifier.pickerFocus(isFocused: Boolean): Modifier {
    val shape = CinemaTheme.shapes.card
    val colors = CinemaTheme.colors
    return background(if (isFocused) colors.accent.copy(alpha = CinemaAlpha.tint) else colors.clear, shape)
        .then(if (isFocused) Modifier.border(TvFocusTokens.focusBorderWidth, colors.accentSoft, shape) else Modifier)
}

/** An accent outline in [corner] while [isFocused], none at rest; the element keeps its own fill. */
@Composable
fun Modifier.accentFocusOutline(
    isFocused: Boolean,
    corner: PanelCorner,
): Modifier =
    border(
        width = TvFocusTokens.focusBorderWidth,
        color = if (isFocused) CinemaTheme.colors.accentSoft else CinemaTheme.colors.clear,
        shape = panelShape(corner),
    )

/** The rows' white focus outline in [corner] while [isFocused], none at rest (the player's seek bar). */
@Composable
fun Modifier.rowFocusOutline(
    isFocused: Boolean,
    corner: PanelCorner,
): Modifier =
    border(
        width = TvFocusTokens.focusBorderWidth,
        color = if (isFocused) TvFocusTokens.focusedRowOutline else CinemaTheme.colors.clear,
        shape = panelShape(corner),
    )

/**
 * The one tab look's fill (every page and section tab: a detail page's, the season tabs, a list's,
 * Search's): [TvFocusTokens.focusFill] while [isFocused], no fill otherwise, so a tab at rest is its
 * label alone. Selection is not a fill (P5): the tab's bottom bar and text carry it.
 */
@Composable
fun Modifier.tabChipFill(isFocused: Boolean): Modifier =
    background(
        color = if (isFocused) TvFocusTokens.focusFill else CinemaTheme.colors.clear,
        shape = CinemaTheme.shapes.chip,
    )

/**
 * The resting ground of a row made of several focus stops (a source row and its buttons), and the
 * rows' white focus outline around the whole row while its main stop [isFocused]: the ring and the
 * lighter focus fill cover the full row, buttons included, as on every other row.
 */
@Composable
fun Modifier.restingRowGround(isFocused: Boolean = false): Modifier =
    background(if (isFocused) TvFocusTokens.focusedContainer else TvFocusTokens.restingContainer, cornerShape(CornerRadius.row))
        .border(
            width = TvFocusTokens.focusBorderWidth,
            color = if (isFocused) TvFocusTokens.focusedRowOutline else CinemaTheme.colors.clear,
            shape = cornerShape(CornerRadius.row),
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
        borderWidthPx = borderWidth.toPx()
        cornerRadiusPx = cornerRadius.toPx()

        val placeable = measurable.measure(constraints)
        val scaledWidth = (placeable.width * currentScale).toInt()
        val scaledHeight = (placeable.height * currentScale).toInt()
        return layout(scaledWidth, scaledHeight) {
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
