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
 * Search's Catalogue and Guide tab row (the search guide tab plan); when
 * the tabs show and what a switch runs: core/ui SearchTabsTest.
 */
@RunWith(RobolectricTestRunner::class)
class MobileSearchTabsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
