package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * Scrolls a focused child of a lazy list to the same place whether its sticky header is pinned or
 * not. [content] puts the modifier it is given on the list.
 *
 * Since Compose 1.12 a lazy list removes the pinned header's height from the offset and the
 * container size before it asks the [BringIntoViewSpec]. A header that is not the first item pins
 * only once the list has scrolled to it. When the scroll stop of the focused child is at that
 * point, each scroll step pins or unpins the header and moves the target by most of the header's
 * height: the list shook without end (TV episodes, focus on episode 2 or 3). This puts the height
 * back, so the target stays where it is. The TV pivot, a third of the list height, is below the
 * header, so a focused child never goes under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IgnorePinnedHeader(content: @Composable (listModifier: Modifier) -> Unit) {
    val outerSpec = LocalBringIntoViewSpec.current
    val listHeight = remember { mutableIntStateOf(0) }
    val spec = remember(outerSpec) { PinnedHeaderFreeSpec(outerSpec) { listHeight.intValue } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        content(Modifier.onSizeChanged { listHeight.intValue = it.height })
    }
}

/** [outer] with the pinned header's height put back: [listHeight] less the container size the list gives. */
@OptIn(ExperimentalFoundationApi::class)
internal class PinnedHeaderFreeSpec(
    private val outer: BringIntoViewSpec,
    private val listHeight: () -> Int,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val pinnedHeight = (listHeight() - containerSize).coerceAtLeast(0f)
        return outer.calculateScrollDistance(offset + pinnedHeight, size, containerSize + pinnedHeight)
    }
}
