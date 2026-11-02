package org.njarasoa.fijerena.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.robolectric.RobolectricTestRunner

/**
 * The TV action menu: focus opens on the first safe action, never on a removal (rule 7); with only
 * removals, on Cancel; the removals come last, and Down from the last row reaches Cancel.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class TvActionMenuFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // The icons are read in composition (they follow the Look).
    private fun setMenu(vararg names: String) {
        composeTestRule.setContent {
            val remove = MenuAction(CinemaIcons.Delete, "Remove from Recent", removal = true) {}
            val mark = MenuAction(CinemaIcons.CheckCircle, "Mark as watched") {}
            TvActionMenu(
                title = "RTP 1 HD",
                actions = names.map { if (it == REMOVE) remove else mark },
                onDismiss = {},
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `the menu opens on the first safe action, with the removal given first`() {
        setMenu(REMOVE, MARK)
        composeTestRule.onNodeWithText("Mark as watched").assertIsFocused()
        val tops =
            listOf("Mark as watched", "Remove from Recent", "Cancel").map { text ->
                composeTestRule
                    .onNodeWithText(text)
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `with only removals the menu opens on Cancel`() {
        setMenu(REMOVE)
        composeTestRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun `Down from the last row reaches Cancel`() {
        setMenu(MARK, REMOVE)
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Remove from Recent").assertIsFocused()
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Cancel").assertIsFocused()
    }

    private companion object {
        const val REMOVE = "remove"
        const val MARK = "mark"
    }
}
