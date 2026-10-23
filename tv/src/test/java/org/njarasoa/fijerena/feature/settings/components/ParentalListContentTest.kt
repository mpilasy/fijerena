package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.robolectric.RobolectricTestRunner

/**
 * A Parental controls list on TV: focus opens on the field, OK on a word removes it with focus on
 * the row that takes its place, and Menu puts the word back at its place, focused again.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ParentalListContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val entries = mutableStateListOf("pokemon", "doraemon", "naruto")
    private var backs = 0
    private var countsLoaded by mutableStateOf(true)

    private fun setList() {
        composeTestRule.setContent {
            ParentalListContent(
                list = ParentalList.EXCEPTIONS,
                entries = entries.toList(),
                countOf = {
                    if (!countsLoaded) {
                        null
                    } else if (it == "naruto") {
                        0
                    } else {
                        3
                    }
                },
                onAdd = { entries.add(it) },
                onRemove = { entries.remove(it) },
                onUndo = { entry, index -> entries.add(index, entry) },
                onReset = {},
                onBack = { backs++ },
            )
        }
        composeTestRule.waitForIdle()
    }

    // Robolectric moves no focus on a D-pad key: the test puts focus on the row itself.
    private fun focus(text: String) {
        composeTestRule.onNodeWithText(text).requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(text).assertIsFocused()
    }

    private fun press(key: Key) {
        composeTestRule.onNode(isFocused()).performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `focus opens on the field`() {
        setList()
        composeTestRule.onNodeWithText("Add a word").assertIsFocused()
    }

    @Test
    fun `each word says what it matches`() {
        setList()
        composeTestRule.onAllNodesWithText("Matches 3", useUnmergedTree = true).assertCountEquals(2)
        composeTestRule.onNodeWithText("Matches nothing on your sources", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `OK on a word removes it and focus goes to the next word`() {
        setList()
        focus("pokemon")

        press(Key.DirectionCenter)

        assertEquals(listOf("doraemon", "naruto"), entries.toList())
        composeTestRule.onNodeWithText("doraemon").assertIsFocused()
        composeTestRule.onNodeWithText("Removed pokemon · Hold OK or press Menu to undo").assertExists()
    }

    @Test
    fun `Menu puts the word back at its place, focused`() {
        setList()
        focus("doraemon")
        press(Key.DirectionCenter)
        assertEquals(listOf("pokemon", "naruto"), entries.toList())

        press(Key.Menu)

        assertEquals(listOf("pokemon", "doraemon", "naruto"), entries.toList())
        composeTestRule.onNodeWithText("doraemon").assertIsFocused()
    }

    @Test
    fun `the last word removed gives focus to the word before it`() {
        setList()
        focus("naruto")

        press(Key.DirectionCenter)

        composeTestRule.onNodeWithText("doraemon").assertIsFocused()
    }

    @Test
    fun `a count that loads keeps focus on its row`() {
        countsLoaded = false
        setList()
        focus("doraemon")

        countsLoaded = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("doraemon").assertIsFocused()
    }

    @Test
    fun `Left leaves the list`() {
        setList()
        press(Key.DirectionLeft)
        assertEquals(1, backs)
    }
}
