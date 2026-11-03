package org.njarasoa.fijerena.feature.settings

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog
import org.njarasoa.fijerena.testing.FocusWalk
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * TV Settings → About → Logs: an entry with a stack trace shows only its root cause, OK opens it to
 * the chain of causes and closes it again, and focus stays on the entry. The search field is the
 * stop above the first entry: Up from the entry reaches it, Down from it returns to the entry.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val walk = FocusWalk(rule)

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
    fun `OK opens and closes an entry with a stack trace, focus stays on it`() {
        CrashLog.record("catalog sync", IOException("sync failed", SocketTimeoutException("timeout")))
        rule.setContent { DiagnosticsScreen() }
        rule.waitUntil(FOCUS_TIMEOUT_MS) { walk.focusedText().contains("catalog sync") }

        val closed = walk.focusedText()
        assertTrue(closed, closed.contains("SocketTimeoutException: timeout"))
        assertTrue(closed, closed.contains("Show details"))
        assertFalse(closed, closed.contains("IOException: sync failed"))

        walk.press(Key.DirectionCenter)
        val opened = walk.focusedText()
        assertTrue(opened, opened.contains("catalog sync"))
        assertTrue(opened, opened.contains("IOException: sync failed\nSocketTimeoutException: timeout"))
        assertTrue(opened, opened.contains("Hide details"))
        assertFalse(opened, opened.contains("\tat "))

        walk.press(Key.DirectionCenter)
        val closedAgain = walk.focusedText()
        assertTrue(closedAgain, closedAgain.contains("catalog sync"))
        assertTrue(closedAgain, closedAgain.contains("Show details"))
    }

    @Test
    fun `Up from the first entry reaches the search field, Down returns, typing filters`() {
        CrashLog.record("catalog sync", IOException("sync failed", SocketTimeoutException("timeout")))
        CrashLog.record("guide refresh", IllegalStateException("bad xml"))
        rule.setContent { DiagnosticsScreen() }
        rule.waitUntil(FOCUS_TIMEOUT_MS) { walk.focusedText().contains(" · ") }
        val first = walk.focusedText()

        walk.press(Key.DirectionUp)
        assertTrue(walk.focusedText(), walk.focusedText().startsWith("Search"))
        walk.press(Key.DirectionDown)
        assertTrue(walk.focusedText(), walk.focusedText() == first)
        walk.press(Key.DirectionUp)

        // OK opens the field; what is typed filters the entries at once.
        walk.press(Key.DirectionCenter)
        rule.onNode(hasSetTextAction()).performTextInput("BAD XML")
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
        walk.press(Key.Back)
        val field = walk.focusedText()
        assertTrue(field, field.contains("BAD XML"))

        walk.press(Key.DirectionDown)
        val match = walk.focusedText()
        assertTrue(match, match.contains("guide refresh"))
        walk.press(Key.DirectionDown)
        assertTrue(walk.focusedText(), walk.focusedText() == match)
        walk.press(Key.DirectionUp)
        assertTrue(walk.focusedText(), walk.focusedText().contains("BAD XML"))
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 5_000L
    }
}
