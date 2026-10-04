package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Focus memory and landing rules for one pane of a two-pane TV screen (a categories column, an
 * items column). Part II P1/P2 of docs/plans/archive/20261003_ux-overhaul-plan.md.
 *
 * ```
 * val categoriesPane = rememberPaneFocus()                       // in the screen, one per pane
 * val itemsPane = rememberPaneFocus()
 * // in each list composable, every composition:
 * itemsPane.bind(selectedKey = lastPlayedId, firstKey = items.firstOrNull()?.id, listState, indexOf = { key -> items.indexOfFirst { it.id == key } })
 * Box(Modifier.tvPane(itemsPane, exitLeft = categoriesPane)) { LazyColumn { items(...) { Card(Modifier.paneItem(itemsPane, item.id)) } } }
 * ```
 *
 * The pane remembers the key of the row that last had focus (`rememberSaveable`, so it survives
 * the Back round trip), and a new selection (the row opened, the channel playing) becomes that
 * memory. A D-pad entry lands on the remembered row, else the selected one, else the first —
 * or, with [bind]'s `preferSelected`, on the selected row first (a categories pane: Left from an
 * item lands on the category being browsed, not the one last scrolled past). A row that is not
 * composed (scrolled away in a `LazyColumn`) is scrolled to first, then focused with
 * [requestFocusWithRetry].
 */
@Stable
class PaneFocusState internal constructor(
    initialRememberedKey: String?,
    initialSelectedKey: String?,
) {
    /** Key of the row that last had focus in this pane; null until one did. */
    var rememberedKey: String? = initialRememberedKey
        internal set

    internal lateinit var scope: CoroutineScope

    private var selectedKey: String? = initialSelectedKey
    private var preferSelected = false
    private var firstKey: String? = null
    private var listState: LazyListState? = null
    private var indexOf: (String) -> Int = { -1 }
    private val requesters = HashMap<String, FocusRequester>()

    /**
     * Tells the pane what it holds. Called by the list composable every time it composes:
     * [selectedKey] is the row the screen considers current (a new value becomes the remembered
     * row), [firstKey] the default landing, [listState] and [indexOf] let the pane scroll a row
     * into composition before focusing it (-1 for a row outside the lazy list, which is then
     * assumed always composed). [preferSelected] lands entries on the selected row before the
     * remembered one.
     */
    fun bind(
        selectedKey: String?,
        firstKey: String?,
        listState: LazyListState?,
        indexOf: (String) -> Int,
        preferSelected: Boolean = false,
    ) {
        if (selectedKey != this.selectedKey) {
            this.selectedKey = selectedKey
            if (selectedKey != null) rememberedKey = selectedKey
        }
        this.firstKey = firstKey
        this.listState = listState
        this.indexOf = indexOf
        this.preferSelected = preferSelected
    }

    /** Where a D-pad entry lands: remembered, else selected (or the reverse), else the first row. */
    fun entryKey(): String? {
        val first = if (preferSelected) selectedKey else rememberedKey
        val second = if (preferSelected) rememberedKey else selectedKey
        return first?.takeIf(::contains) ?: second?.takeIf(::contains) ?: firstKey
    }

    private fun contains(key: String): Boolean = requesters.containsKey(key) || indexOf(key) >= 0

    /**
     * Focuses the row with [key], scrolling it into composition first when needed. Returns whether
     * focus landed. Call from a `LaunchedEffect`.
     */
    suspend fun focusKey(key: String): Boolean {
        var requester = requesters[key]
        if (requester == null) {
            val index = indexOf(key)
            val list = listState
            if (index < 0 || list == null) return false
            list.scrollToItem(index)
            var frames = 0
            while (requester == null && frames < FOCUS_RETRY_MAX_FRAMES) {
                withFrameNanos { }
                frames++
                requester = requesters[key]
            }
        }
        return requester?.requestFocusWithRetry() ?: false
    }

    /**
     * [focusEntry] after the rows under the pane were replaced (another list in the same list
     * state): scrolls the entry row to the top first, since the scroll position belongs to the old
     * rows and could leave the focused row clipped out of view.
     */
    suspend fun focusEntryInNewList(): Boolean {
        val entry = entryKey() ?: return false
        val index = indexOf(entry)
        if (index >= 0) listState?.scrollToItem(index)
        return focusEntry()
    }

    /** Focuses [entryKey], falling back to the first row. Returns whether focus landed. */
    suspend fun focusEntry(): Boolean {
        val entry = entryKey() ?: return false
        if (focusKey(entry)) return true
        val first = firstKey
        return first != null && first != entry && focusKey(first)
    }

    /**
     * A D-pad move is landing in this pane: a composed entry row takes focus right away; one that
     * is not composed is scrolled to and focused a frame or two later, after [enterScope] (when
     * given) cancels the move. Returns false when the pane has nothing to land on.
     */
    internal fun enter(enterScope: FocusEnterExitScope? = null): Boolean {
        val key = entryKey() ?: return false
        val requester = requesters[key]
        if (requester != null && requester.requestFocus(FocusDirection.Enter)) return true
        enterScope?.cancelFocusChange()
        scope.launch { focusEntry() }
        return true
    }

    internal fun register(
        key: String,
        requester: FocusRequester,
    ) {
        requesters[key] = requester
    }

    internal fun unregister(
        key: String,
        requester: FocusRequester,
    ) {
        if (requesters[key] === requester) requesters.remove(key)
    }

    companion object {
        // Keys are never blank, so "" stands in for null: a Bundle can't hold a null list element.
        internal val Saver: Saver<PaneFocusState, List<String>> =
            Saver(
                save = { listOf(it.rememberedKey.orEmpty(), it.selectedKey.orEmpty()) },
                restore = { PaneFocusState(it[0].ifEmpty { null }, it[1].ifEmpty { null }) },
            )
    }
}

