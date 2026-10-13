package org.njarasoa.fijerena.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel
import org.robolectric.RobolectricTestRunner

/**
 * Search's Catalogue and Guide tabs (docs/plans/archive/20261013_search-guide-tab-plan.md): the Guide tab
 * shows only over live channels with a guide (O1), and a tab switch runs the field's query on the
 * new tab only when that tab last ran another (D3).
 */
@RunWith(RobolectricTestRunner::class)
class MobileSearchTabsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun `tapping Guide selects it`() {
        var selected: SearchTab? = null
        composeTestRule.setContent {
            MobileSearchTabs(selected = SearchTab.CATALOGUE, onSelect = { selected = it })
        }

        composeTestRule.onNodeWithText("Catalogue").assertExists()
        composeTestRule.onNodeWithText("Guide").performClick()

        assertEquals(SearchTab.GUIDE, selected)
    }
}
