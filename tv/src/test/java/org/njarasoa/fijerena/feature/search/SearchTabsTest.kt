package org.njarasoa.fijerena.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel

/** Search's Catalogue | Guide tabs: when the Guide tab shows (O1), and what a tab switch runs (D3). */
class SearchTabsTest {
    @Test
    fun theGuideTabShowsForAllAndLiveTvWithAGuide() {
        assertTrue(showsGuideTab(SearchViewModel.CONTENT_TYPE_ALL, hasGuide = true))
        assertTrue(showsGuideTab(ContentType.LIVE_TV, hasGuide = true))
    }

    @Test
    fun noGuideTabWithoutLiveChannels() {
        assertFalse(showsGuideTab(SearchViewModel.CONTENT_TYPE_ALL, hasGuide = false))
        assertFalse(showsGuideTab(ContentType.LIVE_TV, hasGuide = false))
    }

    @Test
    fun noGuideTabOnAFilmsOrShowsSearch() {
        assertFalse(showsGuideTab(ContentType.MOVIES, hasGuide = true))
        assertFalse(showsGuideTab(ContentType.TV_SHOWS, hasGuide = true))
    }

    @Test
    fun aTabRunsTheFieldsTextWhenItLastRanSomethingElse() {
        assertEquals("news", queryToRerun("news", lastRun = "sport"))
        assertEquals("news", queryToRerun("news", lastRun = null))
    }

    @Test
    fun nothingRunsWhenTheTabAlreadyRanIt() {
        assertNull(queryToRerun("news", lastRun = "news"))
        assertNull(queryToRerun("news ", lastRun = "news"))
    }

    @Test
    fun nothingRunsForABlankField() {
        assertNull(queryToRerun("", lastRun = "news"))
        assertNull(queryToRerun("  ", lastRun = null))
    }
}
