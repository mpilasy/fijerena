package org.njarasoa.fijerena.ui.components.rail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RailEntryItemTest {
    private val items = listOf(RailItem.PROFILE, RailItem.HOME, RailItem.MOVIES, RailItem.SEARCH, RailItem.SETTINGS)

    @Test
    fun `focus enters at the current item`() {
        assertEquals(RailItem.MOVIES, railEntryItem(RailItem.MOVIES, items))
    }

    @Test
    fun `no current item enters at the first`() {
        assertEquals(RailItem.PROFILE, railEntryItem(null, items))
    }

    @Test
    fun `a current section the source lacks enters at the first`() {
        assertEquals(RailItem.PROFILE, railEntryItem(RailItem.LIVE_TV, items))
    }

    @Test
    fun `an empty rail has no entry`() {
        assertNull(railEntryItem(RailItem.HOME, emptyList()))
    }
}
