package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.feature.contentselection.components.HomeRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.robolectric.RobolectricTestRunner

/**
 * A card of a row that never had focus, asked for focus from outside the row (the person page's
 * show card when its appearances list closes, after Back from a show): the row's restorer is told
 * of that card, so the request lands on it, not on the row's first card.
 */
@RunWith(RobolectricTestRunner::class)
class HomeRowRestoreFocusTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a card the restorer is told of takes the focus asked for it`() {
        setRow(toldOfCard = true)

        rule.onNodeWithTag("b").assertIsFocused()
    }

    @Test
    fun `without it the restorer takes the request to the first card`() {
        setRow(toldOfCard = false)

        rule.onNodeWithTag("a").assertIsFocused()
    }

    private fun setRow(toldOfCard: Boolean) {
        rule.setContent {
            val first = remember { FocusRequester() }
            val outside = remember { FocusRequester() }
            val cardB = remember { FocusRequester() }
            Column {
                Box(
                    Modifier
                        .size(10.dp)
                        .focusRequester(outside)
                        .testTag("outside")
                        .focusable(),
                )
                HomeRow(
                    title = "Shows",
                    items = listOf("a", "b", "c"),
                    key = { it },
                    listState = rememberLazyListState(),
                    firstItemFocus = first,
                    restoreFocus = if (toldOfCard) cardB else null,
                ) { item, rowModifier ->
                    Box(
                        Modifier
                            .then(if (item == "b") Modifier.focusRequester(cardB) else Modifier)
                            .then(rowModifier)
                            .size(10.dp)
                            .testTag(item)
                            .focusable(),
                    )
                }
            }
            LaunchedEffect(Unit) {
                outside.requestFocusWithRetry()
                cardB.requestFocusWithRetry(fallback = first)
            }
        }
        rule.waitForIdle()
    }
}
