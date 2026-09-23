package org.njarasoa.fijerena.core.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Navigates only if the current back stack entry is RESUMED, guarding against a
 * duplicate push when a D-pad auto-repeat or accidental double-click fires two
 * click events during a screen transition. Without this, destinations that create
 * fresh state per push (e.g. [Screen.Player], which instantiates a new player
 * engine) can briefly end up pushed twice.
 */
fun NavController.navigateOnce(route: Any) {
    val currentEntry = currentBackStackEntry
    if (currentEntry == null || currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}

/** As [navigateOnce], for a call site that also needs a [NavOptionsBuilder] (e.g. `popUpTo`, `launchSingleTop`). */
fun NavController.navigateOnce(
    route: Any,
    builder: NavOptionsBuilder.() -> Unit,
) {
    val currentEntry = currentBackStackEntry
    if (currentEntry == null || currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        navigate(route, builder)
    }
}
