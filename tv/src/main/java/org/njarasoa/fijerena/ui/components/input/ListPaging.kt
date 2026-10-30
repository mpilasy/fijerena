package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * CH+ / CH- and Page Up / Page Down in a long TV list (UX-D9): focus moves one screen of rows up or
 * down, and the list scrolls the same screen, so the focused row stays where it was on the screen
 * and never leaves the view. Put it on the pane that holds the list's `LazyColumn` (outside the
 * column: the column's own scrolling took Page Down and left focus behind, off screen). [keyAt]
 * gives the row key at an index (the key `paneItem` registers), or null for a row that never takes
 * focus (a separator heading). The full-screen player's zap does not pass here: focus is not in a
 * list there.
 */
@Composable
fun Modifier.pageKeys(
    listState: LazyListState,
    pane: PaneFocusState,
    keyAt: (index: Int) -> String?,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentKeyAt by rememberUpdatedState(keyAt)
    // Not read in composition: a plain holder, so a page write recomposes nothing.
    val running = remember { JobHolder() }
    return onPreviewKeyEvent { event ->
        val direction = pageDirection(event.key) ?: return@onPreviewKeyEvent false
        // A page still landing takes the key: its row may not hold focus yet, and a key let
        // through then went to the list's own scrolling, which left focus behind (a fast burst).
        if (running.job?.isActive == true) return@onPreviewKeyEvent true
        val layout = listState.layoutInfo
        val focused = layout.visibleItemsInfo.firstOrNull { it.key == pane.rememberedKey }
        // Focus is not on a row of this list (an empty state's button): the key is not ours.
        if (focused == null) return@onPreviewKeyEvent false
        // A held key pages again on each repeat, once the last page has landed.
        if (event.type == KeyEventType.KeyDown) {
            val visible = layout.visibleItemsInfo.map { it.offset to it.size }
            val page = wholeRowsInView(visible, layout.viewportStartOffset, layout.viewportEndOffset)
            val target = pageTarget(focused.index, direction, page, layout.totalItemsCount) { currentKeyAt(it) != null }
            val key = target?.let { currentKeyAt(it) }
            if (target != null && key != null) {
                running.job =
                    scope.launch {
                        // The list moves the same screen, then the row there takes focus.
                        val first = listState.firstVisibleItemIndex + direction * page
                        listState.scrollToItem(first.coerceIn(0, layout.totalItemsCount - 1))
                        withFrameNanos { }
                        pane.focusKey(key)
                        // The page has landed when its row holds focus (a few frames at most).
                        repeat(LANDING_FRAMES) {
                            if (pane.rememberedKey == key) return@launch
                            withFrameNanos { }
                        }
                    }
            }
        }
        // Up as well as Down: the list's own scrolling never sees these keys.
        true
    }
}

// How many frames a page waits for its row to take focus before the next key may page again.
private const val LANDING_FRAMES = 30

/** The page move under way, if any. */
private class JobHolder {
    var job: Job? = null
}

/** +1 for a key that pages down, -1 for one that pages up, null for any other key. */
internal fun pageDirection(key: Key): Int? =
    when (key) {
        Key.ChannelDown, Key.PageDown -> 1
        Key.ChannelUp, Key.PageUp -> -1
        else -> null
    }

/**
 * How many rows one page moves: the rows wholly in the viewport ([rows]: each row's offset and size,
 * the viewport from [viewportStart] to [viewportEnd]); at least one.
 */
internal fun wholeRowsInView(
    rows: List<Pair<Int, Int>>,
    viewportStart: Int,
    viewportEnd: Int,
): Int = rows.count { (offset, size) -> offset >= viewportStart && offset + size <= viewportEnd }.coerceAtLeast(1)

/**
 * The row a page key lands on: [page] rows from [focused] in [direction] (+1 down, -1 up), kept
 * inside the [count] rows. A row that cannot take focus ([canFocus] false, a separator) gives way
 * to the next one further on, else to the nearest one back towards [focused]. Null when there is no
 * other row to go to (the first row and Page Up, the last and Page Down).
 */
internal fun pageTarget(
    focused: Int,
    direction: Int,
    page: Int,
    count: Int,
    canFocus: (Int) -> Boolean,
): Int? {
    val last = count - 1
    val raw = (focused + direction * page.coerceAtLeast(1)).coerceIn(0, last.coerceAtLeast(0))
    val further = generateSequence(raw) { it + direction }.takeWhile { it in 0..last }
    val back = generateSequence(raw - direction) { it - direction }.takeWhile { it != focused && it in 0..last }
    val target = (further + back).firstOrNull(canFocus)
    return target?.takeIf { it != focused }
}
