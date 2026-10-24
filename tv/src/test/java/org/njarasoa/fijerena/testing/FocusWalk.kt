package org.njarasoa.fijerena.testing

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.fail

/** One key press and the text the focused control must then start with. */
data class FocusStep(
    val key: Key,
    val expected: String,
)

/**
 * A focus walk in a Compose test, the same idea as `scripts/focus-walks/`: each step presses a key
 * on the focused control and checks the text of the control that has focus after it.
 */
@OptIn(ExperimentalTestApi::class)
class FocusWalk(
    private val rule: ComposeTestRule,
) {
    /** The focused control's text, read as `scripts/tv-focus-walk.sh` reads it. */
    fun focusedText(): String {
        rule.waitForIdle()
        return readFocusedText()
    }

    private fun readFocusedText(): String {
        val nodes = rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
        // A dialog is a window of its own, above the screen, and the screen's control keeps its
        // focused state under it: the last focused node is the one in the top window.
        val node = nodes.lastOrNull() ?: return NOTHING_FOCUSED
        val parts = mutableListOf<String>()
        collectText(node, parts)
        return if (parts.isEmpty()) "(no text)" else parts.take(4).joinToString(" / ")
    }

    /** Fails unless the focused control's text starts with [expected]. */
    fun assertFocused(
        expected: String,
        where: String = "start",
    ) {
        val actual = focusedText()
        if (!actual.startsWith(expected)) {
            fail("Focus walk, $where: expected focus on \"$expected\", but it is on \"$actual\"")
        }
    }

    /**
     * Waits until focus is on [expected], for a screen whose first focus waits for data that loads
     * off the main thread (a list from the database), then fails as [assertFocused] if it never is.
     */
    fun awaitFocused(
        expected: String,
        timeoutMs: Long = FIRST_FOCUS_TIMEOUT_MS,
    ) {
        try {
            rule.waitUntil(timeoutMs) { readFocusedText().startsWith(expected) }
        } catch (_: ComposeTimeoutException) {
            // assertFocused names what has focus instead.
        }
        assertFocused(expected)
    }

    fun press(key: Key) {
        rule.onAllNodes(isFocused(), useUnmergedTree = true).onLast().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    /** Runs [steps] in order and stops at the first mismatch, naming its step and key. */
    fun run(vararg steps: FocusStep) {
        steps.forEachIndexed { index, step ->
            press(step.key)
            assertFocused(step.expected, "step ${index + 1} (${keyName(step.key)})")
        }
    }

    private fun collectText(
        node: SemanticsNode,
        parts: MutableList<String>,
    ) {
        val config = node.config
        val texts =
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text) +
                config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        texts.map { it.trim() }.filter { it.isNotEmpty() && it !in parts }.forEach { parts += it }
        node.children.forEach { collectText(it, parts) }
    }

    private fun keyName(key: Key): String =
        when (key) {
            Key.DirectionUp -> "UP"
            Key.DirectionDown -> "DOWN"
            Key.DirectionLeft -> "LEFT"
            Key.DirectionRight -> "RIGHT"
            Key.DirectionCenter -> "CENTER"
            Key.Back -> "BACK"
            else -> key.toString()
        }

    companion object {
        const val NOTHING_FOCUSED = "(nothing focused)"
        private const val FIRST_FOCUS_TIMEOUT_MS = 5_000L
    }
}

fun up(expected: String) = FocusStep(Key.DirectionUp, expected)

fun down(expected: String) = FocusStep(Key.DirectionDown, expected)

fun left(expected: String) = FocusStep(Key.DirectionLeft, expected)

fun right(expected: String) = FocusStep(Key.DirectionRight, expected)

fun center(expected: String) = FocusStep(Key.DirectionCenter, expected)

fun back(expected: String) = FocusStep(Key.Back, expected)
