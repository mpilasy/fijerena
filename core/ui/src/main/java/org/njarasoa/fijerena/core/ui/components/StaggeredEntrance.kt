package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha

private const val STAGGER_STEP_MS = 40L
private const val STAGGER_CAP_INDEX = 12
private const val ENTRANCE_DURATION_MS = 350L
private const val ENTRANCE_TRANSLATION_PX = 40f

/**
 * Fades and slides an item up the first time it enters composition, delayed by
 * `min(index, 12) * 40ms` so long lists keep a short total stagger.
 *
 * A Modifier.Node (not `composed {}`) to avoid per-element composition overhead in dense lists
 * (100-200+ stream/category rows), as `FocusModifiers.kt`/`BounceMarquee.kt`.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = this then StaggeredEntranceElement(index)

private data class StaggeredEntranceElement(
    val index: Int,
) : ModifierNodeElement<StaggeredEntranceNode>() {
    override fun create() = StaggeredEntranceNode(index)

    override fun update(node: StaggeredEntranceNode) {
        node.index = index
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "staggeredEntrance"
        properties["index"] = index
    }
}

private class StaggeredEntranceNode(
    var index: Int,
) : Modifier.Node(),
    LayoutModifierNode {
    private var alpha = CinemaAlpha.none
    private var translationY = ENTRANCE_TRANSLATION_PX
    private var animationJob: Job? = null

    override fun onAttach() {
        animationJob =
            coroutineScope.launch {
                delay(index.coerceAtMost(STAGGER_CAP_INDEX) * STAGGER_STEP_MS)
                val durationNanos = ENTRANCE_DURATION_MS * 1_000_000L
                val startNanos = withFrameNanos { it }
                var progress = 0f
                while (progress < 1f) {
                    val nowNanos = withFrameNanos { it }
                    progress = ((nowNanos - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                    val eased = 1f - (1f - progress) * (1f - progress)
                    alpha = eased
                    translationY = (1f - eased) * ENTRANCE_TRANSLATION_PX
                    // Placement, not measurement: alpha and translationY are applied by the graphics layer in
                    // placeWithLayer below and cannot change the measured size. invalidateMeasurement() would
                    // run this item and its parent lazy list through a full measure pass on every frame. (This
                    // did not fix the stall after a Live TV back-out: the tv ui performance plan task 6b.)
                    invalidatePlacement()
                }
                alpha = CinemaAlpha.opaque
                translationY = 0f
                invalidatePlacement()
            }
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                this.alpha = this@StaggeredEntranceNode.alpha
                this.translationY = this@StaggeredEntranceNode.translationY
            }
        }
    }
}
