package org.njarasoa.fijerena.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.robolectric.RobolectricTestRunner

/** Home's placeholder rows on the first show: until the rows are read, or until the wait's cap. */
@RunWith(RobolectricTestRunner::class)
class HomePlaceholderRowsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var ready by mutableStateOf(false)

    private fun setHome() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CinemaText(text = if (rememberHomeRowsShown(ready)) "rows" else "placeholder")
        }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the placeholder shows until the rows are read`() {
        setHome()
        composeTestRule.onNodeWithText("placeholder").assertIsDisplayed()

        ready = true
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeBy(100)

        composeTestRule.onNodeWithText("rows").assertIsDisplayed()
    }

    @Test
    fun `the rows show after the cap when one row is slow`() {
        setHome()
        composeTestRule.mainClock.advanceTimeBy(HOME_ROWS_WAIT_MS - 100)
        composeTestRule.onNodeWithText("placeholder").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(200)

        composeTestRule.onNodeWithText("rows").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("placeholder").assertCountEquals(0)
    }

    @Test
    fun `the placeholder rows take no tap and no focus`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { HomePlaceholderRows(PaddingValues()) }
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeTestRule.onAllNodes(isFocusable()).assertCountEquals(0)
    }
}
