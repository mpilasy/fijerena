package org.njarasoa.fijerena.ui.components.rail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * The TV navigation rail's shared interface (docs/plans/20261008_tv-nav-rail-plan.md, Phase 0).
 *
 * - The nav host owns one [TvNavRailState], provides it as [LocalTvNavRail] around the NavHost and
 *   draws `TvNavRail` over the content's left margin.
 * - A screen's leftmost focusable item takes [leftToRail]: Left from it goes to the rail, and the
 *   rail's Right / Back come back to it.
 * - A screen that must not show the rail (player, the Live TV preview layer, first-run screens)
 *   calls [HideTvNavRail] while it is composed.
 * - A section's first screen calls [TvNavRailState.focusRail] on Back instead of leaving.
 *
 * Without a provider (previews, tests) [LocalTvNavRail] is null and all of this does nothing.
 */
enum class RailItem {
    PROFILE,
    HOME,
    LIVE_TV,
    MOVIES,
    TV_SHOWS,
    SEARCH,
    SETTINGS,
}

@Stable
class TvNavRailState {
    /** Attached to the rail's item for [current]: focus enters the rail there. */
    val entry = FocusRequester()

    /** The rail item the nav host says the user is in (lit at rest), or null. */
    var current by mutableStateOf<RailItem?>(null)

    /** Whether focus is inside the rail (it is slid out). Set by the rail itself. */
    var expanded by mutableStateOf(false)

    /**
     * The active profile's name and colour, for [RailItem.PROFILE]'s label, content description and
     * avatar colour. Optional: without them the rail labels the avatar with its initial and draws it
     * in the first profile colour.
     */
    var profileName by mutableStateOf<String?>(null)
    var profileColorIndex by mutableIntStateOf(0)

    private var hideCount by mutableIntStateOf(0)

    /** False while any screen holds [HideTvNavRail]. */
    val visible: Boolean get() = hideCount == 0

    /**
     * The left-edge item focus came from, set by [leftToRail] as such an item takes focus. The
     * rail's Right / Back return here; null or detached → the rail moves focus Right instead.
     */
    var returnTo: FocusRequester? = null
        internal set

    /** Moves focus into the rail, at [current]. Returns whether it took. */
    fun focusRail(): Boolean = visible && entry.requestFocus(FocusDirection.Enter)

    internal fun hide() {
        hideCount++
    }

    internal fun show() {
        hideCount = (hideCount - 1).coerceAtLeast(0)
    }
}

val LocalTvNavRail = staticCompositionLocalOf<TvNavRailState?> { null }

/**
 * For a screen's leftmost focusable item: Left goes to the rail (when there is one and it shows),
 * and the rail returns here on Right / Back. Other directions are untouched.
 */
fun Modifier.leftToRail(): Modifier =
    composed {
        val rail = LocalTvNavRail.current ?: return@composed Modifier
        val self = remember { FocusRequester() }
        this
            .focusRequester(self)
            .onFocusChanged { if (it.isFocused || it.hasFocus) rail.returnTo = self }
            .focusProperties { if (rail.visible) left = rail.entry }
    }

/** Hides the rail while this is composed (player, Live TV preview, first-run screens). */
@Composable
fun HideTvNavRail() {
    val rail = LocalTvNavRail.current ?: return
    DisposableEffect(rail) {
        rail.hide()
        onDispose { rail.show() }
    }
}
