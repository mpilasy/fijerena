package org.njarasoa.fijerena.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bottom bar names its tabs on a wide screen and shows icons only under 380 dp. */
@RunWith(RobolectricTestRunner::class)
class MobileBottomBarLabelsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tabs = listOf(MobileTab.LIVE_TV, MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SEARCH, MobileTab.SETTINGS)

    private fun setBar() {
        composeTestRule.setContent { MobileBottomBar(tabs = tabs, selected = MobileTab.LIVE_TV, onSelect = {}) }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a wide phone shows each tab's name`() {
        setBar()
        composeTestRule.onAllNodesWithText("Movies").assertCountEquals(1)
        composeTestRule.onAllNodesWithContentDescription("Movies").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp")
    fun `a compact phone shows icons only, each named for screen readers`() {
        setBar()
        composeTestRule.onAllNodesWithText("Movies").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Movies").assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp")
    fun `a long-press on an icon shows its name`() {
        setBar()
        composeTestRule.onNodeWithContentDescription("Movies").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Movies").assertCountEquals(1)
    }
}
