package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoAction
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoRow
import org.njarasoa.fijerena.core.ui.utils.UiText
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Device Info rows on the phone: a row with an action lines up with the others (the low storage plan, LS-Q3). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MobileDeviceInfoRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun row(
        label: String,
        value: String,
        action: DeviceInfoAction? = null,
    ) = DeviceInfoRow(UiText.DynamicString(label), UiText.DynamicString(value), action)

    @Test
    fun `the image cache value starts where the other values start, with the icon at the end`() {
        composeTestRule.setContent {
            Column {
                DeviceInfoRowLine(row("Database xtream_v2.db", "126.2 MB"), onAction = {})
                DeviceInfoRowLine(row("Image cache", "194.6 KB", DeviceInfoAction.CLEAR_IMAGE_CACHE), onAction = {})
            }
        }

        val other = composeTestRule.onNodeWithText("126.2 MB").getBoundsInRoot()
        val cache = composeTestRule.onNodeWithText("194.6 KB").getBoundsInRoot()
        val icon = composeTestRule.onNode(hasContentDescription("Clear image cache"))
        icon.assertIsDisplayed()
        assertEquals(other.left, cache.left)
        // The icon's 40 dp button sits in its 48 dp touch target, at the row's end.
        val gap = other.right - icon.getBoundsInRoot().right
        assertTrue("icon $gap from the end", gap >= 0.dp && gap <= 4.dp)
    }
}
