package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * The phone's bottom bar tabs (docs/plans/20261019_phone-home-tab-plan.md), in bar order. [route] is
 * the tab's root destination; [contentType] the section it opens.
 */
enum class MobileTab(
    val route: Screen,
    val contentType: String,
) {
    // Not a section: the rows of every section, first whenever the bar shows. [contentType] matches
    // no section.
    HOME(Screen.HomeTab, "HOME"),
    LIVE_TV(Screen.LiveTvTab, ContentType.LIVE_TV),
    MOVIES(Screen.MoviesTab, ContentType.MOVIES),
    TV_SHOWS(Screen.TvShowsTab, ContentType.TV_SHOWS),

    // Not a source's section: the followed teams' and leagues' games (the sports teams plan, SP-D7),
    // after the sections while the profile follows something. [contentType] matches no section.
    SPORTS(Screen.SportsTab, "SPORTS"),

    // Not a section: Settings with what it opens (sources, guide sources, Live sync…). A tab with its
    // own saved stack, but not on the bar: the top bar's profile avatar opens it, through the profile
    // menu. [contentType] matches no section.
    SETTINGS(Screen.Settings, "SETTINGS"),
    ;

    /** A section of the source (Live TV, Movies, TV Shows), as opposed to Home, Sports and Settings. */
    val isSection: Boolean get() = this == LIVE_TV || this == MOVIES || this == TV_SHOWS

    companion object {
        /** The tab [destination] is the root of; null for every other screen, which hides the bar. */
        fun rootedAt(destination: NavDestination?): MobileTab? =
            when {
                destination == null -> null
                destination.hasRoute<Screen.HomeTab>() -> HOME
                destination.hasRoute<Screen.LiveTvTab>() -> LIVE_TV
                destination.hasRoute<Screen.MoviesTab>() -> MOVIES
                destination.hasRoute<Screen.TvShowsTab>() -> TV_SHOWS
                destination.hasRoute<Screen.SportsTab>() -> SPORTS
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
 * The tabs the bar shows: Home, the profile's merged sections (its Xtream sources', multi-source
 * plan Phase 6), then Sports when [sports] (see [showsSportsTab]) — none, so no bar, when it has no
 * section (no Xtream source: the tab roots show the sources panel). [supportedTypes] is null until
 * the sources are read. A single section still shows the bar, for Home. Settings is never on it.
 */
fun visibleTabs(
    supportedTypes: Collection<String>?,
    sports: Boolean = false,
): List<MobileTab> {
    val sections = MobileTab.entries.filter { it.isSection && supportedTypes != null && it.contentType in supportedTypes }
    return if (sections.isEmpty()) {
        emptyList()
    } else {
        listOf(MobileTab.HOME) + sections + listOfNotNull(MobileTab.SPORTS.takeIf { sports })
    }
}

/**
 * Whether the bar has the Sports tab (the sports teams plan, SP-D7): Admin's Sports switch is on
 * ([sportsOn], SP-D9), the profile is not a child ([child], SP-D8), and it follows a team or a
 * league ([follows]) — or the tab is the one open ([inSportsTab]), so unfollowing the last team
 * from inside it does not take the tab away under the user.
 */
fun showsSportsTab(
    sportsOn: Boolean,
    child: Boolean,
    follows: Boolean,
    inSportsTab: Boolean = false,
): Boolean = sportsOn && !child && (follows || inSportsTab)

/**
 * The tab the app opens on: [lastTab] (Home or the section the profile last had open) when its
 * sources have it, else Home. With the sections not known or none ([supportedTypes] null or
 * empty), [lastTab] when it is a section or Home.
 */
fun startTab(
    lastTab: String?,
    supportedTypes: Collection<String>?,
): MobileTab {
    val candidates =
        MobileTab.entries.filter {
            it == MobileTab.HOME || (it.isSection && (supportedTypes.isNullOrEmpty() || it.contentType in supportedTypes))
        }
    return candidates.firstOrNull { it.contentType == lastTab } ?: MobileTab.HOME
}

/** Narrowest screen, in dp, whose bottom bar names its tabs; narrower ones show icons only. */
const val TAB_LABELS_MIN_WIDTH_DP = 380

/** Whether the bottom bar names its tabs on a screen [widthDp] wide (a 360 dp compact phone doesn't). */
fun showsTabLabels(widthDp: Int): Boolean = widthDp >= TAB_LABELS_MIN_WIDTH_DP

/**
 * The phone's bottom bar, as [MobileDockStyle] says it looks: a floating rounded bar, [selected] in
 * a lighter pill in the accent. Each tab is its icon with its name under it, or the icon only on a
 * narrow screen ([showsTabLabels]) — the name then shows in a tooltip on long-press and is the
 * icon's content description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBottomBar(
    tabs: List<MobileTab>,
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLabels = showsTabLabels(LocalConfiguration.current.screenWidthDp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = MobileDockStyle.sideMargin,
                    end = MobileDockStyle.sideMargin,
                    bottom = MobileDockStyle.bottomMargin,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dockBar()
                    .padding(MobileDockStyle.barPadding)
                    .selectableGroup(),
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val color = if (isSelected) MobileDockStyle.selectedContent else MobileDockStyle.content
                val label = tab.label()
                val icon: @Composable (description: String?) -> Unit = { description ->
                    CompositionLocalProvider(LocalContentColor provides color) {
                        CinemaIcon(tab.icon(), contentDescription = description, modifier = Modifier.size(MobileDockStyle.iconSize))
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .dockTab(isSelected)
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                            .padding(vertical = MobileDockStyle.tabVerticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MobileDockStyle.iconToLabel),
                ) {
                    if (showLabels) {
                        icon(null)
                        CompositionLocalProvider(LocalContentColor provides color) {
                            CinemaText(
                                text = label,
                                role = TextRole.Caption,
                                tone = TextTone.Inherit,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    CinemaText(
                                        label,
                                        role = TextRole.BodySmall,
                                        tone = TextTone.Inherit,
                                    )
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            icon(label)
                        }
                    }
                }
            }
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
        MobileTab.SPORTS -> CinemaIcons.Sports
        MobileTab.SETTINGS -> CinemaIcons.Settings
    }

@Composable
fun MobileTab.label(): String =
    when (this) {
        MobileTab.HOME -> stringResource(R.string.home_title)
        MobileTab.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        MobileTab.MOVIES -> stringResource(R.string.provider_movies_label)
        MobileTab.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
        MobileTab.SPORTS -> stringResource(R.string.sports_title)
        MobileTab.SETTINGS -> stringResource(R.string.settings_title)
    }
