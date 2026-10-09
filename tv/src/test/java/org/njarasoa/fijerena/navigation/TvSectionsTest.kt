package org.njarasoa.fijerena.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.ui.components.rail.RailItem

class TvSectionsTest {
    @Test
    fun `rail offers the sections the source has, in order, between Home and Search`() {
        assertEquals(
            listOf(RailItem.PROFILE, RailItem.HOME, RailItem.LIVE_TV, RailItem.TV_SHOWS, RailItem.SEARCH, RailItem.SETTINGS),
            railItems(setOf(ContentType.TV_SHOWS, ContentType.LIVE_TV)),
        )
    }

    @Test
    fun `a single section is still offered`() {
        assertEquals(
            listOf(RailItem.PROFILE, RailItem.HOME, RailItem.MOVIES, RailItem.SEARCH, RailItem.SETTINGS),
            railItems(setOf(ContentType.MOVIES)),
        )
    }

    @Test
    fun `no sections while they are unknown`() {
        assertEquals(listOf(RailItem.PROFILE, RailItem.HOME, RailItem.SEARCH, RailItem.SETTINGS), railItems(null))
    }

    @Test
    fun `Home, Search and Settings on top light their own item, whatever section is below`() {
        assertEquals(RailItem.HOME, railCurrent(TopScreen.HOME, null))
        assertEquals(RailItem.SEARCH, railCurrent(TopScreen.SEARCH, TvSection.MOVIES))
        assertEquals(RailItem.SETTINGS, railCurrent(TopScreen.SETTINGS, TvSection.LIVE_TV))
    }

    @Test
    fun `any other screen lights its section, or Home outside the sections`() {
        assertEquals(RailItem.TV_SHOWS, railCurrent(TopScreen.OTHER, TvSection.TV_SHOWS))
        assertEquals(RailItem.HOME, railCurrent(TopScreen.OTHER, null))
    }

    @Test
    fun `sections map to the tab routes and content types`() {
        assertEquals(Screen.LiveTvTab, TvSection.of(RailItem.LIVE_TV)?.route)
        assertEquals(TvSection.MOVIES, TvSection.of(ContentType.MOVIES))
        assertEquals(Screen.TvShowsTab, TvSection.of(ContentType.TV_SHOWS)?.route)
        assertNull(TvSection.of(RailItem.SEARCH))
        assertNull(TvSection.of("ALL"))
    }
}
