@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * One titled row of cards on Home (TV home overhaul plan, Phase 3). Focus coming in from above or
 * below lands on the card this row had last ([focusRestorer]), else on its first card
 * ([firstItemFocus]); Left on the first card and Right on the last stay put, so the search never
 * falls out of the row onto the header or another row.
 */
@Composable
fun <T> HomeRow(
    title: String,
    items: List<T>,
    key: (T) -> String,
    listState: LazyListState,
    firstItemFocus: FocusRequester,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = CinemaTextPrimary,
            modifier = Modifier.padding(bottom = Spacing.sm, start = Spacing.xxs),
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(horizontal = Spacing.xxs),
            modifier = Modifier.focusRestorer(firstItemFocus),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                itemContent(
                    item,
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .focusProperties {
                            if (index == 0) left = FocusRequester.Cancel
                            if (index == items.lastIndex) right = FocusRequester.Cancel
                        },
                )
            }
        }
    }
}
