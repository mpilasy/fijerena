package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** TV Add Profile: the Nerd switch shows for an Admin only, and not for a child's profile (the Nerd role plan). */
@RunWith(RobolectricTestRunner::class)
class ProfileAddDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(nerdSwitch: Boolean) {
        composeTestRule.setContent {
            ProfileAddDialog(
                initialColorIndex = 0,
                onSave = { _, _, _, _ -> },
                onDismiss = {},
                scale = 1f,
                nerdSwitch = nerdSwitch,
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `an Admin gets the Nerd switch, gone when Child is on`() {
        show(nerdSwitch = true)

        composeTestRule.onNodeWithText("Nerd").assertExists()
        composeTestRule.onNodeWithText("Child Profile").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Nerd").assertDoesNotExist()
    }

    @Test
    fun `a grown-up without Admin gets no Nerd switch`() {
        show(nerdSwitch = false)

        composeTestRule.onNodeWithText("Child Profile").assertExists()
        composeTestRule.onNodeWithText("Nerd").assertDoesNotExist()
    }
}
