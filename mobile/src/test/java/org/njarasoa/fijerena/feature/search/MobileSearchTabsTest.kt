package org.njarasoa.fijerena.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.viewmodels.SearchTab
import org.robolectric.RobolectricTestRunner

/**
 * Search's Catalogue, People and Guide type row (the search guide tab plan, the people search
 * plan); when the types show and what a switch runs: core/ui SearchTabsTest.
 */
@RunWith(RobolectricTestRunner::class)
class MobileSearchTabsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping Guide selects it`() {
        var selected: SearchTab? = null
        composeTestRule.setContent {
            MobileSearchTabs(
                tabs = listOf(SearchTab.CATALOGUE, SearchTab.GUIDE),
                selected = SearchTab.CATALOGUE,
                onSelect = { selected = it },
            )
        }

        composeTestRule.onNodeWithText("Catalogue").assertExists()
        composeTestRule.onNodeWithText("Guide").performClick()

        assertEquals(SearchTab.GUIDE, selected)
    }

    @Test
    fun `People shows between Catalogue and Guide, and tapping it selects it`() {
        var selected: SearchTab? = null
        composeTestRule.setContent {
            MobileSearchTabs(tabs = SearchTab.entries, selected = SearchTab.GUIDE, onSelect = { selected = it })
        }

        composeTestRule.onNodeWithText("People").performClick()

        assertEquals(SearchTab.PEOPLE, selected)
    }
}
