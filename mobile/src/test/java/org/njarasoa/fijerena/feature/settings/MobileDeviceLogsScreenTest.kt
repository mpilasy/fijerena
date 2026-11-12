package org.njarasoa.fijerena.feature.settings

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.DeviceReport
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceLogsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Live Sync → a device's Logs on the phone (the device diagnostics sync plan). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MobileDeviceLogsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val report =
        DeviceReport(
            sentAt = NOW,
            versionName = "0.9.20261111000000",
            safeModeAt = NOW - DAY,
            counts7d = DeviceReport.Counts(crashes = 1, anrs = 1),
            failedCalls24h = 37,
            entries =
                listOf(
                    DeviceReport.Entry(
                        NOW - 1_000,
                        DeviceReport.KIND_CRASH,
                        "uncaught on main",
                        "IllegalStateException: boom",
                        "",
                        "Settings",
                        true,
                    ),
                    DeviceReport.Entry(NOW - 2 * DAY, DeviceReport.KIND_ANR, "exit ANR", "Input dispatching timed out"),
                ),
        )

    @Test
    fun `the page shows the header, every entry, and Copy and Share`() {
        composeTestRule.setContent {
            MobileDeviceLogsContent(
                DeviceLogsViewModel.State("Living room TV", DeviceBuild(versionName = "0.9.20261111000000"), report),
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("Device Logs").assertExists()
        composeTestRule.onNodeWithText("Living room TV").assertExists()
        composeTestRule.onNode(hasText("Sent ", substring = true)).assertExists()
        composeTestRule.onNodeWithText("1 crash, 1 ANR in 7 days").assertExists()
        composeTestRule.onNodeWithText("Failed server calls (24 h): 37").assertExists()
        composeTestRule.onNode(hasText("Started in safe mode ", substring = true)).assertExists()
        composeTestRule.onNode(hasText("· Crash", substring = true)).assertExists()
        composeTestRule.onNodeWithText("IllegalStateException: boom").assertExists()
        composeTestRule.onNodeWithText("This build · Screen: Settings").assertExists()
        // The last entry is under the fold of the test screen.
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Before the last update"))
        composeTestRule.onNodeWithText("Before the last update").assertExists()
        composeTestRule.onNode(hasContentDescription("Copy")).assertExists()
        composeTestRule.onNode(hasContentDescription("Share")).assertExists()
    }

    @Test
    fun `a report with no entries says so`() {
        composeTestRule.setContent {
            MobileDeviceLogsContent(
                DeviceLogsViewModel.State(
                    "Phone",
                    report = report.copy(counts7d = DeviceReport.Counts(), entries = emptyList()),
                ),
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("No problems in 7 days").assertExists()
        composeTestRule.onNodeWithText("No problems reported").assertExists()
    }

    @Test
    fun `a device that sent no report has nothing to copy or share`() {
        composeTestRule.setContent { MobileDeviceLogsContent(DeviceLogsViewModel.State("Old TV"), onBack = {}) }

        composeTestRule.onNodeWithText("Logs not reported yet").assertExists()
        composeTestRule.onNode(hasContentDescription("Copy")).assertDoesNotExist()
        composeTestRule.onNode(hasContentDescription("Share")).assertDoesNotExist()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val NOW = 1_800_000_000_000L
    }
}
