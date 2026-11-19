package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The open field of [ReadOnlyFieldWithEdit] gives its text when focus leaves it, not only on Enter:
 * the TV Move dialog's Check button read an empty address after Tab out of a typed field.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ReadOnlyFieldWithEditTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf("")
    private val checkFocus = FocusRequester()

    private fun setContent() {
        rule.setContent {
            FirstVideoPlayerTheme {
                Column {
                    ReadOnlyFieldWithEdit(value = value, onValueChange = { value = it }, label = "Address")
                    CinemaSecondaryButton(
                        onClick = {},
                        text = "Check",
                        enabled = value.isNotBlank(),
                        modifier = Modifier.focusRequester(checkFocus),
                    )
                }
            }
        }
        openField()
        rule.onNode(hasSetTextAction()).performTextInput("https://new.test")
    }

    /** OK on the row opens the field (a TV row takes keys, not taps). */
    private fun openField() {
        rule.onNodeWithContentDescription("Edit Address").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
    }

    @Test
    fun `focus that leaves the open field gives its text`() {
        setContent()
        rule.onNodeWithText("Check").assertIsNotEnabled()

        rule.runOnIdle { checkFocus.requestFocus() }

        rule.runOnIdle { assertEquals("https://new.test", value) }
        rule.onNodeWithText("Check").assertIsEnabled()
    }

    @Test
    fun `Enter gives the text, Back drops it`() {
        setContent()
        rule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals("https://new.test", value) }

        openField()
        rule.onNode(hasSetTextAction()).performTextInput("/more")
        rule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Back) }

        rule.runOnIdle { assertEquals("https://new.test", value) }
    }
}
