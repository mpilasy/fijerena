package org.njarasoa.fijerena.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.provider.ParentalControlsRepository.CheckedCategory
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.robolectric.RobolectricTestRunner

/**
 * Settings › Parental controls on the phone (docs/plans/20261020_parental-controls-plan.md → The
 * page): a list's words with their counts and Undo, and the check view. Settings' entry is an
 * Expert group's row (SettingsExpertGroupTest).
 */
@RunWith(RobolectricTestRunner::class)
class ParentalControlsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a word shows its match count, nothing while loading`() {
        val counts = mapOf("kids" to 3, "famille" to 0)
        showList(listOf("kids", "famille", "barn*"), counts)

        composeTestRule.onNodeWithText("Matches 3").assertExists()
        composeTestRule.onNodeWithText("Matches nothing on your sources").assertExists()
        composeTestRule.onNodeWithText("barn*").assertExists()
    }

    @Test
    fun `remove takes the word out and Undo puts it back at the same place`() {
        var undone: Pair<String, Int>? = null
        showList(listOf("kids", "famille", "barn*"), emptyMap(), onUndo = { entry, index -> undone = entry to index })

        composeTestRule.onNodeWithContentDescription("Remove famille").performClick()
        composeTestRule.onNodeWithText("famille").assertDoesNotExist()
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitForIdle()

        assertEquals("famille" to 1, undone)
    }

    @Test
    fun `reset asks first`() {
        var reset = false
        showList(listOf("kids"), emptyMap(), onReset = { reset = true })

        composeTestRule.onNodeWithContentDescription("Reset to the app's list").performClick()
        assertEquals(false, reset)
        composeTestRule.onNodeWithText("Replace this list with the app's words? Your changes to this list are lost.").assertExists()
    }

    @Test
    fun `the check view says when a child sees no category`() {
        composeTestRule.setContent {
            ParentalCheckContent(listOf(CheckedCategory("Adults", "VOD", shown = false)))
        }

        composeTestRule.onNodeWithText("Shown (0)").assertExists()
        composeTestRule.onNodeWithText("Hidden (1)").assertExists()
        composeTestRule.onNodeWithText("Adults").assertExists()
        composeTestRule
            .onNodeWithText("A child sees no category on this source. Add the words of its kids' categories to Kids' Categories.")
            .assertExists()
    }

    private fun showList(
        initial: List<String>,
        counts: Map<String, Int>,
        onUndo: (String, Int) -> Unit = { _, _ -> },
        onReset: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            var entries by remember { mutableStateOf(initial) }
            ParentalListContent(
                list = ParentalList.KIDS_CATEGORIES,
                entries = entries,
                countOf = { counts[it] },
                onAdd = { entries = entries + it },
                onRemove = { entries = entries - it },
                onUndo = onUndo,
                onReset = onReset,
                onBack = {},
            )
        }
    }
}
