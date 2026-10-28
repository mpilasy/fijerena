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
 * The profile this page shows: the one the route names for an Expert; for any other profile, the
 * page of the profile in use ([activeId]). Only an Expert opens another profile's page (the
 * profile rules plan, Design 5).
 */
fun Screen.ProfileEdit.shownProfileId(
    activeId: String,
    expert: Boolean,
): String = if (expert) profileId else activeId

/**
 * The page of the profile in use ([activeId]), opened by a profile without Expert mode. It holds
 * only that profile's look and playback, so it asks no PIN, as on TV (the settings flow review plan).
 */
fun NavBackStackEntry?.isOwnPageOfNonExpert(
    activeId: String,
    expert: Boolean,
): Boolean =
    !expert &&
        this != null &&
        destination.hasRoute<Screen.ProfileEdit>() &&
        toRoute<Screen.ProfileEdit>().shownProfileId(activeId, expert) == activeId

/**
 * Settings and the screens only Settings opens: a child profile's phone has none of them (the
 * admin settings plan). The Admin screens are [isExpertOnly]'s. Logs ([Screen.Diagnostics]) is one,
 * except over Safe mode, which the caller checks.
 */
fun NavBackStackEntry?.isSettingsArea(): Boolean =
    this?.destination?.let {
        it.hasRoute<Screen.Settings>() ||
            it.hasRoute<Screen.ProfileEdit>() ||
            it.hasRoute<Screen.DeviceInfo>() ||
            it.hasRoute<Screen.Licenses>() ||
            it.hasRoute<Screen.Terms>() ||
            it.hasRoute<Screen.Diagnostics>()
    } == true

/**
 * The household's shared setup — sources (adding or editing one, Jellyfin's sign-in too), guide
 * sources, live sync, Parental controls: an Expert's only, whatever opened it (the profile rules
 * plan, PR-11, PR-12). For any other profile the screen closes at once; an Expert still types its
 * PIN ([isPinGuarded]).
 */
fun NavBackStackEntry?.isExpertOnly(): Boolean =
    this?.destination?.let {
        it.hasRoute<Screen.AddProvider>() ||
            it.hasRoute<Screen.ProviderSelection>() ||
            it.hasRoute<Screen.EpgManagement>() ||
            it.hasRoute<Screen.SyncSettings>() ||
            it.hasRoute<Screen.ParentalControls>() ||
            it.hasRoute<Screen.ParentalListEdit>() ||
            it.hasRoute<Screen.ParentalCheck>()
    } == true

/**
 * The screens Settings opens that the PIN of the profile in use guards — sources, guide sources,
 * live sync, a profile's page, Parental controls. Settings itself, About's device info and Logs (read-only) open freely;
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
