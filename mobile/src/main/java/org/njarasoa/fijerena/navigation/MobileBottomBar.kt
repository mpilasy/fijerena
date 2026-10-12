package org.njarasoa.fijerena.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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

    // Not a section: Search over everything, after the sections whenever the bar shows.
    // [contentType] matches no section.
    SEARCH(Screen.SearchTab, "SEARCH"),

    // Not a section: Settings with what it opens (sources, guide sources, Live sync…), last on the
    // bar whenever the bar shows. [contentType] matches no section.
    SETTINGS(Screen.Settings, "SETTINGS"),
    ;

    /** A section of the source (Live TV, Movies, TV Shows), as opposed to Search and Settings. */
    val isSection: Boolean get() = this != SEARCH && this != SETTINGS

    companion object {
        /** The tab [destination] is the root of; null for every other screen, which hides the bar. */
        fun rootedAt(destination: NavDestination?): MobileTab? =
            when {
                destination == null -> null
                destination.hasRoute<Screen.LiveTvTab>() -> LIVE_TV
                destination.hasRoute<Screen.MoviesTab>() -> MOVIES
                destination.hasRoute<Screen.TvShowsTab>() -> TV_SHOWS
                destination.hasRoute<Screen.SearchTab>() -> SEARCH
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
 * The tabs the bar shows: the sections the active source has, then Search and Settings — none, so no bar,
 * when the source has no section yet. [supportedTypes] is null until the source is resolved, and
 * stays null while a Jellyfin source waits for its sign-in: there is no library to open yet. A
 * single section still shows the bar, for Search and Settings.
 */
fun visibleTabs(supportedTypes: Collection<String>?): List<MobileTab> {
    val sections = MobileTab.entries.filter { it.isSection && supportedTypes != null && it.contentType in supportedTypes }
    return if (sections.isEmpty()) emptyList() else sections + MobileTab.SEARCH + MobileTab.SETTINGS
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

/** Narrowest screen, in dp, whose bottom bar names its tabs; narrower ones show icons only. */
const val TAB_LABELS_MIN_WIDTH_DP = 380

/** Whether the bottom bar names its tabs on a screen [widthDp] wide (a 360 dp compact phone doesn't). */
fun showsTabLabels(widthDp: Int): Boolean = widthDp >= TAB_LABELS_MIN_WIDTH_DP

/**
 * Material's navigation bar, [selected] highlighted: icon with its name under it on every tab, or
 * icons only on a narrow screen ([showsTabLabels], read live so it follows rotation) — the name
 * then shows in a tooltip on long-press and is the icon's content description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBottomBar(
    tabs: List<MobileTab>,
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
) {
    val showLabels = showsTabLabels(LocalConfiguration.current.screenWidthDp)
    NavigationBar {
        tabs.forEach { tab ->
            val label = tab.label()
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    if (showLabels) {
                        Icon(tab.icon(), contentDescription = null)
                    } else {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(label) } },
                            state = rememberTooltipState(),
                        ) {
                            Icon(tab.icon(), contentDescription = label)
                        }
                    }
                },
                label = if (showLabels) ({ Text(label) }) else null,
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
        MobileTab.SEARCH -> CinemaIcons.Search
        MobileTab.SETTINGS -> CinemaIcons.Settings
    }

@Composable
fun MobileTab.label(): String =
    when (this) {
        MobileTab.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        MobileTab.MOVIES -> stringResource(R.string.provider_movies_label)
        MobileTab.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        MobileTab.SEARCH -> stringResource(R.string.common_search)
        MobileTab.SETTINGS -> stringResource(R.string.settings_title)
    }
