package org.njarasoa.fijerena.feature.person

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner

/**
 * A person's appearances in a show over the TV person page (episode cast plan, EC-D2): focus on the
 * first row, Down to the next, the row the source lacks marked, OK opens the row, Back closes.
 */
@RunWith(RobolectricTestRunner::class)
class ShowAppearancesOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    private val lines =
        listOf(
            ShowAppearance(9, 17, "Authority", "e917"),
            ShowAppearance(10, 2, "Later", null, inSource = false),
        )

    @Test
    fun `the first row has focus, Down moves to the next, OK opens it and Back closes`() {
        val selected = mutableListOf<ShowAppearance>()
        var closed = 0
        rule.setContent {
            FirstVideoPlayerTheme {
                ShowAppearancesOverlay(
                    title = "Law & Order: Special Victims Unit",
                    subtitle = "Robin Williams",
                    lines = lines,
                    returnFocus = rememberNavReturnFocus(),
                    onSelect = { selected += it },
                    onClose = { closed++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("S9E17 · Authority").assertIsFocused()
        rule.onNodeWithText("Not in your source").assertExists()

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithText("S10E2 · Later").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(listOf(lines[1]), selected)

        // Up from the first row stays in the list: the overlay keeps the focus.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.waitForIdle()
        rule.onNodeWithText("S9E17 · Authority").assertIsFocused()

        rule.onRoot().performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()
        assertEquals(1, closed)
    }
}
