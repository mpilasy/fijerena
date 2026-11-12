package org.njarasoa.fijerena.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Settings → About → Logs on the phone: a tap opens an entry's stack trace to its causes and closes
 * it again; the search field filters the entries as the user types.
 */
@RunWith(RobolectricTestRunner::class)
class MobileDiagnosticsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        CrashLog.install(ApplicationProvider.getApplicationContext<Context>())
        CrashLog.clear()
    }

    @After
    fun tearDown() {
        CrashLog.clear()
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `a tap opens and closes an entry with a stack trace`() {
        CrashLog.record("catalog sync", IOException("sync failed", SocketTimeoutException("timeout")))
        composeTestRule.setContent { MobileDiagnosticsScreen(onBack = {}) }
        composeTestRule.waitUntil(LOAD_TIMEOUT_MS) {
            composeTestRule.onAllNodes(hasText("SocketTimeoutException: timeout")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("SocketTimeoutException: timeout").performClick()
        composeTestRule.onNodeWithText("IOException: sync failed\nSocketTimeoutException: timeout").assertExists()

        composeTestRule.onNodeWithText("IOException: sync failed\nSocketTimeoutException: timeout").performClick()
        composeTestRule.onNodeWithText("SocketTimeoutException: timeout").assertExists()
    }

    @Test
    fun `typing in the search field filters the entries`() {
        CrashLog.record("catalog sync", IOException("sync failed", SocketTimeoutException("timeout")))
        CrashLog.record("guide refresh", IllegalStateException("bad xml"))
        composeTestRule.setContent { MobileDiagnosticsScreen(onBack = {}) }
        composeTestRule.waitUntil(LOAD_TIMEOUT_MS) {
            composeTestRule.onAllNodes(hasText("guide refresh", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("catalog sync", substring = true)).assertExists()

        // A cause matches, case ignored.
        composeTestRule.onNode(hasSetTextAction()).performTextInput("SOCKETTIMEOUT")
        composeTestRule.onNode(hasText("catalog sync", substring = true)).assertExists()
        composeTestRule.onNode(hasText("guide refresh", substring = true)).assertDoesNotExist()

        // Nothing matches: the search's no-results state.
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("no such text")
        composeTestRule.onNode(hasText("No results found for \"no such text\"", substring = true)).assertExists()
        composeTestRule.onNode(hasText("catalog sync", substring = true)).assertDoesNotExist()

        // An empty field shows every entry again.
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasText("catalog sync", substring = true)).assertExists()
        composeTestRule.onNode(hasText("guide refresh", substring = true)).assertExists()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
