package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Look's screen frame (Console, docs/plans/20261020_lcars-look-plan.md, D6): a bar down the left
 * edge and a bar across the top, joined by a rounded elbow, drawn in the TV's safe margins so no
 * content moves. Null on [UiStyle.frame]: no frame.
 */
@Immutable
data class UiFrameTokens(
    /** The thickness of both bars. */
    val bar: Dp,
    /** The elbow's outer and inner radii. */
    val elbowOuter: Dp,
    val elbowInner: Dp,
    /** The top bar's distance from the top edge. */
    val top: Dp,
    /** The length of the accent block at the top bar's right end, and the gap before it. */
    val endBlock: Dp,
    val gap: Dp,
)

val ConsoleFrame = UiFrameTokens(bar = 10.dp, elbowOuter = 28.dp, elbowInner = 14.dp, top = 10.dp, endBlock = 160.dp, gap = 6.dp)

/**
 * Draws [frame] over the whole draw area: the elbow and both bars in [bar], the top bar's end block
 * in [end]; the top bar stops [rightInset] from the right edge.
 */
fun DrawScope.drawConsoleFrame(
    frame: UiFrameTokens,
    bar: Color,
    end: Color,
    rightInset: Dp,
) {
    val b = frame.bar.toPx()
    val top = frame.top.toPx()
    val outer = frame.elbowOuter.toPx()
    val inner = frame.elbowInner.toPx()
    val right = size.width - rightInset.toPx()
    val endStart = right - frame.endBlock.toPx()
    val barEnd = endStart - frame.gap.toPx()
    val elbow =
        Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, top + outer)
            arcTo(Rect(Offset(0f, top), Size(outer * 2, outer * 2)), 180f, 90f, false)
            lineTo(barEnd, top)
            lineTo(barEnd, top + b)
            lineTo(b + inner, top + b)
            arcTo(Rect(Offset(b, top + b), Size(inner * 2, inner * 2)), 270f, -90f, false)
            lineTo(b, size.height)
            close()
        }
    drawPath(elbow, bar)
    // The end block, rounded at its right end.
    val endPath =
        Path().apply {
            moveTo(endStart, top)
            lineTo(right - b / 2, top)
            arcTo(Rect(Offset(right - b, top), Size(b, b)), 270f, 180f, false)
            lineTo(endStart, top + b)
            close()
        }
    drawPath(endPath, end)
}
