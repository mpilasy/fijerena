package org.njarasoa.fijerena.ui.components.input

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CH+ / CH- and Page Up / Page Down in long TV lists (UX-D9): the keys, the page size and the row landed on. */
class ListPagingTest {
    private val all: (Int) -> Boolean = { true }

    @Test
    fun `CH and Page keys page, other keys do not`() {
        assertEquals(1, pageDirection(Key.ChannelDown))
        assertEquals(1, pageDirection(Key.PageDown))
        assertEquals(-1, pageDirection(Key.ChannelUp))
        assertEquals(-1, pageDirection(Key.PageUp))
        assertNull(pageDirection(Key.DirectionDown))
    }

    @Test
    fun `a page is the rows wholly in view, at least one`() {
        // Rows of 100 in a viewport of 0..450: four whole, the fifth cut at the bottom.
        val rows = (0 until 6).map { it * 100 to 100 }
        assertEquals(4, wholeRowsInView(rows, 0, 450))
        // A row taller than the viewport: still one row a page.
        assertEquals(1, wholeRowsInView(listOf(0 to 900), 0, 450))
        assertEquals(1, wholeRowsInView(emptyList(), 0, 450))
    }

    @Test
    fun `a page moves focus a page down or up`() {
        assertEquals(15, pageTarget(focused = 10, direction = 1, page = 5, count = 100, canFocus = all))
        assertEquals(5, pageTarget(focused = 10, direction = -1, page = 5, count = 100, canFocus = all))
    }

    @Test
    fun `near an end the page stops on the first or last row`() {
        assertEquals(99, pageTarget(focused = 97, direction = 1, page = 5, count = 100, canFocus = all))
        assertEquals(0, pageTarget(focused = 2, direction = -1, page = 5, count = 100, canFocus = all))
    }

    @Test
    fun `at an end there is nowhere to go`() {
        assertNull(pageTarget(focused = 99, direction = 1, page = 5, count = 100, canFocus = all))
        assertNull(pageTarget(focused = 0, direction = -1, page = 5, count = 100, canFocus = all))
    }

    @Test
    fun `a separator gives way to the next row on, else the nearest one back`() {
        val separators = setOf(15, 16, 99)
        val canFocus: (Int) -> Boolean = { it !in separators }
        assertEquals(17, pageTarget(focused = 10, direction = 1, page = 5, count = 100, canFocus = canFocus))
        // The last row is a separator: the row above it.
        assertEquals(98, pageTarget(focused = 95, direction = 1, page = 5, count = 100, canFocus = canFocus))
        // Up onto a separator with only separators above it: back down towards the focused row.
        val top: (Int) -> Boolean = { it >= 3 }
        assertEquals(3, pageTarget(focused = 6, direction = -1, page = 5, count = 100, canFocus = top))
        assertNull(pageTarget(focused = 3, direction = -1, page = 5, count = 100, canFocus = top))
    }
}
