package org.njarasoa.fijerena.core.navigation

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * The section-root button (docs/plans/20261003_sources-guide-profiles-plan.md → D4, P6) shows on a
 * screen this many back-stack entries above Home ([Screen.ContentTypeSelection]) or more.
 */
const val HOME_BUTTON_MIN_DEPTH = 4

/**
 * Where the section-root button of the last entry of [stack] (bottom first, the asking screen
 * last) leads: the index of its section root, the entry directly above Home — Movies, TV Shows,
 * Live TV, Search, Settings… Null when the button is hidden: no Home on the stack (Settings as the
 * start destination), or the screen fewer than [HOME_BUTTON_MIN_DEPTH] entries above it. Entries
 * below Home (the nav graph's own) don't count.
 */
fun <T> sectionRootIndex(
    stack: List<T>,
    isHome: (T) -> Boolean,
): Int? {
    val home = stack.indexOfLast(isHome)
    return if (home >= 0 && stack.lastIndex - home >= HOME_BUTTON_MIN_DEPTH) home + 1 else null
}

/**
 * Pops one entry at a time until [root] is on top. Not `popBackStack<Screen.CategoryList>()`: that
 * stops at the nearest of several category lists (Movies → film → its category → …), not the
 * section's first. Popping keeps [root]'s saved state (category, scroll, search results). Ignored
 * while the current screen is still entering, as [navigateOnce] does, and when [root] has left
 * the stack.
 */
// currentBackStack is restricted to the navigation library group, but it is the only view of the
// whole stack.
@SuppressLint("RestrictedApi")
fun NavController.popUpToEntry(root: NavBackStackEntry) {
    val current = currentBackStackEntry
    if (current?.lifecycle?.currentState == Lifecycle.State.RESUMED && root in currentBackStack.value) {
        while (currentBackStackEntry != root && popBackStack()) Unit
    }
}
