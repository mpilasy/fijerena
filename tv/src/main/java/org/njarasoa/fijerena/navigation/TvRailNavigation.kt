package org.njarasoa.fijerena.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.dropSearches
import org.njarasoa.fijerena.core.navigation.navigateOnce
import org.njarasoa.fijerena.core.navigation.navigateToSearch
import org.njarasoa.fijerena.ui.components.rail.RailItem

/** Set on Live TV's root entry when a rail pick leaves it: see [closeSavedLivePreview]. */
internal const val CLOSE_SAVED_LIVE_PREVIEW = "closeSavedLivePreview"

/** Set on a section's root entry when it is entered again on its browse: see [openBrowseOnRecent]. */
internal const val OPEN_ON_RECENT = "openOnRecent"

/** Set on Home's entry when the rail opens it: see [openHomeOnFirstCard]. */
internal const val OPEN_HOME_ON_FIRST_CARD = "openHomeOnFirstCard"

/** Settings and its screens on top aren't a section's place: dropped before a pick, not saved. */
internal fun NavController.popSettingsTree() {
    while (currentDestination?.isSettingsTree() == true &&
        previousBackStackEntry != null &&
        popBackStack()
    ) {
        Unit
    }
}

/**
 * Search (with what it opened) and the Settings tree aren't a section's place either, wherever
 * they are on the back stack: Search opened over Settings, Settings opened over Search. A section
 * or Home pick drops them first, so a pop never lands on them and a saved section never brings
 * them back. Returns whether it dropped a screen.
 */
internal fun NavController.dropSearchAndSettings(): Boolean {
    val before = currentBackStackEntry
    dropSearches()
    popSettingsTree()
    return currentBackStackEntry != before
}

/**
 * Leaving Live TV from under the TV Guide its full screen opened: its preview layer, saved open,
 * stays closed when the section comes back (TvCategoryGridScreen's closeSavedLivePreview).
 * Nothing to do with Live TV's browse on top: the layer is closed while the rail shows.
 */
internal fun NavController.closeSavedLivePreview() {
    if (currentDestination?.hasRoute<Screen.LiveTvTab>() == true) return
    try {
        getBackStackEntry(Screen.LiveTvTab).savedStateHandle[CLOSE_SAVED_LIVE_PREVIEW] = true
    } catch (_: IllegalArgumentException) {
        // Live TV isn't on the back stack.
    }
}

/**
 * A section entered again comes back on Recent (the tv browse tabs plan), when it comes back on
 * its browse: one that comes back on a title keeps the list under it, so Back lands on the row.
 */
internal fun NavController.openBrowseOnRecent(section: TvSection) {
    val entry = currentBackStackEntry ?: return
    if (entry.destination.hasRoute(section.route::class)) entry.savedStateHandle[OPEN_ON_RECENT] = true
}

/**
 * Home opened from the rail opens on its first card, not on the card the user last opened from
 * it (Back from that card's screen still returns to the card).
 */
internal fun NavController.openHomeOnFirstCard() {
    if (currentDestination?.hasRoute<Screen.ContentTypeSelection>() == true) return
    runCatching { getBackStackEntry(Screen.ContentTypeSelection) }
        .getOrNull()
        ?.savedStateHandle
        ?.set(OPEN_HOME_ON_FIRST_CARD, true)
}

/**
 * Each section keeps its place: the section's stack above Home is saved under its root, and the
 * section picked comes back where it was left, or opens fresh. Home stays the start and is never
 * popped. What Home opened outside the sections (Search, a title) isn't a place: not saved.
 */
internal fun NavController.showSection(section: TvSection) {
    dropSearchAndSettings()
    closeSavedLivePreview()
    val saveSection = currentSection() != null
    navigate(section.route) {
        popUpTo(Screen.ContentTypeSelection) { saveState = saveSection }
        launchSingleTop = true
        restoreState = true
    }
    openBrowseOnRecent(section)
}

/**
 * The rail's picks (the tv nav rail plan → Phase 0 findings → Navigation rules). [current] is the
 * item lit on the rail; [openSection] opens a section that is not the one on the back stack
 * ([showSection], after what Live TV loads first).
 */
internal fun NavController.selectRailItem(
    item: RailItem,
    current: RailItem?,
    openSection: (TvSection) -> Unit,
) {
    val section = TvSection.of(item)
    when {
        item == RailItem.PROFILE -> {
            navigateOnce(Screen.ProfilePicker)
        }

        item == RailItem.HOME -> {
            dropSearchAndSettings()
            closeSavedLivePreview()
            openHomeOnFirstCard()
            popBackStack(
                Screen.ContentTypeSelection,
                inclusive = false,
                saveState = currentSection() != null,
            )
        }

        // Search over everything, the current section's type chosen (railSearch).
        item == RailItem.SEARCH -> {
            if (current != RailItem.SEARCH) {
                // The only Search: an older one, and what it opened, goes first.
                navigateToSearch(railSearch(current))
            }
        }

        // On top of where you are, or back to its start from one of its screens.
        item == RailItem.SETTINGS -> {
            if (!popBackStack(Screen.Settings, inclusive = false)) {
                navigate(Screen.Settings) { launchSingleTop = true }
            }
        }

        // From Settings or Search opened over a section, that section's item goes back to where
        // you were in it — the film, not the section's start. Both go, also when one was opened
        // over the other: popping only the top one showed the other (TV walk C2). Another
        // screen lit elsewhere (a team's page) is popped, as before.
        section != null && section == currentSection() && current != item -> {
            if (!dropSearchAndSettings()) popBackStack()
        }

        // The section you are in: back to its start, on Recent. Already there, nothing changes.
        section != null && section == currentSection() -> {
            closeSavedLivePreview()
            if (popBackStack(section.route, inclusive = false)) openBrowseOnRecent(section)
        }

        section != null -> {
            openSection(section)
        }
    }
}
