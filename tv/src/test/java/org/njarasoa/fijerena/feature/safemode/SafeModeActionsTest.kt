package org.njarasoa.fijerena.feature.safemode

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV crash-loop safe mode: the round send icon action is taller than the text buttons, and sat
 * lower than them. All four controls are centred on one line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+en")
class SafeModeActionsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the send icon action is centred on the text buttons`() {
        rule.setContent {
            FirstVideoPlayerTheme {
                SafeModeActions(canSendLogs = true, onContinue = {}, onClearCaches = {}, onShowLogs = {}, onSendLogs = {})
            }
        }
        val icon = rule.onNodeWithContentDescription("Send logs to my sync group").getBoundsInRoot()
        val iconMiddle = (icon.top + icon.bottom) / 2
        listOf("Continue", "Clear caches", "Show logs").forEach { text ->
            val button = rule.onNodeWithText(text).getBoundsInRoot()
            val middle = (button.top + button.bottom) / 2
            assertTrue("\"$text\" middle $middle, icon middle $iconMiddle", (middle - iconMiddle).value in -1f..1f)
        }
    }
}
