package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Hands focus back to the control that navigated away from a screen once Back returns to it.
 *
 * Navigation Compose disposes a destination while another one is on top and rebuilds it on Back:
 * every `remember` and FocusRequester is new, nothing is focused, and Compose gives focus to the
 * first focusable on the screen — the "Switch Source" chip on Home, the top card in Settings,
 * scrolling a long list back to its top on the way. What survives the trip is saveable state,
 * kept in the back-stack entry, so this remembers the control by a string key with
 * `rememberSaveable` and hands focus back to it when the screen is RESUMED again.
 *
 * ```
 * val returnFocus = rememberNavReturnFocus()
 * CinemaIconButton(
 *     onClick = { returnFocus.leaveFrom("settings"); onSettings() },
 *     modifier = Modifier.navReturnFocusTarget(returnFocus, "settings"),
 * )
 * NavReturnFocusEffect(returnFocus, listState = listState)
 * ```
 *
 * One-shot: the key is cleared after the hand-back, whether or not it landed, so later
 * recompositions and resumes leave the user's focus alone.
 */
@Stable
class NavReturnFocus internal constructor(
    initialKey: String?,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
) {
    /** The control to give focus back to, or null when there is nothing to hand back. */
    var key: String? by mutableStateOf(initialKey)
        private set

    /**
     * True for the whole life of a composition rebuilt by Back with a hand-back pending. A screen
     * whose first-open focus or scroll can fire again later (data arriving after the hand-back)
     * reads this rather than [key], which is already cleared by then.
     */
    val isReturn: Boolean = initialKey != null

    internal val requester = FocusRequester()

    private var scrollIndex = initialScrollIndex
    private var scrollOffset = initialScrollOffset

    /**
     * Records [key] as the control navigating away, and [listState]'s position at that moment:
     * on the way back Compose's own first focus may scroll the list before the hand-back runs.
     * Call from the control's onClick, before navigating.
     */
    fun leaveFrom(
        key: String,
        listState: LazyListState? = null,
    ) {
        this.key = key
        scrollIndex = listState?.firstVisibleItemIndex ?: -1
        scrollOffset = listState?.firstVisibleItemScrollOffset ?: 0
    }

    /**
     * The requester to attach to the control navigating away as [key], or null when Back is not
     * returning to it — for a control inside a child composable, which takes it as a nullable
     * FocusRequester parameter.
     */
    fun requesterFor(key: String): FocusRequester? = if (this.key == key) requester else null

    internal suspend fun restoreScroll(listState: LazyListState) {
        if (scrollIndex >= 0) listState.scrollToItem(scrollIndex, scrollOffset)
    }

    internal fun clear() {
        key = null
        scrollIndex = -1
    }

    companion object {
        val Saver: Saver<NavReturnFocus, List<Any>> =
            Saver(
                save = { state -> state.key?.let { listOf(it, state.scrollIndex, state.scrollOffset) } },
                restore = { saved -> NavReturnFocus(saved[0] as String, saved[1] as Int, saved[2] as Int) },
            )
    }
}

/** A [NavReturnFocus] that survives the navigation round trip (and an activity recreate). */
@Composable
fun rememberNavReturnFocus(): NavReturnFocus = rememberSaveable(saver = NavReturnFocus.Saver) { NavReturnFocus(null, -1, 0) }

/** Marks this control as the one Back hands focus to when it is the one that navigated away as [key]. */
fun Modifier.navReturnFocusTarget(
    returnFocus: NavReturnFocus,
    key: String,
): Modifier = returnFocus.requesterFor(key)?.let { focusRequester(it) } ?: this

/**
 * Gives focus back to the [navReturnFocusTarget] whose key [NavReturnFocus.leaveFrom] recorded,
 * each time the screen is RESUMED with one pending — that is, after Back, once the pop transition
 * has finished, so it lands after Compose's own first focus and the screen's first-open effects.
 *
 * [listState] is scrolled back to where it was when the user left; [prepare] runs next, for
 * whatever else has to happen before the target can attach (wait for data, scroll an inner row).
 * [fallback] takes focus when the target never attaches (the item is gone).
 */
@Composable
fun NavReturnFocusEffect(
    returnFocus: NavReturnFocus,
    listState: LazyListState? = null,
    fallback: FocusRequester? = null,
    prepare: suspend (key: String) -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentListState by rememberUpdatedState(listState)
    val currentFallback by rememberUpdatedState(fallback)
    val currentPrepare by rememberUpdatedState(prepare)
    LaunchedEffect(returnFocus, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val key = returnFocus.key
            if (key != null) {
                currentListState?.let { returnFocus.restoreScroll(it) }
                currentPrepare(key)
                returnFocus.requester.requestFocusWithRetry(fallback = currentFallback)
                returnFocus.clear()
            }
        }
    }
}
