package org.njarasoa.fijerena.feature.category.components

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/**
 * A row's menu (long-press OK or Menu): focus opens on the first safe action, removals come last
 * (UX-D1), Back closes it without acting, and an action commits and closes it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class FavoriteMenuDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var dismissed = 0
    private var removedFromRecent = 0
    private var favouriteToggled = 0

    private fun setMenu(
        isFavorite: Boolean = false,
        onToggleWatched: (() -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) {
                FavoriteContextMenuDialog(
                    target =
                        FavoriteMenuTarget.Stream(
                            itemId = "1",
                            itemName = "RTP 1 HD",
                            categoryId = "pt",
                            contentType = ContentType.LIVE_TV,
                            isFavorite = isFavorite,
                            isInRecent = true,
                            isWatched = if (onToggleWatched != null) false else null,
                        ),
                    onConfirm = { favouriteToggled++ },
                    onDismiss = {
                        dismissed++
                        open = false
                    },
                    onRemoveFromRecent = { removedFromRecent++ },
                    onToggleWatched = onToggleWatched,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `the menu opens on Add to Favourites, not on Remove from Recent`() {
        setMenu()
        composeTestRule.onNodeWithText("Add to Favourites").assertIsFocused()
    }

    @Test
    fun `a favourite opens on the watched toggle, the removals last above Cancel`() {
        setMenu(isFavorite = true, onToggleWatched = {})
        composeTestRule.onNodeWithText("Mark as watched").assertIsFocused()
        val tops =
            listOf("Mark as watched", "Remove from Favourites", "Remove from Recent", "Cancel").map { text ->
                composeTestRule
                    .onNodeWithText(text)
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `with only removals the menu opens on Cancel`() {
        setMenu(isFavorite = true)
        composeTestRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun `Back closes the menu without acting`() {
        setMenu()
        // The remote's Back goes to the dialog's window, not through Compose's key input.
        val dialog = ShadowDialog.getLatestDialog()
        composeTestRule.runOnUiThread {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeTestRule.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(0, removedFromRecent)
        assertEquals(0, favouriteToggled)
        composeTestRule.onNode(hasText("RTP 1 HD")).assertDoesNotExist()
    }

    @Test
    fun `OK on an action commits it and closes the menu`() {
        setMenu()
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()

        assertEquals(1, favouriteToggled)
        assertEquals(0, removedFromRecent)
        assertEquals(1, dismissed)
        composeTestRule.onNode(hasText("RTP 1 HD")).assertDoesNotExist()
    }
}
