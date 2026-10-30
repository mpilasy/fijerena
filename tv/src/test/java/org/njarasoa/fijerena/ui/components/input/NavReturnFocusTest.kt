package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Back hands focus to the control that was left, and the control keeps it after the hand-back
 * clears the key: its requester stays attached (Home's cards lost focus when it came off).
 */
@RunWith(RobolectricTestRunner::class)
class NavReturnFocusTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the control left takes focus back and keeps it after the key is cleared`() {
        lateinit var returnFocus: NavReturnFocus
        rule.setContent {
            returnFocus = remember { NavReturnFocus("b", -1, 0) }
            Column {
                Box(Modifier.size(10.dp).testTag("a").focusable())
                Box(
                    Modifier
                        .size(10.dp)
                        .navReturnFocusTarget(returnFocus, "b")
                        .testTag("b")
                        .focusable(),
                )
            }
            NavReturnFocusEffect(returnFocus)
        }
        rule.waitForIdle()

        assertNull(returnFocus.key)
        rule.onNodeWithTag("b").assertIsFocused()
    }

    @Test
    fun `the requester is the same for a key, and handed to a child only while pending`() {
        val returnFocus = NavReturnFocus("b", -1, 0)
        val pending = returnFocus.requesterFor("b")

        assertNotNull(pending)
        assertSame(returnFocus.requesterOf("b"), pending)
        assertNull(returnFocus.requesterFor("a"))
    }

    @Test
    fun `discard drops a pending hand-back, the screen opens as new`() {
        val returnFocus = NavReturnFocus("b", 3, 0)

        returnFocus.discard()

        assertNull(returnFocus.key)
        assertFalse(returnFocus.isReturn)
        assertEquals(null, returnFocus.requesterFor("b"))
    }
}