/** A [PaneFocusState] whose remembered row survives the Back round trip (and an activity recreate). */
@Composable
fun rememberPaneFocus(): PaneFocusState {
    val state = rememberSaveable(saver = PaneFocusState.Saver) { PaneFocusState(null, null) }
    state.scope = rememberCoroutineScope()
    return state
}

/**
 * Makes this node a pane: a focus group that D-pad moves enter on [PaneFocusState.entryKey] and
 * leave only where the caller says.
 *
 * Left and Right are taken here, not by Compose's geometric search: they go to the neighbour
 * pane ([exitLeft] / [exitRight]), which lands on its own entry row, or nowhere when there is no
 * neighbour on that side. A row's own key handling (the hidden action buttons) runs first and
 * keeps its Left/Right inside the row. Down at the end of the pane stays put; Up at the top
 * leaves to whatever is above (the screen header) unless [exitUp] is false. Programmatic focus
 * requests ([requestFocusWithRetry], `NavReturnFocus`) are not D-pad moves and pass through.
 */
fun Modifier.tvPane(
    state: PaneFocusState,
    exitLeft: PaneFocusState? = null,
    exitRight: PaneFocusState? = null,
    exitUp: Boolean = true,
): Modifier =
    this
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val neighbour =
                when (event.key) {
                    Key.DirectionLeft -> exitLeft
                    Key.DirectionRight -> exitRight
                    else -> return@onKeyEvent false
                }
            // A neighbour with nothing to land on (still loading, empty) leaves the move to
            // Compose, which finds whatever is focusable there (an empty state's button).
            neighbour == null || neighbour.enter()
        }.focusProperties {
            onEnter = {
                if (requestedFocusDirection.isDpadMove) state.enter(this)
            }
            onExit = {
                val direction = requestedFocusDirection
                if (direction == FocusDirection.Down || (direction == FocusDirection.Up && !exitUp)) cancelFocusChange()
            }
        }.focusGroup()

/** Registers this row with [state] under [key] and records it as the pane's remembered row while it has focus. */
@Composable
fun Modifier.paneItem(
    state: PaneFocusState,
    key: String,
): Modifier {
    val requester = remember(state, key) { FocusRequester() }
    DisposableEffect(state, key, requester) {
        state.register(key, requester)
        onDispose { state.unregister(key, requester) }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) state.rememberedKey = key }
}

private val FocusDirection.isDpadMove: Boolean
    get() =
        this == FocusDirection.Left ||
            this == FocusDirection.Right ||
            this == FocusDirection.Up ||
            this == FocusDirection.Down
