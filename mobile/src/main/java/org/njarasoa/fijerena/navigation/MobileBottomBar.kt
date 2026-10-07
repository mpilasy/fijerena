package org.njarasoa.fijerena.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The phone's bottom bar tabs (docs/plans/20261007_phone-home-overhaul-plan.md → Bottom navigation
 * bar), in bar order. [route] is the tab's root destination; [contentType] the section it opens,
 * null for Home.
 */
enum class MobileTab(
    val route: Screen,
    val contentType: String?,
) {
    HOME(Screen.ContentTypeSelection, null),
    LIVE_TV(Screen.LiveTvTab, ContentType.LIVE_TV),
    MOVIES(Screen.MoviesTab, ContentType.MOVIES),
    TV_SHOWS(Screen.TvShowsTab, ContentType.TV_SHOWS),
    ;

    companion object {
        /** The section tab that opens [contentType]; null for anything else. */
        fun forContentType(contentType: String): MobileTab? = entries.firstOrNull { it.contentType == contentType }

        /** The tab [destination] is the root of; null for every other screen, which hides the bar. */
        fun rootedAt(destination: NavDestination?): MobileTab? =
            when {
                destination == null -> null
                destination.hasRoute<Screen.ContentTypeSelection>() -> HOME
                destination.hasRoute<Screen.LiveTvTab>() -> LIVE_TV
                destination.hasRoute<Screen.MoviesTab>() -> MOVIES
                destination.hasRoute<Screen.TvShowsTab>() -> TV_SHOWS
                else -> null
            }
    }
}

/**
 * The tabs the bar shows: Home, then the sections the active source has. [supportedTypes] is null
 * until Home has resolved the source — and stays null while a Jellyfin source waits for its
 * sign-in, so the section tabs only appear once there is a library to open.
 */
fun visibleTabs(supportedTypes: Collection<String>?): List<MobileTab> =
    MobileTab.entries.filter { tab -> tab.contentType == null || (supportedTypes != null && tab.contentType in supportedTypes) }

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
        MobileTab.HOME -> CinemaIcons.Home
        MobileTab.LIVE_TV -> CinemaIcons.LiveTv
        MobileTab.MOVIES -> CinemaIcons.Movie
        MobileTab.TV_SHOWS -> CinemaIcons.Tv
    }

@Composable
private fun MobileTab.label(): String =
    when (this) {
        MobileTab.HOME -> stringResource(R.string.home_tab_label)
        MobileTab.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        MobileTab.MOVIES -> stringResource(R.string.provider_movies_label)
        MobileTab.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
    }
