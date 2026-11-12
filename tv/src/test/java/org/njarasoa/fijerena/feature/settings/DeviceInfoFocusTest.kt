package org.njarasoa.fijerena.feature.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoAction
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoRow
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoSection
import org.njarasoa.fijerena.core.ui.utils.UiText
import org.njarasoa.fijerena.testing.FocusWalk
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.njarasoa.fijerena.ui.theme.FirstVideoPlayerTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Device Info (the low storage plan → LS-Q3): each section is one focus stop; Right on Storage
 * reaches Clear Image Cache, which sits on the image cache row's line, and Left goes back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w960dp-h540dp")
class DeviceInfoFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

    @Test
    fun `Right on Storage reaches Clear Image Cache on the image cache row`() {
        var acted: DeviceInfoAction? = null
        rule.setContent {
            FirstVideoPlayerTheme {
                DeviceInfoContent(
                    sections = SECTIONS,
                    failed = false,
                    onRefresh = {},
                    onAction = { acted = it },
                    actionFocus = remember { FocusRequester() },
                )
            }
        }

        walk.awaitFocused("Memory")
        walk.run(
            down("Storage"),
            right("Clear image cache"),
            left("Storage"),
            down("Display"),
            up("Storage"),
            right("Clear image cache"),
            up("Memory"),
            down("Storage"),
            right("Clear image cache"),
            down("Display"),
        )
        walk.run(up("Storage"), right("Clear image cache"))
        walk.press(Key.DirectionCenter)
        assertEquals(DeviceInfoAction.CLEAR_IMAGE_CACHE, acted)

        // The icon's middle is on the image cache row, not at the top of the card.
        val row = rule.onNodeWithText("Image cache", useUnmergedTree = true).getBoundsInRoot()
        val icon = rule.onNodeWithContentDescription("Clear image cache").getBoundsInRoot()
        val middle = (icon.top + icon.bottom) / 2
        assertTrue("icon middle $middle, row ${row.top} to ${row.bottom}", middle in row.top..row.bottom)
        assertTrue("icon starts at ${icon.left}, past the label ${row.right}", icon.left > row.right)
    }

    private companion object {
        fun row(
            label: String,
            value: String,
            action: DeviceInfoAction? = null,
        ) = DeviceInfoRow(UiText.DynamicString(label), UiText.DynamicString(value), action)

        val SECTIONS =
            listOf(
                DeviceInfoSection(R.string.device_info_section_memory, listOf(row("RAM", "1.2 GB free of 2.0 GB"))),
                DeviceInfoSection(
                    R.string.device_info_section_storage,
                    listOf(
                        row("Internal storage", "1.1 GB free of 8.0 GB"),
                        row("Database xtream_v2.db", "240 MB"),
                        row("Image cache", "120 MB", DeviceInfoAction.CLEAR_IMAGE_CACHE),
                        row("Caches", "130 MB"),
                    ),
                ),
                DeviceInfoSection(R.string.device_info_section_display, listOf(row("Display mode", "1920×1080 @ 60 Hz"))),
            )
    }
}
