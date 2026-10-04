package org.njarasoa.fijerena.core.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.navigation.popUpToEntry
import org.njarasoa.fijerena.core.navigation.sectionRootIndex
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R

/**
 * The section-root button a screen shows (D4, docs/plans/20261003_sources-guide-profiles-plan.md
 * → P6): [label] names where it leads (Movies, Settings…), [onClick] pops back to it. Screens take
 * it as `sectionRoot: SectionRoot?`, null = no button.
 */
@Immutable
data class SectionRoot(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The section-root button for the screen in [entry]: shown when it is
 * [org.njarasoa.fijerena.core.navigation.HOME_BUTTON_MIN_DEPTH] or more entries above Home, leading
 * to the first entry above Home. Nav hosts pass it to every screen that can sit that deep: details,
 * episodes, category lists, Search, TV Guide, Search the guide, guide sources. Never to the player;
 * the Live TV preview layer (TV) and full screen (mobile) don't draw it.
 */
@SuppressLint("RestrictedApi") // currentBackStack: see popUpToEntry.
@Composable
fun sectionRootFor(
    navController: NavController,
    entry: NavBackStackEntry,
): SectionRoot? {
    val stack by navController.currentBackStack.collectAsStateWithLifecycle()
    // Up to and including this screen's entry: the stack as this screen sees it, also while a
    // screen pushed above it is entering. Empty once the entry is popped (its exit transition).
    val upToEntry = stack.subList(0, stack.indexOf(entry) + 1)
    val root =
        sectionRootIndex(upToEntry) { it.destination.hasRoute<Screen.ContentTypeSelection>() }
            ?.let { upToEntry[it] }
    val label = root?.let { sectionRootLabel(it) }
    return if (root != null && label != null) SectionRoot(label) { navController.popUpToEntry(root) } else null
}

/** The root's name as its own screen shows it; null for a destination that is never a root. */
@Composable
private fun sectionRootLabel(root: NavBackStackEntry): String? {
    val destination = root.destination
    return when {
        destination.hasRoute<Screen.CategoryList>() -> {
            when (root.toRoute<Screen.CategoryList>().contentType) {
                ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
                ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
                ContentType.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
                else -> null
            }
        }

        destination.hasRoute<Screen.Search>() -> {
            stringResource(R.string.common_search)
        }

        destination.hasRoute<Screen.Settings>() -> {
            stringResource(R.string.settings_title)
        }

        destination.hasRoute<Screen.EpgGuide>() -> {
            stringResource(R.string.common_tv_guide)
        }

        destination.hasRoute<Screen.EpgBrowser>() -> {
            stringResource(R.string.epg_browser_title)
        }

        destination.hasRoute<Screen.ProviderSelection>() -> {
            stringResource(R.string.provider_selection_title)
        }

        destination.hasRoute<Screen.AddProvider>() -> {
            stringResource(R.string.provider_edit_title)
        }

        destination.hasRoute<Screen.EpgManagement>() -> {
            stringResource(R.string.epg_sources_header)
        }

        // Home's Continue Watching opens a title straight away: the title is the section.
        destination.hasRoute<Screen.MovieDetails>() -> {
            parseDisplayTitle(root.toRoute<Screen.MovieDetails>().movieName).title
        }

        destination.hasRoute<Screen.EpisodeSelection>() -> {
            parseDisplayTitle(root.toRoute<Screen.EpisodeSelection>().seriesName).title
        }

        // The player, profiles, Live sync, Diagnostics: nothing goes three screens deeper.
        else -> {
            null
        }
    }
}
