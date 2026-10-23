package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * Search's Catalogue and Guide tabs on TV and phone (the search guide tab plan):
 * the Guide tab shows only over live channels with a guide (O1), and a tab switch runs the field's
 * query on the new tab only when that tab last ran another (D3).
 */
class SearchTabsTest {
    @Test
    fun `no Guide tab without a source with live channels`() {
        assertFalse(showsGuideTab(SearchViewModel.CONTENT_TYPE_ALL, hasGuide = false))
        assertFalse(showsGuideTab(ContentType.LIVE_TV, hasGuide = false))
    }

    @Test
    fun `the Guide tab shows over everything or live channels, not films or shows`() {
        assertTrue(showsGuideTab(SearchViewModel.CONTENT_TYPE_ALL, hasGuide = true))
        assertTrue(showsGuideTab(ContentType.LIVE_TV, hasGuide = true))
        assertFalse(showsGuideTab(ContentType.MOVIES, hasGuide = true))
        assertFalse(showsGuideTab(ContentType.TV_SHOWS, hasGuide = true))
    }

    @Test
    fun `a tab switch runs the text when the tab last ran another query or none`() {
        assertEquals("news", queryToRunOnTabSwitch("news", lastRunQuery = "sport"))
        assertEquals("news", queryToRunOnTabSwitch("news", lastRunQuery = null))
    }

    @Test
    fun `a tab switch runs nothing for the same query or a blank field`() {
        assertNull(queryToRunOnTabSwitch("news", lastRunQuery = "news"))
        assertNull(queryToRunOnTabSwitch("news ", lastRunQuery = "news"))
        assertNull(queryToRunOnTabSwitch("", lastRunQuery = "news"))
        assertNull(queryToRunOnTabSwitch("  ", lastRunQuery = null))
    }

    @Test
    fun `the guide's last query is the one it ran or holds for its index`() {
        val results =
            EpgBrowserViewModel.UiState.Results(
                query = "news",
                dateGroups = emptyList(),
                totalPrograms = 0,
                totalAirings = 0,
                truncated = false,
                searchTimeMs = 0,
            )
        assertEquals("news", results.lastRunQuery())
        assertEquals("sport", EpgBrowserViewModel.UiState.IndexBusy(query = "sport", refreshing = true).lastRunQuery())
        assertNull(EpgBrowserViewModel.UiState.Idle.lastRunQuery())
    }
}
