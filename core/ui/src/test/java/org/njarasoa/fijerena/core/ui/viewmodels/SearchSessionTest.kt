package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.sports.teamsSearch

/**
 * A Search screen opened anew takes the type and the pill its route brings, never the ones left
 * from an earlier Search (TV and phone).
 */
class SearchSessionTest {
    @Before
    fun setUp() {
        SearchSession.forProfile("search-session-test-a")
        SearchSession.forProfile("search-session-test-b")
    }

    @Test
    fun `Find Teams opens Search over everything on its Teams type`() {
        val route = teamsSearch()
        assertEquals(SearchViewModel.CONTENT_TYPE_ALL, route.contentType)
        assertTrue(route.initialTeamsTab)
        assertNull(route.initialTypeFilter)
    }

    @Test
    fun `a new Search drops the type and pill of the last one`() {
        // A Films section's Search, with its pill.
        SearchSession.arrive(typeFilter = ContentType.MOVIES)
        assertEquals(ContentType.MOVIES, SearchSession.typeFilter)
        assertEquals(SearchTab.CATALOGUE, SearchSession.tab)

        // Then Find Teams: Teams, and no Films pill left.
        SearchSession.arrive(teamsTab = true)
        assertEquals(SearchTab.TEAMS, SearchSession.tab)
        assertNull(SearchSession.typeFilter)

        // Then Search from Home: the catalogue over everything.
        SearchSession.arrive()
        assertEquals(SearchTab.CATALOGUE, SearchSession.tab)
        assertNull(SearchSession.typeFilter)
    }

    @Test
    fun `the Guide type opens when the route asks for it`() {
        SearchSession.arrive(guideTab = true)
        assertEquals(SearchTab.GUIDE, SearchSession.tab)
    }

    @Test
    fun `a new Search keeps the term`() {
        SearchSession.query = "lupin"
        SearchSession.arrive(teamsTab = true)
        assertEquals("lupin", SearchSession.query)
    }
}
