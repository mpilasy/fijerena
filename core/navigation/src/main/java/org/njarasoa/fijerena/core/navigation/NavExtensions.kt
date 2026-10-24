package org.njarasoa.fijerena.core.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.toRoute

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

/**
 * Opens [route] as the only Search on the back stack: an older one, and what it opened, is dropped
 * first, so Back never lands on a search its query and results have since replaced. As
 * [navigateOnce], ignored while the current screen is leaving.
 */
fun NavController.navigateToSearch(route: Screen.Search) {
    val currentEntry = currentBackStackEntry
    if (currentEntry == null || currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        dropSearches()
        navigate(route)
    }
}

/** Drops every Search on the back stack and the screens above it. */
fun NavController.dropSearches() {
    while (popBackStack<Screen.Search>(inclusive = true)) Unit
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

/**
 * Add Source for a new source ([Screen.AddProvider] without an `editId`). Only an Expert profile
 * may open it: a PIN alone is not enough, because a profile without a PIN has nothing to ask.
 * Edit mode stays open to all, for the Browse sign-in (a Jellyfin login is per profile).
 */
fun NavBackStackEntry?.isNewSource(): Boolean =
    this != null && destination.hasRoute<Screen.AddProvider>() && toRoute<Screen.AddProvider>().editId < 0

/**
 * The screens Settings opens that the PIN of the profile in use guards — sources, guide sources,
 * live sync, a profile's page, Parental controls. Settings itself, About's device info and Diagnostics (read-only) open freely;
 * Settings asks the PIN before what changes shared setup.
 */
fun NavDestination?.isPinGuarded(): Boolean =
    this != null &&
        (
            hasRoute<Screen.ProviderSelection>() ||
                hasRoute<Screen.AddProvider>() ||
                hasRoute<Screen.ProfileEdit>() ||
                hasRoute<Screen.EpgManagement>() ||
                hasRoute<Screen.SyncSettings>() ||
                hasRoute<Screen.ParentalControls>() ||
                hasRoute<Screen.ParentalListEdit>() ||
                hasRoute<Screen.ParentalCheck>()
        )
