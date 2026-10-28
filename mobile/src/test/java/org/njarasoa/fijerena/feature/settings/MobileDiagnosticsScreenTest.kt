package org.njarasoa.fijerena.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Settings → About → Logs on the phone: a tap opens an entry's stack trace to its causes and closes it again. */
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

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
