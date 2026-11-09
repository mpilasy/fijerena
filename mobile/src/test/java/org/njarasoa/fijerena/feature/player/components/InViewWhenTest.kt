package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A list taller than its dialog opens with the selected row in view. */
@RunWith(RobolectricTestRunner::class)
class InViewWhenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(selectedRow: Int?) {
        composeTestRule.setContent {
            Box(Modifier.height(150.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (0 until 5).forEach { row ->
                        Text("Row $row", inViewWhen(row == selectedRow).height(100.dp))
                    }
                }
            }
        }
    }

    @Test
    fun `a selected row below the visible part comes into view`() {
        show(selectedRow = 4)

        composeTestRule.onNodeWithText("Row 4").assertIsDisplayed()
    }

    @Test
    fun `with no selected row the list stays at its start`() {
        show(selectedRow = null)

        composeTestRule.onNodeWithText("Row 0").assertIsDisplayed()
    }
}
