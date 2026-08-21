package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Lays children out left to right, wrapping to a new line when the current one is full.
 *
 * This exists because `FlowRow` cannot be called in this project. The compile classpath resolves
 * `androidx.compose.foundation:foundation-layout` **1.7.6**, whose only `FlowRow` overload is
 *
 * ```
 * FlowRow(Modifier, Arrangement.Horizontal, Arrangement.Vertical, int, int, FlowRowOverflow, …)
 * ```
 *
 * while the APK ships **1.8.2**, where every overload takes an `Alignment.Vertical` as its fourth
 * parameter. A call site compiled here therefore references a descriptor that does not exist at
 * runtime and throws `NoSuchMethodError` the moment it composes. Aligning those two versions is the
 * real fix and would also retire this file; until then a direct [Layout] is a dozen lines and
 * carries no version risk at all.
 *
 * Children are measured against the incoming width with unbounded height, so a chip sizes to its
 * own content. Rows are vertically centred against their tallest child.
 *
 * @param horizontalSpacing gap between children on the same line
 * @param verticalSpacing gap between lines
 */
@Composable
fun CinemaFlowRow(
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val horizontalGap = horizontalSpacing.roundToPx()
        val verticalGap = verticalSpacing.roundToPx()
        // An unbounded parent (a horizontally scrolling one, say) has nothing to wrap against, so
        // fall back to a single line rather than wrapping after every child.
        val lineWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE

        val placeables =
            measurables.map {
                it.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
            }

        val lines = mutableListOf<MutableList<Placeable>>()
        var line = mutableListOf<Placeable>()
        var used = 0
        placeables.forEach { placeable ->
            val widthIfAdded = if (line.isEmpty()) placeable.width else used + horizontalGap + placeable.width
            if (line.isNotEmpty() && widthIfAdded > lineWidth) {
                lines += line
                line = mutableListOf(placeable)
                used = placeable.width
            } else {
                line += placeable
                used = widthIfAdded
            }
        }
        if (line.isNotEmpty()) {
            lines += line
        }

        val lineHeights = lines.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        val totalHeight = lineHeights.sum() + verticalGap * (lines.size - 1).coerceAtLeast(0)
        val totalWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                lines.maxOfOrNull { row -> row.sumOf { it.width } + horizontalGap * (row.size - 1) } ?: 0
            }

        layout(totalWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            lines.forEachIndexed { index, row ->
                var x = 0
                val lineHeight = lineHeights[index]
                row.forEach { placeable ->
                    placeable.place(x, y + (lineHeight - placeable.height) / 2)
                    x += placeable.width + horizontalGap
                }
                y += lineHeight + verticalGap
            }
        }
    }
}
