package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

/** Channel up/down on the live player. See docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-13. */
class ChannelCursorTest {
    private fun channels(vararg ids: String) = ids.map { MediaItem(it, it, MediaType.LIVE_CHANNEL, "c") }

    @Test
    fun `next and previous wrap around the list`() {
        val cursor = ChannelCursor.at(channels("a", "b", "c"), "c")
        assertEquals("a", cursor.next().current?.id)
        assertEquals("b", cursor.previous().current?.id)
        val first = ChannelCursor.at(channels("a", "b", "c"), "a")
        assertEquals("c", first.previous().current?.id)
    }

    @Test
    fun `an index beyond a shorter list is clamped, not thrown`() {
        val stale = ChannelCursor(channels("a", "b"), index = 7)
        assertNull(stale.current)
        assertEquals("a", stale.next().current?.id)
        assertEquals("a", stale.previous().current?.id)
    }

    @Test
    fun `an empty list goes nowhere`() {
        val empty = ChannelCursor()
        assertNull(empty.next().current)
        assertNull(empty.previous().current)
    }

    @Test
    fun `a stream missing from the list starts at the first channel`() {
        assertEquals(0, ChannelCursor.at(channels("a", "b"), "zz").index)
        assertEquals(-1, ChannelCursor.at(emptyList(), "zz").index)
        val onA = ChannelCursor.at(channels("a", "b"), "a")
        assertEquals(-1, onA.pointingAt("zz").index)
    }

    @Test
    fun `nothing selected steps to either end`() {
        val unselected = ChannelCursor(channels("a", "b", "c"), index = -1)
        assertEquals("a", unselected.next().current?.id)
        assertEquals("c", unselected.previous().current?.id)
    }
}
