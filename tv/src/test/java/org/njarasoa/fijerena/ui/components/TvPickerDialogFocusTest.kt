package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.robolectric.RobolectricTestRunner

/**
 * The TV picker, one `TvRadioRow` for each option: focus opens on the current value (rule 5), each
 * row is one focus stop (rule 1), Down from the last option reaches Cancel, and OK picks the
 * focused option and closes the picker.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class TvPickerDialogFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var picked: String? = null

    private fun setPicker(selected: String) {
        composeTestRule.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) {
                TvPickerDialog(
                    title = "Language",
                    options = listOf("English" to "en", "Français" to "fr", "Malagasy" to "mg"),
                    selected = selected,
                    onSelect = { picked = it },
                    onDismiss = { open = false },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `the picker opens on the current value`() {
        setPicker("fr")
        composeTestRule.onNodeWithText("Français").assertIsFocused()
    }

    @Test
    fun `Down moves one row at a time and reaches Cancel`() {
        setPicker("en")
        press(Key.DirectionDown)
        composeTestRule.onNodeWithText("Français").assertIsFocused()
        press(Key.DirectionDown)
        composeTestRule.onNodeWithText("Malagasy").assertIsFocused()
        press(Key.DirectionDown)
        composeTestRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun `OK picks the focused option`() {
        setPicker("en")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals("fr", picked)
    }
}
