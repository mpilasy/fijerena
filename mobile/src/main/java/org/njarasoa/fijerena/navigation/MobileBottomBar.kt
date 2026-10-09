package org.njarasoa.fijerena.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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

    // Not a section: Settings with what it opens (sources, guide sources, Live sync…), last on the
    // bar whenever the bar shows. [contentType] matches no section.
    SETTINGS(Screen.Settings, "SETTINGS"),
    ;

    /** A section of the source (Live TV, Movies, TV Shows), as opposed to Settings. */
    val isSection: Boolean get() = this != SETTINGS

    companion object {
        /** The tab [destination] is the root of; null for every other screen, which hides the bar. */
        fun rootedAt(destination: NavDestination?): MobileTab? =
            when {
                destination == null -> null
                destination.hasRoute<Screen.LiveTvTab>() -> LIVE_TV
                destination.hasRoute<Screen.MoviesTab>() -> MOVIES
                destination.hasRoute<Screen.TvShowsTab>() -> TV_SHOWS
                destination.hasRoute<Screen.Settings>() -> SETTINGS
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

/**
 * Screens inside a tab that still hide the bar: the player, and the two forms (Add / Edit Source,
 * a profile's page), which have their own Save. Settings and what else it opens keep it — Settings
 * is a tab.
 */
fun hidesBottomBar(destination: NavDestination?): Boolean =
    destination != null &&
        (
            destination.hasRoute<Screen.Player>() ||
                destination.hasRoute<Screen.AddProvider>() ||
                destination.hasRoute<Screen.ProfileEdit>()
        )

/**
 * The tabs the bar shows: the sections the active source has, then Settings — none, so no bar,
 * when the source has no section yet. [supportedTypes] is null until the source is resolved, and
 * stays null while a Jellyfin source waits for its sign-in: there is no library to open yet. A
 * single section still shows the bar, for Settings.
 */
fun visibleTabs(supportedTypes: Collection<String>?): List<MobileTab> {
    val sections = MobileTab.entries.filter { it.isSection && supportedTypes != null && it.contentType in supportedTypes }
    return if (sections.isEmpty()) emptyList() else sections + MobileTab.SETTINGS
}

/**
 * The tab the app opens on: [lastTab] (the section the profile last had open) when the source has
 * it, else the first the source has in bar order — Live TV, Movies, TV Shows. With the source's
 * sections not known ([supportedTypes] null or empty), [lastTab] or Live TV.
 */
fun startTab(
    lastTab: String?,
    supportedTypes: Collection<String>?,
): MobileTab {
    val sections = MobileTab.entries.filter { it.isSection }
    val candidates =
        if (supportedTypes.isNullOrEmpty()) sections else sections.filter { it.contentType in supportedTypes }
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
                // The theme's accent for the selected tab, not Material's default (the palette's
                // orange, the same in every theme).
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
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
        MobileTab.SETTINGS -> CinemaIcons.Settings
    }

@Composable
fun MobileTab.label(): String =
    when (this) {
        MobileTab.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        MobileTab.MOVIES -> stringResource(R.string.provider_movies_label)
        MobileTab.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        MobileTab.SETTINGS -> stringResource(R.string.settings_title)
    }
