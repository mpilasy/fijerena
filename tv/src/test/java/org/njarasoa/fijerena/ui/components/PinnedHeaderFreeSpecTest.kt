package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** The episode list scrolls a focused card to one place, pinned header or not. See [IgnorePinnedHeader]. */
@OptIn(ExperimentalFoundationApi::class)
class PinnedHeaderFreeSpecTest {
    // The TV pivot: the leading edge of the child at a third of the container.
    private val pivot =
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = offset - containerSize * 0.3f
        }

    private val listHeight = 1080
    private val spec = PinnedHeaderFreeSpec(pivot) { listHeight }

    // What a Compose 1.12 lazy list asks: the pinned header's height taken off the offset and the container.
    private fun askedByList(
        offset: Float,
        pinnedHeight: Float,
    ) = spec.calculateScrollDistance(offset - pinnedHeight, 200f, listHeight - pinnedHeight)

    @Test
    fun `a pinned header does not move the target`() {
        assertEquals(askedByList(390f, pinnedHeight = 0f), askedByList(390f, pinnedHeight = 140f), 0.001f)
    }

    @Test
    fun `the target is the pivot of the whole list`() {
        assertEquals(390f - listHeight * 0.3f, askedByList(390f, pinnedHeight = 140f), 0.001f)
    }

    @Test
    fun `before the list is measured the values pass through`() {
        val unmeasured = PinnedHeaderFreeSpec(pivot) { 0 }

        assertEquals(pivot.calculateScrollDistance(250f, 200f, 940f), unmeasured.calculateScrollDistance(250f, 200f, 940f), 0.001f)
    }
}
