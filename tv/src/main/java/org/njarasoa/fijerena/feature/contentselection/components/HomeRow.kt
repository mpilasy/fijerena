@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.FocusDirection
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
 * [leading]: a control in the row's header, after its title (a Trending chart row's switch), so
 * the cards start where every other row's cards start. It takes the rail's Left, Left on the first
 * card goes to it, and Right or Down on it goes to the cards; focus coming into the row from above
 * or below still lands on the cards. With it, the row shows even with no card, and it takes the
 * row's focus then.
 * [emptyText]: said in the cards' place while the row has no card ("None in your sources" on a
 * person's page); not a focus stop, the header's control is.
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
    emptyText: String? = null,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    if (items.isEmpty() && leading == null) return
    val leadingFocus = remember { FocusRequester() }
    val cardsFocus = remember { FocusRequester() }
    val lastIndex = items.size - 1
    // [index]: the card's place in the row.
    val placed: (Int) -> Modifier = { index ->
        Modifier
            .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
            .then(if (index == 0 && leading == null) Modifier.leftToRail() else Modifier)
            // leftToRail is outside these, so it wins while the rail shows; without
            // one, Left on the first card stays put (or goes to the header's control).
            .focusProperties {
                if (index == 0) left = if (leading != null) leadingFocus else FocusRequester.Cancel
                if (index == lastIndex) right = FocusRequester.Cancel
            }
    }
    val leadingModifier =
        Modifier
            .then(if (items.isEmpty()) Modifier.focusRequester(firstItemFocus) else Modifier)
            .focusRequester(leadingFocus)
            .leftToRail()
            .focusProperties {
                left = FocusRequester.Cancel
                if (items.isNotEmpty()) {
                    right = cardsFocus
                    down = cardsFocus
                }
            }
    val rowInView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            Modifier
                .bringIntoViewRequester(rowInView)
                .then(modifier)
                .onFocusChanged { if (it.hasFocus) scope.launch { rowInView.bringIntoView() } }
                .then(
                    if (leading != null && items.isNotEmpty()) {
                        // A D-pad move into the row lands on the cards, not on the header's control,
                        // which is nearer to a move down from the row above.
                        Modifier
                            .focusProperties {
                                onEnter = {
                                    val direction = requestedFocusDirection
                                    val vertical = direction == FocusDirection.Down || direction == FocusDirection.Up
                                    if (vertical && !cardsFocus.requestFocus(FocusDirection.Enter)) cancelFocusChange()
                                }
                            }.focusGroup()
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(bottom = Spacing.sm, start = Spacing.xxs),
        ) {
            CinemaText(
                text = title,
                // The TV section-title style (TV UI audit #2): the same on every Home row and on Search's groups.
                role = TextRole.Title,
                tone = TextTone.Primary,
            )
            leading?.invoke(leadingModifier)
        }
        if (items.isEmpty()) {
            if (emptyText != null) {
                CinemaText(text = emptyText, tone = TextTone.Secondary, modifier = Modifier.padding(start = Spacing.xxs))
            }
        } else {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(horizontal = Spacing.xxs),
                modifier = Modifier.focusRequester(cardsFocus).focusRestorer(restoreFocus ?: firstItemFocus),
            ) {
                itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                    itemContent(item, placed(index))
                }
            }
        }
    }
}

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
