@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * One titled row of cards on Home (TV home overhaul plan, Phase 3). Focus coming in from above or
 * below lands on the card this row had last ([focusRestorer]), else on its first card
 * ([firstItemFocus]); Left on the first card goes to the navigation rail and Right on the last
 * stays put, so the search never falls out of the row onto the header or another row.
 *
 * [restoreFocus]: the card Back returns to, while that hand-back is pending and the card is in this
 * row (`NavReturnFocus.requesterWhere`). Back rebuilds Home, so the restorer remembers no card, and
 * its fallback took the hand-back from the card to the row's first card.
 *
 * [leading]: one more item before the cards (a Trending chart row's switch), the row's first. It
 * takes the rail's Left, and Left on the first card goes to it; focus coming into the row still
 * lands on the first card. With it, the row shows even with no card, and it takes the row's focus.
 *
 * Focus coming into the row brings the whole row into view, its title too: the card's own request
 * scrolled Home only as far as the card, and the title stayed under the header.
 */
@Composable
fun <T> HomeRow(
    title: String,
    items: List<T>,
    key: (T) -> String,
    listState: LazyListState,
    firstItemFocus: FocusRequester,
    modifier: Modifier = Modifier,
    restoreFocus: FocusRequester? = null,
    leading: (@Composable (modifier: Modifier) -> Unit)? = null,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    if (items.isEmpty() && leading == null) return
    val leadingFocus = remember { FocusRequester() }
    val offset = if (leading != null) 1 else 0
    val lastIndex = items.size - 1 + offset
    // [index]: the place in the row, the leading item included.
    val placed: (Int) -> Modifier = { index ->
        // The row's focus: its first card, else the leading item alone.
        val takesRowFocus = index == offset || (index == 0 && items.isEmpty())
        Modifier
            .then(if (takesRowFocus) Modifier.focusRequester(firstItemFocus) else Modifier)
            .then(if (index == 0 && leading != null) Modifier.focusRequester(leadingFocus) else Modifier)
            .then(if (index == 0) Modifier.leftToRail() else Modifier)
            // leftToRail is outside these, so it wins while the rail shows; without
            // one, Left on the first item stays put.
            .focusProperties {
                if (index == 0) left = FocusRequester.Cancel
                if (index == 1 && leading != null) left = leadingFocus
                if (index == lastIndex) right = FocusRequester.Cancel
            }
    }
    val rowInView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            Modifier
                .bringIntoViewRequester(rowInView)
                .then(modifier)
                .onFocusChanged { if (it.hasFocus) scope.launch { rowInView.bringIntoView() } },
    ) {
        CinemaText(
            text = title,
            // The TV section-title style (TV UI audit #2): the same on every Home row and on Search's groups.
            role = TextRole.Title,
            tone = TextTone.Primary,
            modifier = Modifier.padding(bottom = Spacing.sm, start = Spacing.xxs),
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(horizontal = Spacing.xxs),
            // The cards are all one height; a smaller leading item sits level with their middle.
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.focusRestorer(restoreFocus ?: firstItemFocus),
        ) {
            if (leading != null) item(key = LEADING_KEY) { leading(placed(0)) }
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                itemContent(item, placed(index + offset))
            }
        }
    }
}

/** The leading item's key: no card's key (a source and an id) looks like it. */
private const val LEADING_KEY = "homeRow:leading"

/**
 * The Menu key on a Home card opens its actions, as long-press OK does (row actions, CONTRIBUTING.md →
 * D-Pad & Focus). While an Undo bar shows, the screen's `undoOnRemoteKeys` takes Menu and a held OK first.
 */
internal fun Modifier.openActionsOnMenuKey(onOpenActions: () -> Unit): Modifier =
    onKeyEvent { event ->
        val opens = event.type == KeyEventType.KeyDown && event.key == Key.Menu
        if (opens) onOpenActions()
        opens
    }
