package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The tabs the bar shows: the profile's merged sections (its Xtream sources', multi-source plan
 * Phase 6), then Search and Settings — none, so no bar, when it has no section (no Xtream source:
 * the tab roots show the sources panel). [supportedTypes] is null until the sources are read. A
 * single section still shows the bar, for Search and Settings.
 */
fun visibleTabs(supportedTypes: Collection<String>?): List<MobileTab> {
    val sections = MobileTab.entries.filter { it.isSection && supportedTypes != null && it.contentType in supportedTypes }
    return if (sections.isEmpty()) emptyList() else sections + MobileTab.SEARCH + MobileTab.SETTINGS
}

/**
 * The tab the app opens on: [lastTab] (the section the profile last had open) when its sources have
 * it, else the first they have in bar order — Live TV, Movies, TV Shows. With the sections not
 * known or none ([supportedTypes] null or empty), [lastTab] or Live TV.
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
                    .clip(MobileDockStyle.barShape)
                    .background(MobileDockStyle.barContainer)
                    .border(MobileDockStyle.barOutlineWidth, MobileDockStyle.barOutline, MobileDockStyle.barShape)
                    .padding(MobileDockStyle.barPadding)
                    .selectableGroup(),
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val label = tab.label()
                val color = if (isSelected) MobileDockStyle.selectedContent else MobileDockStyle.content
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(MobileDockStyle.tabShape)
                            .background(if (isSelected) MobileDockStyle.selectedTabContainer else Color.Transparent)
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                            .padding(vertical = MobileDockStyle.tabVerticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MobileDockStyle.iconToLabel),
                ) {
                    if (showLabels) {
                        Icon(tab.icon(), contentDescription = null, tint = color, modifier = Modifier.size(MobileDockStyle.iconSize))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(label) } },
                            state = rememberTooltipState(),
                        ) {
                            Icon(tab.icon(), contentDescription = label, tint = color, modifier = Modifier.size(MobileDockStyle.iconSize))
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
