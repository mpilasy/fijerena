package org.njarasoa.fijerena.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The phone's bottom bar tabs (docs/plans/archive/20261007_phone-home-overhaul-plan.md → Redesign: no Home
 * on the phone), in bar order. [route] is the tab's root destination; [contentType] the section it
 * opens.
 */
enum class MobileTab(
    val route: Screen,
    val contentType: String,
) {
    LIVE_TV(Screen.LiveTvTab, ContentType.LIVE_TV),
    MOVIES(Screen.MoviesTab, ContentType.MOVIES),
    TV_SHOWS(Screen.TvShowsTab, ContentType.TV_SHOWS),
    ;

    companion object {
        /** The tab [destination] is the root of; null for every other screen, which hides the bar. */
        fun rootedAt(destination: NavDestination?): MobileTab? =
            when {
                destination == null -> null
                destination.hasRoute<Screen.LiveTvTab>() -> LIVE_TV
                destination.hasRoute<Screen.MoviesTab>() -> MOVIES
                destination.hasRoute<Screen.TvShowsTab>() -> TV_SHOWS
                else -> null
            }
    }
}

/**
 * The tab whose stack is on the back stack: its root is the one tab entry there (one tab's stack at
 * a time). Asked per tab with `getBackStackEntry`, which throws when the route isn't on the stack —
 * `currentBackStack` would say it in one read, but it is restricted API.
 */
fun NavController.currentTab(): MobileTab? =
    MobileTab.entries.firstOrNull { tab ->
        try {
            getBackStackEntry(tab.route)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

/** Screens inside a tab that still hide the bar: the player, and Settings with what it opens. */
fun hidesBottomBar(destination: NavDestination?): Boolean =
    destination != null &&
        (
            destination.hasRoute<Screen.Player>() ||
                destination.hasRoute<Screen.Settings>() ||
                destination.hasRoute<Screen.AddProvider>() ||
                destination.hasRoute<Screen.ProviderSelection>() ||
                destination.hasRoute<Screen.ProfileEdit>() ||
                destination.hasRoute<Screen.SyncSettings>() ||
                destination.hasRoute<Screen.Diagnostics>() ||
                destination.hasRoute<Screen.DeviceInfo>()
        )

/**
 * The tabs the bar shows: the sections the active source has — none, so no bar, when it has only
 * one. [supportedTypes] is null until the source is resolved, and stays null while a Jellyfin
 * source waits for its sign-in: there is no library to open yet.
 */
fun visibleTabs(supportedTypes: Collection<String>?): List<MobileTab> =
    MobileTab.entries
        .filter { supportedTypes != null && it.contentType in supportedTypes }
        .takeIf { it.size > 1 }
        .orEmpty()

/**
 * The tab the app opens on: [lastTab] (the section the profile last had open) when the source has
 * it, else the first the source has in bar order — Live TV, Movies, TV Shows. With the source's
 * sections not known ([supportedTypes] null or empty), [lastTab] or Live TV.
 */
fun startTab(
    lastTab: String?,
    supportedTypes: Collection<String>?,
): MobileTab {
    val candidates =
        if (supportedTypes.isNullOrEmpty()) MobileTab.entries else MobileTab.entries.filter { it.contentType in supportedTypes }
    return candidates.firstOrNull { it.contentType == lastTab } ?: candidates.first()
}

/** Material's navigation bar: icon with its name under it on every tab, [selected] highlighted. */
@Composable
fun MobileBottomBar(
    tabs: List<MobileTab>,
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.label()) },
            )
        }
    }
}

@Composable
private fun MobileTab.icon(): ImageVector =
    when (this) {
        MobileTab.LIVE_TV -> CinemaIcons.LiveTv
        MobileTab.MOVIES -> CinemaIcons.Movie
        MobileTab.TV_SHOWS -> CinemaIcons.Tv
    }

@Composable
fun MobileTab.label(): String =
    when (this) {
        MobileTab.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        MobileTab.MOVIES -> stringResource(R.string.provider_movies_label)
        MobileTab.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
    }
